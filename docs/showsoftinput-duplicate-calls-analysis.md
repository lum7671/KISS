# showSoftInput 중복 호출 문제 분석

**날짜**: 2025년 10월 17일  
**우선순위**: 🟡 Medium (성능/배터리 최적화)

## 🐛 문제 발견

### 사용자 로그 분석

```log
10-17 10:50:20.441  showSoftInput() SHOW_SOFT_INPUT
10-17 10:50:20.457  showSoftInput() SHOW_SOFT_INPUT
10-17 10:50:20.473  showSoftInput() SHOW_SOFT_INPUT
...
10-17 10:50:21.018  showSoftInput() SHOW_SOFT_INPUT (11번째)
```

**문제**: 짧은 시간(577ms)에 `showSoftInput()` **11번 호출**

### 원인 분석

**위치**: `app/src/main/java/fr/neamar/kiss/forwarder/ExperienceTweaks.java:255-264`

```java
if (shouldShowKeyboard()) {
    // Display keyboard
    mainActivity.showKeyboard();  // 1번째 호출

    new Handler(Looper.getMainLooper()).postDelayed(displayKeyboardRunnable, 10);   // 2번째
    new Handler(Looper.getMainLooper()).postDelayed(displayKeyboardRunnable, 100);  // 3번째
    new Handler(Looper.getMainLooper()).postDelayed(displayKeyboardRunnable, 500);  // 4번째
}

// Line 62
private final Runnable displayKeyboardRunnable = mainActivity::showKeyboard;
```

**설명 (주석 참조)**:

```java
// For some weird reasons, keyboard may be hidden by the system
// So we have to run this multiple time at different time
// See https://github.com/Neamar/KISS/issues/119
```

**의도**:

- 시스템이 키보드를 강제로 숨기는 경우 대비
- 여러 타이밍에 재시도하여 키보드 표시 보장

**실제 동작**:

- 위로 스크롤 제스처 반복 시 이 로직이 여러 번 실행
- 각 실행마다 4번의 호출 (즉시 + 3번 지연)
- **11번 호출** = 제스처 여러 번 감지 + 각 4번씩 호출

### 성능 영향

1. **CPU 사용**: 불필요한 InputMethodManager 호출
2. **배터리 소모**: 반복적인 시스템 서비스 호출
3. **로그 오염**: Debug 빌드에서 로그 과다 생성

---

## 🔍 Release 빌드 로그 확인

### InputMethodManager 소스 분석

```java
// Android Framework: InputMethodManager.java
public boolean showSoftInput(View view, int flags) {
    if (DEBUG) Log.v(TAG, "showSoftInput() view=" + view + " flags=" + flags);
    // ...
}
```

**결론**: `Log.v()` (Verbose 레벨)은 **DEBUG 플래그**로 보호됨

### ProGuard/R8 로그 제거

**위치**: `app/proguard-rules.pro`

```proguard
# Remove all logging
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
```

**확인 필요**:

1. KISS 프로젝트의 ProGuard 설정 확인
2. Release 빌드 시 로그 제거 여부 검증

### 답변

**Release 빌드에서는**:

- ✅ Android Framework의 DEBUG 로그는 **자동 제거됨** (시스템 빌드 시)
- ✅ ProGuard/R8이 활성화되면 애플리케이션 로그도 제거
- ⚠️ 하지만 **showSoftInput() 호출 자체**는 여전히 발생

**성능 영향**:

- ❌ 로그는 없지만 **11번의 시스템 호출**은 여전히 발생
- ❌ CPU 및 배터리 소모는 동일
- ❌ InputMethodManager의 내부 로직 11번 실행

---

## 💡 해결 방안

### Option 1: 중복 호출 방지 플래그 (권장 ⭐)

**전략**: 키보드가 이미 표시 중이면 재호출 스킵

```java
// ExperienceTweaks.java
private boolean isKeyboardShowPending = false;
private final Handler keyboardHandler = new Handler(Looper.getMainLooper());

private void showKeyboardWithRetry() {
    if (isKeyboardShowPending) {
        return; // 이미 표시 중이면 스킵
    }
    
    isKeyboardShowPending = true;
    mainActivity.showKeyboard();
    
    // 재시도 로직 (기존 유지)
    keyboardHandler.postDelayed(() -> {
        if (isKeyboardShowPending) {
            mainActivity.showKeyboard();
        }
    }, 10);
    
    keyboardHandler.postDelayed(() -> {
        if (isKeyboardShowPending) {
            mainActivity.showKeyboard();
        }
    }, 100);
    
    keyboardHandler.postDelayed(() -> {
        mainActivity.showKeyboard();
        isKeyboardShowPending = false; // 마지막 재시도 후 플래그 해제
    }, 500);
}

// onCreate() 호출 부분 수정
if (shouldShowKeyboard()) {
    showKeyboardWithRetry(); // 기존 로직 대체
}
```

**장점**:

- ✅ 중복 호출 완전 방지
- ✅ 기존 재시도 로직 유지 (Issue #119 대응)
- ✅ 간단한 구현

**단점**:

- ⚠️ 플래그 관리 필요

---

### Option 2: Handler 재사용 + removeCallbacks

**전략**: 이전 예약된 콜백 취소 후 새로 스케줄링

```java
// ExperienceTweaks.java
private final Handler keyboardHandler = new Handler(Looper.getMainLooper());

private void showKeyboardWithRetry() {
    // 이전 예약 취소
    keyboardHandler.removeCallbacks(displayKeyboardRunnable);
    
    // 즉시 실행
    mainActivity.showKeyboard();
    
    // 새로 스케줄링
    keyboardHandler.postDelayed(displayKeyboardRunnable, 10);
    keyboardHandler.postDelayed(displayKeyboardRunnable, 100);
    keyboardHandler.postDelayed(displayKeyboardRunnable, 500);
}
```

**장점**:

- ✅ 매우 간단 (3줄 추가)
- ✅ 중복 스케줄링 방지

**단점**:

- ⚠️ 여전히 최대 4번 호출 가능 (즉시 + 3번)
- ⚠️ 빠른 제스처 반복 시 중복 가능

---

### Option 3: 키보드 상태 확인 (가장 정확)

**전략**: 키보드가 이미 표시되어 있으면 호출 스킵

```java
// MainActivity.java
private boolean isKeyboardVisible = false;

public void showKeyboard() {
    if (isKeyboardVisible) {
        return; // 이미 표시 중
    }
    
    if (searchEditText.requestFocus()) {
        searchEditText.setCursorVisible(true);
        InputMethodManager mgr = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        mgr.showSoftInput(searchEditText, InputMethodManager.SHOW_IMPLICIT);
        systemUiVisibilityHelper.onKeyboardVisibilityChanged(true);
        isKeyboardVisible = true;
    }
}

@Override
public void hideKeyboard() {
    View view = this.getCurrentFocus();
    if (view != null) {
        InputMethodManager inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        inputManager.hideSoftInputFromWindow(view.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
        systemUiVisibilityHelper.onKeyboardVisibilityChanged(false);
        isKeyboardVisible = false;
    }
    // ...
}

// onCreate()에서 키보드 상태 추적
View rootView = findViewById(android.R.id.content);
rootView.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
    int heightDiff = rootView.getRootView().getHeight() - rootView.getHeight();
    if (heightDiff > 200) { // 키보드가 표시됨
        isKeyboardVisible = true;
    } else { // 키보드가 숨겨짐
        isKeyboardVisible = false;
    }
});
```

**장점**:

- ✅ 가장 정확한 상태 추적
- ✅ 완전한 중복 방지

**단점**:

- ⚠️ 구현 복잡도 높음
- ⚠️ ViewTreeObserver 오버헤드

---

## 🎯 권장 솔루션: Option 2 (Handler 재사용)

### 이유

1. **간단함**: 3줄만 추가
2. **효과적**: 대부분의 중복 호출 방지
3. **안전성**: 기존 로직 유지 (Issue #119 대응)
4. **작업량**: 5분

### 구현

```java
// ExperienceTweaks.java
private final Handler keyboardHandler = new Handler(Looper.getMainLooper());
private final Runnable displayKeyboardRunnable = mainActivity::showKeyboard;

// onCreate() - Line 255-264 수정
if (shouldShowKeyboard()) {
    // 이전 예약된 콜백 취소 (중복 방지)
    keyboardHandler.removeCallbacks(displayKeyboardRunnable);
    
    // Display keyboard
    mainActivity.showKeyboard();

    // For some weird reasons, keyboard may be hidden by the system
    // So we have to run this multiple time at different time
    // See https://github.com/Neamar/KISS/issues/119
    keyboardHandler.postDelayed(displayKeyboardRunnable, 10);
    keyboardHandler.postDelayed(displayKeyboardRunnable, 100);
    keyboardHandler.postDelayed(displayKeyboardRunnable, 500);
} else {
    // Not used (thanks windowSoftInputMode)
    // unless coming back from KISS settings
    mainActivity.hideKeyboard();
}
```

### 예상 효과

**Before**:

```
위로 스크롤 3번 → 12번 호출 (4번씩 3회)
```

**After**:

```
위로 스크롤 3번 → 4번 호출 (마지막 스크롤만 유효)
```

**개선율**: 66-75% 감소

---

## 📊 ProGuard 설정 확인

### 현재 설정

**위치**: `app/proguard-rules.pro`

확인 필요:

- Log 제거 규칙 존재 여부
- Release 빌드 시 ProGuard/R8 활성화 여부

### 권장 추가 규칙

```proguard
# Remove all debug and verbose logs in release builds
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}

# Keep error and warning logs for crash reporting
# public static *** e(...);
# public static *** w(...);
```

---

## 🔄 구현 단계

### Step 1: Handler 재사용 추가 (5분)

```java
// ExperienceTweaks.java - 필드 추가
private final Handler keyboardHandler = new Handler(Looper.getMainLooper());
```

### Step 2: onCreate() 수정 (5분)

```java
if (shouldShowKeyboard()) {
    keyboardHandler.removeCallbacks(displayKeyboardRunnable);
    // ... 기존 코드
}
```

### Step 3: onDestroy()에서 정리 (3분)

```java
@Override
public void onDestroy() {
    keyboardHandler.removeCallbacks(displayKeyboardRunnable);
}
```

### Step 4: 테스트 (10분)

1. 빠르게 위로 스크롤 반복
2. logcat으로 호출 횟수 확인
3. Before: 10+ 호출 → After: 4번 이하

---

## 📝 결론

### 사용자 질문에 대한 답변

> "혹시 release compile 에도 저런 로그가 쌓일까?"

**답변**:

- ✅ **로그는 안 쌓입니다** (Framework의 DEBUG 로그는 Release에서 제거됨)
- ❌ **하지만 호출 자체는 발생** (CPU/배터리 소모)

### 권장 조치

1. **즉시**: Handler 재사용으로 중복 호출 방지 (5분)
2. **선택**: ProGuard 로그 제거 규칙 확인 (불필요할 수 있음)

**효과**:

- 🔋 배터리 소모 감소
- 🚀 성능 향상
- 📉 불필요한 시스템 호출 66-75% 감소
