---
layout: post
title: "AsyncTask → Coroutines 마이그레이션 마스터 플랜"
category: advanced
date: 2025-10-14
---

# AsyncTask → Coroutines 마이그레이션 마스터 플랜

## 📊 현재 상황 분석 (2025-10-14)

### ✅ 이미 완료된 작업

1. **기반 작업 100% 완료**
   - ✅ Kotlin Coroutines 의존성 추가 (`kotlinx-coroutines-android:1.10.2`)
   - ✅ `CoroutineUtils.kt` 완전 구현됨
   - ✅ `LoadPojosCoroutine.kt` 추상 클래스 완성

2. **Provider 시스템 Coroutines 전환 완료**
   - ✅ `LoadAppPojosCoroutine.kt` - 앱 목록 로딩
   - ✅ `LoadShortcutsPojosCoroutine.kt` - 단축키 로딩
   - ✅ `LoadContactsPojosCoroutine.kt` - 연락처 로딩
   - ✅ `Provider.java` - Coroutines 지원 (`initializeCoroutines()`)

3. **UI 이미지 로딩 Coroutines 전환 완료**
   - ✅ `SetImageCoroutine.kt` - 이미지 비동기 로딩
   - ✅ `Result.java` - AsyncSetImage 제거됨

4. **단축키 저장 Coroutines 전환 완료**
   - ✅ SaveSingleOreoShortcut
   - ✅ SaveAllOreoShortcuts

5. **Legacy Code 정리 완료**
   - ✅ `Utilities.AsyncRun` deprecated 처리
   - ✅ 모든 Java AsyncTask import 제거됨

### 🔴 남은 작업: Searcher 시스템만 남음

**현재 Searcher는 ExecutorService 패턴 사용 중**

- `Searcher.SEARCH_THREAD = Executors.newSingleThreadExecutor()`
- `executeOnExecutor(ExecutorService)` 패턴
- 7개의 Searcher 클래스들이 이 패턴 공유

```
Searcher (abstract, Runnable 구현)
├── QuerySearcher ⭐ (메인 검색)
├── HistorySearcher (히스토리 검색)
├── ApplicationsSearcher (앱 검색)
├── NullSearcher (빈 검색)
├── PojoWithTagSearcher (abstract)
│   ├── TagsSearcher (태그 검색)
│   └── UntaggedSearcher (태그 없는 항목)
```

## 🎯 최종 단계: Searcher Coroutines 전환 계획

### Step 1: Searcher 아키텍처 분석 및 설계 (분석만)

#### 현재 구조 분석

```java
// Searcher.java (현재)
public abstract class Searcher implements Runnable {
    public static final ExecutorService SEARCH_THREAD = Executors.newSingleThreadExecutor();
    
    public Future<?> executeOnExecutor(ExecutorService executor) {
        this.task = executor.submit(this);
        return this.task;
    }
    
    @Override
    public final void run() {
        mainHandler.post(this::onPreExecute);  // UI 준비
        doInBackground();                       // 백그라운드 작업
        mainHandler.post(this::onPostExecute);  // 결과 처리
    }
    
    protected abstract void doInBackground();
}
```

#### 변환 목표 구조

```kotlin
// SearcherCoroutine.kt (목표)
abstract class SearcherCoroutine(
    activity: MainActivity,
    query: String?,
    isRefresh: Boolean
) {
    companion object {
        // Single thread dispatcher for sequential search execution
        private val searchDispatcher = Dispatchers.IO.limitedParallelism(1)
    }
    
    suspend fun execute(): Job {
        return CoroutineScope(Dispatchers.Main).launch {
            onPreExecute()
            
            withContext(searchDispatcher) {
                doInBackground()
            }
            
            onPostExecute()
        }
    }
    
    protected abstract suspend fun doInBackground()
}
```

#### 변환 전략

1. **Single Thread 보장**: `limitedParallelism(1)` 사용
2. **메모리 안전**: `WeakReference<MainActivity>` 유지
3. **취소 지원**: Job 기반 취소 메커니즘
4. **성능 유지**: 기존 ExecutorService와 동일한 동작

#### 분석 결과 문서화

- [ ] Searcher 시스템 호출 경로 분석
- [ ] MainActivity와의 상호작용 패턴 분석
- [ ] 각 Searcher 하위 클래스 특성 분석
- [ ] 성능 요구사항 확인 (특히 QuerySearcher)

**Step 1 완료 조건**:

- 상세 분석 문서 작성 완료
- 변환 시 고려사항 목록화
- 테스트 계획 수립

---

### Step 2: Base Searcher 클래스 전환 (코드 수정)

#### 작업 목표

`Searcher.java` → `SearcherCoroutine.kt` 변환

#### 구현 순서

1. **SearcherCoroutine.kt 신규 생성**

   ```kotlin
   abstract class SearcherCoroutine(
       activity: MainActivity,
       protected val query: String?,
       private val isRefresh: Boolean
   ) {
       // 기존 Searcher 로직을 Coroutines로 변환
   }
   ```

2. **핵심 기능 구현**
   - Single thread dispatcher 설정
   - Job 기반 취소 메커니즘
   - WeakReference 메모리 관리
   - 에러 핸들링

3. **MainActivity 인터페이스 유지**
   - `displayLoader()` 호출 패턴 유지
   - `adapter.updateResults()` 호출 패턴 유지

#### 변환 체크리스트

- [ ] SearcherCoroutine.kt 생성
- [ ] execute() 메서드 Coroutines로 구현
- [ ] cancel() 메서드 Job 기반으로 구현
- [ ] onPreExecute/doInBackground/onPostExecute 구조 유지
- [ ] WeakReference 패턴 구현
- [ ] 에러 핸들링 구현

**Step 2 완료 조건**:

- SearcherCoroutine.kt 컴파일 성공
- 기존 Searcher.java와 동일한 인터페이스 제공

---

### Step 3: QuerySearcher 전환 및 테스트 (가장 중요)

#### 작업 목표

`QuerySearcher.java` → `QuerySearcherCoroutine.kt` 변환

**QuerySearcher가 가장 중요한 이유**:

- 메인 검색 기능 (가장 많이 사용됨)
- 복잡한 로직 (히스토리 매칭, 관련성 점수)
- 성능 민감 (사용자가 직접 느끼는 반응속도)

#### 구현 순서

1. **QuerySearcherCoroutine.kt 생성**

   ```kotlin
   class QuerySearcherCoroutine(
       activity: MainActivity,
       query: String,
       isRefresh: Boolean
   ) : SearcherCoroutine(activity, query, isRefresh) {
       // QuerySearcher 로직 변환
   }
   ```

2. **MainActivity 연동**

   ```kotlin
   // MainActivity.java 일부 수정
   if (useCoroutineSearcher) {
       searchTask = QuerySearcherCoroutine(...).execute()
   } else {
       searchTask = QuerySearcher(...).executeOnExecutor(...)
   }
   ```

3. **A/B 테스트 구조 구현**
   - Feature flag로 전환 제어
   - 성능 비교 로깅

#### 테스트 계획

- [ ] 기본 검색 동작 테스트
- [ ] 히스토리 기반 관련성 점수 테스트
- [ ] 빠른 타이핑 시 취소 동작 테스트
- [ ] 메모리 사용량 비교
- [ ] 검색 속도 비교

**Step 3 완료 조건**:

- QuerySearcherCoroutine 정상 동작
- 기존 QuerySearcher와 동일한 검색 결과
- 성능 저하 없음

---

### Step 4: 나머지 Searcher 클래스 전환 (순차적)

#### 전환 순서 (난이도순)

1. **🟢 NullSearcher** (가장 쉬움)
   - 빈 검색 결과만 반환
   - 로직 거의 없음
   - [ ] NullSearcherCoroutine.kt 생성
   - [ ] 테스트

2. **🟡 HistorySearcher** (보통)
   - 히스토리 DB 조회
   - 단순한 로직
   - [ ] HistorySearcherCoroutine.kt 생성
   - [ ] 테스트

3. **🟡 ApplicationsSearcher** (보통)
   - 앱 목록 검색
   - 중간 복잡도
   - [ ] ApplicationsSearcherCoroutine.kt 생성
   - [ ] 테스트

4. **🟠 PojoWithTagSearcher** (보통-어려움)
   - 추상 클래스
   - 태그 기반 검색 로직
   - [ ] PojoWithTagSearcherCoroutine.kt 생성

5. **🟠 TagsSearcher** (보통-어려움)
   - PojoWithTagSearcher 상속
   - [ ] TagsSearcherCoroutine.kt 생성
   - [ ] 테스트

6. **🟠 UntaggedSearcher** (보통-어려움)
   - PojoWithTagSearcher 상속
   - [ ] UntaggedSearcherCoroutine.kt 생성
   - [ ] 테스트

#### 각 Searcher별 체크리스트

- [ ] Kotlin 파일 생성
- [ ] SearcherCoroutine 상속
- [ ] doInBackground() 로직 변환
- [ ] 기능 테스트 통과
- [ ] 메모리 테스트 통과

**Step 4 완료 조건**:

- 모든 Searcher 클래스 Coroutines 전환 완료
- 각 Searcher별 독립 테스트 통과

---

### Step 5: Legacy Code 제거 및 최적화

#### 제거 대상

1. **Searcher.java 제거**
   - [ ] 모든 하위 클래스 Coroutines 전환 확인
   - [ ] Searcher.java 파일 삭제

2. **ExecutorService 제거**
   - [ ] `SEARCH_THREAD` 제거
   - [ ] `executeOnExecutor()` 메서드 제거

3. **MainActivity 정리**
   - [ ] Feature flag 제거 (Coroutines만 사용)
   - [ ] Legacy 코드 경로 제거

#### 최적화 작업

1. **성능 최적화**
   - [ ] Dispatcher 설정 최적화
   - [ ] 메모리 사용량 최적화
   - [ ] 검색 응답 속도 최적화

2. **코드 정리**
   - [ ] 불필요한 import 제거
   - [ ] Deprecated 마커 제거
   - [ ] 문서 업데이트

**Step 5 완료 조건**:

- AsyncTask/ExecutorService 코드 완전 제거
- Coroutines만 사용하는 깨끗한 코드베이스

---

## 📝 각 Step별 진행 방식

### Step 진행 원칙

1. **분석 → 설계 → 구현 → 테스트** 순서 엄수
2. 한 Step이 완료되어야 다음 Step 시작
3. 각 Step 완료 시 git commit
4. 문제 발생 시 즉시 rollback 가능하도록

### Step별 검증 기준

#### Step 1 검증 (분석)

- [ ] 분석 문서 작성 완료
- [ ] 아키텍처 설계 완료
- [ ] 고려사항 목록화 완료

#### Step 2 검증 (Base 클래스)

- [ ] SearcherCoroutine.kt 컴파일 성공
- [ ] 단위 테스트 작성 및 통과
- [ ] 메모리 누수 없음

#### Step 3 검증 (QuerySearcher)

- [ ] 기능 동작 확인
- [ ] 성능 비교 (기존과 동일 이상)
- [ ] 안정성 테스트 (crash 없음)

#### Step 4 검증 (나머지 Searcher)

- [ ] 모든 Searcher 개별 테스트 통과
- [ ] 통합 테스트 통과
- [ ] 메모리 사용량 확인

#### Step 5 검증 (정리)

- [ ] Legacy 코드 완전 제거
- [ ] 최종 통합 테스트 통과
- [ ] 문서 업데이트 완료

---

## 🧪 테스트 전략

### 단계별 테스트

#### 1. 단위 테스트

- 각 SearcherCoroutine 클래스별 개별 테스트
- Mock 데이터 사용
- 로직 정확성 검증

#### 2. 통합 테스트

- MainActivity와의 연동 테스트
- 실제 데이터 사용
- 전체 검색 플로우 테스트

#### 3. 성능 테스트

```kotlin
// 검색 속도 비교
val startTime = System.currentTimeMillis()
searcher.execute()
val endTime = System.currentTimeMillis()
Log.d("Performance", "Search took: ${endTime - startTime}ms")
```

#### 4. 메모리 테스트

- LeakCanary로 메모리 누수 확인
- 반복 검색 테스트 (100회)
- 메모리 프로파일링

#### 5. 안정성 테스트

- 빠른 타이핑 테스트
- 중간 취소 테스트
- Activity 회전 테스트
- 백그라운드/포어그라운드 전환 테스트

---

## 📊 진행 상황 추적

### Overall Progress

```
Step 1: ⬜️ 0%  (분석 단계)
Step 2: ⬜️ 0%  (Base 클래스)
Step 3: ⬜️ 0%  (QuerySearcher)
Step 4: ⬜️ 0%  (나머지 Searcher)
Step 5: ⬜️ 0%  (정리 단계)

전체 진행률: 0%
```

### Step별 세부 진행률

- **Step 1 세부**: 0/4 완료
- **Step 2 세부**: 0/6 완료
- **Step 3 세부**: 0/5 완료
- **Step 4 세부**: 0/6 Searcher 완료
- **Step 5 세부**: 0/3 작업 완료

---

## ⚠️ 주의사항 및 리스크

### 높은 리스크 작업

1. **QuerySearcher 전환** ⚠️⚠️⚠️
   - 가장 많이 사용되는 기능
   - 성능 저하 시 사용자 경험 악화
   - 충분한 테스트 필수

2. **Single Thread Dispatcher** ⚠️⚠️
   - 기존 ExecutorService의 단일 스레드 보장 필요
   - 잘못 구현 시 검색 결과 순서 꼬임

3. **취소 메커니즘** ⚠️⚠️
   - Job 취소가 제대로 동작하지 않으면 메모리 누수
   - WeakReference 제대로 처리 필요

### 실패 방지 전략

1. **Feature Flag 사용**

   ```kotlin
   // BuildConfig 또는 SharedPreferences
   val USE_COROUTINE_SEARCHER = false  // 초기엔 false
   ```

2. **Rollback 계획**
   - 각 Step별로 git branch 생성
   - 문제 발생 시 즉시 이전 Step으로 복구

3. **점진적 배포**
   - Step 3에서 A/B 테스트 구조 사용
   - 안정성 확인 후 전체 전환

---

## 🎉 완료 기준

### 최종 완료 조건

- [ ] 모든 Searcher 클래스 Coroutines 전환
- [ ] ExecutorService 코드 완전 제거
- [ ] 모든 테스트 통과 (단위/통합/성능/메모리/안정성)
- [ ] 성능 저하 없음 (기존 대비 ±5% 이내)
- [ ] 메모리 누수 없음
- [ ] 문서 업데이트 완료

### 성공 메트릭

- 검색 속도: 기존 대비 ±5% 이내
- 메모리 사용량: 기존 대비 20% 감소 목표
- Crash rate: 0% 유지
- 코드 라인 수: 10~15% 감소 예상

---

## 📚 참고 자료

### 내부 문서

- [asynctask-to-coroutines-migration.md](./asynctask-to-coroutines-migration.md) - 기존 작업 내역
- [refactoring-guide.md](./refactoring-guide.md) - 리팩토링 가이드
- [testing-guide.md](./testing-guide.md) - 테스트 가이드

### 외부 문서

- [Android Developers: Kotlin Coroutines](https://developer.android.com/kotlin/coroutines)
- [Coroutines Best Practices](https://developer.android.com/kotlin/coroutines/coroutines-best-practices)
- [Structured Concurrency](https://kotlinlang.org/docs/coroutines-basics.html#structured-concurrency)

---

**작성일**: 2025년 10월 14일  
**최종 수정**: 2025년 10월 14일  
**상태**: 마스터 플랜 수립 완료, Step 1 시작 준비  
**예상 소요 기간**: 2~3주 (Step당 2~3일)  
**작성자**: GitHub Copilot (Claude Sonnet 4.5)
