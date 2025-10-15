# Phase 2: Searcher Improvements - Testing Guide

## 테스트 개요

Phase 2의 5가지 개선 사항을 검증하기 위한 통합 테스트 가이드입니다.

### 테스트 환경
- Device: Android Emulator
- Build: Debug APK (phase2-step5-logging-consolidation)
- Log Tag: `SearchPerf`, `SearcherCoroutine`

---

## Step 1: Thread Safety 테스트 🔒

### 목표
synchronized 블록이 동시성 문제 없이 정상 동작하는지 확인

### 테스트 시나리오

#### T1.1: 빠른 연속 검색
```
1. KISS 런처 열기
2. 검색어 빠르게 입력: "a" → "ab" → "abc" → "abcd"
3. 각 입력마다 새로운 검색 실행됨
4. 결과가 깨지지 않고 정상 표시되는지 확인
```

**예상 결과:**
- ✅ 결과 순서 유지
- ✅ 크래시 없음
- ✅ 중복 결과 없음

#### T1.2: Provider 동시 결과 추가
```
1. 빈 화면에서 히스토리 표시 (여러 Provider가 동시에 결과 추가)
2. 결과 개수와 순서 확인
```

**예상 결과:**
- ✅ 모든 Provider 결과 정상 표시
- ✅ 관련성 순서 유지

---

## Step 2: Error Handling 테스트 ⚠️

### 목표
CancellationException과 실제 에러가 구분되어 로깅되는지 확인

### 테스트 시나리오

#### T2.1: 정상 취소 (CancellationException)
```
1. 검색어 입력: "test"
2. 결과 표시 전에 검색어 변경: "testing"
3. logcat 확인
```

**예상 로그:**
```
D/SearcherCoroutine: Search cancelled: QuerySearcherCoroutine
V/SearchPerf: [CANCELLED] QuerySearcherCoroutine query='test' time=XXms results=X providersLoaded=false
```

**예상 Amplitude 이벤트:**
- Event: `Search`
- Properties: `status=CANCELLED`

#### T2.2: 실제 에러 (Exception)
```
이 테스트는 에러 시뮬레이션이 어려워 로그 확인만 수행
만약 에러 발생 시:
```

**예상 로그:**
```
E/SearchPerf: [ERROR] SearcherType query='...' time=XXms results=X providersLoaded=true error=Exception: message
```

**예상 Amplitude 이벤트:**
- Event: `SearchError`
- Properties: `errorType`, `errorMessage`

---

## Step 3: Cancellation Checks 테스트 ⚡

### 목표
긴 작업 중간에 취소 체크가 작동하여 빠른 응답을 제공하는지 확인

### 테스트 시나리오

#### T3.1: 빠른 취소 응답 (QuerySearcherCoroutine)
```
1. 검색어 입력: "a"
2. 즉시 검색어 변경: "b"
3. 취소 응답 시간 확인 (목표: < 50ms)
```

**예상 결과:**
- ✅ 취소 즉시 새 검색 시작
- ✅ 불필요한 DB 쿼리 없음

#### T3.2: 히스토리 검색 취소 (HistorySearcherCoroutine)
```
1. 빈 화면 (히스토리 표시)
2. 즉시 검색어 입력: "test"
3. 히스토리 로딩 중단되고 쿼리 검색 시작
```

**예상 결과:**
- ✅ 히스토리 로딩 중단
- ✅ 쿼리 검색 즉시 시작

#### T3.3: 앱 리스트 표시 중 취소 (ApplicationsSearcherCoroutine)
```
1. 앱 drawer 열기 (모든 앱 표시)
2. 즉시 검색어 입력: "test"
3. 앱 리스트 로딩 중단되고 검색 시작
```

**예상 결과:**
- ✅ 앱 리스트 로딩 중단
- ✅ 검색 즉시 시작

#### T3.4: 태그 검색 취소 (PojoWithTagSearcherCoroutine)
```
1. 태그 검색: "#work"
2. 즉시 다른 태그: "#home"
3. 첫 번째 검색 중단되고 두 번째 검색 시작
```

**예상 결과:**
- ✅ 첫 번째 검색 중단
- ✅ 두 번째 검색 즉시 시작

---

## Step 4: Static Cache Removal 테스트 🧹

### 목표
Instance 변수로 변경된 maxResultCount가 정상 동작하는지 확인

### 테스트 시나리오

#### T4.1: 검색 결과 개수 제한
```
1. 설정 → "표시할 결과 개수" 확인 (기본값: 50)
2. 검색어 입력하여 많은 결과 생성: "a"
3. 결과 개수가 설정값 이하인지 확인
```

**예상 결과:**
- ✅ 결과 개수 ≤ 50

#### T4.2: 설정 변경 반영
```
1. 설정 → "표시할 결과 개수" 변경: 50 → 20
2. 뒤로가기 (설정 나가기)
3. 검색어 입력: "a"
4. 결과 개수 확인
```

**예상 결과:**
- ✅ 결과 개수 ≤ 20 (새로운 설정값)
- ✅ clearMaxResultCountCache() 호출 없이도 자동 반영

**검증 포인트:**
- 각 Searcher 인스턴스가 생성 시 최신 설정값 읽음
- Static cache 없어도 정상 동작

---

## Step 5: Logging Consolidation 테스트 📝

### 목표
통합 로깅이 모든 상태에서 일관된 형식으로 출력되는지 확인

### 테스트 시나리오

#### T5.1: 정상 완료 로그
```
1. 검색어 입력: "test"
2. 결과 표시 대기
3. logcat 확인
```

**예상 로그 형식:**
```
V/SearchPerf: [COMPLETED] QuerySearcherCoroutine query='test' time=123ms results=45 providersLoaded=true
```

**검증 포인트:**
- ✅ [COMPLETED] 상태
- ✅ 시간 측정
- ✅ 결과 개수
- ✅ providersLoaded 상태

#### T5.2: 취소 로그
```
1. 검색어 입력: "test"
2. 즉시 변경: "testing"
3. logcat 확인
```

**예상 로그 형식:**
```
V/SearchPerf: [CANCELLED] QuerySearcherCoroutine query='test' time=45ms results=12 providersLoaded=false
```

**검증 포인트:**
- ✅ [CANCELLED] 상태
- ✅ 부분 결과 개수 표시

#### T5.3: 모든 Searcher 타입 로그
```
다양한 검색 수행하여 모든 Searcher 타입 로그 확인:
- QuerySearcherCoroutine: 일반 검색
- HistorySearcherCoroutine: 빈 화면
- ApplicationsSearcherCoroutine: 앱 drawer
- TagsSearcherCoroutine: #태그 검색
- UntaggedSearcherCoroutine: !태그 없는 항목
```

**예상 결과:**
- ✅ 모든 Searcher가 동일한 로그 형식 사용
- ✅ 각 Searcher 이름 명확히 표시

#### T5.4: Amplitude 이벤트
```
검색 후 Amplitude 대시보드(또는 로그) 확인
```

**예상 이벤트:**
```json
Event: "Search"
{
  "type": "QuerySearcherCoroutine",
  "length": 4,
  "time": 123,
  "resultCount": 45,
  "allProvidersLoaded": true,
  "status": "COMPLETED"
}
```

---

## 통합 테스트 시나리오 🎯

### 시나리오 1: 일반적인 검색 흐름
```
1. KISS 런처 열기 (히스토리 표시)
2. 검색어 입력: "gm" (Gmail 검색)
3. 결과 선택하여 앱 실행
4. 다시 KISS 런처 열기 (Gmail이 히스토리 최상단)
```

**검증:**
- ✅ 히스토리 로딩 정상
- ✅ 검색 결과 정확
- ✅ 히스토리 업데이트
- ✅ 로그 정상 출력

### 시나리오 2: 빠른 연속 검색 (취소 체크)
```
1. 검색어 빠르게 변경: "a" → "ab" → "abc" → "abcd"
2. 각 단계마다 로그 확인
```

**검증:**
- ✅ 이전 검색 즉시 취소
- ✅ [CANCELLED] 로그 여러 개
- ✅ 마지막 검색만 [COMPLETED]
- ✅ UI 버벅임 없음

### 시나리오 3: 설정 변경 후 검색
```
1. 검색: "test" (결과 50개까지)
2. 설정 → 결과 개수 20으로 변경
3. 검색: "test" (결과 20개까지)
```

**검증:**
- ✅ 설정 변경 자동 반영
- ✅ 결과 개수 제한 정상
- ✅ Static cache 관련 에러 없음

---

## 로그 모니터링 명령어

### 실시간 로그 확인
```bash
# SearchPerf 로그만 보기
adb logcat -s SearchPerf:V

# SearcherCoroutine 로그 포함
adb logcat -s SearchPerf:V SearcherCoroutine:*

# 모든 KISS 관련 로그
adb logcat | grep -E "SearchPerf|SearcherCoroutine"
```

### 로그 저장
```bash
# 파일로 저장
adb logcat -s SearchPerf:V SearcherCoroutine:* > phase2_test_log.txt
```

---

## 테스트 체크리스트

### Step 1: Thread Safety
- [ ] T1.1: 빠른 연속 검색 - 결과 정상 표시
- [ ] T1.2: Provider 동시 결과 추가 - 순서 유지

### Step 2: Error Handling
- [ ] T2.1: 정상 취소 로그 (DEBUG 레벨)
- [ ] T2.2: 에러 로그 (ERROR 레벨) - 해당 시

### Step 3: Cancellation Checks
- [ ] T3.1: QuerySearcher 빠른 취소
- [ ] T3.2: HistorySearcher 취소
- [ ] T3.3: ApplicationsSearcher 취소
- [ ] T3.4: PojoWithTagSearcher 취소

### Step 4: Static Cache Removal
- [ ] T4.1: 결과 개수 제한 정상
- [ ] T4.2: 설정 변경 자동 반영

### Step 5: Logging Consolidation
- [ ] T5.1: [COMPLETED] 로그 형식
- [ ] T5.2: [CANCELLED] 로그 형식
- [ ] T5.3: 모든 Searcher 타입 로그
- [ ] T5.4: Amplitude 이벤트

### 통합 테스트
- [ ] 시나리오 1: 일반 검색 흐름
- [ ] 시나리오 2: 빠른 연속 검색
- [ ] 시나리오 3: 설정 변경 후 검색

---

## 테스트 결과 기록

### 환경 정보
- 날짜: 2025-10-15
- Device: 
- Android Version: 
- Build: phase2-step5-logging-consolidation
- Commit: d6dc416d6

### 발견된 이슈
_(테스트 중 발견된 문제점 기록)_

### 성공 사항
_(정상 동작 확인된 항목 기록)_

### 다음 단계
_(추가 테스트 필요 항목 또는 개선 사항)_
