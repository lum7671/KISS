# 키보드 레이아웃 조정 문제 재분석

**날짜**: 2025년 10월 17일  
**우선순위**: 🔴 Critical (검색바가 키보드에 가려짐)

## 🐛 새로운 문제 발견

### 레이아웃 구조 (스크린샷 분석)

```text
┌────────────────────────┐
│   Widget Area (상단)    │
├────────────────────────┤
│                        │
│   Result List (중간)   │ ← 검색 결과
│                        │
├────────────────────────┤
│  Favorite Bar (중하단)  │
├────────────────────────┤
│  Search Bar (최하단)    │ ← 검색 입력창
└────────────────────────┘
```

### adjustPan 문제

```text
[키보드 표시 시 - adjustPan]
┌────────────────────────┐
│   (가려짐)              │
├────────────────────────┤
│   Result List          │
├────────────────────────┤
│  Favorite Bar          │
├────────────────────────┤
│  Search Bar            │ ← 키보드가 가림! ❌
├────────────────────────┤
│   🎹 Keyboard          │
└────────────────────────┘
```

**문제점**:

- ❌ 검색바가 키보드에 완전히 가려짐
- ❌ 사용자가 입력하는 텍스트를 볼 수 없음
- ❌ 검색 결과도 키보드에 가려질 수 있음

---

## 🔍 올바른 해결 방법

### Option 1: adjustResize + KeyboardScrollHider 개선 (권장 ⭐)

**전략**: `adjustResize` 유지하되, "지진" 효과만 제거

**원리**:

- `adjustResize`는 키보드 표시 시 창 크기를 줄여서 검색바가 키보드 위에 보이도록 함
- 문제는 `KeyboardScrollHider`의 애니메이션과 타이밍 충돌
- **해결**: 애니메이션 타이밍 개선으로 충돌 방지

#### 구현 방법

**1단계**: `KeyboardScrollHider` 개선

```java
// KeyboardScrollHider.java
@Override
public boolean onTouch(View v, MotionEvent event) {
    switch (event.getActionMasked()) {
        case MotionEvent.ACTION_MOVE:
            this.offsetYCurrent = event.getY();
            this.lastMotionEvent = event;

            // 기존: updateListViewHeight() 즉시 호출
            // 문제: adjustResize와 충돌
            
            // 개선: 키보드가 완전히 숨겨질 때까지 대기
            if (isScrolled() && !isKeyboardHiding) {
                isKeyboardHiding = true;
                this.handler.hideKeyboard();
                
                // 키보드 숨김 완료 후 애니메이션 시작
                this.list.postDelayed(() -> {
                    updateListViewHeight();
                    isKeyboardHiding = false;
                }, 100); // 키보드 숨김 시작 후 약간의 딜레이
            }
            break;
    }
    return false;
}
```

**2단계**: ViewTreeObserver로 키보드 상태 감지

```java
// MainActivity.java onCreate()
View rootView = findViewById(android.R.id.content);
rootView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
    private int previousHeight = 0;
    
    @Override
    public void onGlobalLayout() {
        int currentHeight = rootView.getHeight();
        
        if (previousHeight != 0) {
            if (currentHeight < previousHeight) {
                // 키보드가 나타남 (창 축소)
                onKeyboardShown();
            } else if (currentHeight > previousHeight) {
                // 키보드가 숨겨짐 (창 확대)
                onKeyboardHidden();
            }
        }
        
        previousHeight = currentHeight;
    }
});

private void onKeyboardHidden() {
    // 키보드 완전히 숨겨진 후에 리스트 애니메이션 시작
    if (hider != null) {
        hider.handleResizeDone();
    }
}
```

**장점**:

- ✅ 검색바가 키보드 위에 정상 표시
- ✅ 검색 결과 리스트도 적절히 리사이징
- ✅ "지진" 효과 제거 (타이밍 조정)

**단점**:

- ⚠️ 구현 복잡도 중간 (1-2일)
- ⚠️ 타이밍 튜닝 필요

---

### Option 2: adjustNothing + WindowInsetsController (복잡)

**API 30+ (Android 11+)만 지원**

```kotlin
// MainActivity.kt
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
    window.setDecorFitsSystemWindows(false)
    
    ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
        val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
        val systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        
        // 검색바를 키보드 위로 이동
        searchEditLayout.updatePadding(bottom = imeInsets.bottom)
        
        // 리스트 높이 조정
        resultLayout.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            bottomMargin = imeInsets.bottom + searchEditLayout.height
        }
        
        insets
    }
}
```

**장점**:

- ✅ 완전한 제어 가능
- ✅ 부드러운 애니메이션

**단점**:

- ❌ API 30+ 전용 (Android 11+)
- ❌ 구현 복잡도 매우 높음
- ❌ 기존 코드 대폭 수정 필요

---

### Option 3: adjustResize + 즉시 복구 (임시 해결)

**전략**: `adjustResize` 복구 + 스크롤 시 애니메이션 완전 비활성화

```java
// KeyboardScrollHider.java - 간단한 수정
@Override
public boolean onTouch(View v, MotionEvent event) {
    switch (event.getActionMasked()) {
        case MotionEvent.ACTION_MOVE:
            // 스크롤 감지 시 즉시 키보드 숨김 (애니메이션 없음)
            if (isScrolled()) {
                this.handler.hideKeyboard();
                // 애니메이션 로직 전부 스킵
                return false;
            }
            break;
    }
    return false;
}
```

**장점**:

- ✅ 매우 간단 (10분 작업)
- ✅ 검색바 정상 표시
- ✅ "지진" 효과 제거 (애니메이션 자체 스킵)

**단점**:

- ⚠️ 리스트 애니메이션 없어짐 (즉시 전환)
- ⚠️ UX 품질 다소 저하

---

## 🎯 권장 솔루션: Option 1 (adjustResize + 타이밍 개선)

### 이유

1. **검색바 가시성**: 필수 요구사항 충족
2. **UX 품질**: 애니메이션 유지하면서 "지진" 제거
3. **호환성**: 모든 Android 버전 지원
4. **작업량**: 1-2일 (적정 수준)

### 구현 계획

#### Phase 1: adjustResize 복구 (5분)

```xml
<!-- AndroidManifest.xml - 원래대로 -->
android:windowSoftInputMode="stateAlwaysHidden|adjustResize"
```

#### Phase 2: KeyboardScrollHider 개선 (1일)

**파일**: `app/src/main/java/fr/neamar/kiss/ui/KeyboardScrollHider.java`

**변경 사항**:

1. **키보드 상태 플래그 추가**

```java
private boolean isKeyboardVisible = false;
private boolean isKeyboardAnimating = false;
```

2. **스크롤 시 대기 로직**

```java
case MotionEvent.ACTION_MOVE:
    this.offsetYCurrent = event.getY();
    this.lastMotionEvent = event;

    if (isScrolled() && isKeyboardVisible && !isKeyboardAnimating) {
        isKeyboardAnimating = true;
        this.handler.hideKeyboard();
        
        // 키보드 숨김 애니메이션 시작 후 약간 대기
        this.list.postDelayed(() -> {
            updateListViewHeight();
            isKeyboardAnimating = false;
            isKeyboardVisible = false;
        }, 150); // 키보드 숨김 애니메이션 시간
    } else if (!isKeyboardVisible) {
        // 키보드가 이미 숨겨진 상태에서는 즉시 업데이트
        updateListViewHeight();
    }
    break;
```

3. **키보드 상태 추적**

```java
// MainActivity.java에서 호출
public void setKeyboardVisible(boolean visible) {
    this.isKeyboardVisible = visible;
}
```

#### Phase 3: MainActivity에서 키보드 상태 추적 (30분)

```java
// MainActivity.java onCreate()
searchEditText.setOnFocusChangeListener((v, hasFocus) -> {
    if (hasFocus) {
        hider.setKeyboardVisible(true);
    }
});

// hideKeyboard() 메서드 수정
@Override
public void hideKeyboard() {
    View view = this.getCurrentFocus();
    if (view != null) {
        InputMethodManager inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        inputManager.hideSoftInputFromWindow(view.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
        
        systemUiVisibilityHelper.onKeyboardVisibilityChanged(false);
        
        // 키보드 숨김 시작 알림
        if (hider != null) {
            hider.setKeyboardVisible(false);
        }
    }
    // ... 기존 코드
}
```

---

## 📊 예상 결과

### Before (현재 adjustPan)

```text
✅ "지진" 효과 없음
❌ 검색바 가려짐
❌ 검색 결과 일부 가려짐
```

### After (adjustResize + 타이밍 개선)

```text
✅ "지진" 효과 없음
✅ 검색바 키보드 위에 표시
✅ 검색 결과 정상 표시
✅ 부드러운 애니메이션
```

---

## 🔄 즉시 임시 조치 (Option 3)

더 나은 해결책 구현 전까지 **즉시 적용 가능**:

```xml
<!-- AndroidManifest.xml - 복구 -->
android:windowSoftInputMode="stateAlwaysHidden|adjustResize"
```

```java
// KeyboardScrollHider.java - 애니메이션 간소화
private void updateListViewHeight() {
    // 애니메이션 로직 전부 주석 처리
    // 즉시 리사이징만 적용
    if (this.getWindowPadding() < this.initialWindowPadding) {
        return; // 키보드 숨김 중에는 아무것도 안함
    }
}
```

**효과**:

- ✅ 검색바 정상 표시 (즉시 해결)
- ⚠️ "지진" 효과는 남아있음 (향후 개선 필요)

---

## 📝 작업 순서

### 즉시 (5분)

1. `adjustResize` 복구
2. 빌드 및 테스트
3. 검색바 가시성 확인

### 1-2일 후 (정식 해결)

1. `KeyboardScrollHider` 타이밍 개선
2. 키보드 상태 추적 추가
3. 테스트 및 튜닝

---

**결론**: `adjustPan`은 검색바 가림 문제가 있으므로, `adjustResize` + 타이밍 개선이 올바른 해결책입니다. 먼저 즉시 `adjustResize`로 복구하고, 이후 "지진" 효과를 제거하는 방향으로 진행하겠습니다.
