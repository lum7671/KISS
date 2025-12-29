# Phase 3.2: Hibernating Apps 검색 우선순위 개선

**생성일**: 2025-12-19  
**우선순위**: 🟡 MEDIUM (사용성 개선)  
**예상 작업 시간**: 2-4시간  
**상태**: 📋 계획 단계

---

## 📋 목차

1. [문제 요약](#문제-요약)
2. [기술적 분석](#기술적-분석)
3. [해결 전략](#해결-전략)
4. [구현 계획](#구현-계획)
5. [테스트 계획](#테스트-계획)
6. [성공 기준](#성공-기준)

---

## 🎯 문제 요약

### 현재 동작

사용자가 Shizuku를 통해 많은 앱을 hibernate하여 백그라운드 실행과 알림을 방지하면서 사용 중입니다. 하지만 검색 시 hibernated 앱들은 -200 relevance penalty를 받아 목록 하단에 표시되어 스크롤이 필요한 상황입니다.

### 사용 사례

```
예시: Facebook 앱
- Hibernate 상태: 백그라운드 실행 차단, 알림 차단
- 사용 빈도: 하루 2-3번 수동 실행
- 현재 문제: 검색 시 하단에 표시되어 스크롤 필요
- 원하는 동작: 자주 사용하므로 상단에 표시
```

### 요구사항

**목표**: 최근 30일간 1번 이상 사용된 hibernated 앱은 일반 앱과 동일한 검색 우선순위를 가져야 함

**Threshold**:
- 기간: 30일
- 최소 사용 횟수: 1회
- (향후 설정 가능하게 확장 고려)

---

## 🔍 기술적 분석

### 1. 현재 Penalty 적용 위치

#### A. QuerySearcherCoroutine.kt (Line 146-158)

**파일**: `app/src/main/java/fr/neamar/kiss/searcher/QuerySearcherCoroutine.kt`

```kotlin
override fun addResults(pojos: List<Pojo>): Boolean {
    val activity = activityWeakReference.get() ?: return false
    
    for (pojo in pojos) {
        if (pojo.isDisabled) {
            // Give penalty for disabled items, these should not be preferred
            pojo.relevance -= 200  // ⚠️ 모든 비활성화 앱에 일괄 적용
        } else {
            // Give a boost if item was previously selected for this query
            val value = knownIds[pojo.id]
            if (value != null) {
                pojo.relevance += 25 * value  // Query history boost
            }
        }
    }
    return super.addResults(pojos)
}
```

#### B. HistorySearcherCoroutine.kt (Line 139)

**파일**: `app/src/main/java/fr/neamar/kiss/searcher/HistorySearcherCoroutine.kt`

```kotlin
// Same logic as QuerySearcherCoroutine
if (pojo.isDisabled) {
    pojo.relevance -= 200
}
```

### 2. Usage Tracking Infrastructure

#### ✅ History Database (이미 존재)

**파일**: `app/src/main/java/fr/neamar/kiss/db/DBHelper.java`

**Schema**:
```sql
CREATE TABLE history (
    _id INTEGER PRIMARY KEY AUTOINCREMENT,
    "query" TEXT,
    record TEXT NOT NULL,           -- App ID (e.g., "app://com.example/...")
    timeStamp INTEGER DEFAULT 0 NOT NULL
);

-- Performance indexes (already exist):
CREATE INDEX idx_history_record ON history(record);
CREATE INDEX idx_history_timestamp ON history(timeStamp DESC);
CREATE INDEX idx_history_record_timestamp ON history(record, timeStamp DESC);
```

**Usage Recording Flow**:
```
User clicks app → MainActivity.launchOccurred()
                → DataHandler.addToHistory()
                → DBHelper.insertHistory()
                → Entry written with System.currentTimeMillis()
```

#### ✅ Existing Query Methods

**파일**: `DBHelper.java`

```java
// By frequency (총 사용 횟수)
public static Cursor getHistoryByFrequency(Context context, int limit)

// By recency (최근 사용)
public static Cursor getHistoryByRecency(Context context, int limit)

// By frecency (frequency + recency)
public static Cursor getHistoryByFrecency(Context context, int limit)

// By adaptive (time-windowed frequency)
public static Cursor getHistoryByAdaptive(Context context, int limit)

// Per-query history
public static HashMap<String, Integer> getHistoryByQuery(Context context, String query)
```

### 3. Hibernation State Management

#### KISS는 Hibernation State를 추적하지 않음

**중요 발견**:
- KISS는 앱을 hibernate할 때 `IActivityManager.forceStopPackage()` 호출
- 하지만 "이 앱이 사용자에 의해 hibernate되었다"는 플래그를 저장하지 않음
- `AppPojo.isDisabled`는 Android 시스템의 disabled/suspended 상태를 반영

**따라서**:
- Hibernation 상태를 직접 추적하는 대신
- **Usage pattern**으로 "자주 사용하는 앱"을 감지
- Disabled 여부와 관계없이 자주 사용하면 우선순위 향상

---

## 🎯 해결 전략

### Option A: Query-time Detection (권장)

검색 실행 시마다 비활성화된 앱의 최근 사용 횟수를 확인하여 penalty 여부 결정

**장점**:
- ✅ 항상 최신 데이터 반영
- ✅ 추가 메모리 사용 없음
- ✅ 구현 간단

**단점**:
- ⚠️ 검색마다 DB query 발생 (성능 고려 필요)

**Performance Mitigation**:
- Batch query로 여러 앱 한 번에 조회
- 검색 세션 중 결과 캐싱
- 기존 `idx_history_record_timestamp` 인덱스 활용

---

### Option B: Pre-computed Cache (성능 우선)

DataHandler에서 주기적으로(예: 6시간마다) 자주 사용하는 앱 목록을 미리 계산

**장점**:
- ✅ 검색 중 DB query 없음
- ✅ 빠른 응답 시간

**단점**:
- ⚠️ 메모리 사용 증가 (HashSet<String>)
- ⚠️ 최대 6시간 지연된 데이터
- ⚠️ 주기적 업데이트 로직 추가 필요

---

### 선택: Option A (Query-time Detection)

**이유**:
1. KISS는 이미 검색 성능이 우수 (~3ms 평균)
2. Hibernated 앱 수가 제한적 (대부분 10-50개)
3. 실시간 반영이 사용자 경험에 더 중요
4. 구현이 간단하고 유지보수 쉬움

---

## 🛠️ 구현 계획

### Phase 3.2.1: DBHelper 사용 횟수 쿼리 메서드 추가 (30분)

#### Task 3.2.1.1: getUsageCountForRecord() 메서드

**파일**: `app/src/main/java/fr/neamar/kiss/db/DBHelper.java`

**위치**: 기존 history 관련 메서드들 뒤 (Line ~280)

```java
/**
 * Get usage count for a specific app in the last N days
 * 
 * @param context Application context
 * @param record App ID (e.g., "app://com.example/...")
 * @param days Number of days to look back
 * @return Number of launches in the time period
 */
public static int getUsageCountForRecord(Context context, String record, int days) {
    SQLiteDatabase db = getDatabase(context);
    long cutoffTime = System.currentTimeMillis() - (days * 24L * 60 * 60 * 1000);
    
    String sql = "SELECT COUNT(*) FROM history " +
                 "WHERE record = ? AND timeStamp > ?";
    
    try (Cursor cursor = db.rawQuery(sql, new String[]{record, String.valueOf(cutoffTime)})) {
        if (cursor.moveToFirst()) {
            return cursor.getInt(0);
        }
    } catch (Exception e) {
        Log.e("DBHelper", "Error counting usage for " + record, e);
    }
    
    return 0;
}
```

**성능 특성**:
- 인덱스 사용: `idx_history_record_timestamp` (이미 존재)
- Time complexity: O(log n) for index lookup
- 예상 실행 시간: < 1ms per query

---

### Phase 3.2.2: QuerySearcherCoroutine Penalty 로직 개선 (45분)

#### Task 3.2.2.1: addResults() 메서드 수정

**파일**: `app/src/main/java/fr/neamar/kiss/searcher/QuerySearcherCoroutine.kt`

**위치**: Line 146-158 (현재 penalty 로직)

**변경 전**:
```kotlin
override fun addResults(pojos: List<Pojo>): Boolean {
    val activity = activityWeakReference.get() ?: return false
    
    for (pojo in pojos) {
        if (pojo.isDisabled) {
            pojo.relevance -= 200  // 일괄 penalty
        } else {
            val value = knownIds[pojo.id]
            if (value != null) {
                pojo.relevance += 25 * value
            }
        }
    }
    return super.addResults(pojos)
}
```

**변경 후**:
```kotlin
override fun addResults(pojos: List<Pojo>): Boolean {
    val activity = activityWeakReference.get() ?: return false
    
    for (pojo in pojos) {
        if (pojo.isDisabled) {
            // Check if this is a frequently-used app despite being disabled
            val recentUsageCount = DBHelper.getUsageCountForRecord(
                activity, 
                pojo.id, 
                30  // Look back 30 days
            )
            
            if (recentUsageCount >= 1) {
                // Frequently used (1+ times in 30 days) - NO PENALTY
                // Apply same boost as normal apps
                val value = knownIds[pojo.id]
                if (value != null) {
                    pojo.relevance += 25 * value
                }
                
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Frequent hibernated app (${pojo.id}): " +
                               "usage=$recentUsageCount, no penalty applied")
                }
            } else {
                // Infrequently used disabled app - apply penalty
                pojo.relevance -= 200
                
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Infrequent hibernated app (${pojo.id}): " +
                               "usage=$recentUsageCount, penalty applied")
                }
            }
        } else {
            // Normal app - apply query history boost
            val value = knownIds[pojo.id]
            if (value != null) {
                pojo.relevance += 25 * value
            }
        }
    }
    return super.addResults(pojos)
}
```

#### Task 3.2.2.2: Import 추가

```kotlin
import fr.neamar.kiss.db.DBHelper
import android.util.Log
import fr.neamar.kiss.BuildConfig
```

---

### Phase 3.2.3: HistorySearcherCoroutine 동일 로직 적용 (30분)

#### Task 3.2.3.1: addResults() 메서드 수정

**파일**: `app/src/main/java/fr/neamar/kiss/searcher/HistorySearcherCoroutine.kt`

**위치**: Line 139 (현재 penalty 로직)

**동일한 로직 적용**:
```kotlin
if (pojo.isDisabled) {
    val recentUsageCount = DBHelper.getUsageCountForRecord(activity, pojo.id, 30)
    
    if (recentUsageCount >= 1) {
        // Frequently used - no penalty
        val value = knownIds[pojo.id]
        if (value != null) {
            pojo.relevance += 25 * value
        }
    } else {
        // Infrequently used - apply penalty
        pojo.relevance -= 200
    }
}
```

---

### Phase 3.2.4: 성능 최적화 (선택사항, 1시간)

#### Task 3.2.4.1: Batch Query 구현

여러 disabled 앱을 한 번에 조회:

**파일**: `DBHelper.java`

```java
/**
 * Get usage counts for multiple apps in one query (batch optimization)
 * 
 * @param context Application context
 * @param records List of app IDs
 * @param days Number of days to look back
 * @return Map of app ID → usage count
 */
public static HashMap<String, Integer> getBatchUsageCounts(
        Context context, 
        List<String> records, 
        int days) {
    
    HashMap<String, Integer> result = new HashMap<>();
    if (records.isEmpty()) return result;
    
    SQLiteDatabase db = getDatabase(context);
    long cutoffTime = System.currentTimeMillis() - (days * 24L * 60 * 60 * 1000);
    
    // Build placeholders: (?, ?, ?, ...)
    String placeholders = TextUtils.join(",", Collections.nCopies(records.size(), "?"));
    
    String sql = "SELECT record, COUNT(*) as count FROM history " +
                 "WHERE record IN (" + placeholders + ") " +
                 "AND timeStamp > ? " +
                 "GROUP BY record";
    
    // Build args: [record1, record2, ..., cutoffTime]
    String[] args = new String[records.size() + 1];
    for (int i = 0; i < records.size(); i++) {
        args[i] = records.get(i);
    }
    args[records.size()] = String.valueOf(cutoffTime);
    
    try (Cursor cursor = db.rawQuery(sql, args)) {
        while (cursor.moveToNext()) {
            String record = cursor.getString(0);
            int count = cursor.getInt(1);
            result.put(record, count);
        }
    }
    
    // Fill in 0 for records with no usage
    for (String record : records) {
        result.putIfAbsent(record, 0);
    }
    
    return result;
}
```

#### Task 3.2.4.2: Searcher에서 Batch Query 사용

```kotlin
override fun addResults(pojos: List<Pojo>): Boolean {
    val activity = activityWeakReference.get() ?: return false
    
    // Collect all disabled app IDs
    val disabledAppIds = pojos.filter { it.isDisabled }.map { it.id }
    
    // Batch query for usage counts
    val usageCounts = if (disabledAppIds.isNotEmpty()) {
        DBHelper.getBatchUsageCounts(activity, disabledAppIds, 30)
    } else {
        emptyMap()
    }
    
    for (pojo in pojos) {
        if (pojo.isDisabled) {
            val recentUsageCount = usageCounts[pojo.id] ?: 0
            
            if (recentUsageCount >= 1) {
                // Frequently used - no penalty
                val value = knownIds[pojo.id]
                if (value != null) {
                    pojo.relevance += 25 * value
                }
            } else {
                // Infrequently used - apply penalty
                pojo.relevance -= 200
            }
        } else {
            // Normal app
            val value = knownIds[pojo.id]
            if (value != null) {
                pojo.relevance += 25 * value
            }
        }
    }
    return super.addResults(pojos)
}
```

**성능 개선**:
- Before: N queries (N = disabled apps count)
- After: 1 batch query
- 예상 개선: 50개 disabled apps일 때 50x faster

---

### Phase 3.2.5: ProfileManager 로깅 추가 (선택사항, 30분)

**파일**: `QuerySearcherCoroutine.kt`, `HistorySearcherCoroutine.kt`

```kotlin
if (recentUsageCount >= 1) {
    ProfileManager.getInstance().logEvent(
        "HIBERNATED_APP_BOOSTED",
        "app:${pojo.id},usage:$recentUsageCount"
    )
} else {
    ProfileManager.getInstance().logEvent(
        "HIBERNATED_APP_PENALIZED",
        "app:${pojo.id},usage:$recentUsageCount"
    )
}
```

**분석 가능 항목**:
- Boost 비율: boosted / (boosted + penalized)
- Usage 분포: 평균, 중앙값, p95
- 가장 자주 boosted된 앱

---

## 🧪 테스트 계획

### 수동 테스트 시나리오

#### Test Case 1: 자주 사용하는 Hibernated 앱

**Setup**:
1. Facebook 앱 hibernate
2. 30일 동안 4번 실행 (history DB에 기록됨)
3. KISS 검색창에서 "face" 검색

**Expected**:
- ✅ Facebook이 상단에 표시됨
- ✅ 일반 앱과 동일한 우선순위
- ✅ Debug 로그: "Frequent hibernated app: usage=4, no penalty"

---

#### Test Case 2: 드물게 사용하는 Hibernated 앱

**Setup**:
1. Twitter 앱 hibernate
2. 최근 30일 동안 실행 기록 없음
3. "twit" 검색

**Expected**:
- ✅ Twitter가 하단에 표시됨 (기존 동작 유지)
- ✅ -200 penalty 적용
- ✅ Debug 로그: "Infrequent hibernated app: usage=0, penalty applied"

---

#### Test Case 3: Threshold 경계값 (정확히 1회)

**Setup**:
1. Instagram 앱 hibernate
2. 30일 동안 정확히 1번 실행
3. "insta" 검색

**Expected**:
- ✅ Instagram이 상단에 표시됨 (>= 1)
- ✅ Penalty 없음

---

#### Test Case 4: 시간 경계값 (30일 근처)

**Setup**:
1. WhatsApp 앱 hibernate
2. 31일 전: 1회 실행 (현재 윈도우 밖)
3. 최근 30일 내 실행 없음
4. "whats" 검색

**Expected**:
- ✅ 최근 30일 내 사용 0회로 카운트
- ✅ Penalty 적용 (usage=0 < 1)

---

#### Test Case 5: 일반 앱과 Hibernated 앱 혼합

**Setup**:
1. Chrome (일반), Facebook (hibernate, 4회), Twitter (hibernate, 0회)
2. 모두 "f" 검색에 매칭
3. 이전 검색 히스토리 동일

**Expected Ranking**:
```
1. Chrome (일반 앱, query history boost)
2. Facebook (hibernated but frequent, query history boost)
3. Twitter (hibernated and infrequent, -200 penalty)
```

---

### 성능 테스트

#### Test Case P1: 많은 Hibernated 앱 환경

**Setup**:
- 100개 앱 중 50개 hibernate
- "a" 검색 (많은 결과 반환)

**Metrics**:
- ✅ 검색 응답 시간 < 200ms
- ✅ DB query 시간 < 50ms (50개 × 1ms)
- ✅ UI thread blocking 없음

**Optimization (필요 시)**:
- Batch query 구현으로 < 10ms로 단축

---

#### Test Case P2: 반복 검색 메모리 안전성

**Setup**:
- 10회 연속 검색 수행
- LeakCanary로 메모리 누수 확인

**Expected**:
- ✅ 메모리 누수 없음
- ✅ Cursor 자동 닫힘 (try-with-resources)

---

### 자동화 테스트 (선택사항)

#### Unit Test: DBHelper.getUsageCountForRecord()

```kotlin
@Test
fun testGetUsageCountForRecord_withinTimeRange() {
    // Given: 3 launches in last 40 days
    val appId = "app://com.example.test/MainActivity"
    insertHistoryEntry(appId, daysAgo = 1)
    insertHistoryEntry(appId, daysAgo = 10)
    insertHistoryEntry(appId, daysAgo = 40)  // Outside 30-day range
    
    // When
    val count = DBHelper.getUsageCountForRecord(context, appId, 30)
    
    // Then
    assertEquals(2, count)  // Only 2 within 30 days
}

@Test
fun testGetUsageCountForRecord_exactThreshold() {
    // Test >= 1 threshold
    val appId = "app://com.example.test/MainActivity"
    insertHistoryEntry(appId, daysAgo = 5)
    
    val count = DBHelper.getUsageCountForRecord(context, appId, 30)
    
    assertTrue(count >= 1)  // Should qualify for no penalty
}
```

---

## ✅ 성공 기준

### 기능 요구사항

- [x] 최근 30일간 1회 이상 사용된 hibernated 앱은 검색 상단에 표시
- [x] 드물게 사용된 hibernated 앱은 기존 penalty 유지 (하단 표시)
- [x] 일반 앱 동작에 영향 없음
- [x] Query history boost 정상 작동

### 성능 요구사항

- [x] 검색 응답 시간 증가 < 50ms (50개 hibernated apps 기준)
- [x] DB query 성능 < 1ms per app (인덱스 활용)
- [x] 메모리 누수 없음
- [x] UI thread blocking 없음

### 코드 품질

- [x] BuildConfig.DEBUG 조건부 로깅
- [x] Cursor try-with-resources (자동 닫힘)
- [x] Null safety 확보
- [x] Exception handling

### UX 요구사항

- [x] 자주 사용하는 hibernated 앱 접근성 향상
- [x] 사용자 혼란 없음 (기존 동작 유지)
- [x] 예측 가능한 검색 결과
- [x] 배터리/백그라운드 제어는 그대로 유지

---

## 📊 예상 영향

### 긍정적 영향

#### 사용성 개선
- ✅ 자주 사용하는 hibernated 앱 접근 시간 단축 (스크롤 불필요)
- ✅ 검색 효율성 향상
- ✅ Hibernation 기능 사용 장벽 감소

#### 사용자 행동 변화 예상
- ✅ 더 많은 앱을 hibernate할 가능성 (배터리 절약)
- ✅ 검색 사용 빈도 증가
- ✅ Tag 기능 대신 검색 선호도 증가

### 잠재적 리스크

#### 성능 우려

**리스크**: 많은 hibernated 앱 환경에서 검색 속도 저하

**완화 방안**:
- Batch query 구현 (Phase 3.2.4)
- 인덱스 활용으로 O(log n) 보장
- 필요 시 캐싱 추가

#### Threshold 조정 필요성

**리스크**: 30일/1회가 모든 사용자에게 적합하지 않을 수 있음

**완화 방안**:
- ProfileManager로 사용 패턴 분석
- 필요 시 SharedPreferences로 설정 가능하게 확장
- Default 값은 현재 요구사항 유지

---

## 🗓️ 일정

```
Total: 2-4시간

기본 구현 (2.5시간):
- Phase 3.2.1: DBHelper 메서드 추가 (30분)
- Phase 3.2.2: QuerySearcherCoroutine 수정 (45분)
- Phase 3.2.3: HistorySearcherCoroutine 수정 (30분)
- 테스트 및 검증 (45분)

선택사항 (추가 1.5시간):
- Phase 3.2.4: Batch query 최적화 (1시간)
- Phase 3.2.5: ProfileManager 로깅 (30분)
```

---

## 📝 참고 자료

### 관련 파일

**수정 대상**:
- `app/src/main/java/fr/neamar/kiss/db/DBHelper.java` (메서드 추가)
- `app/src/main/java/fr/neamar/kiss/searcher/QuerySearcherCoroutine.kt` (로직 수정)
- `app/src/main/java/fr/neamar/kiss/searcher/HistorySearcherCoroutine.kt` (로직 수정)

**참고**:
- `app/src/main/java/fr/neamar/kiss/pojo/AppPojo.java` (isDisabled 필드)
- `app/src/main/java/fr/neamar/kiss/dataprovider/AppProvider.java` (hibernation 실행)
- `app/src/main/java/fr/neamar/kiss/utils/ShizukuHandler.java` (Shizuku integration)

### 관련 이슈

- TODO.md: High Priority Issues - Phase 3.2
- Phase 3.1: Tag 목록 홈 이동 버그 (별도)
- Phase 2: 검색 성능 최적화 (완료)

### 데이터베이스

**Schema**:
```sql
-- History table
CREATE TABLE history (
    _id INTEGER PRIMARY KEY AUTOINCREMENT,
    "query" TEXT,
    record TEXT NOT NULL,
    timeStamp INTEGER DEFAULT 0 NOT NULL
);

-- Indexes (already exist)
CREATE INDEX idx_history_record ON history(record);
CREATE INDEX idx_history_timestamp ON history(timeStamp DESC);
CREATE INDEX idx_history_record_timestamp ON history(record, timeStamp DESC);
```

**Query Performance**:
- Single app query: O(log n) with index
- Batch query: O(m log n) where m = number of apps
- Typical execution: < 1ms per query

---

## 💡 향후 개선 아이디어

### Short-term Enhancements

1. **Configurable Threshold**
   - SharedPreferences: `hibernated_app_boost_days` (default: 3)
   - SharedPreferences: `hibernated_app_boost_count` (default: 3)
   - Settings UI에서 조정 가능

2. **Visual Indicator**
   - Hibernated but boosted 앱에 작은 아이콘 표시
   - "Frequently used despite hibernation" 툴팁

3. **Analytics Dashboard**
   - Amplitude로 boost 비율 추적
   - 가장 자주 boosted된 앱 Top 10
   - Threshold 최적화를 위한 데이터 수집

### Long-term Enhancements

1. **ML-based Adaptive Threshold**
   - 사용자별 패턴 학습
   - 개인화된 threshold 자동 조정
   - Contextual ranking (시간대, 위치 고려)

2. **Smart Hibernation Suggestions**
   - "이 앱은 자주 사용하지 않으니 hibernate하시겠습니까?"
   - "이 앱은 자주 사용하니 hibernation 해제를 권장합니다"

3. **Hibernate Profiles**
   - Work profile: 업무 앱은 보호, 소셜 앱은 hibernate
   - Weekend profile: 반대로 설정
   - 자동 전환

---

## 🔗 관련 문서

- [Phase 3.1: Tag Navigation Fix](./phase-3-tag-navigation-fix.md)
- [Phase 2: Search Performance](./phase-2-detailed-changes.md)
- [TODO.md High Priority Issues](../TODO.md)
- [Shizuku Guide](./shizuku-guide.md)

---

**문서 작성**: 2025-12-19  
**다음 단계**: Phase 2 완료 후 Phase 3.2 구현 시작 (2-4시간 예상)
