# AsyncTask → Coroutines 마이그레이션 실행 요약

## 🎯 현재 상황 한눈에 보기

### 완료율: 95%

**✅ 완료**: Provider 시스템, 이미지 로딩, 유틸리티  
**🔴 남음**: Searcher 시스템 (검색 기능) 8개 클래스

---

## 📊 상세 분석 결과

### ✅ 이미 Coroutines로 변환 완료

1. **기반 시스템**
   - `CoroutineUtils.kt` - 비동기 실행 유틸리티
   - Coroutines 의존성 (`kotlinx-coroutines-android:1.10.2`)

2. **Provider 시스템** (앱 데이터 로딩)
   - `LoadPojosCoroutine.kt` - 기반 클래스
   - `LoadAppPojosCoroutine.kt` - 앱 목록
   - `LoadShortcutsPojosCoroutine.kt` - 단축키
   - `LoadContactsPojosCoroutine.kt` - 연락처

3. **UI 컴포넌트**
   - `SetImageCoroutine.kt` - 이미지 로딩

4. **기타 유틸리티**
   - SaveSingleOreoShortcut
   - SaveAllOreoShortcuts

### 🔴 변환 필요: Searcher 시스템

현재 **ExecutorService 패턴** 사용 중:

```java
// 현재 구조
public abstract class Searcher implements Runnable {
    public static final ExecutorService SEARCH_THREAD = 
        Executors.newSingleThreadExecutor();
    
    public Future<?> executeOnExecutor(ExecutorService executor) {
        return executor.submit(this);
    }
}
```

**변환 대상 클래스들**:
1. `Searcher.java` - 기반 클래스
2. `QuerySearcher.java` ⭐ - **메인 검색 (가장 중요)**
3. `HistorySearcher.java` - 히스토리 검색
4. `ApplicationsSearcher.java` - 앱 검색
5. `NullSearcher.java` - 빈 검색
6. `PojoWithTagSearcher.java` - 태그 검색 기반
7. `TagsSearcher.java` - 태그로 검색
8. `UntaggedSearcher.java` - 태그 없는 항목

---

## 🎯 변환 전략: 5-Step 접근법

### Why 5 Steps?

**과거 실패 원인**:
- 한번에 모든 것 변경 시도 → 실패
- 테스트 부족 → 예상치 못한 버그
- Rollback 계획 없음 → 복구 어려움

**새로운 전략**:
- 점진적 변환 (한번에 하나씩)
- 각 단계마다 충분한 테스트
- Feature Flag로 안전한 배포

---

## 📋 5-Step 실행 계획

### Step 1: 분석 단계 (코드 수정 없음)

**목표**: Searcher 시스템 완전 이해

**작업**:
- [ ] Searcher 호출 경로 분석
- [ ] MainActivity 상호작용 패턴 분석
- [ ] 각 Searcher 특성 분석
- [ ] 성능 요구사항 확인

**산출물**:
- 상세 분석 문서
- 아키텍처 다이어그램
- 리스크 목록

**소요 시간**: 0.5-1일

**완료 조건**: 
- 분석 문서 작성 완료
- 변환 시 고려사항 목록화
- 다음 단계 설계 승인

---

### Step 2: Base 클래스 변환

**목표**: `Searcher.java` → `SearcherCoroutine.kt` 변환

**작업**:
- [ ] SearcherCoroutine.kt 생성
- [ ] Single thread dispatcher 구현
- [ ] Job 기반 취소 구현
- [ ] WeakReference 패턴 구현
- [ ] 단위 테스트 작성

**핵심 구현**:

```kotlin
abstract class SearcherCoroutine(
    activity: MainActivity,
    protected val query: String?,
    private val isRefresh: Boolean
) {
    companion object {
        // Single thread 보장
        private val searchDispatcher = 
            Dispatchers.IO.limitedParallelism(1)
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

**소요 시간**: 1-2일

**완료 조건**:
- SearcherCoroutine.kt 컴파일 성공
- 단위 테스트 통과
- 메모리 누수 없음

---

### Step 3: QuerySearcher 변환 (가장 중요)

**목표**: 메인 검색 기능 Coroutines 전환

**Why Most Important?**
- 가장 많이 사용되는 기능
- 사용자가 직접 느끼는 성능
- 복잡한 로직 (히스토리, 관련성 점수)

**작업**:
- [ ] QuerySearcherCoroutine.kt 생성
- [ ] 히스토리 매칭 로직 변환
- [ ] MainActivity 연동
- [ ] Feature Flag 구현
- [ ] A/B 테스트 구조
- [ ] 성능 비교 테스트

**Feature Flag 구현**:

```kotlin
// MainActivity에서
val useCoroutineSearcher = 
    prefs.getBoolean("use_coroutine_searcher", false)

if (useCoroutineSearcher) {
    searchTask = QuerySearcherCoroutine(...).execute()
} else {
    searchTask = QuerySearcher(...).executeOnExecutor(...)
}
```

**테스트 계획**:
- [ ] 기본 검색 동작
- [ ] 히스토리 기반 점수
- [ ] 빠른 타이핑 취소
- [ ] 메모리 사용량
- [ ] 검색 속도 비교

**소요 시간**: 2-3일 (테스트 포함)

**완료 조건**:
- 기존과 동일한 검색 결과
- 성능 저하 없음 (±5% 이내)
- 1주일 안정화 기간

---

### Step 4: 나머지 Searcher 변환

**목표**: 6개 Searcher 순차적 변환

**변환 순서** (쉬운 것부터):

1. **🟢 NullSearcher** (0.5일)
   - 가장 단순 (빈 결과만 반환)

2. **🟡 HistorySearcher** (0.5-1일)
   - DB 조회만

3. **🟡 ApplicationsSearcher** (0.5-1일)
   - 앱 검색 로직

4. **🟠 PojoWithTagSearcher** (1일)
   - 추상 클래스 (태그 기반)

5. **🟠 TagsSearcher** (0.5일)
   - PojoWithTagSearcher 상속

6. **🟠 UntaggedSearcher** (0.5일)
   - PojoWithTagSearcher 상속

**각 Searcher 체크리스트**:
- [ ] Kotlin 파일 생성
- [ ] SearcherCoroutine 상속
- [ ] doInBackground() 변환
- [ ] 테스트 작성
- [ ] 테스트 통과

**소요 시간**: 3-5일

**완료 조건**:
- 모든 Searcher 개별 테스트 통과
- 통합 테스트 통과
- 메모리 누수 없음

---

### Step 5: Legacy 코드 정리

**목표**: AsyncTask/ExecutorService 완전 제거

**작업**:

1. **Legacy 코드 제거**
   - [ ] Searcher.java 삭제
   - [ ] SEARCH_THREAD 제거
   - [ ] executeOnExecutor() 제거

2. **MainActivity 정리**
   - [ ] Feature Flag 제거
   - [ ] Legacy 코드 경로 제거
   - [ ] Coroutines만 사용

3. **최적화**
   - [ ] Dispatcher 설정 최적화
   - [ ] 메모리 사용량 최적화
   - [ ] 불필요한 import 제거

4. **문서 업데이트**
   - [ ] 마이그레이션 문서 최종 업데이트
   - [ ] 코드 주석 정리
   - [ ] README 업데이트

**소요 시간**: 1-2일

**완료 조건**:
- AsyncTask 코드 완전 제거
- 전체 통합 테스트 통과
- 문서 업데이트 완료

---

## ⚠️ 리스크 관리

### High Risk (🔴)

1. **QuerySearcher 변환**
   - **리스크**: 검색 기능 중단 시 사용자 경험 악화
   - **완화**: Feature Flag + A/B 테스트

2. **Single Thread 보장**
   - **리스크**: 순차 실행 실패 시 결과 순서 꼬임
   - **완화**: `limitedParallelism(1)` + 테스트

3. **취소 메커니즘**
   - **리스크**: 메모리 누수
   - **완화**: Job-based cancellation + LeakCanary

### Rollback 계획

```bash
# 각 Step마다 branch 생성
git checkout -b step1-searcher-analysis
git checkout -b step2-searcher-base
git checkout -b step3-query-searcher
...

# 문제 발생 시
git checkout step2-searcher-base  # 이전 단계로
```

---

## 📅 타임라인

### 전체 예상 기간: 2-3주

```
Week 1:
├── Mon-Tue: Step 1 (분석)
├── Wed-Thu: Step 2 (Base 클래스)
└── Fri: Step 3 시작

Week 2:
├── Mon-Wed: Step 3 완료 (QuerySearcher + 테스트)
├── Thu: Step 4 시작 (NullSearcher, HistorySearcher)
└── Fri: Step 4 계속 (ApplicationsSearcher)

Week 3:
├── Mon-Tue: Step 4 완료 (Tag 관련)
├── Wed-Thu: Step 5 (정리)
└── Fri: 최종 테스트
```

---

## 🚀 즉시 시작 가능한 작업

### 1. Git Branch 생성

```bash
git checkout -b step1-searcher-analysis
```

### 2. 분석 문서 작성 시작

분석할 내용:
- [ ] `Searcher.java` 전체 코드 리뷰
- [ ] `MainActivity.java`에서 Searcher 호출 부분
- [ ] 각 Searcher 하위 클래스 특성
- [ ] 성능 요구사항

### 3. 테스트 환경 준비

```bash
# LeakCanary 설정 확인
# Unit test 스캐폴딩 준비
# Performance benchmark 준비
```

---

## 📊 성공 메트릭

### 기술 메트릭

- **검색 속도**: 기존 ±5% 이내
- **메모리 사용**: 15-20% 감소
- **Crash rate**: 0% 유지
- **코드 라인**: 10-15% 감소

### 프로세스 메트릭

- **Step 완료율**: 각 Step 100% 완료
- **테스트 커버리지**: 80% 이상
- **Rollback 횟수**: 0회 목표
- **예정일 준수**: ±20% 이내

---

## 📚 참고 문서

1. **[asynctask-migration-master-plan.md](./asynctask-migration-master-plan.md)**
   - 상세한 5-Step 계획
   - 코드 예제
   - 체크리스트

2. **[asynctask-migration-final-analysis.md](./asynctask-migration-final-analysis.md)**
   - 현황 분석
   - 기술 가이드
   - 리스크 관리

3. **[asynctask-to-coroutines-migration.md](./asynctask-to-coroutines-migration.md)**
   - 기존 작업 히스토리
   - 완료된 작업 목록

---

## 💬 질문 & 답변

### Q: 왜 이전에 실패했나요?

A: 세 가지 주요 원인:
1. 한번에 모든 것 변경 시도
2. 충분한 테스트 없이 진행
3. Rollback 계획 부재

### Q: 이번엔 왜 성공할 수 있나요?

A: 새로운 전략:
1. 점진적 접근 (5-Step)
2. 각 Step마다 충분한 테스트
3. Feature Flag로 안전한 배포
4. 명확한 Rollback 계획

### Q: 가장 어려운 부분은?

A: **Step 3 (QuerySearcher)**
- 가장 복잡한 로직
- 사용자가 직접 느끼는 기능
- 충분한 테스트 기간 필요 (1주)

### Q: Step 3 실패하면?

A: Feature Flag로 즉시 복구 가능
```kotlin
// 설정 변경만으로 이전 코드로 복귀
USE_COROUTINE_SEARCHER = false
```

---

## ✅ 다음 액션

### 지금 바로 할 일

1. **이 문서 검토**
   - 전략 이해
   - 질문 정리

2. **Step 1 시작 승인**
   - 분석 작업 시작
   - Git branch 생성

3. **일정 확인**
   - 2-3주 시간 확보
   - 리뷰 시점 결정

### 승인 필요 사항

- [ ] 5-Step 전략 승인
- [ ] 2-3주 일정 확보 승인
- [ ] Step 1 시작 승인

---

**문서 작성**: 2025-10-14  
**작성자**: GitHub Copilot (Claude Sonnet 4.5)  
**상태**: 실행 준비 완료  
**다음 단계**: Step 1 시작 대기
