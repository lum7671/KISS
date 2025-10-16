# Phase 6: Preference 마이그레이션 Step-by-Step 실행 가이드

**작성일**: 2025년 10월 15일  
**전략**: Bottom-Up, 각 Step별 독립 브랜치  
**참고**: Phase 2 Searcher 성공 사례 기반

---

## 📋 전체 개요

### Phase 2 성공 사례에서 배운 것

```bash
feature/phase2-step1-analysis      # 분석만, 코드 수정 없음
feature/phase2-step2-base          # Base 클래스 구현
feature/phase2-step3-query-searcher # 하나씩 전환
feature/phase2-step4-expansion     # 순차적 확장
feature/phase2-step5-cleanup       # 정리
```

**핵심 원칙**:

1. ✅ 각 Step은 독립 브랜치
2. ✅ 완료 후 dev에 머지
3. ✅ 문제 발생 시 해당 Step만 롤백
4. ✅ 각 Step별 완료 보고서 작성

---

## 🎯 Phase 6 Step별 브랜치 전략

### Step 구조

```
dev (기준)
├── feature/phase6-step1-switch-base       → dev
├── feature/phase6-step2-switch-subclasses → dev
├── feature/phase6-step3-simple-dialogs    → dev
├── feature/phase6-step4-medium-dialogs    → dev
├── feature/phase6-step5-complex-dialogs   → dev
├── feature/phase6-step6-special-cases     → dev
├── feature/phase6-step7-settings-activity → dev
└── feature/phase6-step8-cleanup           → dev
```

---

## 📝 Step 1: SwitchPreference 베이스 클래스

### 목표

- androidx 기반 SwitchPreferenceCompat 생성
- 기존 SwitchPreference와 공존
- 테스트로 동작 확인

### 브랜치 생성

```bash
cd /Users/1001028/git/KISS
git checkout dev
git pull origin dev
git checkout -b feature/phase6-step1-switch-base
```

### 작업 내용

#### 1. build.gradle 의존성 추가

**파일**: `app/build.gradle`

```gradle
dependencies {
    // 기존 의존성들...
    
    // AndroidX Preference 추가
    implementation 'androidx.preference:preference-ktx:1.2.1'
}
```

#### 2. SwitchPreferenceCompat.java 생성

**파일**: `app/src/main/java/fr/neamar/kiss/preference/SwitchPreferenceCompat.java`

```java
package fr.neamar.kiss.preference;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import androidx.preference.PreferenceViewHolder;

/**
 * AndroidX 기반 SwitchPreference
 * 기존 SwitchPreference.java와 동일한 기능 제공
 * - 요약 텍스트 최대 10줄 제한
 * 
 * Note: 마이그레이션 완료 후 기존 SwitchPreference.java를 제거하고
 *       이 클래스를 SwitchPreference.java로 리네임 예정
 */
public class SwitchPreferenceCompat extends androidx.preference.SwitchPreferenceCompat {

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

        // 기존 SwitchPreference와 동일: 요약 텍스트 최대 10줄
        View summary = holder.findViewById(android.R.id.summary);
        if (summary instanceof TextView) {
            ((TextView) summary).setMaxLines(10);
        }
    }
}
```

#### 3. 테스트용 XML 항목 추가

**파일**: `app/src/main/res/xml/preferences.xml`

기존 항목은 유지하고, 테스트용으로 하나 추가:

```xml
<!-- 테스트용: SwitchPreferenceCompat -->
<fr.neamar.kiss.preference.SwitchPreferenceCompat
    android:key="test-switch-compat"
    android:title="[TEST] Switch Compat"
    android:summary="AndroidX 기반 새 버전 테스트"
    android:defaultValue="false"
    android:order="999" />
```

#### 4. 빌드 및 테스트

```bash
# 빌드
./gradlew assembleDebug

# 설치 (에뮬레이터 또는 실제 기기)
./gradlew installDebug

# 테스트
# 1. 앱 실행
# 2. 설정 화면 진입
# 3. 맨 아래 [TEST] Switch Compat 항목 확인
# 4. 토글 동작 확인
# 5. 요약 텍스트 10줄 제한 확인
```

### 완료 기준

- [ ] build.gradle에 androidx.preference 추가됨
- [ ] SwitchPreferenceCompat.java 생성됨
- [ ] 컴파일 성공
- [ ] 설치 성공
- [ ] 설정 화면에서 테스트 항목 표시됨
- [ ] 토글 동작 정상
- [ ] 기존 SwitchPreference 항목들도 정상 동작

### 커밋 및 머지

```bash
# 변경사항 커밋
git add app/build.gradle
git add app/src/main/java/fr/neamar/kiss/preference/SwitchPreferenceCompat.java
git add app/src/main/res/xml/preferences.xml
git commit -m "Phase 6 Step 1: Add SwitchPreferenceCompat base class

- Add androidx.preference:preference-ktx:1.2.1 dependency
- Create SwitchPreferenceCompat.java (AndroidX-based)
- Add test preference item in preferences.xml
- Existing SwitchPreference.java remains for compatibility

Related: phase6-preference-ui-hierarchy.md"

# dev에 머지
git checkout dev
git merge feature/phase6-step1-switch-base --no-ff

# 푸시
git push origin dev

# 브랜치 정리 (선택)
git branch -d feature/phase6-step1-switch-base
```

### 완료 보고서 작성

**파일**: `docs/phase6-step1-completion-report.md`

```markdown
# Phase 6 Step 1 완료 보고서

**완료일**: 2025년 10월 XX일
**브랜치**: feature/phase6-step1-switch-base
**목표**: SwitchPreferenceCompat 베이스 클래스 생성

## 작업 내용
- androidx.preference 의존성 추가
- SwitchPreferenceCompat.java 생성 (35줄)
- 테스트 항목 추가

## 테스트 결과
- ✅ 컴파일 성공
- ✅ 앱 실행 정상
- ✅ 설정 화면 진입 정상
- ✅ 테스트 항목 표시됨
- ✅ 토글 동작 정상
- ✅ 기존 항목 영향 없음

## 다음 단계
Phase 6 Step 2: SwitchPreference 하위 클래스 마이그레이션
```

---

## 📝 Step 2: SwitchPreference 하위 클래스 마이그레이션

### 목표

- FreezeHistorySwitch, RootModeSwitch, ShizukuModeSwitch를 Compat 버전으로 마이그레이션
- 기존 클래스와 공존
- XML에서 선택적으로 사용 가능

### 브랜치 생성

```bash
git checkout dev
git pull origin dev
git checkout -b feature/phase6-step2-switch-subclasses
```

### 작업 내용

#### 1. FreezeHistorySwitchCompat.java 생성

**파일**: `app/src/main/java/fr/neamar/kiss/preference/FreezeHistorySwitchCompat.java`

```java
package fr.neamar.kiss.preference;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AttributeSet;

import androidx.preference.PreferenceManager;

import fr.neamar.kiss.DataHandler;
import fr.neamar.kiss.KissApplication;

/**
 * AndroidX 기반 FreezeHistorySwitch
 * 기존 FreezeHistorySwitch.java와 동일한 기능
 */
public class FreezeHistorySwitchCompat extends SwitchPreferenceCompat {

    public FreezeHistorySwitchCompat(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public FreezeHistorySwitchCompat(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public FreezeHistorySwitchCompat(Context context) {
        super(context);
    }

    @Override
    protected void onClick() {
        super.onClick();
        
        // 기존 로직 그대로 복사
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        DataHandler dataHandler = KissApplication.getApplication(getContext()).getDataHandler();
        
        if (dataHandler != null) {
            boolean isFrozen = prefs.getBoolean(getKey(), false);
            dataHandler.setFreezeHistory(isFrozen);
        }
    }
}
```

#### 2. RootModeSwitchCompat.java 생성

**파일**: `app/src/main/java/fr/neamar/kiss/preference/RootModeSwitchCompat.java`

```java
package fr.neamar.kiss.preference;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.Toast;

import fr.neamar.kiss.KissApplication;
import fr.neamar.kiss.R;
import fr.neamar.kiss.RootHandler;

/**
 * AndroidX 기반 RootModeSwitch
 */
public class RootModeSwitchCompat extends SwitchPreferenceCompat {

    public RootModeSwitchCompat(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public RootModeSwitchCompat(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public RootModeSwitchCompat(Context context) {
        super(context);
    }

    @Override
    protected void onClick() {
        RootHandler rootHandler = KissApplication.getApplication(getContext()).getRootHandler();
        
        if (!rootHandler.isRootAvailable()) {
            setChecked(false);
            Toast.makeText(getContext(), R.string.root_mode_error, Toast.LENGTH_SHORT).show();
            return;
        }
        
        super.onClick();
    }
}
```

#### 3. ShizukuModeSwitchCompat.java 생성

**파일**: `app/src/main/java/fr/neamar/kiss/preference/ShizukuModeSwitchCompat.java`

```java
package fr.neamar.kiss.preference;

import android.content.Context;
import android.util.AttributeSet;

import fr.neamar.kiss.KissApplication;

/**
 * AndroidX 기반 ShizukuModeSwitch
 */
public class ShizukuModeSwitchCompat extends SwitchPreferenceCompat {

    public ShizukuModeSwitchCompat(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public ShizukuModeSwitchCompat(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ShizukuModeSwitchCompat(Context context) {
        super(context);
    }

    @Override
    protected void onClick() {
        // 기존 ShizukuModeSwitch 로직 복사
        // Shizuku 권한 체크 등
        super.onClick();
    }
}
```

#### 4. XML에서 일부 항목 전환 (테스트)

**파일**: `app/src/main/res/xml/preferences.xml`

기존 항목 중 하나만 Compat으로 전환하여 테스트:

```xml
<!-- Before -->
<fr.neamar.kiss.preference.FreezeHistorySwitch
    android:defaultValue="false"
    android:key="freeze-history"
    android:order="48"
    android:summary="@string/freeze_history_summary"
    android:title="@string/freeze_history_name" />

<!-- After (테스트) -->
<fr.neamar.kiss.preference.FreezeHistorySwitchCompat
    android:defaultValue="false"
    android:key="freeze-history"
    android:order="48"
    android:summary="@string/freeze_history_summary"
    android:title="@string/freeze_history_name" />
```

### 완료 기준

- [ ] 3개 Compat 클래스 생성됨
- [ ] 컴파일 성공
- [ ] 설치 성공
- [ ] FreezeHistorySwitch 동작 확인
- [ ] RootModeSwitch 권한 체크 확인
- [ ] ShizukuModeSwitch 권한 체크 확인
- [ ] 기존 클래스도 여전히 동작

### 커밋 및 머지

```bash
git add app/src/main/java/fr/neamar/kiss/preference/*Compat.java
git add app/src/main/res/xml/preferences.xml
git commit -m "Phase 6 Step 2: Add SwitchPreference subclass Compat versions

- Create FreezeHistorySwitchCompat.java
- Create RootModeSwitchCompat.java
- Create ShizukuModeSwitchCompat.java
- Test one item in preferences.xml (freeze-history)
- Existing classes remain for compatibility"

git checkout dev
git merge feature/phase6-step2-switch-subclasses --no-ff
git push origin dev
```

---

## 📝 Step 3: 간단한 DialogPreference 마이그레이션

### 목표

- RestartPreference 등 7개 간단한 DialogPreference를 Compat 버전으로 마이그레이션
- DialogFragment 패턴 확립

### 브랜치 생성

```bash
git checkout dev
git pull origin dev
git checkout -b feature/phase6-step3-simple-dialogs
```

### 작업 순서 (우선순위)

1. **RestartPreference** (가장 간단, 패턴 확립용)
2. **ResetPreference** (히스토리 초기화)
3. **ResetFavoritesPreference**
4. **ResetSearchProvidersPreference**
5. **ResetExcludedAppsPreference**
6. **ResetExcludedFromHistoryAppsPreference**
7. **ResetExcludedAppShortcutsPreference**

### 예제: RestartPreference

#### 1. RestartPreferenceCompat.java

```java
package fr.neamar.kiss.preference;

import android.content.Context;
import android.util.AttributeSet;

/**
 * AndroidX 기반 RestartPreference
 */
public class RestartPreferenceCompat extends androidx.preference.DialogPreference {

    public RestartPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public RestartPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public RestartPreferenceCompat(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public RestartPreferenceCompat(Context context) {
        super(context);
    }
}
```

#### 2. RestartPreferenceDialogFragmentCompat.java

```java
package fr.neamar.kiss.preference;

import android.os.Bundle;

import androidx.preference.PreferenceDialogFragmentCompat;

/**
 * RestartPreferenceCompat을 위한 DialogFragment
 */
public class RestartPreferenceDialogFragmentCompat extends PreferenceDialogFragmentCompat {

    public static RestartPreferenceDialogFragmentCompat newInstance(String key) {
        RestartPreferenceDialogFragmentCompat fragment = new RestartPreferenceDialogFragmentCompat();
        Bundle args = new Bundle(1);
        args.putString(ARG_KEY, key);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onDialogClosed(boolean positiveResult) {
        if (positiveResult) {
            // 기존 RestartPreference의 onClick 로직
            android.os.Process.killProcess(android.os.Process.myPid());
        }
    }
}
```

### 완료 기준

- [ ] 7개 클래스 + 7개 DialogFragment 생성 (총 14개 파일)
- [ ] 각각 컴파일 성공
- [ ] 각각 다이얼로그 표시 확인
- [ ] 각각 실제 동작 확인 (초기화, 재시작 등)

### 커밋 및 머지

```bash
git add app/src/main/java/fr/neamar/kiss/preference/*Compat.java
git add app/src/main/java/fr/neamar/kiss/preference/*DialogFragmentCompat.java
git commit -m "Phase 6 Step 3: Add simple DialogPreference Compat versions

- Create 7 DialogPreference Compat classes
- Create 7 corresponding DialogFragment classes
- Establish DialogFragment pattern for future steps"

git checkout dev
git merge feature/phase6-step3-simple-dialogs --no-ff
git push origin dev
```

---

## 📝 Step 4-8: 나머지 Steps

**동일한 패턴으로 계속 진행**:

### Step 4: 중간 복잡도 DialogPreference

- 브랜치: `feature/phase6-step4-medium-dialogs`
- 대상: DefaultLauncher, Notification, ResetShortcuts

### Step 5: 복잡한 DialogPreference

- 브랜치: `feature/phase6-step5-complex-dialogs`
- 대상: Import/Export, AddSearchProvider, ColorPreference

### Step 6: 특수 케이스

- 브랜치: `feature/phase6-step6-special-cases`
- 대상: ExcludePreferenceScreen, PreferenceScreenHelper

### Step 7: SettingsActivity

- 브랜치: `feature/phase6-step7-settings-activity`
- 대상: SettingsFragment 생성, SettingsActivity 단순화

### Step 8: 구버전 제거 및 정리

- 브랜치: `feature/phase6-step8-cleanup`
- 작업: Compat 접미사 제거, 구버전 클래스 삭제

---

## 📊 진행 상황 추적

### Progress Tracker

```markdown
# Phase 6 Progress

- [ ] Step 1: SwitchPreference 베이스 (2-3h)
  - [ ] 브랜치 생성
  - [ ] SwitchPreferenceCompat 생성
  - [ ] 빌드 및 테스트
  - [ ] 커밋 및 머지
  - [ ] 완료 보고서 작성

- [ ] Step 2: SwitchPreference 하위 클래스 (3-4h)
  - [ ] 3개 Compat 클래스 생성
  - [ ] 테스트
  - [ ] 머지

- [ ] Step 3: 간단한 DialogPreference (4-5h)
  - [ ] 7개 클래스 + DialogFragment
  - [ ] 테스트
  - [ ] 머지

- [ ] Step 4: 중간 DialogPreference (3-4h)
- [ ] Step 5: 복잡한 DialogPreference (6-8h)
- [ ] Step 6: 특수 케이스 (4-5h)
- [ ] Step 7: SettingsActivity (6-8h)
- [ ] Step 8: 정리 (2-3h)
```

---

## 🚨 각 Step 실패 시 대응

### 문제 발생 시

1. **즉시 중단**

   ```bash
   git checkout dev
   ```

2. **문제 분석**
   - 로그 확인
   - 테스트 결과 검토
   - 문제 문서화

3. **브랜치 보존**

   ```bash
   # 브랜치 삭제하지 말고 보관
   git checkout -b feature/phase6-stepX-debug
   ```

4. **대안 고려**
   - 해당 Step 스킵 가능
   - 다음 Step 진행 가능 (독립적이므로)

---

## 📚 각 Step별 체크리스트 템플릿

### 작업 전

- [ ] dev 브랜치 최신 상태 확인
- [ ] 새 브랜치 생성
- [ ] 작업 목표 명확히 파악

### 작업 중

- [ ] 코드 작성
- [ ] 컴파일 확인
- [ ] 로컬 테스트

### 작업 후

- [ ] 기능 테스트 통과
- [ ] 기존 기능 영향 없음 확인
- [ ] 커밋 메시지 작성
- [ ] dev에 머지
- [ ] 푸시
- [ ] 완료 보고서 작성

---

## 🎯 성공 기준

### 각 Step 성공

- ✅ 컴파일 성공
- ✅ 기능 테스트 통과
- ✅ 기존 기능 영향 없음
- ✅ 문서 업데이트 완료

### 전체 Phase 6 성공

- ✅ 모든 8 Steps 완료
- ✅ 43개 Preference warning 제거
- ✅ 전체 설정 화면 정상 동작
- ✅ 회귀 테스트 통과

---

## 📖 참고 문서

- `phase6-preference-ui-hierarchy.md` - UI 계층 구조
- `phase5-preference-migration-analysis.md` - 전체 분석
- `phase2-completion-report.md` - Phase 2 성공 사례
- `warning-removal-phases1-5-completion-report.md` - Phase 1-5 성공 사례

---

**준비 완료!** Step 1부터 시작하세요! 🚀

```bash
cd /Users/1001028/git/KISS
git checkout dev
git pull origin dev
git checkout -b feature/phase6-step1-switch-base
```
