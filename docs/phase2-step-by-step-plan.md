# Phase 2: 단계별 실행 계획

**작성일**: 2025-01-17  
**전략**: 브랜치별 점진적 개선 (Phase 1과 동일)  
**목표**: 안전하고 검증 가능한 단계적 개선

---

## 🎯 실행 전략: "One Branch, One Thing"

Phase 1 마이그레이션의 성공 전략을 Phase 2에도 적용:

1. **각 개선 항목마다 별도 브랜치**
2. **독립적인 검증**
3. **안전한 롤백 가능**
4. **명확한 진행 상황**

---

## 📋 권장 실행 순서 (최적화)

### ⚠️ 순서 변경 이유

문서의 우선순위(1→2→3→4→5)를 다음과 같이 재배치:

```
기존 순서:
1. Thread Safety (Medium)
2. Error Handling (Medium)  
3. Cancellation Checks (High) ⚠️ 가장 중요한데 3번째
4. Static Cache (Low)
5. Logging (Low)

최적화 순서:
Step 1: Thread Safety (Medium) ✅ 다른 개선의 기반
Step 2: Error Handling (Medium) ✅ Cancellation과 함께 테스트 필요
Step 3: Cancellation Checks (High) ✅ 가장 복잡하고 중요
Step 4: Static Cache (Low)
Step 5: Logging (Low)
```

### 왜 이 순서가 더 좋은가?

1. **Thread Safety 먼저**: 
   - 다른 개선 작업의 안전한 기반
   - 가장 간단하고 리스크 낮음
   - Cancellation Checks 작업 전에 완료해야 안전

2. **Error Handling 두 번째**:
   - Cancellation 체크와 함께 테스트해야 함
   - Error와 Cancellation 구분 로직 필요
   - 선행 작업으로 완료

3. **Cancellation Checks 세 번째**:
   - 가장 복잡하고 시간 많이 소요
   - Thread Safety + Error Handling 완료 후 안전
   - 4개 Searcher 모두 수정 필요

---

## 🚀 Step-by-Step 실행 계획

### Step 1: Thread Safety (0.5일) 🟡

**Branch**: `phase2-step1-thread-safety`

#### 목표
- PriorityQueue에 synchronized 추가
- 명시적 thread safety 보장

#### 작업 파일
- `app/src/main/java/fr/neamar/kiss/searcher/SearcherCoroutine.kt`

#### 변경 내용
```kotlin
// Line 87-92 수정
open fun addResults(pojos: List<Pojo>): Boolean {
    if (isCancelled()) {
        return false
    }
    // ✅ synchronized 추가
    synchronized(processedPojos) {
        return processedPojos.addAll(pojos)
    }
}
```

#### 검증
1. **기능 테스트**
   - 모든 검색 타입 정상 동작
   - 결과 순서 유지
   - 취소 동작 정상

2. **성능 테스트**
   - 검색 속도 변화 없음 (synchronized 오버헤드 확인)

3. **동시성 테스트** (선택)
   - 빠른 연속 검색 시 정상 동작

#### 완료 기준
- ✅ synchronized 블록 추가
- ✅ 모든 검색 타입 테스트 통과
- ✅ 성능 저하 없음
- ✅ PR 생성 및 리뷰

#### Commit 메시지
```
feat(searcher): Add explicit thread safety to addResults()

- Add synchronized block to processedPojos operations
- Ensure thread-safe result collection from providers
- No functional changes, performance impact negligible

Phase 2 Step 1/5: Thread Safety
Related: phase2-searcher-improvements.md
```

---

### Step 2: Error Handling (0.5일) 🟡

**Branch**: `phase2-step2-error-handling`

#### 목표
- Exception 타입별 처리
- Error와 Cancellation 구분
- onError() 콜백 추가

#### 작업 파일
- `app/src/main/java/fr/neamar/kiss/searcher/SearcherCoroutine.kt`

#### 변경 내용

**1. execute() 메서드 수정 (line 118-145)**
```kotlin
currentJob = CoroutineScope(Dispatchers.Main).launch {
    try {
        onPreExecute()
        
        withContext(searchDispatcher) {
            doInBackground()
        }
        
        onPostExecute()
        
    } catch (e: CancellationException) {
        // ✅ 정상 취소 처리
        Log.d(TAG, "Search cancelled: ${this@SearcherCoroutine::class.simpleName}")
        onCancelled()
        
    } catch (e: Exception) {
        // ✅ 실제 에러 처리
        Log.e(TAG, "Error in ${this@SearcherCoroutine::class.simpleName}", e)
        onError(e)
    }
}
```

**2. onError() 메서드 추가 (line 236 이후)**
```kotlin
/**
 * Called when search encounters an error (not cancellation)
 * Can be overridden for custom error handling
 */
protected open fun onError(error: Exception) {
    Log.e(TAG, "Search error in ${this::class.simpleName}: ${error.message}", error)
    
    // Log to Amplitude
    try {
        val eventProperties = JSONObject()
        eventProperties.put("type", this::class.simpleName)
        eventProperties.put("errorType", error::class.simpleName)
        eventProperties.put("errorMessage", error.message)
        
        Amplitude.getInstance().logEvent("SearchError", eventProperties)
    } catch (e: JSONException) {
        Log.e(TAG, "Failed to log error to Amplitude", e)
    }
    
    // Cleanup UI (same as cancellation)
    val activity = activityWeakReference.get()
    if (activity != null) {
        hideActivityLoader(activity)
    }
}
```

#### 검증
1. **정상 동작 테스트**
   - 에러 없는 검색: onPostExecute() 호출
   - 정상 취소: onCancelled() 호출

2. **에러 시나리오 테스트**
   - DB 에러 시뮬레이션: onError() 호출 확인
   - Exception 타입 구분 확인
   - Amplitude 로깅 확인

3. **로그 확인**
   - CancellationException: DEBUG 레벨
   - 기타 Exception: ERROR 레벨

#### 완료 기준
- ✅ CancellationException 별도 처리
- ✅ onError() 메서드 추가
- ✅ Amplitude 에러 이벤트 추가
- ✅ 모든 검색 타입 테스트 통과
- ✅ PR 생성 및 리뷰

#### Commit 메시지
```
feat(searcher): Distinguish errors from cancellations

- Add separate handling for CancellationException
- Add onError() callback for actual errors
- Log errors to Amplitude for tracking
- Improve debugging with type-specific logging

Phase 2 Step 2/5: Error Handling
Related: phase2-searcher-improvements.md
```

---

### Step 3: Cancellation Checks (1일) 🔴

**Branch**: `phase2-step3-cancellation-checks`

#### 목표
- 긴 작업 중간에 취소 체크 추가
- 빠른 취소 반응
- 리소스 절약

#### 작업 파일 (4개)
1. `QuerySearcherCoroutine.kt`
2. `HistorySearcherCoroutine.kt`
3. `ApplicationsSearcherCoroutine.kt`
4. `PojoWithTagSearcherCoroutine.kt`

#### 패턴 (공통)

```kotlin
override suspend fun doInBackground() {
    val activity = activityWeakReference.get() ?: return
    
    // ✅ Step 1: 긴 작업 전 체크
    if (!isActive) return
    
    // 긴 작업 (DB 조회, 필터링 등)
    val data = withContext(Dispatchers.IO) {
        // ... 데이터 로딩 ...
    }
    
    // ✅ Step 2: 데이터 처리 전 체크
    if (!isActive) return
    
    // 데이터 처리 (HashMap 생성, 필터링 등)
    val processed = processData(data)
    
    // ✅ Step 3: Provider 요청 전 체크
    if (!isActive) return
    
    // Provider 요청
    dataHandler.requestXxx(...)
}

// 큰 루프의 경우
for (item in largeList) {
    if (!isActive) return  // ✅ 주기적 체크
    // 처리...
}
```

#### 상세 작업

**3.1. QuerySearcherCoroutine.kt (2시간)**

```kotlin
override suspend fun doInBackground() {
    val activity = activityWeakReference.get() ?: return
    val dataHandler = KissApplication.getApplication(activity).dataHandler
    
    // ✅ DB 조회 전
    if (!isActive) return
    
    val lastIdsForQuery = DBHelper.getPreviousResultsForQuery(activity, query)
    
    // ✅ HashMap 생성 전
    if (!isActive) return
    
    knownIds = HashMap()
    for (id in lastIdsForQuery) {
        // ✅ 큰 루프는 주기적으로 체크 (예: 1000개마다)
        if (!isActive) return
        knownIds[id.record] = id.value
    }
    
    // ✅ Provider 요청 전
    if (!isActive) return
    
    val searcherAdapter = object : Searcher(activity, query, false) {
        // ...
    }
    
    dataHandler.requestResults(query, searcherAdapter)
}
```

**3.2. HistorySearcherCoroutine.kt (1시간)**

```kotlin
override suspend fun doInBackground() {
    val activity = activityWeakReference.get() ?: return
    
    // ✅ DB 조회 전
    if (!isActive) return
    
    val historyRecords = DBHelper.getHistory(activity)
    
    // ✅ 결과 추가 전
    if (!isActive) return
    
    addResults(historyRecords)
}
```

**3.3. ApplicationsSearcherCoroutine.kt (2시간)**

```kotlin
override suspend fun doInBackground() {
    val activity = activityWeakReference.get() ?: return
    val dataHandler = KissApplication.getApplication(activity).dataHandler
    
    // ✅ 앱 목록 요청 전
    if (!isActive) return
    
    val allApps = dataHandler.getApplications()
    
    // ✅ 필터링 전
    if (!isActive) return
    
    val filteredApps = allApps.filter { app ->
        // ✅ 필터링 중간에도 체크 (큰 리스트의 경우)
        if (!isActive) return
        app.isEnabled
    }
    
    // ✅ 결과 추가 전
    if (!isActive) return
    
    addResults(filteredApps)
}
```

**3.4. PojoWithTagSearcherCoroutine.kt (1시간)**

```kotlin
override suspend fun doInBackground() {
    val activity = activityWeakReference.get() ?: return
    val dataHandler = KissApplication.getApplication(activity).dataHandler
    
    // ✅ Searcher adapter 생성 전
    if (!isActive) return
    
    val searcherAdapter = object : Searcher(activity, query, false) {
        // ...
    }
    
    // ✅ Provider 요청 전
    if (!isActive) return
    
    if (this is TagsSearcherCoroutine && query != null && query != "<tags>") {
        dataHandler.requestRecordsByTag(query, searcherAdapter)
    } else {
        dataHandler.requestAllRecords(searcherAdapter)
    }
}
```

#### 검증
1. **취소 응답 테스트**
   - 검색 입력 후 즉시 변경: 이전 검색 즉시 중단 확인
   - Logcat에서 "Search cancelled" 메시지 확인

2. **기능 테스트**
   - 정상 검색: 결과 정상 표시
   - 취소 후 재검색: 정상 동작

3. **성능 테스트**
   - 취소 반응 시간: 즉시 (< 50ms)
   - CPU 사용량: 취소 후 즉시 감소

#### 완료 기준
- ✅ 4개 Searcher 모두 수정
- ✅ 모든 긴 작업 전후에 체크 추가
- ✅ 취소 반응 시간 < 50ms
- ✅ 기능 테스트 통과
- ✅ PR 생성 및 리뷰

#### Commit 메시지
```
feat(searcher): Add cancellation checks in long operations

- Add isActive checks before DB queries
- Add checks before data processing
- Add checks in large loops
- Improve cancellation response time to < 50ms

Modified searchers:
- QuerySearcherCoroutine
- HistorySearcherCoroutine  
- ApplicationsSearcherCoroutine
- PojoWithTagSearcherCoroutine

Phase 2 Step 3/5: Cancellation Checks
Related: phase2-searcher-improvements.md
```

---

### Step 4: Static Cache Removal (0.5일) 🟢

**Branch**: `phase2-step4-static-cache-removal`

#### 목표
- QuerySearcher의 static MAX_RESULT_COUNT 제거
- Instance 변수로 변경
- 테스트 용이성 향상

#### 작업 파일
- `app/src/main/java/fr/neamar/kiss/searcher/QuerySearcherCoroutine.kt`

#### 변경 내용

**Before:**
```kotlin
companion object {
    @Volatile
    private var MAX_RESULT_COUNT = -1
    
    @JvmStatic
    fun clearMaxResultCountCache() {
        MAX_RESULT_COUNT = -1
    }
}

override fun getMaxResultCount(): Int {
    if (MAX_RESULT_COUNT == -1) {
        MAX_RESULT_COUNT = prefs.getString("number-of-display-elements", "50")
            .toIntOrNull() ?: DEFAULT_MAX_RESULTS
    }
    return MAX_RESULT_COUNT
}
```

**After:**
```kotlin
// ✅ Companion object에서 제거

// ✅ Instance 변수로 변경
private var maxResultCount: Int? = null

override fun getMaxResultCount(): Int {
    if (maxResultCount == null) {
        maxResultCount = prefs.getString("number-of-display-elements", "50")
            .toIntOrNull() ?: DEFAULT_MAX_RESULTS
    }
    return maxResultCount!!
}

// ✅ clearMaxResultCountCache() 메서드 제거 (더 이상 필요 없음)
```

#### 영향받는 코드 확인
```bash
# clearMaxResultCountCache() 사용처 검색
git grep "clearMaxResultCountCache"
```

만약 사용처가 있다면:
- `MainActivity.kt` 또는 설정 화면에서 호출하는 경우
- 해당 호출 코드 제거 (더 이상 필요 없음)

#### 검증
1. **기능 테스트**
   - 검색 결과 개수 제한 정상 동작
   - 설정 변경 후 동작 확인

2. **설정 변경 테스트**
   - 설정에서 결과 개수 변경
   - 새로운 검색 시 반영 확인

#### 완료 기준
- ✅ Static 변수 제거
- ✅ Instance 변수로 변경
- ✅ clearMaxResultCountCache() 호출 제거
- ✅ 기능 테스트 통과
- ✅ PR 생성 및 리뷰

#### Commit 메시지
```
refactor(searcher): Remove static cache from QuerySearcher

- Convert MAX_RESULT_COUNT from static to instance variable
- Remove clearMaxResultCountCache() method
- Improve testability and code cleanliness
- No functional changes

Phase 2 Step 4/5: Static Cache Removal
Related: phase2-searcher-improvements.md
```

---

### Step 5: Logging Consolidation (0.5일) 🟢

**Branch**: `phase2-step5-logging-consolidation`

#### 목표
- 로깅 유틸리티 클래스 생성
- 일관된 로깅 형식
- 에러 로깅 개선

#### 작업 파일
1. `app/src/main/java/fr/neamar/kiss/utils/SearchPerformanceLogger.kt` (신규)
2. `app/src/main/java/fr/neamar/kiss/searcher/SearcherCoroutine.kt` (수정)

#### 변경 내용

**1. SearchPerformanceLogger.kt 생성**

```kotlin
package fr.neamar.kiss.utils

import android.util.Log
import com.amplitude.api.Amplitude
import org.json.JSONException
import org.json.JSONObject

/**
 * Centralized logging for Searcher performance and errors
 * 
 * Provides consistent logging format across all searchers
 * with both Android Log and Amplitude tracking.
 */
object SearchPerformanceLogger {
    
    private const val TAG = "SearchPerf"
    
    data class SearchMetrics(
        val searcherType: String,
        val query: String?,
        val timeMs: Long,
        val resultCount: Int,
        val allProvidersLoaded: Boolean,
        val cancelled: Boolean = false,
        val error: Exception? = null
    )
    
    /**
     * Log search completion, cancellation, or error
     */
    fun log(metrics: SearchMetrics) {
        // Determine status
        val status = when {
            metrics.error != null -> "ERROR"
            metrics.cancelled -> "CANCELLED"
            else -> "COMPLETED"
        }
        
        // Android Log
        val logLevel = if (metrics.error != null) Log.ERROR else Log.VERBOSE
        val message = buildString {
            append("[$status] ")
            append("${metrics.searcherType} ")
            append("query='${metrics.query?.replace("<null>", "")}' ")
            append("time=${metrics.timeMs}ms ")
            append("results=${metrics.resultCount} ")
            append("providersLoaded=${metrics.allProvidersLoaded}")
            
            if (metrics.error != null) {
                append(" error=${metrics.error.javaClass.simpleName}: ${metrics.error.message}")
            }
        }
        
        Log.println(logLevel, TAG, message)
        
        // Amplitude logging
        logToAmplitude(metrics, status)
    }
    
    private fun logToAmplitude(metrics: SearchMetrics, status: String) {
        try {
            val eventName = if (metrics.error != null) "SearchError" else "Search"
            
            val eventProperties = JSONObject().apply {
                put("type", metrics.searcherType)
                put("length", metrics.query?.replace("<null>", "")?.length ?: 0)
                put("time", metrics.timeMs)
                put("resultCount", metrics.resultCount)
                put("allProvidersLoaded", metrics.allProvidersLoaded)
                put("status", status)
                
                if (metrics.error != null) {
                    put("errorType", metrics.error::class.simpleName)
                    put("errorMessage", metrics.error.message)
                }
            }
            
            Amplitude.getInstance().logEvent(eventName, eventProperties)
        } catch (e: JSONException) {
            Log.e(TAG, "Failed to log to Amplitude", e)
        }
    }
}
```

**2. SearcherCoroutine.kt 수정**

```kotlin
// 기존 logPerformance() 메서드 제거하고 새로운 메서드로 교체

protected open fun onPostExecute() {
    // ... 기존 로직 ...
    
    // ✅ 새로운 통합 로깅
    logPerformance(cancelled = false, error = null)
}

protected open fun onCancelled() {
    // ... 기존 로직 ...
    
    val activity = activityWeakReference.get() ?: return
    
    // ✅ 새로운 통합 로깅
    logPerformance(cancelled = true, error = null)
}

protected open fun onError(error: Exception) {
    // ... 기존 로직 ...
    
    val activity = activityWeakReference.get()
    if (activity != null) {
        hideActivityLoader(activity)
    }
    
    // ✅ 새로운 통합 로깅
    logPerformance(cancelled = false, error = error)
}

/**
 * Log search performance using centralized logger
 */
private fun logPerformance(
    cancelled: Boolean,
    error: Exception?
) {
    val activity = activityWeakReference.get() ?: return
    val time = System.currentTimeMillis() - startTime
    
    SearchPerformanceLogger.log(
        SearchPerformanceLogger.SearchMetrics(
            searcherType = this::class.simpleName ?: "Unknown",
            query = query,
            timeMs = time,
            resultCount = processedPojos.size,
            allProvidersLoaded = KissApplication.getApplication(activity)
                .dataHandler.allProvidersHaveLoaded,
            cancelled = cancelled,
            error = error
        )
    )
}
```

#### 검증
1. **로그 확인**
   - 정상 검색: `[COMPLETED]` 로그
   - 취소: `[CANCELLED]` 로그
   - 에러: `[ERROR]` 로그

2. **Amplitude 확인**
   - Search 이벤트: status 필드 확인
   - SearchError 이벤트: errorType, errorMessage 확인

3. **기능 테스트**
   - 로깅이 검색 기능에 영향 없음

#### 완료 기준
- ✅ SearchPerformanceLogger.kt 생성
- ✅ SearcherCoroutine.kt 통합
- ✅ 모든 상태(완료/취소/에러) 로깅 확인
- ✅ Amplitude 이벤트 확인
- ✅ PR 생성 및 리뷰

#### Commit 메시지
```
refactor(searcher): Consolidate search performance logging

- Add SearchPerformanceLogger utility class
- Centralize Android Log and Amplitude logging
- Support completion, cancellation, and error states
- Improve log readability and consistency

Phase 2 Step 5/5: Logging Consolidation
Related: phase2-searcher-improvements.md
```

---

## 📊 진행 추적

### 체크리스트

```
Phase 2 Progress:

□ Step 1: Thread Safety (0.5일)
  □ Branch 생성: phase2-step1-thread-safety
  □ synchronized 블록 추가
  □ 테스트 통과
  □ PR 생성 및 머지
  
□ Step 2: Error Handling (0.5일)
  □ Branch 생성: phase2-step2-error-handling
  □ CancellationException 구분
  □ onError() 추가
  □ 테스트 통과
  □ PR 생성 및 머지
  
□ Step 3: Cancellation Checks (1일)
  □ Branch 생성: phase2-step3-cancellation-checks
  □ QuerySearcherCoroutine 수정
  □ HistorySearcherCoroutine 수정
  □ ApplicationsSearcherCoroutine 수정
  □ PojoWithTagSearcherCoroutine 수정
  □ 취소 응답 테스트 (< 50ms)
  □ 테스트 통과
  □ PR 생성 및 머지
  
□ Step 4: Static Cache Removal (0.5일)
  □ Branch 생성: phase2-step4-static-cache-removal
  □ Instance 변수로 변경
  □ clearMaxResultCountCache() 제거
  □ 테스트 통과
  □ PR 생성 및 머지
  
□ Step 5: Logging Consolidation (0.5일)
  □ Branch 생성: phase2-step5-logging-consolidation
  □ SearchPerformanceLogger.kt 생성
  □ SearcherCoroutine.kt 통합
  □ 로그 확인
  □ 테스트 통과
  □ PR 생성 및 머지
```

### Git 워크플로우

각 Step마다:

```bash
# 1. dev 브랜치에서 시작
git checkout dev
git pull origin dev

# 2. 작업 브랜치 생성
git checkout -b phase2-step1-thread-safety

# 3. 작업 수행
# ... 코드 수정 ...

# 4. 커밋
git add .
git commit -m "feat(searcher): Add explicit thread safety to addResults()

- Add synchronized block to processedPojos operations
- Ensure thread-safe result collection from providers
- No functional changes, performance impact negligible

Phase 2 Step 1/5: Thread Safety
Related: phase2-searcher-improvements.md"

# 5. 푸시 및 PR 생성
git push origin phase2-step1-thread-safety

# 6. GitHub에서 PR 생성 (dev ← phase2-step1-thread-safety)

# 7. 리뷰 및 테스트 후 머지

# 8. 다음 Step으로
git checkout dev
git pull origin dev
git checkout -b phase2-step2-error-handling
```

---

## 🎯 각 Step 완료 기준

### 공통 기준
- ✅ 브랜치 생성 및 작업 완료
- ✅ 컴파일 에러 없음
- ✅ 모든 검색 타입 기능 테스트 통과
- ✅ 성능 저하 없음
- ✅ 메모리 누수 없음
- ✅ Commit 메시지 작성
- ✅ PR 생성 및 리뷰
- ✅ dev 브랜치 머지

### Step별 추가 기준

**Step 1 (Thread Safety)**
- ✅ synchronized 블록 추가 확인
- ✅ Race condition 없음

**Step 2 (Error Handling)**
- ✅ CancellationException 별도 처리 확인
- ✅ onError() 콜백 호출 확인
- ✅ Amplitude 에러 로깅 확인

**Step 3 (Cancellation Checks)**
- ✅ 4개 Searcher 모두 수정 확인
- ✅ 취소 반응 시간 < 50ms
- ✅ 불필요한 작업 중단 확인

**Step 4 (Static Cache)**
- ✅ Static 변수 완전 제거
- ✅ clearMaxResultCountCache() 호출 제거
- ✅ 설정 변경 후 동작 확인

**Step 5 (Logging)**
- ✅ SearchPerformanceLogger 동작 확인
- ✅ 3가지 상태 로그 확인 (완료/취소/에러)
- ✅ Amplitude 이벤트 확인

---

## 📝 참고 문서

- **상세 개선 내역**: [phase2-searcher-improvements.md](./phase2-searcher-improvements.md)
- **전체 요약**: [searcher-coroutines-migration-summary.md](./searcher-coroutines-migration-summary.md)
- **Phase 1 참고**: [step2-searcher-base-implementation.md](./step2-searcher-base-implementation.md) ~ [step5-legacy-cleanup-summary.md](./step5-legacy-cleanup-summary.md)

---

**작성 완료**: 2025-01-17  
**시작 예정**: Phase 2 Step 1 (Thread Safety)  
**예상 완료**: 3일 후
