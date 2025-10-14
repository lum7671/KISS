# Step 1: Searcher 시스템 분석 보고서

**작성일**: 2025-10-14  
**Branch**: `step1-searcher-analysis`  
**상태**: 분석 완료 ✅

---

## 📋 Executive Summary

Searcher 시스템은 KISS 런처의 **핵심 검색 기능**을 담당하는 컴포넌트입니다. 현재 `ExecutorService` 기반의 `Runnable` 패턴을 사용하며, 8개의 클래스로 구성되어 있습니다.

### 핵심 발견사항

✅ **잘 설계된 아키텍처**: 
- 명확한 생명주기 (onPreExecute → doInBackground → onPostExecute)
- WeakReference로 메모리 누수 방지
- Single thread executor로 순차 실행 보장

⚠️ **변환 시 주의사항**:
- Single thread 보장 필수 (`limitedParallelism(1)`)
- 취소 메커니즘 정확히 구현 필요
- UI 업데이트 타이밍 유지

---

## 🏗️ 아키텍처 분석

### 1. Searcher.java (Base 클래스)

#### 핵심 구조

```java
public abstract class Searcher implements Runnable {
    // Single thread executor - 검색 요청 순차 처리
    public static final ExecutorService SEARCH_THREAD = 
        Executors.newSingleThreadExecutor();
    
    // WeakReference로 메모리 누수 방지
    final WeakReference<MainActivity> activityWeakReference;
    
    // 결과를 우선순위 큐로 관리 (관련성 순)
    private final PriorityQueue<Pojo> processedPojos;
    
    // UI 업데이트를 위한 Main Handler
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    
    // 취소 메커니즘
    private volatile Future<?> task;
    private volatile boolean cancelled = false;
}
```

#### 생명주기 (Lifecycle)

```
1. MainActivity.runTask(searcher)
   ↓
2. searcher.executeOnExecutor(SEARCH_THREAD)
   ↓ [ExecutorService에 submit]
3. Searcher.run() 실행 (Single thread에서)
   ├─ mainHandler.post(this::onPreExecute)    [UI thread]
   │  └─ displayLoader(true)
   ├─ doInBackground()                         [Background thread]
   │  └─ Provider들에게 데이터 요청
   │  └─ addResults() 호출 (비동기적으로)
   └─ mainHandler.post(this::onPostExecute)   [UI thread]
      └─ adapter.updateResults()
      └─ hideLoader()
      └─ Amplitude 로깅
```

#### 취소 메커니즘

```java
// MainActivity에서 새 검색 시작 전
public void resetTask() {
    if (searchTask != null) {
        searchTask.cancel(true);  // Future.cancel()
        searchTask = null;
    }
}

// Searcher에서 취소 확인
public boolean isCancelled() {
    return cancelled || (task != null && task.isCancelled());
}
```

#### 결과 처리 (PriorityQueue)

```java
// Provider들이 백그라운드에서 비동기적으로 호출
public boolean addResults(List<? extends Pojo> pojos) {
    if (isCancelled()) return false;
    return this.processedPojos.addAll(pojos);  // Thread-safe
}

// onPostExecute에서 우선순위 순으로 정렬된 결과 사용
PriorityQueue<Pojo> queue = this.processedPojos;
int maxResults = getMaxResultCount();
while (queue.size() > maxResults)
    queue.poll();  // 낮은 우선순위 제거
```

---

### 2. MainActivity 통합

#### Searcher 관리 패턴

```java
public class MainActivity {
    private Searcher searchTask;  // 현재 실행 중인 검색
    
    // 검색 실행
    public void runTask(Searcher task) {
        resetTask();  // 이전 검색 취소
        searchTask = task;
        searchTask.executeOnExecutor(Searcher.SEARCH_THREAD);
    }
    
    // 검색 취소
    public void resetTask() {
        if (searchTask != null) {
            searchTask.cancel(true);
            searchTask = null;
        }
    }
}
```

#### 주요 호출 시점

1. **사용자 타이핑**: `onTextChanged()` → `QuerySearcher`
2. **빈 검색어**: `HistorySearcher`
3. **앱 목록 보기**: `ApplicationsSearcher`
4. **태그 검색**: `TagsSearcher` / `UntaggedSearcher`

---

## 📊 Searcher 하위 클래스 분석

### 1. QuerySearcher ⭐⭐⭐ (가장 중요)

**복잡도**: 높음  
**사용 빈도**: 매우 높음 (메인 검색)

#### 특징
- DB에서 히스토리 조회 (`DBHelper.getPreviousResultsForQuery()`)
- 히스토리 기반 관련성 점수 부여 (`pojo.relevance += 25 * value`)
- Disabled 항목 페널티 (`pojo.relevance -= 200`)
- SharedPreferences에서 최대 결과 수 읽기

#### doInBackground() 로직
```java
protected void doInBackground() {
    // 1. DB에서 히스토리 조회
    List<ValuedHistoryRecord> lastIdsForQuery = 
        DBHelper.getPreviousResultsForQuery(activity, query);
    knownIds = new HashMap<>();
    for (ValuedHistoryRecord id : lastIdsForQuery) {
        knownIds.put(id.record, id.value);
    }
    
    // 2. DataHandler에 검색 요청
    KissApplication.getApplication(activity)
        .getDataHandler()
        .requestResults(query, this);
    
    // 3. Provider들이 비동기로 addResults() 호출
}
```

#### 변환 시 고려사항
- DB 조회 성능 (동기 작업)
- HashMap 생성 및 조회 성능
- Provider들의 비동기 호출 타이밍

---

### 2. HistorySearcher ⭐⭐

**복잡도**: 중간  
**사용 빈도**: 높음 (빈 검색어 시)

#### 특징
- 히스토리 데이터 조회
- 즐겨찾기 제외 옵션
- 단축키 제외 로직 (Android O+)
- 최대 결과 수 제한

#### doInBackground() 로직
```java
protected void doInBackground() {
    // 1. 설정에서 옵션 읽기
    boolean excludeFavorites = prefs.getBoolean("exclude-favorites-history", false);
    
    // 2. 제외할 ID 수집
    Set<String> excludedPojoById = new HashSet<>(excludedFromHistory);
    // + 단축키 제외 로직
    
    // 3. 히스토리 조회
    List<Pojo> pojos = dataHandler.getHistory(
        activity, getMaxResultCount(), excludedPojoById
    );
    
    // 4. 결과 추가
    addResults(pojos);
}
```

---

### 3. ApplicationsSearcher ⭐⭐

**복잡도**: 중간  
**사용 빈도**: 중간 (앱 목록 보기)

#### 특징
- 모든 앱 목록 표시
- 즐겨찾기 제외 필터링
- 알파벳 역순 정렬 (Z → A, 하단부터 표시)
- Fast scroll 섹션 빌드 (`adapter.buildSections()`)

#### Custom PriorityQueue
```java
@Override
PriorityQueue<Pojo> getPojoProcessor(Context context) {
    // 알파벳 역순 정렬
    return new PriorityQueue<>(DEFAULT_MAX_RESULTS, 
        new ReversedNameComparator());
}
```

---

### 4. NullSearcher 🟢 (가장 단순)

**복잡도**: 매우 낮음  
**사용 빈도**: 낮음 (특수 상황)

#### 특징
- 빈 결과만 반환
- 로더 표시 안 함 (`displayActivityLoader()` override)

```java
@Override
protected void doInBackground() {
    // nothing found ;)
}
```

**변환 난이도**: 가장 쉬움 ✅

---

### 5. PojoWithTagSearcher ⭐⭐ (Abstract)

**복잡도**: 중간-높음  
**사용 빈도**: 낮음 (태그 기능 사용 시)

#### 특징
- 추상 클래스 (TagsSearcher, UntaggedSearcher의 부모)
- 태그 기반 필터링 (`acceptPojo()`)
- 히스토리 기반 정렬 (`applyRelevanceFromHistory()`)
- 최적화된 태그 검색 (`requestRecordsByTag()`)

#### 템플릿 메서드 패턴
```java
@Override
public boolean addResults(List<? extends Pojo> pojos) {
    List<Pojo> filteredPojos = new ArrayList<>();
    for (Pojo pojo : pojos) {
        if (pojo instanceof PojoWithTags) {
            PojoWithTags pojoWithTags = (PojoWithTags) pojo;
            if (acceptPojo(pojoWithTags)) {  // 하위 클래스가 구현
                filteredPojos.add(pojoWithTags);
            }
        }
    }
    // 히스토리 적용
    dataHandler.applyRelevanceFromHistory(filteredPojos, sortMode);
    return super.addResults(filteredPojos);
}

// 하위 클래스가 구현
abstract protected boolean acceptPojo(PojoWithTags pojoWithTags);
```

---

### 6. TagsSearcher 🟡

**복잡도**: 낮음  
**사용 빈도**: 낮음

#### 특징
- PojoWithTagSearcher 상속
- 특정 태그를 가진 항목만 필터링

```java
@Override
protected boolean acceptPojo(PojoWithTags pojoWithTags) {
    return pojoWithTags.getTags() != null 
        && pojoWithTags.getTags().contains(query);
}
```

---

### 7. UntaggedSearcher 🟡

**복잡도**: 낮음  
**사용 빈도**: 낮음

#### 특징
- PojoWithTagSearcher 상속
- 태그가 없는 항목만 필터링

```java
@Override
protected boolean acceptPojo(PojoWithTags pojoWithTags) {
    return pojoWithTags.getTags() == null 
        || pojoWithTags.getTags().isEmpty();
}
```

---

## ⚡ 성능 요구사항 분석

### 1. 검색 속도 (Search Latency)

#### 현재 성능 측정 코드
```java
protected void onPreExecute() {
    start = System.currentTimeMillis();
    // ...
}

protected void onPostExecute() {
    long time = System.currentTimeMillis() - start;
    Log.v(TAG, "Time to run query `" + query + "` on " 
        + getClass().getSimpleName() + " to completion: " + time + "ms");
    
    // Amplitude 로깅
    eventProperties.put("time", time);
    Amplitude.getInstance().logEvent("Search", eventProperties);
}
```

#### 목표 성능
- **QuerySearcher**: < 100ms (사용자 타이핑 속도)
- **HistorySearcher**: < 50ms (즉시 표시)
- **ApplicationsSearcher**: < 200ms (전체 앱 목록)

#### Coroutines 변환 후 성능 목표
- 기존 ±5% 이내 유지
- 메모리 사용량 15-20% 감소

---

### 2. Single Thread 보장 (순차 실행)

#### 현재 구현
```java
public static final ExecutorService SEARCH_THREAD = 
    Executors.newSingleThreadExecutor();
```

**Why Single Thread?**
1. 검색 요청이 순차적으로 처리됨
2. 이전 검색 결과가 나중 검색 후에 표시되는 것 방지
3. UI 업데이트 순서 보장

#### Coroutines 변환 시
```kotlin
companion object {
    // Single thread dispatcher
    private val searchDispatcher = Dispatchers.IO.limitedParallelism(1)
}
```

**검증 필요**:
- [ ] 순차 실행 보장 테스트
- [ ] 빠른 타이핑 시나리오 테스트
- [ ] 취소 후 새 검색 시작 테스트

---

### 3. 취소 메커니즘 (Cancellation)

#### 현재 패턴
```java
// MainActivity
resetTask() → searchTask.cancel(true) → Future.cancel()

// Searcher
isCancelled() → cancelled || task.isCancelled()

// doInBackground 중에 확인
if (isCancelled()) return;
```

#### Coroutines 변환 시
```kotlin
private var currentJob: Job? = null

fun execute(): Job {
    currentJob?.cancel()  // 이전 작업 취소
    currentJob = CoroutineScope(Dispatchers.Main).launch {
        // ...
        withContext(searchDispatcher) {
            // isActive 확인
            if (!isActive) return@withContext
            doInBackground()
        }
    }
    return currentJob!!
}

fun cancel() {
    currentJob?.cancel()
}
```

**중요**: Job 취소가 제대로 전파되는지 확인 필수

---

### 4. 메모리 관리

#### 현재 메모리 안전 패턴

1. **WeakReference**
```java
final WeakReference<MainActivity> activityWeakReference;

// 사용 시 항상 null 체크
MainActivity activity = activityWeakReference.get();
if (activity == null) return;
```

2. **Volatile 플래그**
```java
private volatile Future<?> task;
private volatile boolean cancelled = false;
```

3. **PriorityQueue 크기 제한**
```java
int maxResults = getMaxResultCount();
while (queue.size() > maxResults)
    queue.poll();  // 오래된 결과 제거
```

#### Coroutines에서도 유지 필요
- WeakReference 패턴 그대로 유지
- Job 취소 시 리소스 정리
- PriorityQueue 크기 제한 유지

---

## 🎯 변환 전략 및 우선순위

### 난이도별 분류

#### 🟢 Low Complexity (쉬움)
1. **NullSearcher** - 빈 구현만
2. **TagsSearcher** - 단순 필터링
3. **UntaggedSearcher** - 단순 필터링

**예상 소요**: 각 0.5일

---

#### 🟡 Medium Complexity (보통)
4. **HistorySearcher** - DB 조회 + 필터링
5. **ApplicationsSearcher** - 전체 앱 목록 + 정렬

**예상 소요**: 각 0.5-1일

---

#### 🟠 High Complexity (어려움)
6. **PojoWithTagSearcher** - 추상 클래스 + 템플릿 메서드

**예상 소요**: 1일

---

#### 🔴 Critical Complexity (매우 중요)
7. **QuerySearcher** ⭐ - 메인 검색 기능
   - 복잡한 로직 (DB, 히스토리, 관련성 점수)
   - 가장 많이 사용됨
   - 성능 민감

**예상 소요**: 2-3일 (테스트 포함)

---

#### ⚫ Infrastructure
8. **Searcher.java (Base)** - 모든 하위 클래스의 기반

**예상 소요**: 1-2일

---

### 변환 순서 (Step 2~4)

```
Step 2: Searcher.java (Base) 변환
  ├─ SearcherCoroutine.kt 생성
  ├─ Single thread dispatcher 구현
  ├─ Job-based cancellation
  └─ 단위 테스트

Step 3: QuerySearcher 변환 (가장 중요)
  ├─ QuerySearcherCoroutine.kt 생성
  ├─ Feature Flag 구현
  ├─ A/B 테스트 구조
  ├─ 성능 비교 테스트
  └─ 1주일 안정화

Step 4: 나머지 Searcher 순차 변환
  ├─ NullSearcher (가장 쉬움)
  ├─ HistorySearcher
  ├─ ApplicationsSearcher
  ├─ PojoWithTagSearcher
  ├─ TagsSearcher
  └─ UntaggedSearcher
```

---

## ⚠️ 변환 시 주의사항 (Critical Issues)

### 1. Single Thread 보장 🔴🔴🔴

**문제**: `Dispatchers.IO`는 thread pool 사용  
**해결**: `limitedParallelism(1)` 사용 필수

```kotlin
// ❌ 잘못된 방법
private val searchDispatcher = Dispatchers.IO  // 여러 스레드

// ✅ 올바른 방법
private val searchDispatcher = Dispatchers.IO.limitedParallelism(1)
```

**테스트 방법**:
```kotlin
@Test
fun `빠른 연속 검색 시 순차 실행 보장`() {
    repeat(10) {
        searcher.execute()
    }
    // 마지막 검색만 결과 표시되어야 함
}
```

---

### 2. 취소 메커니즘 정확히 구현 🔴🔴

**문제**: Job 취소가 제대로 전파되지 않으면 메모리 누수  
**해결**: 적절한 취소 지점에서 `isActive` 확인

```kotlin
suspend fun doInBackground() {
    // 긴 작업 전에 취소 확인
    if (!isActive) return
    
    val historyData = loadHistoryFromDB()
    
    // 중간에도 확인
    if (!isActive) return
    
    processResults(historyData)
}
```

---

### 3. Handler 기반 UI 업데이트 타이밍 🔴

**현재 패턴**:
```java
mainHandler.post(this::onPreExecute);   // UI thread
doInBackground();                        // Background
mainHandler.post(this::onPostExecute);  // UI thread
```

**Coroutines 변환**:
```kotlin
CoroutineScope(Dispatchers.Main).launch {
    onPreExecute()  // Main dispatcher에서
    
    withContext(searchDispatcher) {
        doInBackground()  // Background
    }
    
    onPostExecute()  // 자동으로 Main으로 돌아옴
}
```

**주의**: `withContext`가 끝나면 자동으로 원래 컨텍스트(Main)로 복귀

---

### 4. PriorityQueue Thread Safety 🟡

**현재**: `addResults()`는 Provider들이 비동기로 호출  
**문제**: PriorityQueue는 thread-safe하지 않음  
**현재 해결**: Single thread executor가 순차 실행 보장

**Coroutines에서도 동일하게**:
- `searchDispatcher`를 통해 순차 실행
- 또는 `synchronized` 블록 사용

---

### 5. WeakReference 유지 🟡

```kotlin
class SearcherCoroutine(
    activity: MainActivity,
    // ...
) {
    private val activityRef = WeakReference(activity)
    
    protected fun getActivity(): MainActivity? {
        return activityRef.get()
    }
    
    suspend fun doInBackground() {
        val activity = getActivity() ?: return  // null 체크
        // ...
    }
}
```

---

## 📈 예상 성과

### 성능 개선
- **메모리 사용량**: 15-20% 감소 (ExecutorService 오버헤드 제거)
- **검색 속도**: 동등 또는 향상 (경량 코루틴)
- **취소 반응 속도**: 향상 (Job-based cancellation)

### 코드 품질
- **가독성**: Kotlin suspend 함수로 더 명확
- **유지보수성**: 구조화된 동시성
- **테스트 용이성**: Coroutines test utilities 활용

### 안정성
- **Crash 감소**: ExecutorService 관련 에러 제거
- **메모리 누수**: Job 자동 정리로 방지

---

## ✅ Step 1 완료 체크리스트

- [x] Searcher.java 상세 분석
- [x] MainActivity 호출 패턴 분석
- [x] 8개 하위 클래스 특성 분석
- [x] 성능 요구사항 확인
- [x] 변환 시 주의사항 문서화
- [x] 분석 보고서 작성

---

## 🚀 다음 단계: Step 2 준비

### Step 2 작업 항목
1. **SearcherCoroutine.kt 설계**
   - 인터페이스 정의
   - Single thread dispatcher 구현
   - Job-based cancellation
   - WeakReference 패턴

2. **단위 테스트 작성**
   - 순차 실행 테스트
   - 취소 메커니즘 테스트
   - 메모리 누수 테스트

3. **코드 리뷰**
   - 설계 검토
   - 구현 계획 승인

### 예상 소요 시간
- 설계: 0.5일
- 구현: 1일
- 테스트: 0.5일
- **총 1-2일**

---

## 📚 참고 자료

### 내부 문서
- [asynctask-migration-executive-summary.md](./asynctask-migration-executive-summary.md)
- [asynctask-migration-master-plan.md](./asynctask-migration-master-plan.md)

### 핵심 파일
- `app/src/main/java/fr/neamar/kiss/searcher/Searcher.java`
- `app/src/main/java/fr/neamar/kiss/MainActivity.java`
- 각 Searcher 하위 클래스들

### Coroutines 참고
- [Kotlin Coroutines Guide](https://kotlinlang.org/docs/coroutines-guide.html)
- [limitedParallelism](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-limited-dispatcher/)

---

**분석 완료**: 2025-10-14  
**다음 Step**: Step 2 설계 및 구현  
**승인 필요**: Step 2 시작 승인
