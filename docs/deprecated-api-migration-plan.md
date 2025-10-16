# Deprecated API 마이그레이션 가이드

## 즉시 수정 완료 항목

### ✅ 1. 사용하지 않는 리소스 제거
- `ic_contact_background.xml` 삭제
- `R.string.toast_hibernate_error` 제거 (모든 번역 파일 포함)
- `R.string.rate_the_app` 제거 (모든 번역 파일 포함)

### ✅ 2. ObsoleteSdkInt 이슈 수정
- `ShortcutUtil.canDeviceShowShortcuts()` → 항상 `true` 반환
- 불필요한 `@RequiresApi` 어노테이션 제거 (O, LOLLIPOP, JELLY_BEAN, M, S)
- `values-v21`, `values-v31` 폴더 제거 (minSdk 33보다 낮음)
- `MainActivity.java` - Edge-to-edge 조건문 제거

## 🔄 3. Deprecated API (참고용)

다음 API들은 deprecated 되었지만 즉시 수정하지 않고 향후 리팩토링 계획:

### MainActivity.java
```java
// ❌ Deprecated (현재 사용 중)
getWindow().setStatusBarColor(Color.TRANSPARENT);
getWindow().setNavigationBarColor(Color.TRANSPARENT);
getWindow().getDecorView().setSystemUiVisibility(
    View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
);

// ✅ Modern API (향후 적용)
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
if (controller != null) {
    controller.setSystemBarsBehavior(
        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    );
}
```

### UIColors.java
```java
// ❌ Deprecated
Resources.getColor(int id)

// ✅ Modern
ContextCompat.getColor(Context context, int id)
```

### SettingsFragment.java
```java
// ❌ Deprecated
setTargetFragment(Fragment fragment, int requestCode)

// ✅ Modern (Fragment Result API)
getParentFragmentManager().setFragmentResultListener("requestKey", this, 
    (requestKey, result) -> {
        // Handle result
    }
);
```

### PreferenceManager
```java
// ❌ Deprecated
android.preference.PreferenceManager.getDefaultSharedPreferences(context)

// ✅ Modern
androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
```

## 📊 수정 통계

- **즉시 수정**: 57개 이슈
  - 리소스 제거: 3개
  - ObsoleteSdkInt: 54개
  
- **향후 계획**: 52개 deprecated API
  - WindowInsets API: 높은 우선순위
  - Fragment Result API: 중간 우선순위
  - Resources API: 낮은 우선순위

## 다음 단계

1. ✅ 빌드 테스트
2. ✅ Lint 재검사
3. ✅ Git commit
4. 📝 향후: Deprecated API 마이그레이션 Issue 생성
