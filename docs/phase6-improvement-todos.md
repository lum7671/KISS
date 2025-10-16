# Phase 6 Preference Migration - Improvement TODOs

**작성일**: 2025-10-15  
**목적**: Preference 마이그레이션 중 발견한 코드 개선 기회 기록

---

## 🎯 우선순위 분류

- 🔴 **HIGH**: 기능/성능에 직접 영향, 빠른 수정 권장
- 🟡 **MEDIUM**: 코드 품질 개선, 유지보수성 향상
- 🟢 **LOW**: Nice-to-have, 시간 여유시 개선

---

## 📋 발견된 개선 사항

### 🟡 MEDIUM: ColorPreference - OnGlobalLayoutListener 메모리 누수 가능성

**위치**: `ColorPreferenceDialogFragmentCompat.java:60-75`  
**발견 단계**: Step 5 (ColorPreference 마이그레이션)

**현재 코드**:

```java
view.getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
    private boolean ignoreNextUpdate = false;
    
    public void onGlobalLayout() {
        if (this.ignoreNextUpdate) {
            this.ignoreNextUpdate = false;
            return;
        }
        // ... 레이아웃 계산
        this.ignoreNextUpdate = true;
        drawPalette();
    }
});
```

**문제점**:

- Listener가 제거되지 않음 (add만 하고 remove 없음)
- Dialog가 닫혀도 ViewTreeObserver에 listener 남아있을 가능성
- 메모리 누수 가능성 (Fragment lifecycle과 연동 안됨)

**개선 방안**:

```java
// Option 1: Listener를 멤버 변수로 저장 후 onDestroyView에서 제거
private OnGlobalLayoutListener layoutListener;

@Override
protected void onBindDialogView(View view) {
    layoutListener = new OnGlobalLayoutListener() {
        // ...
    };
    view.getViewTreeObserver().addOnGlobalLayoutListener(layoutListener);
}

@Override
public void onDestroyView() {
    if (layoutListener != null && getView() != null) {
        getView().getViewTreeObserver().removeOnGlobalLayoutListener(layoutListener);
    }
    super.onDestroyView();
}

// Option 2: 한 번만 실행 후 자동 제거 (SDK 16+)
view.getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
    @Override
    public void onGlobalLayout() {
        view.getViewTreeObserver().removeOnGlobalLayoutListener(this);
        // ... 레이아웃 계산 (한 번만 실행)
    }
});
```

**우선순위**: 🟡 MEDIUM  
**예상 작업**: 10-15분  
**영향 범위**: ColorPreference 사용 시  

---

### 🟡 MEDIUM: AddSearchProviderPreference - 테마별 색상 하드코딩

**위치**: `AddSearchProviderPreferenceDialogFragmentCompat.java:88-95`  
**발견 단계**: Step 5 (AddSearchProvider 마이그레이션)

**현재 코드**:

```java
String theme = prefs.getString("theme", "light");
if (!theme.contains("dark")) {
    TypedArray ta = context.obtainStyledAttributes(R.style.AppThemeLight, attrs);
    providerName.setTextColor(ta.getColor(0, Color.TRANSPARENT));
    providerUri.setTextColor(ta.getColor(0, Color.TRANSPARENT));
    ta.recycle();
}
```

**문제점**:

- `R.style.AppThemeLight` 하드코딩
- 테마 확장 시 (예: 새로운 테마 추가) 수정 필요
- String comparison으로 테마 판단 (theme.contains("dark"))

**개선 방안**:

```java
// Option 1: 현재 테마에서 textColor 직접 가져오기
TypedValue typedValue = new TypedValue();
context.getTheme().resolveAttribute(android.R.attr.editTextColor, typedValue, true);
int textColor = typedValue.data;
providerName.setTextColor(textColor);
providerUri.setTextColor(textColor);

// Option 2: ContextThemeWrapper 사용
// (이미 적용된 테마 속성 자동 상속)
```

**우선순위**: 🟡 MEDIUM  
**예상 작업**: 15-20분  
**영향 범위**: AddSearchProvider 사용 시, 새 테마 추가 시  

---

### 🟢 LOW: Import/ExportSettings - Magic Number 183

**위치**: `ExportSettingsPreferenceDialogFragmentCompat.java:53`  
**발견 단계**: Step 5 (Export/Import 마이그레이션)

**현재 코드**:

```java
// Min version required to read those settings
out.put("__v", 183);
```

**문제점**:

- Magic number `183` 의미 불명확
- BuildConfig.VERSION_CODE와 관계 불분명
- 버전 정책이 코드에 하드코딩

**개선 방안**:

```java
// Constants 클래스에 정의
public class SettingsConstants {
    /**
     * Minimum KISS version code required to import these settings.
     * Version 183 = v4.1.0 (settings format stabilized)
     */
    public static final int MIN_SETTINGS_VERSION = 183;
}

// 사용
out.put("__v", SettingsConstants.MIN_SETTINGS_VERSION);
```

**우선순위**: 🟢 LOW  
**예상 작업**: 10분  
**영향 범면**: 문서화, 유지보수성  

---

### 🟡 MEDIUM: DialogPreference - 중복된 4-constructor 패턴

**위치**: 모든 Preference Compat 클래스  
**발견 단계**: Step 1-5 (전체 마이그레이션 과정)

**현재 코드** (18개 클래스에서 반복):

```java
public XxxPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
    // ... 개별 초기화
}

public XxxPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr) {
    this(context, attrs, defStyleAttr, 0);
}

public XxxPreferenceCompat(Context context, AttributeSet attrs) {
    this(context, attrs, androidx.preference.R.attr.dialogPreferenceStyle);
}

public XxxPreferenceCompat(Context context) {
    this(context, null);
}
```

**문제점**:

- Boilerplate 코드 반복 (18개 클래스 × 4 constructors = 72개)
- 일관성은 좋으나 유지보수 부담
- 추상 베이스 클래스로 통합 가능

**개선 방안**:

```java
// Option 1: Abstract base class 생성
public abstract class BaseDialogPreferenceCompat extends DialogPreference {
    public BaseDialogPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        onPreferenceCreated(context, attrs);
    }
    
    // 나머지 3개 constructor 구현
    
    protected abstract void onPreferenceCreated(Context context, AttributeSet attrs);
}

// 사용
public class ExportSettingsPreferenceCompat extends BaseDialogPreferenceCompat {
    @Override
    protected void onPreferenceCreated(Context context, AttributeSet attrs) {
        // 개별 초기화만
    }
}
```

**우선순위**: 🟡 MEDIUM (Phase 6 완료 후)  
**예상 작업**: 1-2시간 (전체 리팩토링)  
**영향 범위**: 모든 Preference Compat 클래스  
**주의사항**: Phase 6 완료 후 별도 리팩토링 단계에서 진행 권장

---

### 🟢 LOW: AddSearchProvider - 6단계 검증 로직 가독성

**위치**: `AddSearchProviderPreferenceDialogFragmentCompat.java:137-188`  
**발견 단계**: Step 5

**현재 코드**:

```java
private boolean validate() {
    if (!validateEmpty()) { return false; }
    if (!validatePipes()) {
        Toast.makeText(..., R.string.search_provider_error_char, ...).show();
        return false;
    }
    if (!validateNameExists()) {
        Toast.makeText(..., R.string.search_provider_error_exists, ...).show();
        return false;
    }
    // ... 6단계 검증 계속
}
```

**문제점**:

- 검증 로직이 길고 복잡 (50줄)
- Early return 남발로 가독성 저하
- Toast 메시지 반복 패턴

**개선 방안**:

```java
// Option 1: Validation chain pattern
private boolean validate() {
    ValidationError error = new ValidationChain()
        .check(this::validateEmpty, null)
        .check(this::validatePipes, R.string.search_provider_error_char)
        .check(this::validateNameExists, R.string.search_provider_error_exists)
        .check(this::validateQueryPlaceholder, R.string.search_provider_error_placeholder)
        .checkOrExit(this::isPlaceholder)
        .check(this::validateUrl, null)
        .check(this::validateUri, this::getUriErrorMessage)
        .validate();
    
    if (error != null) {
        if (error.messageId != null) {
            Toast.makeText(requireContext(), error.messageId, Toast.LENGTH_SHORT).show();
        }
        return false;
    }
    return true;
}

// Option 2: Result<T, E> pattern (Kotlin-style)
```

**우선순위**: 🟢 LOW  
**예상 작업**: 1-2시간  
**영향 범위**: AddSearchProvider만  
**주의사항**: 현재 코드도 충분히 작동, over-engineering 주의

---

### 🔴 HIGH: NotificationPreference - 잘못된 string 리소스 참조

**위치**: `preferences.xml:602` (Step 4에서 발견 및 수정 완료)  
**발견 단계**: Step 5 빌드 시  
**상태**: ✅ **수정 완료**

**문제**:

```xml
<!-- 잘못된 코드 -->
android:dialogMessage="@string/notification_dialog_text"
```

**수정**:

```xml
<!-- 올바른 코드 -->
android:dialogMessage="@string/notification_dialog"
```

**교훈**:

- 원본 Preference 파일의 속성을 정확히 복사해야 함
- 빌드 에러로 즉시 발견 가능하므로 큰 문제는 아님

---

## 📊 개선 우선순위 요약

### 🔴 HIGH (즉시 수정)

- ✅ NotificationPreference string 리소스 (수정 완료)

### 🟡 MEDIUM (Phase 6 완료 후)

1. **ColorPreference OnGlobalLayoutListener 제거** (10-15분)
   - 메모리 누수 방지

2. **AddSearchProvider 테마 색상 로직** (15-20분)
   - 하드코딩 제거, 확장성 향상

3. **4-constructor 패턴 리팩토링** (1-2시간)
   - BaseDialogPreferenceCompat 추상 클래스 생성
   - 모든 Compat 클래스 통합

### 🟢 LOW (시간 여유시)

1. **Magic Number 183 상수화** (10분)
2. **AddSearchProvider 검증 로직 리팩토링** (1-2시간)

---

## 🎯 실행 계획

### Phase 6 완료 전

- ✅ 개선사항 문서화 (현재 문서)
- 🔄 Step 6-8 마이그레이션 집중

### Phase 6 완료 후 (Step 9: 개선 단계)

1. **Week 1**: 🟡 MEDIUM 우선순위 개선
   - ColorPreference listener 제거
   - AddSearchProvider 테마 로직 개선

2. **Week 2**: 🟡 MEDIUM 리팩토링
   - BaseDialogPreferenceCompat 생성
   - 모든 Compat 클래스 통합

3. **Week 3**: 🟢 LOW 개선 (선택)
   - Magic number 상수화
   - 검증 로직 리팩토링 (필요시)

---

## 📝 개선 추적

| 항목 | 우선순위 | 상태 | 완료일 | 커밋 |
|------|---------|------|--------|------|
| NotificationPreference string | 🔴 HIGH | ✅ 완료 | 2025-10-15 | 8a7e6e376 |
| ColorPreference listener | 🟡 MEDIUM | ⚪ 대기 | - | - |
| AddSearchProvider 테마 | 🟡 MEDIUM | ⚪ 대기 | - | - |
| 4-constructor 리팩토링 | 🟡 MEDIUM | ⚪ 대기 | - | - |
| Magic number 상수화 | 🟢 LOW | ⚪ 대기 | - | - |
| 검증 로직 리팩토링 | 🟢 LOW | ⚪ 대기 | - | - |

---

## 🤔 개선 가이드라인

### 언제 개선할까?

- ✅ **즉시**: 빌드 에러, 기능 버그
- ✅ **Phase 6 완료 후**: 메모리 누수, 확장성 문제
- ⚠️ **신중히**: 리팩토링 (over-engineering 주의)
- ❌ **하지 않기**: 작동하는 코드의 불필요한 변경

### 개선 시 주의사항

1. **기능 유지**: 마이그레이션 중에는 1:1 호환성 최우선
2. **테스트**: 개선 후 반드시 빌드 및 동작 테스트
3. **문서화**: 변경 이유와 영향 범위 명확히 기록
4. **단계적**: 한 번에 하나씩, 커밋 분리

---

**마지막 업데이트**: 2025-10-15  
**다음 업데이트**: Phase 6 완료 후 (Step 9)
