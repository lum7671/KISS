# Phase 6: Preference UI 계층 구조 및 마이그레이션 전략

**작성일**: 2025년 10월 15일  
**전략**: Bottom-Up 방식, 공통 베이스 클래스 우선 마이그레이션  

---

## 📊 UI 계층 구조 (Hierarchy)

### Level 0: Android Framework (마이그레이션 대상)

```
android.preference.*  →  androidx.preference.*
├── android.preference.SwitchPreference
├── android.preference.DialogPreference
└── android.preference.PreferenceActivity
```

### Level 1: KISS 공통 베이스 클래스 (최우선 마이그레이션)

```
fr.neamar.kiss.preference.*
├── SwitchPreference (extends android.preference.SwitchPreference)
│   - 역할: 요약 텍스트 최대 10줄 제한
│   - 라인 수: 35줄
│   - 사용처: 3개 하위 클래스 + XML 직접 사용
│
└── (DialogPreference 공통 베이스는 없음, 각자 직접 상속)
```

### Level 2: 비즈니스 로직 클래스 (Level 1 완료 후)

#### 2-1. SwitchPreference 기반 (3개)

```
SwitchPreference (KISS)
├── FreezeHistorySwitch
│   - 역할: 히스토리 동결 스위치
│   - 라인 수: 32줄
│   - 추가 로직: DataHandler 연동
│
├── RootModeSwitch
│   - 역할: Root 모드 on/off
│   - 라인 수: 30줄
│   - 추가 로직: RootHandler 체크
│
└── ShizukuModeSwitch
    - 역할: Shizuku 모드 on/off
    - 라인 수: 45줄
    - 추가 로직: Shizuku 권한 체크, 리스너 등록
```

#### 2-2. DialogPreference 기반 - 간단한 것들 (7개)

```
android.preference.DialogPreference (직접 상속)
├── ResetPreference
│   - 역할: 히스토리 초기화
│   - 라인 수: 32줄
│   - 로직: DataHandler.clearHistory()
│
├── ResetFavoritesPreference
│   - 역할: 즐겨찾기 초기화
│   - 라인 수: 32줄
│   - 로직: SharedPreferences 수정
│
├── ResetSearchProvidersPreference
│   - 역할: 검색 프로바이더 초기화
│   - 라인 수: 29줄
│   - 로직: SharedPreferences 수정
│
├── ResetExcludedAppsPreference
│   - 역할: 제외 앱 목록 초기화
│   - 라인 수: 32줄
│   - 로직: SharedPreferences 수정
│
├── ResetExcludedFromHistoryAppsPreference
│   - 역할: 히스토리 제외 앱 초기화
│   - 라인 수: 29줄
│   - 로직: SharedPreferences 수정
│
├── ResetExcludedAppShortcutsPreference
│   - 역할: 제외 앱 바로가기 초기화
│   - 라인 수: 40줄
│   - 로직: SharedPreferences + DataHandler 연동
│
└── RestartPreference
    - 역할: 앱 재시작
    - 라인 수: 25줄
    - 로직: 액티비티 재시작
```

#### 2-3. DialogPreference 기반 - 중간 복잡도 (3개)

```
android.preference.DialogPreference (직접 상속)
├── DefaultLauncherPreference
│   - 역할: 기본 런처 설정 다이얼로그
│   - 라인 수: 30줄
│   - 로직: RoleManager 사용 (Android 10+)
│
├── NotificationPreference
│   - 역할: 알림 권한 요청
│   - 라인 수: 40줄
│   - 로직: NotificationManager 체크
│
└── ResetShortcutsPreference
    - 역할: 바로가기 초기화
    - 라인 수: 35줄
    - 로직: DataHandler + SharedPreferences
```

#### 2-4. DialogPreference 기반 - 복잡한 것들 (3개)

```
android.preference.DialogPreference (직접 상속)
├── ImportSettingsPreference
│   - 역할: 설정 가져오기 (파일 선택)
│   - 라인 수: 150줄+
│   - 로직: 파일 I/O, JSON 파싱, 권한 처리
│   - 복잡도: ⚠️⚠️⚠️ 높음
│
├── ExportSettingsPreference
│   - 역할: 설정 내보내기 (파일 저장)
│   - 라인 수: 140줄+
│   - 로직: 파일 I/O, JSON 생성, 권한 처리
│   - 복잡도: ⚠️⚠️⚠️ 높음
│
└── AddSearchProviderPreference
    - 역할: 검색 프로바이더 추가 (커스텀 다이얼로그)
    - 라인 수: 200줄+
    - 로직: 복잡한 다이얼로그 뷰, 동적 UI 생성
    - 복잡도: ⚠️⚠️⚠️ 높음
```

#### 2-5. DialogPreference 기반 - 가장 복잡 (1개)

```
android.preference.DialogPreference (직접 상속)
└── ColorPreference
    - 역할: 색상 선택 다이얼로그
    - 라인 수: 180줄
    - 로직: ColorPickerPalette 통합, 커스텀 뷰
    - 복잡도: ⚠️⚠️⚠️⚠️ 매우 높음
    - 의존성: com.android.colorpicker 패키지
```

### Level 3: 특수 클래스 (별도 처리 필요)

```
특수 케이스
├── ExcludePreferenceScreen
│   - 역할: 동적으로 100+ 개의 SwitchPreference 생성
│   - 라인 수: 180줄
│   - 복잡도: ⚠️⚠️⚠️ 매우 높음
│   - 의존성: PreferenceScreen, DataHandler
│
└── PreferenceScreenHelper
    - 역할: 유틸리티 헬퍼
    - 라인 수: 50줄
    - 복잡도: ⚠️ 낮음
```

### Level 4: 액티비티 (최상위)

```
SettingsActivity (extends PreferenceActivity)
- 역할: 전체 설정 화면 호스팅
- 라인 수: 859줄
- 복잡도: ⚠️⚠️⚠️⚠️ 매우 높음
- 마이그레이션: PreferenceFragmentCompat 기반으로 전환
```

---

## 🎯 Bottom-Up 마이그레이션 전략

### 핵심 원칙

1. **공통 베이스 클래스 먼저**
   - 새 버전과 구 버전 공존
   - 하위 클래스는 점진적 전환
   - 마이그레이션 완료 후 구버전 제거

2. **간단한 것부터 복잡한 것으로**
   - 의존성 없는 것 우선
   - 테스트 용이한 것 우선
   - 리스크 낮은 것 우선

3. **각 단계별 완전한 테스트**
   - 기능 동작 확인
   - UI 일관성 확인
   - 롤백 가능한 상태 유지

---

## 📝 단계별 마이그레이션 계획

### Step 1: 공통 베이스 클래스 생성 (2-3시간)

**목표**: androidx 기반 새 베이스 클래스 생성, 구버전과 공존

#### 1-1. SwitchPreference 마이그레이션

**파일 생성**:
```
app/src/main/java/fr/neamar/kiss/preference/
├── SwitchPreference.java (기존, android.preference 기반)
└── SwitchPreferenceCompat.java (신규, androidx.preference 기반)
```

**SwitchPreferenceCompat.java 구현**:
```java
package fr.neamar.kiss.preference;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;
import androidx.preference.SwitchPreferenceCompat as AndroidXSwitchPreference;

/**
 * AndroidX 기반 SwitchPreference
 * 기존 SwitchPreference와 동일한 기능 (요약 텍스트 최대 10줄)
 */
public class SwitchPreferenceCompat extends AndroidXSwitchPreference {

    public SwitchPreferenceCompat(Context context) {
        this(context, null);
    }

    public SwitchPreferenceCompat(Context context, AttributeSet attrs) {
        this(context, attrs, androidx.preference.R.attr.switchPreferenceCompatStyle);
    }

    public SwitchPreferenceCompat(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        View summary = holder.findViewById(android.R.id.summary);
        if (summary instanceof TextView) {
            ((TextView) summary).setMaxLines(10);
        }
    }
}
```

**체크리스트**:
- [ ] SwitchPreferenceCompat.java 생성
- [ ] 기존 SwitchPreference와 동일한 동작 확인
- [ ] 테스트 코드 작성 (가능하면)
- [ ] 문서 업데이트

#### 1-2. DialogPreference 베이스 (선택적)

**분석 결과**: 각 DialogPreference 클래스가 직접 상속하므로 공통 베이스 불필요  
**대신**: 각 클래스를 개별적으로 마이그레이션

**참고 패턴**:
```java
// 기존 (android.preference)
public class ResetPreference extends android.preference.DialogPreference {
    @Override
    public void onClick(DialogInterface dialog, int which) { ... }
}

// 신규 (androidx.preference)
public class ResetPreferenceCompat extends androidx.preference.DialogPreference {
    // DialogFragment 기반으로 재구현 필요
}
```

---

### Step 2: SwitchPreference 하위 클래스 마이그레이션 (3-4시간)

**우선순위**: 간단한 순서대로

#### 2-1. FreezeHistorySwitch (가장 간단)

**파일**:
```
app/src/main/java/fr/neamar/kiss/preference/
├── FreezeHistorySwitch.java (기존, SwitchPreference 기반)
└── FreezeHistorySwitchCompat.java (신규, SwitchPreferenceCompat 기반)
```

**변경 사항**:
```java
// Before
public class FreezeHistorySwitch extends SwitchPreference {
    // 기존 로직 유지
}

// After
public class FreezeHistorySwitchCompat extends SwitchPreferenceCompat {
    // 동일한 로직, 베이스 클래스만 변경
}
```

#### 2-2. RootModeSwitch (중간)

**추가 고려사항**: RootHandler 연동 확인

#### 2-3. ShizukuModeSwitch (복잡)

**추가 고려사항**: Shizuku 리스너, 권한 체크

**체크리스트**:
- [ ] 각 클래스별 Compat 버전 생성
- [ ] XML에서 사용할 수 있도록 등록
- [ ] 기존 버전과 병렬 테스트
- [ ] 동작 확인

---

### Step 3: 간단한 DialogPreference 마이그레이션 (4-5시간)

**대상**: Reset 계열 7개 클래스

#### 3-1. 가장 간단한 것부터

1. **RestartPreference** (25줄)
   - 단순 액티비티 재시작
   - 의존성 없음
   - 테스트 용이

2. **ResetPreference** (32줄)
   - DataHandler.clearHistory() 호출만
   - 간단한 로직

3. **Reset* 계열 5개**
   - 유사한 패턴
   - SharedPreferences 수정
   - 한 번 패턴 확립하면 빠르게 적용 가능

**AndroidX 패턴 (DialogFragment 기반)**:
```java
// Step 1: DialogPreference 클래스
public class ResetPreferenceCompat extends androidx.preference.DialogPreference {
    public ResetPreferenceCompat(Context context, AttributeSet attrs) {
        super(context, attrs);
    }
}

// Step 2: DialogFragment 클래스
public class ResetPreferenceDialogFragmentCompat 
        extends PreferenceDialogFragmentCompat {
    
    public static ResetPreferenceDialogFragmentCompat newInstance(String key) {
        ResetPreferenceDialogFragmentCompat fragment = 
            new ResetPreferenceDialogFragmentCompat();
        Bundle args = new Bundle(1);
        args.putString(ARG_KEY, key);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onDialogClosed(boolean positiveResult) {
        if (positiveResult) {
            // 기존 onClick 로직 이동
            KissApplication.getApplication(getContext())
                .getDataHandler()
                .clearHistory();
        }
    }
}
```

**체크리스트**:
- [ ] 각 Reset* 클래스별 Compat + DialogFragment 생성
- [ ] XML에 등록
- [ ] 다이얼로그 표시 테스트
- [ ] 실제 동작 테스트

---

### Step 4: 중간 복잡도 DialogPreference (3-4시간)

**대상**: DefaultLauncherPreference, NotificationPreference, ResetShortcutsPreference

**추가 고려사항**:
- RoleManager API (DefaultLauncher)
- NotificationManager 권한 체크 (Notification)
- DataHandler 연동 (ResetShortcuts)

---

### Step 5: 복잡한 DialogPreference (6-8시간)

**대상**: Import/Export, AddSearchProvider, ColorPreference

#### 5-1. Import/ExportSettingsPreference

**복잡도 요인**:
- 파일 I/O (SAF - Storage Access Framework)
- JSON 파싱/생성
- 권한 처리
- ActivityResult API

**마이그레이션 접근**:
```java
// ActivityResultLauncher 사용 (AndroidX 패턴)
private ActivityResultLauncher<Intent> filePickerLauncher;

@Override
public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    filePickerLauncher = registerForActivityResult(
        new ActivityResultContracts.OpenDocument(),
        uri -> {
            // 파일 처리
        }
    );
}
```

#### 5-2. AddSearchProviderPreference

**복잡도 요인**:
- 커스텀 다이얼로그 뷰
- 동적 UI 생성
- EditText 입력 검증

#### 5-3. ColorPreference

**복잡도 요인**:
- ColorPickerPalette 통합
- 커스텀 뷰 생성
- 색상 선택 콜백

**추천 접근**:
- ColorPickerDialog를 PreferenceDialogFragmentCompat 안에 통합
- 기존 ColorPickerPalette 재사용
- onColorSelected 콜백을 onDialogClosed로 변환

---

### Step 6: 특수 케이스 (4-5시간)

#### 6-1. ExcludePreferenceScreen

**현재 구조**:
```java
public class ExcludePreferenceScreen {
    public static android.preference.PreferenceScreen getInstance(
        @NonNull PreferenceActivity preferenceActivity,
        @NonNull String activityTitle
    ) {
        // 동적으로 100+ 개의 SwitchPreference 생성
        for (AppPojo app : appList) {
            SwitchPreference pref = createExcludeAppSwitch(...);
            excludedAppsScreen.addPreference(pref);
        }
        return excludedAppsScreen;
    }
}
```

**AndroidX 마이그레이션**:
```java
public class ExcludePreferenceScreenCompat {
    public static androidx.preference.PreferenceScreen getInstance(
        @NonNull PreferenceFragmentCompat fragment,
        @NonNull String activityTitle
    ) {
        PreferenceManager preferenceManager = fragment.getPreferenceManager();
        PreferenceScreen screen = preferenceManager.createPreferenceScreen(
            fragment.requireContext()
        );
        
        // SwitchPreferenceCompat 사용
        for (AppPojo app : appList) {
            SwitchPreferenceCompat pref = createExcludeAppSwitch(...);
            screen.addPreference(pref);
        }
        return screen;
    }
}
```

**주의사항**:
- PreferenceActivity → PreferenceFragmentCompat 파라미터 변경
- SwitchPreference → SwitchPreferenceCompat 사용
- 동적 생성 로직은 최대한 유지

---

### Step 7: SettingsActivity 마이그레이션 (6-8시간)

**가장 큰 변경**: PreferenceActivity → AppCompatActivity + PreferenceFragmentCompat

#### 7-1. SettingsFragment 생성

```java
public class SettingsFragment extends PreferenceFragmentCompat {
    
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        // 기존 addPreferencesFromResource() 로직
        setPreferencesFromResource(R.xml.preferences, rootKey);
        
        // 기존 onCreate()의 Preference 설정 로직 이동
        initializePreferences();
    }
    
    @Override
    public void onDisplayPreferenceDialog(Preference preference) {
        // 커스텀 DialogPreference 처리
        if (preference instanceof ResetPreferenceCompat) {
            DialogFragment fragment = 
                ResetPreferenceDialogFragmentCompat.newInstance(preference.getKey());
            fragment.setTargetFragment(this, 0);
            fragment.show(getParentFragmentManager(), null);
        } else {
            super.onDisplayPreferenceDialog(preference);
        }
    }
    
    private void initializePreferences() {
        // 기존 SettingsActivity.onCreate()의 로직 이동
        // - Preference 찾기
        // - 리스너 설정
        // - 동적 Preference 추가
    }
}
```

#### 7-2. SettingsActivity 단순화

```java
public class SettingsActivity extends AppCompatActivity {
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings_container, new SettingsFragment())
                .commit();
        }
    }
}
```

#### 7-3. XML 리소스 업데이트

```xml
<!-- app/src/main/res/xml/preferences.xml -->
<!-- Before -->
<PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android">
    <fr.neamar.kiss.preference.SwitchPreference ... />
    <fr.neamar.kiss.preference.ResetPreference ... />
</PreferenceScreen>

<!-- After -->
<PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto">
    <fr.neamar.kiss.preference.SwitchPreferenceCompat ... />
    <fr.neamar.kiss.preference.ResetPreferenceCompat ... />
</PreferenceScreen>
```

---

### Step 8: 구버전 제거 (2-3시간)

**모든 마이그레이션 완료 후 안전하게 제거**:

1. 구버전 클래스 삭제
   ```
   - SwitchPreference.java
   - FreezeHistorySwitch.java (구버전)
   - Reset*.java (구버전)
   - etc.
   ```

2. Compat 접미사 제거
   ```
   SwitchPreferenceCompat → SwitchPreference
   ResetPreferenceCompat → ResetPreference
   etc.
   ```

3. 최종 테스트
   - 모든 설정 화면 동작 확인
   - 앱 재시작 테스트
   - 설정 저장/로드 테스트

---

## 📈 예상 시간표

| Step | 작업 내용 | 예상 시간 | 누적 시간 |
|------|----------|----------|----------|
| Step 1 | 공통 베이스 클래스 | 2-3시간 | 2-3시간 |
| Step 2 | SwitchPreference 하위 3개 | 3-4시간 | 5-7시간 |
| Step 3 | 간단한 DialogPreference 7개 | 4-5시간 | 9-12시간 |
| Step 4 | 중간 DialogPreference 3개 | 3-4시간 | 12-16시간 |
| Step 5 | 복잡한 DialogPreference 4개 | 6-8시간 | 18-24시간 |
| Step 6 | 특수 케이스 2개 | 4-5시간 | 22-29시간 |
| Step 7 | SettingsActivity | 6-8시간 | 28-37시간 |
| Step 8 | 구버전 제거 및 정리 | 2-3시간 | 30-40시간 |

**총 예상 시간**: 30-40시간 (기존 예상 15-20시간보다 많음)

---

## ✅ 각 Step별 완료 기준

### Step 1 완료 기준
- [ ] SwitchPreferenceCompat.java 생성 및 동작 확인
- [ ] 기존 SwitchPreference와 병렬 존재
- [ ] 테스트 앱에서 둘 다 동작 확인

### Step 2 완료 기준
- [ ] 3개 하위 클래스 모두 Compat 버전 생성
- [ ] XML에서 전환 가능
- [ ] 기능 테스트 통과

### Step 3-6 완료 기준
- [ ] 각 클래스별 Compat 버전 생성
- [ ] DialogFragment 정상 동작
- [ ] 기존 기능 유지 확인

### Step 7 완료 기준
- [ ] SettingsFragment 정상 동작
- [ ] 모든 Preference 표시 확인
- [ ] 동적 Preference 생성 정상
- [ ] 설정 변경 저장 확인

### Step 8 완료 기준
- [ ] 구버전 클래스 모두 제거
- [ ] 컴파일 warning 0개
- [ ] 전체 기능 테스트 통과
- [ ] 회귀 테스트 통과

---

## 🚨 리스크 및 대응 방안

### 리스크 1: DialogFragment 패턴 복잡도

**문제**: PreferenceDialogFragmentCompat 사용이 복잡함  
**대응**: 
- 먼저 가장 간단한 RestartPreference로 패턴 확립
- 템플릿 코드 작성 후 복사/수정

### 리스크 2: ColorPicker 통합

**문제**: com.android.colorpicker 패키지가 androidx와 호환되지 않을 수 있음  
**대응**:
- 먼저 ColorPicker 없는 다른 Preference 완료
- 필요시 대체 라이브러리 검토
- 최악의 경우 커스텀 구현

### 리스크 3: ExcludePreferenceScreen 동적 생성

**문제**: 100+ 개의 동적 Preference 생성이 느려질 수 있음  
**대응**:
- 기존 로직 최대한 유지
- 성능 테스트 우선
- 필요시 RecyclerView 기반 커스텀 구현 고려

### 리스크 4: 예상 시간 초과

**문제**: 40시간이 예상보다 길 수 있음  
**대응**:
- Step 1-3 완료 후 중간 평가
- 필요시 우선순위 재조정
- Phase 6를 Phase 6A, 6B로 분할

---

## 📚 참고 자료

### AndroidX Preference 문서
- [Preference Guide](https://developer.android.com/guide/topics/ui/settings)
- [PreferenceFragmentCompat](https://developer.android.com/reference/androidx/preference/PreferenceFragmentCompat)
- [DialogPreference](https://developer.android.com/reference/androidx/preference/DialogPreference)
- [PreferenceDialogFragmentCompat](https://developer.android.com/reference/androidx/preference/PreferenceDialogFragmentCompat)

### 마이그레이션 가이드
- [AndroidX Migration Guide](https://developer.android.com/jetpack/androidx/migrate)
- [Preference Migration](https://developer.android.com/jetpack/androidx/releases/preference)

### KISS 프로젝트 문서
- `phase5-preference-migration-analysis.md` (전체 분석)
- `warning-removal-phases1-5-completion-report.md` (Phase 1-5 성공 사례)
- `copilot-instructions.md` (프로젝트 가이드)

---

## 🎯 결론

### Bottom-Up 전략의 장점

1. ✅ **리스크 최소화**
   - 간단한 것부터 시작
   - 각 단계별 테스트 가능
   - 문제 발생 시 롤백 용이

2. ✅ **구버전과 공존**
   - 점진적 마이그레이션
   - 언제든 중단 가능
   - 부분 완료 상태로도 배포 가능 (Compat 접미사로 구분)

3. ✅ **학습 곡선**
   - 간단한 클래스로 패턴 학습
   - 템플릿 코드 재사용
   - 복잡한 클래스는 마지막에

### 다음 단계

1. **Step 1 시작**: SwitchPreferenceCompat 생성
2. **브랜치 생성**: `feature/phase6-step1-switch-base`
3. **문서 업데이트**: 각 Step별 진행 상황 기록

**시작 권장**: Step 1부터 차근차근 진행하세요! 🚀
