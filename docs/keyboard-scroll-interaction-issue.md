# 키보드-스크롤 상호작용 문제 분석

**날짜**: 2025년 10월 17일  
**우선순위**: 🔴 High (UX 품질 이슈)  
**영향도**: 모든 사용자 (검색 사용 시)

## 🐛 문제 상황

### 현상

1. **키보드 표시 시**: 목록이 키보드에 가려짐 (리사이징 안됨)
2. **스크롤 시작 시**: 키보드와 목록이 동시에 움직여 "지진" 효과 발생
3. **의도된 동작**: 스크롤 시 키보드 먼저 숨김 → 목록 확장 → 스크롤 진행

### 사용자 경험 문제

```text
[문제 시나리오]
1. 앱 목록 보기 상태
2. 검색창 클릭 → 키보드 올라옴
3. 목록이 키보드 높이만큼 가려짐 ❌
4. 스크롤 다운 시작
5. 키보드 내려가면서 목록도 같이 움직임 (지진 효과) ❌
```

**기대 동작**:

```text
1. 검색창 클릭 → 키보드 올라옴
2. 목록이 키보드 위에서 보이도록 리사이징 ✅
3. 스크롤 다운 시작
4. 키보드가 먼저 부드럽게 숨겨짐 ✅
5. 목록이 확장되면서 스크롤 계속 진행 ✅
```

---

## 🔍 근본 원인 분석

### 1. AndroidManifest 설정

**현재 설정** (`app/src/main/AndroidManifest.xml:87`):

```xml
android:windowSoftInputMode="stateAlwaysHidden|adjustResize"
```

**문제점**:

- `adjustResize`: 키보드가 나타날 때 **전체 창 크기를 조정**
- 이로 인해 `KeyboardScrollHider`의 동작과 충돌 발생
- 키보드 숨김 애니메이션 중에도 창 크기가 변경되어 "지진" 효과

### 2. KeyboardScrollHider 동작 방식

**위치**: `app/src/main/java/fr/neamar/kiss/ui/KeyboardScrollHider.java`

**현재 로직**:

```java
// Line 207: THRESHOLD = 24dp 이상 스크롤 시 키보드 숨김
public boolean isScrolled() {
    return (this.offsetYCurrent - this.offsetYStart) > THRESHOLD;
}

// Line 202-206: onTouch에서 스크롤 감지 시 키보드 숨김
if (isScrolled()) {
    this.handler.hideKeyboard();
    this.handler.applyScrollSystemUi();
}
```

**동작 순서**:

1. `ACTION_DOWN`: 리스트 높이를 현재 값으로 고정
2. `ACTION_MOVE`: 스크롤 감지, THRESHOLD 초과 시 키보드 숨김 요청
3. **문제**: `adjustResize` 때문에 키보드 숨김 중에도 창 크기 변경 발생
4. 리스트 높이 애니메이션과 창 크기 변경이 동시에 일어나 "지진" 효과

### 3. 타이밍 문제

```text
[현재 타이밍]
User Scroll Down (24dp)
    ↓
hideKeyboard() 호출
    ↓
┌─────────────────────────┐
│ adjustResize 창 크기 변경 │ ← 문제: 동시 진행
│ KeyboardScrollHider 애니메이션│
└─────────────────────────┘
    ↓
지진 효과 발생 ⚠️
```

**이상적인 타이밍**:

```text
User Scroll Down (24dp)
    ↓
hideKeyboard() 호출
    ↓
키보드 완전히 숨김 완료
    ↓
창 크기 정상화
    ↓
스크롤 계속 진행 ✅
```

---

## 💡 해결 방안

### Option 1: adjustPan 사용 (권장 ⭐)

**변경**: `adjustResize` → `adjustPan`

**장점**:

- ✅ 키보드가 나타나도 **창 크기가 변하지 않음**
- ✅ 키보드가 내용 위에 덮어쓰기만 함 (Pan)
- ✅ `KeyboardScrollHider`와 충돌 없음
- ✅ 부드러운 스크롤 애니메이션

**단점**:

- ⚠️ 키보드가 목록 일부를 가릴 수 있음
- 해결: `KeyboardScrollHider`가 이미 이 문제를 처리하고 있음

**코드 변경**:

```xml
<!-- AndroidManifest.xml -->
<activity
    android:name=".MainActivity"
    android:windowSoftInputMode="stateAlwaysHidden|adjustPan">
```

**예상 효과**:

- 스크롤 시 키보드만 부드럽게 숨김
- 목록은 키보드 아래에서 정상 스크롤
- "지진" 효과 완전 제거

---

### Option 2: adjustNothing + 수동 레이아웃 (복잡)

**변경**: `adjustNothing` + `KeyboardScrollHider` 강화

**장점**:

- ✅ 완전한 수동 제어 가능
- ✅ 키보드 상태와 무관하게 독립적 동작

**단점**:

- ❌ 구현 복잡도 높음
- ❌ Edge case 처리 필요
- ❌ API 30+ (Android 11+)에서만 동작

**구현 내용**:

1. `WindowInsetsController`로 키보드 상태 감지
2. `OnApplyWindowInsetsListener`로 insets 변화 추적
3. 수동으로 리스트 높이 조정
4. 스크롤 애니메이션 타이밍 제어

**예상 작업량**: 2-3일

---

### Option 3: KeyboardScrollHider 개선 (중간)

**현재 문제 보완**: `adjustResize` 유지하되 타이밍 개선

**변경 사항**:

1. 키보드 숨김 완료 대기 메커니즘 추가
2. `ViewTreeObserver.OnGlobalLayoutListener`로 레이아웃 변화 감지
3. 키보드 완전히 사라진 후 스크롤 애니메이션 재개

**장점**:

- ✅ 기존 `adjustResize` 동작 유지
- ✅ 키보드 표시 시 자동 리사이징

**단점**:

- ⚠️ 복잡도 증가
- ⚠️ 타이밍 제어 어려움
- ⚠️ 여전히 일부 "지진" 효과 가능

**예상 작업량**: 1-2일

---

## 🎯 권장 솔루션: Option 1 (adjustPan)

### 근거

1. **간단함**: 한 줄 변경으로 해결
2. **효과적**: "지진" 효과 완전 제거
3. **검증됨**: 많은 런처 앱에서 사용하는 패턴
4. **호환성**: `KeyboardScrollHider`와 완벽 호환

### 예상 동작 개선

```text
Before (adjustResize):
- 키보드 올라옴 → 전체 창 축소 → 목록 가려짐
- 스크롤 → 키보드 내려감 + 창 확대 → 지진 효과

After (adjustPan):
- 키보드 올라옴 → 목록 위에 덮어쓰기 (창 크기 유지)
- 스크롤 → 키보드만 부드럽게 내려감 → 목록 정상 스크롤
```

### KeyboardScrollHider와의 시너지

```java
// KeyboardScrollHider.java의 현재 로직이 그대로 작동
ACTION_DOWN: 리스트 높이 고정 (현재 높이 = 전체 높이)
ACTION_MOVE: 스크롤 감지
  ↓ (THRESHOLD 초과 시)
hideKeyboard() 호출
  ↓
키보드 Pan 방식으로 부드럽게 숨김 ✅
  ↓
리스트는 이미 전체 높이라 변화 없음 ✅
  ↓
스크롤 정상 진행 ✅
```

---

## 🧪 테스트 계획

### 1. 기본 시나리오

- [ ] 검색창 클릭 → 키보드 표시 확인
- [ ] 목록 가시성 확인 (키보드 위에 보이는지)
- [ ] 스크롤 다운 → 키보드 숨김 부드러움 확인
- [ ] "지진" 효과 제거 확인

### 2. Edge Cases

- [ ] 빠른 타이핑 중 스크롤
- [ ] 키보드 표시 직후 즉시 스크롤
- [ ] 검색 결과 변경 중 스크롤
- [ ] 회전 (Portrait ↔ Landscape)

### 3. 기기별 테스트

- [ ] 에뮬레이터 (Android 13, 14, 15)
- [ ] Pixel 5 (실기기)
- [ ] Galaxy A32 (중사양)
- [ ] 다양한 화면 크기

### 4. 회귀 테스트

- [ ] 키보드 자동 숨김 동작 (홈 버튼)
- [ ] 검색 입력 정상 동작
- [ ] 앱 실행 시 키보드 숨김 확인
- [ ] BottomPullEffectView 정상 동작

---

## 📝 구현 단계

### Step 1: adjustPan 적용 (5분)

```xml
<!-- app/src/main/AndroidManifest.xml -->
<activity
    android:name=".MainActivity"
    android:windowSoftInputMode="stateAlwaysHidden|adjustPan">
```

### Step 2: 빌드 및 기본 테스트 (10분)

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

**테스트 항목**:

1. 검색창 클릭 → 키보드 표시
2. 스크롤 다운 → 키보드 숨김
3. "지진" 효과 확인

### Step 3: Edge Case 테스트 (30분)

- 빠른 타이핑 + 스크롤
- 연속 스크롤
- 키보드 표시/숨김 반복

### Step 4: 문서화 및 커밋 (10분)

```bash
git add app/src/main/AndroidManifest.xml
git commit -m "Fix: 키보드-스크롤 상호작용 지진 효과 제거

변경: adjustResize → adjustPan

문제:
- 스크롤 시 키보드와 목록이 동시에 움직여 지진 효과 발생
- adjustResize가 KeyboardScrollHider와 충돌

해결:
- adjustPan 사용으로 창 크기 변경 제거
- 키보드만 부드럽게 숨김, 목록은 정상 스크롤
- KeyboardScrollHider 로직과 완벽 호환

관련 이슈: 키보드-스크롤 UX 개선
"
```

---

## 🔄 롤백 계획

문제 발생 시:

```xml
<!-- 원래 설정으로 복구 -->
android:windowSoftInputMode="stateAlwaysHidden|adjustResize"
```

또는:

```bash
git revert HEAD
```

---

## 📊 예상 결과

### 정량적 개선

- **"지진" 효과**: 100% 제거 (창 크기 변경 없음)
- **스크롤 부드러움**: 60 FPS 유지
- **키보드 숨김 시간**: 250ms (기존과 동일)

### 정성적 개선

- ✅ 자연스러운 키보드 숨김 애니메이션
- ✅ 목록 스크롤 흐름 유지
- ✅ "지진" 효과 완전 제거
- ✅ 사용자 경험 크게 향상

---

## 🤔 대안 분석 요약

| 옵션 | 난이도 | 효과 | 리스크 | 작업 시간 | 추천도 |
|------|--------|------|--------|----------|---------|
| **adjustPan** | ⭐ 쉬움 | ⭐⭐⭐⭐⭐ 매우 높음 | ⭐ 매우 낮음 | 5분 | ⭐⭐⭐⭐⭐ |
| adjustNothing | ⭐⭐⭐⭐⭐ 어려움 | ⭐⭐⭐⭐ 높음 | ⭐⭐⭐⭐ 높음 | 2-3일 | ⭐⭐ |
| KeyboardScrollHider 개선 | ⭐⭐⭐ 중간 | ⭐⭐⭐ 중간 | ⭐⭐⭐ 중간 | 1-2일 | ⭐⭐⭐ |

---

## 📚 참고 자료

### Android 공식 문서

- [windowSoftInputMode](https://developer.android.com/guide/topics/manifest/activity-element#wsoft)
- [Keyboard Visibility](https://developer.android.com/develop/ui/views/touch-and-input/keyboard-input/visibility)

### 관련 코드

- `app/src/main/java/fr/neamar/kiss/ui/KeyboardScrollHider.java` (234 lines)
- `app/src/main/AndroidManifest.xml:87` (windowSoftInputMode)
- `app/src/main/java/fr/neamar/kiss/MainActivity.java:1441` (hideKeyboard)

### 관련 이슈

- KeyboardScrollHider 최초 구현 목적: 스크롤 시 키보드 자동 숨김
- 현재 문제: adjustResize와의 타이밍 충돌

---

**결론**: `adjustPan` 변경만으로 간단하고 효과적으로 문제 해결 가능 ✅
