# Step 3: QuerySearcher Migration to Coroutines

**Date**: 2025-01-17  
**Branch**: `step3-query-searcher`  
**Goal**: Migrate QuerySearcher.java → QuerySearcherCoroutine.kt  
**Phase**: Phase 1 - Functional Equivalence

## 목차
1. [구현 개요](#구현-개요)
2. [핵심 설계 결정](#핵심-설계-결정)
3. [Searcher 어댑터 패턴](#searcher-어댑터-패턴)
4. [Feature Flag 시스템](#feature-flag-시스템)
5. [코드 구조](#코드-구조)
6. [검증 및 테스트](#검증-및-테스트)
7. [Next Steps](#next-steps)

---

## 구현 개요

### 완성된 파일

1. **QuerySearcherCoroutine.kt** (142 lines)
   - SearcherCoroutine 상속
   - QuerySearcher.java와 100% 기능 동등성
   - Searcher 어댑터 패턴으로 Provider 호환

2. **MainActivity.java** 수정
   - runTaskCoroutine() 메서드 추가
   - resetTask() 양쪽 취소 지원
   - Feature flag 기반 searcher 선택

3. **BuildConfig** 수정
   - USE_SEARCHER_COROUTINE flag 추가
   - 기본값: true (Coroutines 활성화)

4. **SettingsActivity.java** 수정
   - 양쪽 MAX_RESULT_COUNT cache clear 지원

### Phase 1 원칙 준수

✅ QuerySearcher.java와 **100% 기능 동등성** 유지:
- DB query history 로직 동일
- Relevance adjustment 로직 동일 (disabled -200, history +25 * count)
- MAX_RESULT_COUNT static caching 동일
- Provider coordination 동일

---

## 핵심 설계 결정

### 1. Searcher 어댑터 패턴 (가장 중요)

**문제**:
- Provider들이 `Searcher` 타입만 받음 (`requestResults(String, Searcher)`)
- QuerySearcherCoroutine은 `SearcherCoroutine` 타입
- Provider interface를 변경하면 모든 8개 Provider 수정 필요 (Phase 1 원칙 위배)

**해결책**: Anonymous Searcher 어댑터 객체 생성
```kotlin
val searcherAdapter = object : Searcher(activity, query, false) {
    override fun doInBackground() {
        // Not used - only need addResult() bridge
    }
    
    // addResult() is final in Searcher, so we override addResults() which it calls
    override fun addResults(pojos: List<Pojo>): Boolean {
        return this@QuerySearcherCoroutine.addResults(pojos)
    }
    
    override fun isCancelled(): Boolean {
        return this@QuerySearcherCoroutine.isCancelled()
    }
}

KissApplication.getApplication(activity).dataHandler.requestResults(query, searcherAdapter)
```

**설계 근거**:
1. **Phase 1 원칙 유지**: Provider 코드 변경 없음
2. **최소 침투성**: 어댑터는 QuerySearcherCoroutine 내부에만 존재
3. **Bridge Pattern**: Searcher → SearcherCoroutine 호출 브릿지
4. **Future-proof**: Phase 2에서 Provider 리팩토링 시 쉽게 제거 가능

**Phase 2 개선 계획**:
- `ISearchResultReceiver` 인터페이스 생성 (이미 파일 생성됨)
- Provider.requestResults() → `ISearchResultReceiver` 받도록 변경
- Searcher와 SearcherCoroutine 모두 인터페이스 구현
- 어댑터 제거

---

### 2. Static Cache 유지

**QuerySearcher.java** (line 21-22):
```java
private static int MAX_RESULT_COUNT = -1;
```

**QuerySearcherCoroutine.kt** (line 26-28):
```kotlin
companion object {
    @Volatile
    private var MAX_RESULT_COUNT = -1
}
```

**설계 근거**:
- Phase 1: 기존 동작 그대로 유지
- `@Volatile`: Thread-safe read (single thread write는 보장됨)
- Phase 2 개선 대상 (docs/step1-improvement-analysis.md #4)

---

### 3. Relevance Adjustment 로직

**QuerySearcher.java** (lines 51-66):
```java
@Override
public boolean addResults(List<? extends Pojo> pojos) {
    for (Pojo pojo : pojos) {
        if (pojo.isDisabled()) {
            pojo.relevance -= 200;
        } else {
            Integer value = knownIds.get(pojo.id);
            if (value != null) {
                pojo.relevance += 25 * value;
            }
        }
    }
    return super.addResults(pojos);
}
```

**QuerySearcherCoroutine.kt** (lines 82-100):
```kotlin
override fun addResults(pojos: List<Pojo>): Boolean {
    for (pojo in pojos) {
        if (pojo.isDisabled) {
            pojo.relevance -= 200
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

**완전 동일**: Java → Kotlin 문법만 변경, 로직 100% 동일

---

## Feature Flag 시스템

### BuildConfig (app/build.gradle)

**Line 38-39**:
```gradle
// Feature flags for AsyncTask → Coroutines migration
buildConfigField "boolean", "USE_SEARCHER_COROUTINE", "true"
```

### MainActivity Usage

**Before** (line 1255):
```java
runTask(new QuerySearcher(this, query, isRefresh));
```

**After** (lines 1252-1258):
```java
// Feature flag: Use Coroutines or AsyncTask for QuerySearcher
if (BuildConfig.USE_SEARCHER_COROUTINE) {
    runTaskCoroutine(new fr.neamar.kiss.searcher.QuerySearcherCoroutine(this, query, isRefresh));
} else {
    runTask(new QuerySearcher(this, query, isRefresh));
}
```

### Rollback 방법

1. **Quick Rollback** (빌드 없이):
   ```bash
   # build.gradle 수정
   buildConfigField "boolean", "USE_SEARCHER_COROUTINE", "false"
   
   # 재빌드
   ./gradlew assembleDebug
   ```

2. **Complete Rollback** (Git):
   ```bash
   git checkout dev
   # step3-query-searcher 브랜치 삭제 (필요시)
   ```

---

## 코드 구조

### QuerySearcherCoroutine.kt 구조

```
QuerySearcherCoroutine (extends SearcherCoroutine)
├── Companion Object
│   ├── MAX_RESULT_COUNT: Int @Volatile (static cache)
│   └── clearMaxResultCountCache(): Unit @JvmStatic
├── Constructor Parameters
│   ├── activity: MainActivity
│   ├── query: String
│   └── isRefresh: Boolean
├── Properties
│   ├── prefs: SharedPreferences
│   └── knownIds: HashMap<String, Int>
└── Methods
    ├── getMaxResultCount(): Int override
    ├── addResults(pojos: List<Pojo>): Boolean override
    └── doInBackground(): Unit override suspend
        ├── Load DB history → knownIds
        ├── Create Searcher adapter (anonymous object)
        └── DataHandler.requestResults(query, adapter)
```

### MainActivity 변경 사항

**새로운 필드** (line 338-342):
```java
private Searcher searchTask;  // Existing

/**
 * Coroutine Job for Searcher (AsyncTask → Coroutines migration)
 */
private kotlinx.coroutines.Job searchJob;  // NEW
```

**새로운 메서드** (lines 1273-1278):
```java
public void runTaskCoroutine(fr.neamar.kiss.searcher.SearcherCoroutine task) {
    resetTask();
    searchJob = task.execute();
}
```

**수정된 메서드** (lines 1280-1292):
```java
public void resetTask() {
    // Cancel legacy AsyncTask searcher
    if (searchTask != null) {
        searchTask.cancel(true);
        searchTask = null;
    }
    
    // Cancel Coroutines searcher
    if (searchJob != null) {
        searchJob.cancel(null);
        searchJob = null;
    }
}
```

---

## 검증 및 테스트

### 1. Compilation ✅

**환경**:
- Android Studio JDK 21
- Gradle 8.13
- Kotlin 2.0.21

**결과**:
```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug

BUILD SUCCESSFUL in 10s
```

**경고**:
- 100 deprecation warnings (기존 코드, QuerySearcherCoroutine과 무관)
- No new errors

### 2. 기능 동등성 체크리스트

| 기능 | QuerySearcher.java | QuerySearcherCoroutine.kt | 검증 |
|------|-------------------|--------------------------|------|
| DB query history | ✅ | ✅ | DBHelper.getPreviousResultsForQuery() |
| knownIds HashMap | ✅ | ✅ | HashMap<String, Int> |
| Disabled penalty | ✅ (-200) | ✅ (-200) | addResults() |
| History boost | ✅ (+25 * count) | ✅ (+25 * count) | addResults() |
| MAX_RESULT_COUNT cache | ✅ static | ✅ @Volatile companion | getMaxResultCount() |
| Provider coordination | ✅ | ✅ | Searcher adapter pattern |
| Preference reading | ✅ | ✅ | PreferenceManager |
| Result sorting | ✅ PriorityQueue | ✅ PriorityQueue | Inherited from SearcherCoroutine |

### 3. Manual Testing (TODO)

**테스트 시나리오**:

1. **Basic Search**:
   - 앱 검색 (예: "gal" → "갤러리")
   - 연락처 검색
   - 설정 검색

2. **History-based Ranking**:
   - 동일 쿼리 반복 검색 시 previously selected item이 상위로 오는지 확인
   - knownIds boost 작동 확인

3. **Disabled Apps**:
   - Disabled app은 relevance -200 적용되어 하위로 가는지 확인

4. **Performance**:
   - Amplitude 로그 확인
   - 검색 시간이 QuerySearcher.java와 비슷한지 확인

5. **Cancellation**:
   - 빠른 연속 입력 시 이전 검색이 취소되는지 확인
   - Memory leak 없는지 확인 (LeakCanary)

6. **Settings Integration**:
   - "number-of-display-elements" 설정 변경 후 MAX_RESULT_COUNT 반영 확인
   - SettingsActivity에서 cache clear 정상 작동 확인

### 4. Rollback Test (TODO)

```gradle
// 1. Rollback to AsyncTask version
buildConfigField "boolean", "USE_SEARCHER_COROUTINE", "false"

// 2. Rebuild
./gradlew clean assembleDebug

// 3. Test: 기존 QuerySearcher.java로 정상 작동 확인
```

---

## 알려진 제약사항

### 1. Searcher 어댑터 패턴

**제약**:
- 어댑터 객체가 doInBackground()에서 생성됨
- 약간의 메모리 오버헤드 (객체 1개 추가)

**영향**:
- 무시할 수 있는 수준 (lightweight anonymous object)
- Phase 2에서 Provider 리팩토링으로 제거 예정

### 2. ISearchResultReceiver 미사용

**상태**:
- 파일은 생성됨 (`ISearchResultReceiver.kt`)
- 현재 사용 안 함 (어댑터 패턴으로 해결)

**이유**:
- Phase 1 원칙: Provider 변경 최소화
- Phase 2에서 활성화 예정

---

## Phase 2 개선 계획

### 1. Provider Interface Refactoring

**현재** (Phase 1):
```java
// Provider.java
public void requestResults(String query, Searcher searcher) { ... }

// QuerySearcherCoroutine.kt (workaround)
val adapter = object : Searcher(...) { ... }
```

**Phase 2 목표**:
```kotlin
// Provider.kt
fun requestResults(query: String, receiver: ISearchResultReceiver) { ... }

// QuerySearcherCoroutine.kt (clean)
class QuerySearcherCoroutine(...) : SearcherCoroutine(...), ISearchResultReceiver {
    // No adapter needed
}
```

### 2. Static Cache 제거

**현재**:
```kotlin
companion object {
    @Volatile
    private var MAX_RESULT_COUNT = -1
}
```

**Phase 2 목표**:
```kotlin
class QuerySearcherCoroutine(...) : SearcherCoroutine(...) {
    private val maxResultCount: Int by lazy {
        prefs.getString("number-of-display-elements", "50")?.toIntOrNull() ?: 50
    }
}
```

---

## Next Steps

### Step 4: 나머지 Searcher 클래스 마이그레이션

**Priority 순서** (docs/step1-analysis-report.md 기준):

1. **HistorySearcher** (Medium complexity)
   - QuerySearcher와 유사한 구조
   - DB query 사용

2. **ApplicationsSearcher** (Medium complexity)
   - Custom PriorityQueue processor
   - getPojoProcessor() override

3. **NullSearcher** (Low complexity)
   - 가장 간단 (빈 결과 반환)

4. **TagsSearcher, UntaggedSearcher** (Low complexity)
   - PojoWithTagSearcher 상속

**예상 소요 시간**: 2-3일
- 0.5일 per searcher (simple ones)
- 1일 per searcher (complex ones)
- 0.5일 testing & verification

### Step 5: Legacy Code Cleanup

- Searcher.java 제거 (모든 subclass 마이그레이션 후)
- ISearchResultReceiver 활성화
- Feature flag 제거 (production에서 충분히 검증 후)

---

## 결론

### 완료 사항

✅ **QuerySearcherCoroutine.kt 구현 완료** (142 lines)
- 100% functional equivalence with QuerySearcher.java
- Searcher adapter pattern for Provider compatibility
- Phase 1 goal achieved: "Migrate First, Improve Later"

✅ **Feature Flag 시스템 구축**
- BuildConfig.USE_SEARCHER_COROUTINE
- MainActivity integration
- Rollback mechanism

✅ **Compilation 검증** ✅
- Android Studio JDK 21
- No new errors

### Manual Testing 필요

⏳ **다음 단계**:
1. APK 빌드 및 설치
2. 검색 기능 수동 테스트
3. Performance & Memory leak 확인
4. Step 4 준비 (나머지 Searcher 클래스)

---

**End of Step 3 Implementation Document**
