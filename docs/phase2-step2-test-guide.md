# Phase 2 Step 2: Error Handling - 테스트 가이드

**Branch**: `phase2-step2-error-handling`  
**변경 파일**: `SearcherCoroutine.kt`  
**변경 내용**: CancellationException과 Exception 구분, onError() 추가

---

## ✅ 완료 사항

1. **코드 수정 완료**
   - `execute()`: CancellationException과 Exception 별도 처리
   - `onError()`: 새로운 에러 콜백 추가
   - Amplitude 에러 이벤트 로깅 추가

2. **빌드 성공**
   - ✅ `./gradlew compileDebugKotlin` 성공
   - ✅ 컴파일 에러 없음

3. **커밋 완료**
   - ✅ Commit: `feat(searcher): Distinguish errors from cancellations`

---

## 🧪 테스트 시나리오

### 1. 정상 동작 테스트 (필수)

#### 1.1 에러 없는 정상 검색

```
목적: onPostExecute()가 정상 호출되는지 확인

1. 앱 실행
2. 텍스트 입력: "설정"
   ✅ 검색 결과 정상 표시
   ✅ onPostExecute() 호출 (에러 아님)

3. Logcat 확인:
   adb logcat | grep "SearchPerf"
   
   예상 출력:
   V/SearchPerf: [COMPLETED] QuerySearcherCoroutine query='설정' time=45ms ...
   
   ⚠️ SearchError 이벤트 없음
```

#### 1.2 정상 취소 동작

```
목적: onCancelled()가 호출되고 DEBUG 레벨로 로깅되는지 확인

1. 텍스트 입력: "앱"
2. 즉시 다른 텍스트 입력: "앱2"
   ✅ 첫 번째 검색 취소됨
   ✅ 두 번째 검색 결과만 표시

3. Logcat 확인:
   adb logcat | grep "SearcherCoroutine"
   
   예상 출력:
   D/SearcherCoroutine: Search cancelled: QuerySearcherCoroutine
   
   ✅ DEBUG 레벨 (D/)
   ✅ "Search cancelled" 메시지
   ⚠️ SearchError 이벤트 없음
```

---

### 2. 에러 처리 테스트 (중요)

#### 2.1 실제 에러 시뮬레이션

**방법 1: 임시 에러 코드 추가** (테스트용)

```kotlin
// QuerySearcherCoroutine.kt - doInBackground()에 임시 추가
override suspend fun doInBackground() {
    // 테스트용 에러
    if (query == "error") {
        throw RuntimeException("Test error for Phase 2 Step 2")
    }
    
    // ... 기존 코드 ...
}
```

**테스트**:

```
1. "error" 입력
2. Logcat 확인:
   adb logcat | grep -E "SearcherCoroutine|SearchError"
   
   예상 출력:
   E/SearcherCoroutine: Error in QuerySearcherCoroutine
   E/SearcherCoroutine: Search error in QuerySearcherCoroutine: Test error for Phase 2 Step 2
   
   ✅ ERROR 레벨 (E/)
   ✅ "Search error" 메시지
   ✅ onError() 호출됨
   ✅ UI 정리됨 (로딩 인디케이터 사라짐)

3. Amplitude 확인 (웹 또는 앱):
   - 이벤트 이름: SearchError
   - 속성:
     - type: QuerySearcherCoroutine
     - errorType: RuntimeException
     - errorMessage: Test error for Phase 2 Step 2
     - query: error
```

**방법 2: DB 에러 시뮬레이션** (고급)

```kotlin
// 일시적으로 DB 접근 차단 (예: 권한 문제)
// 또는 매우 긴 쿼리로 OutOfMemoryError 유발
```

#### 2.2 에러 후 복구

```
목적: 에러 발생 후 정상 동작으로 복구되는지 확인

1. "error" 입력 (에러 발생)
   ✅ 에러 로그 출력
   ✅ UI 정리됨

2. "정상" 입력
   ✅ 정상 검색 동작
   ✅ 결과 표시됨
   ✅ 에러 영향 없음

3. 여러 번 반복:
   "error" → "정상" → "error" → "정상"
   ✅ 모두 예상대로 동작
```

---

### 3. 로그 레벨 검증 (필수)

#### 3.1 CancellationException vs Exception

```
Logcat 필터:
adb logcat *:S SearcherCoroutine:D SearchPerf:V

테스트 시나리오:
1. 정상 검색: "앱"
   → V/SearchPerf: [COMPLETED] ...

2. 빠른 취소: "a" → "ab" (즉시)
   → D/SearcherCoroutine: Search cancelled: ...

3. 에러 발생: "error"
   → E/SearcherCoroutine: Error in ...
   → E/SearcherCoroutine: Search error in ...

✅ 레벨 구분 명확:
   - Completed: VERBOSE
   - Cancelled: DEBUG
   - Error: ERROR
```

---

### 4. Amplitude 이벤트 검증 (권장)

#### 4.1 정상 검색 이벤트

```
이벤트: Search
속성:
- type: QuerySearcherCoroutine
- length: 2 (쿼리 길이)
- time: 45 (ms)
- resultCount: 10
- allProvidersLoaded: true
- status: COMPLETED (없을 수도 있음)
```

#### 4.2 에러 이벤트

```
이벤트: SearchError
속성:
- type: QuerySearcherCoroutine
- errorType: RuntimeException
- errorMessage: Test error for Phase 2 Step 2
- query: error

✅ Search 이벤트와 별도로 전송됨
✅ 에러 추적 가능
```

---

## 📊 테스트 체크리스트

### 필수 테스트

- [ ] 정상 검색: onPostExecute() 호출, VERBOSE 로그
- [ ] 정상 취소: onCancelled() 호출, DEBUG 로그
- [ ] 에러 발생: onError() 호출, ERROR 로그
- [ ] 에러 후 복구: 정상 동작으로 복귀
- [ ] UI 정리: 에러/취소 시 로딩 인디케이터 제거

### 로그 검증

- [ ] CancellationException → DEBUG 레벨
- [ ] Exception → ERROR 레벨
- [ ] 로그 메시지 구분 명확

### Amplitude 검증

- [ ] Search 이벤트: 정상 검색
- [ ] SearchError 이벤트: 에러 발생
- [ ] 에러 속성: errorType, errorMessage, query

### 안정성

- [ ] 앱 크래시 없음
- [ ] 에러 후 정상 복구
- [ ] 메모리 누수 없음

---

## 🚀 테스트 실행 방법

### 빠른 테스트

```bash
# 1. APK 설치
./scripts/install_and_test.sh

# 2. Logcat 모니터링 (별도 터미널)
adb logcat *:S SearcherCoroutine:D SearchPerf:V Amplitude:V

# 3. 앱에서 테스트
# - 정상 검색: "앱"
# - 빠른 취소: "a" → "ab"
# - 에러 테스트: 임시 에러 코드 추가 후 "error"
```

### 에러 테스트 코드 추가

```kotlin
// app/src/main/java/fr/neamar/kiss/searcher/QuerySearcherCoroutine.kt
override suspend fun doInBackground() {
    // ⚠️ 테스트용 - 커밋하지 말 것!
    if (query == "error") {
        throw RuntimeException("Test error for Phase 2 Step 2")
    }
    
    val activity = activityWeakReference.get() ?: return
    // ... 기존 코드 계속 ...
}
```

**테스트 후 반드시 제거!**

---

## ✅ 완료 조건

### Step 2 완료 기준

- ✅ 코드 수정 완료 (CancellationException 구분)
- ✅ 빌드 성공
- ✅ 커밋 완료
- [ ] **정상 검색/취소 테스트 통과** (필수)
- [ ] **에러 처리 테스트 통과** (필수)
- [ ] **로그 레벨 검증** (필수)
- [ ] **Amplitude 이벤트 확인** (권장)
- [ ] **PR 생성** (다음 단계)

---

## 📝 테스트 결과 기록

```
테스트 날짜: ___________
테스터: ___________

[기능 테스트]
□ 정상 검색 (onPostExecute): PASS / FAIL
□ 정상 취소 (onCancelled): PASS / FAIL
□ 에러 발생 (onError): PASS / FAIL
□ 에러 후 복구: PASS / FAIL

[로그 검증]
□ CancellationException → DEBUG: PASS / FAIL
□ Exception → ERROR: PASS / FAIL
□ 로그 메시지 구분: PASS / FAIL

[Amplitude]
□ Search 이벤트: 확인됨 / 확인 안 됨
□ SearchError 이벤트: 확인됨 / 확인 안 됨
□ 에러 속성 정확: PASS / FAIL

[종합 판정]
□ PASS - PR 생성 진행
□ FAIL - 문제 수정 필요

[참고 사항]
_______________________________________________
```

---

## 🔄 다음 단계

### 테스트 통과 시

```bash
# dev에 머지
git checkout dev
git merge --no-ff phase2-step2-error-handling

# Step 3 시작
git checkout -b phase2-step3-cancellation-checks
```

### 테스트 실패 시

```bash
# 문제 분석 및 수정
# 재테스트
# 통과 시 머지
```

---

**작성 완료**: 2025-01-17  
**현재 상태**: ✅ 코드 수정 및 빌드 완료 → 테스트 대기  
**다음**: 에러 처리 테스트 수행 (임시 에러 코드 사용)
