# Searcher 시스템 개선 사항 분석 (Phase 2 TODO)

**작성일**: 2025-10-14  
**Branch**: step1-searcher-analysis  
**적용 시점**: ⚠️ **마이그레이션 완료 후** (Phase 2)

---

## 🎯 전략: "One Thing at a Time"

### Phase 1: AsyncTask → Coroutines 마이그레이션 (현재 진행 중)
- ✅ **목표**: 기능 동등성 유지
- ✅ **검증**: 기존과 동일하게 동작
- ✅ **범위**: Searcher 8개 클래스 전환

### Phase 2: 코드 개선 (마이그레이션 완료 후)
- � **목표**: 성능 및 안정성 향상
- 📋 **검증**: 더 나은 코드 품질
- 📋 **범위**: 아래 5가지 개선 사항

**Why Separate?**
1. 검증 단순화 ("동일 동작" vs "개선 효과")
2. 리스크 분산 (한 번에 한 가지만)
3. 명확한 진행 상황 추적

---

## �📊 발견된 개선 가능 사항 (Phase 2 적용)

### 1. 🟡 PriorityQueue Thread Safety 이슈 (Medium Priority)

#### 현재 코드
```java
public abstract class Searcher implements Runnable {
    private final PriorityQueue<Pojo> processedPojos;
    
    public boolean addResults(List<? extends Pojo> pojos) {
        if (isCancelled())
            return false;
        return this.processedPojos.addAll(pojos);  // ⚠️ Not thread-safe
    }
}
```

#### 문제점
- `PriorityQueue`는 thread-safe하지 않음
- `addResults()`는 Provider들이 **비동기적으로** 호출 가능
- 현재는 Single thread executor로 우연히 안전하지만, 명시적 보호 없음

#### 개선 방안

**Option A: Synchronized 추가 (즉시 적용 가능)**
```java
public synchronized boolean addResults(List<? extends Pojo> pojos) {
    if (isCancelled())
        return false;
    return this.processedPojos.addAll(pojos);
}
```

**Option B: ConcurrentLinkedQueue 사용 (Coroutines 전환 시)**
```kotlin
private val processedPojos = ConcurrentLinkedQueue<Pojo>()
```

#### 권장 사항
- ✅ **Coroutines 전환 시 함께 개선** (지금은 변경하지 않음)
- Single thread executor가 현재는 안전성 보장
- Coroutines에서 적절한 동기화 메커니즘 사용

---

### 2. 🟢 QuerySearcher의 static 캐시 (Low Priority)

#### 현재 코드
```java
public class QuerySearcher extends Searcher {
    private static int MAX_RESULT_COUNT = -1;  // ⚠️ Mutable static
    
    @Override
    protected int getMaxResultCount() {
        if (MAX_RESULT_COUNT == -1) {
            MAX_RESULT_COUNT = Double.valueOf(prefs.getString(...)).intValue();
        }
        return MAX_RESULT_COUNT;
    }
    
    public static void clearMaxResultCountCache() {
        MAX_RESULT_COUNT = -1;
    }
}
```

#### 문제점
- Mutable static state (테스트 어려움)
- SharedPreferences 변경 시 명시적 clear 필요
- 멀티 인스턴스 환경에서 혼란 가능성

#### 개선 방안

**Option A: Instance 변수로 변경**
```kotlin
class QuerySearcherCoroutine(...) {
    private var maxResultCount: Int? = null
    
    override fun getMaxResultCount(): Int {
        if (maxResultCount == null) {
            maxResultCount = prefs.getString(...).toIntOrNull() ?: DEFAULT_MAX_RESULTS
        }
        return maxResultCount!!
    }
}
```

**Option B: SharedPreferences Listener 사용**
```kotlin
init {
    prefs.registerOnSharedPreferenceChangeListener { _, key ->
        if (key == "number-of-display-elements") {
            maxResultCount = null  // 캐시 무효화
        }
    }
}
```

#### 권장 사항
- ✅ **Coroutines 전환 시 Option A 적용**
- 더 깨끗한 코드, 테스트 용이
- 현재는 동작하므로 변경 불필요

---

### 3. 🟡 Exception Handling 개선 (Medium Priority)

#### 현재 코드
```java
@Override
public final void run() {
    mainHandler.post(this::onPreExecute);
    
    try {
        doInBackground();
        mainHandler.post(this::onPostExecute);
    } catch (Exception e) {
        Log.e(TAG, "Error in searcher", e);
        mainHandler.post(this::onCancelled);  // ⚠️ 에러를 취소로 처리
    }
}
```

#### 문제점
- 모든 Exception을 "취소"로 처리
- 실제 에러와 취소를 구분 못함
- 사용자에게 에러 피드백 없음

#### 개선 방안

**Option A: 에러 타입별 처리**
```kotlin
try {
    doInBackground()
    onPostExecute()
} catch (e: CancellationException) {
    onCancelled()  // 정상적인 취소
} catch (e: Exception) {
    onError(e)  // 실제 에러 처리
}
```

**Option B: 에러 콜백 추가**
```kotlin
protected open fun onError(error: Exception) {
    Log.e(TAG, "Error in searcher", error)
    
    // 선택적으로 사용자에게 토스트 메시지
    val activity = getActivity()
    if (activity != null && error !is CancellationException) {
        Toast.makeText(activity, 
            "Search error: ${error.message}", 
            Toast.LENGTH_SHORT).show()
    }
    
    onCancelled()  // UI 정리
}
```

#### 권장 사항
- ✅ **Coroutines 전환 시 Option A + B 적용**
- 더 나은 디버깅
- 사용자 경험 향상
- 현재는 동작하므로 변경 불필요

---

### 4. 🟢 성능 로깅 중복 (Low Priority)

#### 현재 코드
```java
protected void onPostExecute() {
    // ... 결과 처리 ...
    
    long time = System.currentTimeMillis() - start;
    Log.v(TAG, "Time to run query `" + query + "`...");  // Android Log
    
    // Amplitude 로깅
    JSONObject eventProperties = new JSONObject();
    eventProperties.put("time", time);
    Amplitude.getInstance().logEvent("Search", eventProperties);
}
```

#### 개선 방안
```kotlin
protected open fun onPostExecute() {
    // ... 결과 처리 ...
    
    val time = System.currentTimeMillis() - start
    
    // 통합 로깅 유틸
    SearchPerformanceLogger.log(
        searcherType = this::class.simpleName,
        query = query,
        timeMs = time,
        resultCount = processedPojos.size,
        allProvidersLoaded = dataHandler.allProvidersHaveLoaded
    )
}
```

#### 권장 사항
- 🔵 **선택 사항** (Nice to have)
- 로깅 일관성 향상
- 낮은 우선순위

---

### 5. 🔴 취소 체크 타이밍 개선 (High Priority - Coroutines 전환 시)

#### 현재 코드
```java
public boolean addResults(List<? extends Pojo> pojos) {
    if (isCancelled())  // ✅ 체크 있음
        return false;
    return this.processedPojos.addAll(pojos);
}

protected void doInBackground() {
    // ⚠️ 긴 작업 중간에 취소 체크 없음
    List<ValuedHistoryRecord> lastIdsForQuery = 
        DBHelper.getPreviousResultsForQuery(activity, query);  // DB 조회
    
    // HashMap 생성
    knownIds = new HashMap<>();
    for (ValuedHistoryRecord id : lastIdsForQuery) {
        knownIds.put(id.record, id.value);
    }
    
    // Provider 요청
    dataHandler.requestResults(query, this);
}
```

#### 개선 방안 (Coroutines)
```kotlin
protected suspend fun doInBackground() {
    val activity = getActivity() ?: return
    
    // DB 조회 전 취소 확인
    if (!isActive) return
    
    val lastIdsForQuery = withContext(Dispatchers.IO) {
        DBHelper.getPreviousResultsForQuery(activity, query)
    }
    
    // HashMap 생성 전 취소 확인
    if (!isActive) return
    
    val knownIds = HashMap<String, Int>()
    for (id in lastIdsForQuery) {
        // 큰 루프는 주기적으로 확인
        if (!isActive) return
        knownIds[id.record] = id.value
    }
    
    // Provider 요청 전 취소 확인
    if (!isActive) return
    
    dataHandler.requestResults(query, this)
}
```

#### 권장 사항
- ✅ **Coroutines 전환 시 반드시 적용**
- 빠른 취소 반응
- 불필요한 작업 방지
- 메모리 및 CPU 절약

---

## 📋 개선 사항 우선순위 및 적용 시점

### 🔴 High Priority (Coroutines 전환 시 필수)

1. **취소 체크 타이밍 개선**
   - **적용 시점**: Step 3 (QuerySearcher 전환)
   - **이유**: 응답성 및 성능

### 🟡 Medium Priority (Coroutines 전환 시 권장)

2. **PriorityQueue Thread Safety**
   - **적용 시점**: Step 2 (Base 클래스)
   - **방법**: 적절한 동기화 메커니즘

3. **Exception Handling 개선**
   - **적용 시점**: Step 2 (Base 클래스)
   - **방법**: 에러 타입별 처리

### 🟢 Low Priority (선택 사항)

4. **Static 캐시 제거**
   - **적용 시점**: Step 3 (QuerySearcher)
   - **방법**: Instance 변수로 변경

5. **성능 로깅 통합**
   - **적용 시점**: Step 5 (정리 단계)
   - **방법**: 유틸리티 클래스 생성

---

## ✅ 최종 권장 사항

### Phase 1: 마이그레이션만 집중 ⭐⭐⭐

**현재 Searcher 시스템 → SearcherCoroutine 전환 시**:
- ✅ **기능 동등성 유지** (가장 중요)
- ✅ 최소한의 변경만
- ✅ 기존 패턴 그대로 유지
- ❌ 개선 사항 반영 **안 함**

**이유**:
1. **검증 단순화**: "기존과 동일하게 동작하는가?"만 확인
2. **리스크 최소화**: 한 번에 한 가지만 변경
3. **Rollback 용이**: 문제 발생 시 원인 명확

### Phase 2: 마이그레이션 완료 후 개선 ⭐⭐

#### Phase 1: 기본 Coroutines 전환 (마이그레이션)
```kotlin
abstract class SearcherCoroutine(...) {
    // ✅ 기존 패턴 유지 (PriorityQueue 그대로)
    private val processedPojos = PriorityQueue<Pojo>(...)
    
    // ✅ 기본 에러 처리만 (기존과 동일)
    suspend fun execute(): Job {
        return CoroutineScope(Dispatchers.Main).launch {
            try {
                onPreExecute()
                withContext(searchDispatcher) {
                    doInBackground()
                }
                onPostExecute()
            } catch (e: Exception) {
                Log.e(TAG, "Error", e)
                onCancelled()  // 기존과 동일
            }
        }
    }
}
```

#### Phase 2: 개선 작업 (마이그레이션 완료 후)
```kotlin
abstract class SearcherCoroutine(...) {
    // 🆕 개선 1: Thread-safe 결과 수집
    private val processedPojos = ConcurrentLinkedQueue<Pojo>()
    
    // 🆕 개선 2: 에러 타입별 처리
    suspend fun execute(): Job {
        return coroutineScope {
            try {
                onPreExecute()
                doInBackground()
                onPostExecute()
            } catch (e: CancellationException) {
                onCancelled()
            } catch (e: Exception) {
                onError(e)  // 새로운 에러 처리
            }
        }
    }
}
```

---

## 🎯 최종 결론 및 전략

### 전략: "Migrate First, Improve Later" ⭐

```
┌─────────────────────────────────────────────────────────┐
│ Phase 1: AsyncTask → Coroutines 마이그레이션             │
├─────────────────────────────────────────────────────────┤
│ Step 1: 분석 ✅                                          │
│ Step 2: Base 클래스 (기능 동등성 유지)                    │
│ Step 3: QuerySearcher (기능 동등성 유지)                 │
│ Step 4: 나머지 Searcher (기능 동등성 유지)                │
│ Step 5: Legacy 코드 정리                                 │
│                                                         │
│ 검증: "기존과 동일하게 동작하는가?"                        │
└─────────────────────────────────────────────────────────┘
                         ↓
              마이그레이션 완료 후
                         ↓
┌─────────────────────────────────────────────────────────┐
│ Phase 2: 코드 개선 (별도 작업)                            │
├─────────────────────────────────────────────────────────┤
│ Improvement 1: Thread Safety                            │
│ Improvement 2: Error Handling                           │
│ Improvement 3: Cancellation Checks                      │
│ Improvement 4: Static State Removal                     │
│ Improvement 5: Logging Consolidation                    │
│                                                         │
│ 검증: "더 나아졌는가?"                                    │
└─────────────────────────────────────────────────────────┘
```

### 장점 (Phase 분리)

1. **검증 단순화**
   - Phase 1: 동일 동작만 확인
   - Phase 2: 개선 효과 측정

2. **리스크 분산**
   - 한 번에 한 가지 변경
   - 문제 발생 시 원인 명확

3. **진행 상황 명확**
   - Milestone 추적 용이
   - 언제든 중단/재개 가능

4. **Rollback 용이**
   - Phase 1 실패 → 전체 rollback
   - Phase 2 실패 → Phase 1 유지

---

## 📝 Phase 1: Step 2 계획 (기능 동등성 유지)

### SearcherCoroutine.kt 기본 설계 (개선 사항 제외)

```kotlin
abstract class SearcherCoroutine(
    activity: MainActivity,
    protected val query: String?,
    private val isRefresh: Boolean
) {
    companion object {
        // ✅ Single thread 보장 (필수)
        private val searchDispatcher = Dispatchers.IO.limitedParallelism(1)
    }
    
    // ✅ WeakReference (기존 패턴 유지)
    private val activityRef = WeakReference(activity)
    
    // ✅ PriorityQueue (기존과 동일)
    private val processedPojos = PriorityQueue<Pojo>(
        DEFAULT_MAX_RESULTS, 
        RelevanceComparator()
    )
    
    private var currentJob: Job? = null
    
    // ✅ 기본 Coroutines 패턴 (개선 없이)
    fun execute(): Job {
        currentJob?.cancel()
        
        currentJob = CoroutineScope(Dispatchers.Main).launch {
            try {
                onPreExecute()
                
                withContext(searchDispatcher) {
                    doInBackground()
                }
                
                onPostExecute()
            } catch (e: Exception) {
                // ✅ 기존과 동일한 에러 처리
                Log.e(TAG, "Error in searcher", e)
                onCancelled()
            }
        }
        
        return currentJob!!
    }
    
    fun cancel() {
        currentJob?.cancel()
    }
    
    // ✅ 기존 메서드 시그니처 유지
    protected open fun onPreExecute() { /* ... */ }
    protected abstract suspend fun doInBackground()
    protected open fun onPostExecute() { /* ... */ }
    protected open fun onCancelled() { /* ... */ }
}
```

**주의**: Phase 2 개선 사항(Thread-safe, 에러 개선 등)은 **포함하지 않음**

---

## 📌 Phase 2 TODO List (마이그레이션 완료 후)

### 개선 작업 순서

1. **Improvement 1: Thread-safe 결과 수집** (1일)
   - PriorityQueue → ConcurrentLinkedQueue
   - 또는 synchronized 블록 추가

2. **Improvement 2: 에러 타입별 처리** (0.5일)
   - CancellationException 구분
   - onError() 콜백 추가

3. **Improvement 3: 취소 체크 타이밍** (1일)
   - doInBackground() 중간 체크
   - 빠른 취소 반응

4. **Improvement 4: Static 캐시 제거** (0.5일)
   - QuerySearcher instance 변수로

5. **Improvement 5: 성능 로깅 통합** (0.5일)
   - 로깅 유틸리티 클래스

**예상 총 소요**: 3-4일

---

**작성 완료**: 2025-10-14  
**Phase 1 다음**: Step 2 (기본 Coroutines 전환만)  
**Phase 2 시작**: 마이그레이션 완료 후
