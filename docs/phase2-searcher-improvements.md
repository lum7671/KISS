# Phase 2: Searcher 시스템 개선 사항

**작성일**: 2025-01-17  
**상태**: ✅ Phase 1 완료 (AsyncTask → Coroutines 마이그레이션)  
**다음 단계**: Phase 2 개선 사항 적용

---

## 🎉 Phase 1 완료 상태

### ✅ 완료된 마이그레이션

- **Base Class**: `SearcherCoroutine.kt` (245 lines)
- **Query Search**: `QuerySearcherCoroutine.kt`
- **History Search**: `HistorySearcherCoroutine.kt`
- **Applications Search**: `ApplicationsSearcherCoroutine.kt`
- **Tag Search**: `TagsSearcherCoroutine.kt`, `UntaggedSearcherCoroutine.kt`
- **Null Search**: `NullSearcherCoroutine.kt`
- **Base Abstract**: `PojoWithTagSearcherCoroutine.kt`

### ✅ 현재 사용 중

- Feature Flag 제거됨 (Coroutines가 기본)
- MainActivity에서 직접 `runTaskCoroutine()` 호출
- Legacy `Searcher.java` 클래스들은 유지 (호환성)

### ✅ 검증 완료

- 기능 동등성 유지 ✅
- 메모리 안전성 (WeakReference) ✅
- Single thread 검색 순서 보장 ✅
- 성능 동일 ✅

---

## 📊 Phase 2 개선 사항 (5개 항목)

### 우선순위 요약

| 항목 | 우선순위 | 소요 시간 | 리스크 | 효과 |
|------|---------|---------|-------|------|
| 1. Thread Safety | 🟡 Medium | 0.5일 | Low | 명시적 안전성 |
| 2. Error Handling | 🟡 Medium | 0.5일 | Low | 디버깅 개선 |
| 3. Cancellation Checks | 🔴 High | 1일 | Low | 응답성 향상 |
| 4. Static Cache | 🟢 Low | 0.5일 | Low | 코드 품질 |
| 5. Logging | 🟢 Low | 0.5일 | Very Low | 일관성 |

**총 예상 소요 시간**: 3일

---

## 🔴 High Priority: Cancellation Checks

### 현재 상태 (Phase 1)

```kotlin
// SearcherCoroutine.kt - addResults()만 체크
open fun addResults(pojos: List<Pojo>): Boolean {
    if (isCancelled()) {  // ✅ 체크 있음
        return false
    }
    return processedPojos.addAll(pojos)
}

// doInBackground()는 체크 없음
protected abstract suspend fun doInBackground()
```

### 문제점

```kotlin
// QuerySearcherCoroutine.kt 예시
override suspend fun doInBackground() {
    val activity = activityWeakReference.get() ?: return
    
    // ⚠️ DB 조회 - 긴 작업이지만 취소 체크 없음
    val lastIdsForQuery = DBHelper.getPreviousResultsForQuery(activity, query)
    
    // ⚠️ HashMap 생성 - 큰 데이터셋에서 느릴 수 있음
    val knownIds = HashMap<String, Int>()
    for (id in lastIdsForQuery) {
        knownIds[id.record] = id.value
    }
    
    // ⚠️ Provider 요청 전에도 취소 체크 없음
    dataHandler.requestResults(query, searcherAdapter)
}
```

**영향**:

- 사용자가 검색을 취소해도 긴 작업은 계속 실행
- 불필요한 CPU 및 메모리 사용
- 다음 검색 시작이 지연됨

### 개선 방안

```kotlin
override suspend fun doInBackground() {
    val activity = activityWeakReference.get() ?: return
    
    // ✅ DB 조회 전 취소 확인
    if (!isActive) return
    
    val lastIdsForQuery = withContext(Dispatchers.IO) {
        DBHelper.getPreviousResultsForQuery(activity, query)
    }
    
    // ✅ HashMap 생성 전 취소 확인
    if (!isActive) return
    
    val knownIds = HashMap<String, Int>()
    for (id in lastIdsForQuery) {
        // ✅ 큰 루프는 주기적으로 확인
        if (!isActive) return
        knownIds[id.record] = id.value
    }
    
    // ✅ Provider 요청 전 취소 확인
    if (!isActive) return
    
    dataHandler.requestResults(query, searcherAdapter)
}
```

### 적용 대상

- `QuerySearcherCoroutine.kt` - DB 조회 및 HashMap 생성
- `HistorySearcherCoroutine.kt` - DB 조회
- `ApplicationsSearcherCoroutine.kt` - 앱 목록 필터링
- `PojoWithTagSearcherCoroutine.kt` - 태그 필터링

### 기대 효과

- ⚡ **즉시 취소 반응**: 사용자가 검색을 취소하면 즉시 중단
- 💾 **리소스 절약**: 불필요한 작업 방지
- 🚀 **빠른 전환**: 다음 검색이 더 빠르게 시작

---

## 🟡 Medium Priority: Thread Safety

### 현재 상태 (Phase 1)

```kotlin
// SearcherCoroutine.kt (line 50-51)
// PriorityQueue for result processing (same as Searcher.java)
private val processedPojos = PriorityQueue<Pojo>(DEFAULT_MAX_RESULTS, RelevanceComparator())

// Line 87-92
open fun addResults(pojos: List<Pojo>): Boolean {
    if (isCancelled()) {
        return false
    }
    return processedPojos.addAll(pojos)  // ⚠️ Not thread-safe
}
```

### 문제점

- `PriorityQueue`는 thread-safe하지 않음
- `addResults()`는 Provider들이 **비동기적으로** 호출 가능
- 현재는 Single thread executor로 우연히 안전하지만, 명시적 보호 없음

### 개선 방안

#### Option A: Synchronized 추가 (간단, 즉시 적용 가능)

```kotlin
open fun addResults(pojos: List<Pojo>): Boolean {
    if (isCancelled()) {
        return false
    }
    synchronized(processedPojos) {
        return processedPojos.addAll(pojos)
    }
}
```

**장점**:

- 최소 변경
- 명시적 thread safety
- 기존 코드와 100% 호환

**단점**:

- 약간의 성능 오버헤드 (실제로는 미미함)

#### Option B: ConcurrentLinkedQueue 사용 (더 나은 설계)

```kotlin
// Phase 2 개선
private val processedPojos = ConcurrentLinkedQueue<Pojo>()

open fun addResults(pojos: List<Pojo>): Boolean {
    if (isCancelled()) {
        return false
    }
    return processedPojos.addAll(pojos)  // ✅ Thread-safe by design
}

// onPostExecute()에서 정렬 필요
protected open fun onPostExecute() {
    // ...
    
    // Convert to sorted list
    val sortedList = processedPojos.toList()
        .sortedWith(RelevanceComparator())
        .take(getMaxResultCount())
    
    val results = sortedList.map { Result.fromPojo(activity, it) }
    
    // ...
}
```

**장점**:

- Lock-free (더 나은 성능)
- 명시적 thread-safe design
- 더 현대적인 Kotlin 스타일

**단점**:

- 더 많은 변경 필요
- onPostExecute() 로직 수정 필요

### 권장 사항

**단기 (Phase 2.1)**: Option A (Synchronized)

- 빠르고 안전한 수정
- 리스크 최소

**장기 (Phase 2.2 또는 Phase 3)**: Option B (ConcurrentLinkedQueue)

- 더 나은 설계
- 성능 최적화 기회

---

## 🟡 Medium Priority: Error Handling

### 현재 상태 (Phase 1)

```kotlin
// SearcherCoroutine.kt (line 118-130)
currentJob = CoroutineScope(Dispatchers.Main).launch {
    try {
        onPreExecute()
        
        withContext(searchDispatcher) {
            doInBackground()
        }
        
        onPostExecute()
        
    } catch (e: Exception) {
        // ⚠️ 모든 Exception을 "취소"로 처리
        Log.e(TAG, "Error in searcher", e)
        onCancelled()
    }
}
```

### 문제점

- 모든 Exception을 "취소"로 처리
- 실제 에러와 정상 취소를 구분 못함
- 사용자에게 에러 피드백 없음
- 디버깅 어려움

### 개선 방안

#### Option A: 에러 타입별 처리

```kotlin
currentJob = CoroutineScope(Dispatchers.Main).launch {
    try {
        onPreExecute()
        
        withContext(searchDispatcher) {
            doInBackground()
        }
        
        onPostExecute()
        
    } catch (e: CancellationException) {
        // ✅ 정상적인 취소
        Log.d(TAG, "Search cancelled: ${this@SearcherCoroutine::class.simpleName}")
        onCancelled()
        
    } catch (e: Exception) {
        // ✅ 실제 에러 처리
        Log.e(TAG, "Error in ${this@SearcherCoroutine::class.simpleName}", e)
        onError(e)
    }
}
```

#### Option B: 에러 콜백 추가 + 사용자 피드백

```kotlin
/**
 * Called when search encounters an error
 * Can be overridden for custom error handling
 */
protected open fun onError(error: Exception) {
    Log.e(TAG, "Error in ${this::class.simpleName}: ${error.message}", error)
    
    // Optional: 사용자에게 토스트 메시지
    val activity = activityWeakReference.get()
    if (activity != null && !activity.isFinishing) {
        // Only show error for non-trivial exceptions
        if (error !is CancellationException) {
            activity.runOnUiThread {
                android.widget.Toast.makeText(
                    activity,
                    "Search error: ${error.localizedMessage}",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    // UI 정리
    onCancelled()
}
```

### 적용 대상

- `SearcherCoroutine.kt` - Base class에 추가
- 모든 서브클래스에 자동 적용
- 특정 Searcher는 `onError()` 오버라이드 가능

### 기대 효과

- 🐛 **디버깅 개선**: 에러와 취소를 명확히 구분
- 📊 **에러 추적**: Amplitude에 에러 이벤트 추가 가능
- 👤 **사용자 경험**: 에러 발생 시 피드백 제공 (선택적)

---

## 🟢 Low Priority: Static Cache Removal

### 현재 상태 (Phase 1)

```kotlin
// QuerySearcherCoroutine.kt
class QuerySearcherCoroutine(...) : SearcherCoroutine(...) {
    companion object {
        @Volatile
        private var MAX_RESULT_COUNT = -1  // ⚠️ Mutable static state
        
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
}
```

### 문제점

- Mutable static state (테스트 어려움)
- SharedPreferences 변경 시 명시적 clear 필요
- 멀티 인스턴스 환경에서 혼란 가능성

### 개선 방안

#### Option A: Instance 변수로 변경 (간단)

```kotlin
class QuerySearcherCoroutine(...) : SearcherCoroutine(...) {
    
    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
    
    // ✅ Instance 변수로 변경
    private var maxResultCount: Int? = null
    
    override fun getMaxResultCount(): Int {
        if (maxResultCount == null) {
            maxResultCount = prefs.getString("number-of-display-elements", "50")
                .toIntOrNull() ?: DEFAULT_MAX_RESULTS
        }
        return maxResultCount!!
    }
}
```

**장점**:

- 더 깨끗한 코드
- 테스트 용이
- Static state 제거

**단점**:

- 인스턴스마다 캐시 중복 (실제로는 문제 없음)

#### Option B: SharedPreferences Listener 사용 (더 나은 설계)

```kotlin
class QuerySearcherCoroutine(...) : SearcherCoroutine(...) {
    
    private var maxResultCount: Int? = null
    
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "number-of-display-elements") {
            maxResultCount = null  // ✅ 자동 캐시 무효화
        }
    }
    
    init {
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
    }
    
    override fun getMaxResultCount(): Int {
        if (maxResultCount == null) {
            maxResultCount = prefs.getString("number-of-display-elements", "50")
                .toIntOrNull() ?: DEFAULT_MAX_RESULTS
        }
        return maxResultCount!!
    }
    
    // Cleanup
    fun cleanup() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
    }
}
```

**장점**:

- 설정 변경 시 자동 반영
- 명시적 clear 불필요

**단점**:

- Listener 관리 필요
- cleanup() 호출 필요

### 권장 사항

**Phase 2**: Option A (Instance 변수)

- 간단하고 효과적
- 리스크 최소

**Phase 3 (선택)**: Option B (Listener)

- 더 나은 설계
- 우선순위 낮음

---

## 🟢 Low Priority: Logging Consolidation

### 현재 상태 (Phase 1)

```kotlin
// SearcherCoroutine.kt (line 202-220)
private fun logPerformance(activity: MainActivity) {
    val time = System.currentTimeMillis() - startTime
    
    // Android Log
    Log.v(TAG, "Time to run query `$query` on ${this::class.simpleName} to completion: ${time}ms")
    
    try {
        // Amplitude 로깅
        val eventProperties = JSONObject()
        eventProperties.put("type", this::class.simpleName)
        eventProperties.put("length", query?.replace("<null>", "")?.length ?: 0)
        eventProperties.put("time", time)
        
        val dataHandler = KissApplication.getApplication(activity).dataHandler
        eventProperties.put("allProvidersHaveLoaded", dataHandler.allProvidersHaveLoaded)
        
        Amplitude.getInstance().logEvent("Search", eventProperties)
    } catch (e: JSONException) {
        e.printStackTrace()
    }
}
```

### 개선 방안

#### 통합 로깅 유틸리티

```kotlin
// SearchPerformanceLogger.kt (새 파일)
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
    
    fun log(metrics: SearchMetrics) {
        // Android Log
        val status = when {
            metrics.error != null -> "ERROR"
            metrics.cancelled -> "CANCELLED"
            else -> "COMPLETED"
        }
        
        Log.v(TAG, buildString {
            append("[$status] ")
            append("${metrics.searcherType} ")
            append("query='${metrics.query}' ")
            append("time=${metrics.timeMs}ms ")
            append("results=${metrics.resultCount} ")
            append("providersLoaded=${metrics.allProvidersLoaded}")
            
            if (metrics.error != null) {
                append(" error=${metrics.error.message}")
            }
        })
        
        // Amplitude 로깅
        try {
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
            
            Amplitude.getInstance().logEvent("Search", eventProperties)
        } catch (e: JSONException) {
            Log.e(TAG, "Failed to log to Amplitude", e)
        }
    }
}
```

#### SearcherCoroutine 적용

```kotlin
// SearcherCoroutine.kt 수정
protected open fun onPostExecute() {
    // ... 기존 로직 ...
    
    // ✅ 통합 로깅
    logPerformance(activity, cancelled = false, error = null)
}

protected open fun onCancelled() {
    // ... 기존 로직 ...
    
    val activity = activityWeakReference.get() ?: return
    logPerformance(activity, cancelled = true, error = null)
}

protected open fun onError(error: Exception) {
    // ... 기존 로직 ...
    
    val activity = activityWeakReference.get() ?: return
    logPerformance(activity, cancelled = false, error = error)
}

private fun logPerformance(
    activity: MainActivity,
    cancelled: Boolean,
    error: Exception?
) {
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

### 기대 효과

- 📊 **일관된 로깅**: 모든 상태에서 동일한 형식
- 🐛 **디버깅 개선**: 에러 추적 용이
- 📈 **분석 향상**: 더 많은 메트릭스 수집

---

## 📋 Phase 2 실행 계획

### 추천 순서

```
Phase 2.1: Critical Improvements (2일)
├── Day 1: Cancellation Checks 🔴
│   ├── QuerySearcherCoroutine (2시간)
│   ├── HistorySearcherCoroutine (1시간)
│   ├── ApplicationsSearcherCoroutine (2시간)
│   └── PojoWithTagSearcherCoroutine (1시간)
│
└── Day 2: Thread Safety + Error Handling 🟡
    ├── Thread Safety: Synchronized (2시간)
    ├── Error Handling: Type distinction (2시간)
    └── Testing & Verification (2시간)

Phase 2.2: Code Quality (1일) - 선택 사항
├── Morning: Static Cache Removal 🟢
│   └── QuerySearcherCoroutine refactoring (3시간)
│
└── Afternoon: Logging Consolidation 🟢
    ├── SearchPerformanceLogger.kt (2시간)
    └── Integration & Testing (1시간)
```

### 검증 기준

각 개선 사항마다:

1. **기능 테스트**
   - 검색 기능 정상 동작
   - 취소 동작 정상
   - 에러 핸들링 정상

2. **성능 테스트**
   - 검색 속도 유지 또는 개선
   - 메모리 사용량 증가 없음
   - 취소 반응 속도 개선 (Cancellation Checks)

3. **안정성 테스트**
   - 메모리 누수 없음
   - Race condition 없음 (Thread Safety)
   - Exception 처리 정상 (Error Handling)

---

## 🎯 Phase 2 목표 및 성공 기준

### 목표

1. **안정성 향상** 🛡️
   - Thread-safe 결과 수집
   - 명시적 에러 처리
   - 빠른 취소 반응

2. **코드 품질 개선** 📝
   - Static state 제거
   - 일관된 로깅
   - 테스트 용이성 향상

3. **사용자 경험 개선** 🚀
   - 더 빠른 취소 반응
   - 에러 피드백 (선택적)
   - 더 안정적인 검색

### 성공 기준

- ✅ 모든 High Priority 항목 완료
- ✅ Thread Safety 보장 (synchronized 또는 concurrent collection)
- ✅ Error와 Cancellation 구분
- ✅ Cancellation checks in all long operations
- ✅ 기존 기능 100% 유지
- ✅ 성능 저하 없음
- ✅ 메모리 누수 없음

### Optional Goals (Low Priority)

- 🟢 Static cache 제거
- 🟢 로깅 통합
- 🟢 에러 메시지 사용자 표시

---

## 📝 변경 이력

- **2025-01-17**: Phase 2 계획 문서 작성
  - Phase 1 마이그레이션 완료 확인
  - 5가지 개선 사항 정리
  - 우선순위 및 실행 계획 수립

---

## 🔗 관련 문서

- **Phase 1**:
  - [step1-analysis-report.md](./step1-analysis-report.md) - 초기 분석
  - [step2-searcher-base-implementation.md](./step2-searcher-base-implementation.md) - Base 클래스
  - [step3-summary.md](./step3-summary.md) - QuerySearcher 완료
  - [step4-summary.md](./step4-summary.md) - 나머지 Searcher 완료
  - [step5-legacy-cleanup-summary.md](./step5-legacy-cleanup-summary.md) - 정리 완료

- **현재**: `SearcherCoroutine.kt` (245 lines) - 프로덕션 사용 중

- **다음**: Phase 2 개선 사항 적용
