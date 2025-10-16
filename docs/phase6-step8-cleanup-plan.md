# Phase 6 Step 8: Cleanup & Legacy 제거 계획

**작성일**: 2025년 10월 16일  
**목표**: APK 용량 감소 및 코드 정리  
**예상 시간**: 2-3시간  
**예상 효과**: 200-300KB APK 용량 감소

---

## 📊 현재 상태 분석

### APK 용량 추이

```
v4.2.0 (2025-10-15): 2.4MB
v4.2.4 (2025-10-16): 3.8MB  ← 1.4MB 증가!
```

### 용량 증가 원인

1. **NewSettingsActivity 마이그레이션** (Phase 6 Step 7 완료)
   - NewSettingsActivity.kt (4KB)
   - SettingsFragment.java (52KB, 1,183줄)

2. **Compat 클래스 33개 추가** (Phase 6 Step 1-6 완료)
   - 총 약 200KB

3. **Legacy 클래스와 중복 유지**
   - 기존 15개 Preference 클래스
   - 새 33개 Compat 클래스
   - **문제**: 동일 기능이 2벌 존재

---

## 🎯 Step 8 목표

### 1차 목표: Legacy 파일 제거

- ✅ NewSettingsActivity 완전 전환 확인
- ✅ Legacy Preference 클래스 15개 제거
- ✅ 예상 효과: 100-150KB 감소

### 2차 목표: ProGuard/R8 최적화

- ✅ 미사용 코드 제거 강화
- ✅ 최적화 옵션 추가
- ✅ 예상 효과: 50-100KB 추가 감소

### 3차 목표: 불필요한 Compat 클래스 검토

- ✅ 실제 사용되지 않는 클래스 확인
- ✅ 필요시 제거

---

## 📋 작업 체크리스트

### Phase 1: 현재 상태 확인 ✅

- [x] APK 용량 추이 분석
- [x] Legacy vs Compat 파일 매핑
- [x] 사용 중인 클래스 확인
- [x] AndroidManifest 확인

### Phase 2: Legacy 파일 안전성 검증

- [ ] SettingsActivity 사용 여부 확인
- [ ] Legacy Preference 참조 검색
- [ ] 테스트 항목 확인

### Phase 3: Legacy 파일 제거

- [ ] 15개 Legacy Preference 삭제
- [ ] SettingsActivity.java 처리 결정
- [ ] Import 문 정리

### Phase 4: ProGuard/R8 최적화

- [ ] proguard-rules.pro 강화
- [ ] R8 최적화 옵션 추가
- [ ] gradle.properties 설정

### Phase 5: 빌드 & 테스트

- [ ] Release APK 빌드
- [ ] 용량 확인
- [ ] 기능 테스트

### Phase 6: 문서화

- [ ] 완료 보고서 작성
- [ ] 용량 감소 측정
- [ ] Phase 6 전체 완료 표시

---

## 📁 Legacy 파일 제거 리스트

### A. 반드시 제거할 파일 (15개)

#### 1. SwitchPreference 계열 (4개)

```
❌ app/src/main/java/fr/neamar/kiss/preference/SwitchPreference.java
❌ app/src/main/java/fr/neamar/kiss/preference/FreezeHistorySwitch.java
❌ app/src/main/java/fr/neamar/kiss/preference/RootModeSwitch.java
❌ app/src/main/java/fr/neamar/kiss/preference/ShizukuModeSwitch.java

✅ 대체: *Compat.java (이미 생성됨)
```

#### 2. DialogPreference 계열 (11개)

```
❌ AddSearchProviderPreference.java
❌ ColorPreference.java
❌ DefaultLauncherPreference.java
❌ ExportSettingsPreference.java
❌ ImportSettingsPreference.java
❌ NotificationPreference.java
❌ ResetPreference.java
❌ ResetExcludedAppShortcutsPreference.java
❌ ResetExcludedAppsPreference.java
❌ ResetExcludedFromHistoryAppsPreference.java
❌ ResetFavoritesPreference.java
❌ ResetSearchProvidersPreference.java
❌ ResetShortcutsPreference.java
❌ RestartPreference.java

✅ 대체: *PreferenceCompat.java + *PreferenceDialogFragmentCompat.java
```

### B. 검토가 필요한 파일

#### 1. SettingsActivity.java (859줄)

```java
// 현재 상태: AndroidManifest에 fallback으로 유지 중
// 옵션:
// - Option 1: 완전 제거 (권장)
// - Option 2: Deprecated 마킹 후 유지
// - Option 3: 최소화 후 유지
```

**결정 기준**:

- NewSettingsActivity 안정성 확인
- 사용자 피드백 대기 기간 고려
- 롤백 가능성 평가

#### 2. ExcludePreferenceScreen.java

```java
// 현재 상태: ExcludePreferenceScreenCompat.java 생성됨
// 확인 필요: SettingsActivity.java에서 직접 참조 여부
```

#### 3. PreferenceScreenHelper.java

```java
// 현재 상태: Helper 클래스
// 확인 필요: NewSettingsActivity에서 사용 여부
```

---

## 🔍 사용 중인 클래스 확인 결과

### XML 파일 (preferences.xml)

```xml
✅ 모든 참조가 *Compat 클래스로 변경됨
   - fr.neamar.kiss.preference.SwitchPreferenceCompat
   - fr.neamar.kiss.preference.ColorPreferenceCompat
   - fr.neamar.kiss.preference.Reset*PreferenceCompat
   등등...

❌ Legacy 클래스 참조 없음
```

### Java 파일 참조

```java
// SettingsActivity.java (Line 51) - Legacy 사용 중
import fr.neamar.kiss.preference.SwitchPreference;

// RootModeSwitch.java (Line 7)
import android.preference.SwitchPreference;  // Android SDK (OK)

// ShizukuModeSwitch.java (Line 6)
import android.preference.SwitchPreference;  // Android SDK (OK)

// FreezeHistorySwitch.java (Line 7)
import android.preference.SwitchPreference;  // Android SDK (OK)
```

**결론**: SettingsActivity.java만 Legacy 클래스 사용 중

---

## 🛠️ ProGuard/R8 최적화 계획

### 1. proguard-rules.pro 강화

#### A. 현재 설정 확인

```bash
cat app/proguard-rules.pro
```

#### B. 추가할 규칙

```proguard
# ======================================================================
# Phase 6 Step 8: Preference 최적화
# ======================================================================

# 미사용 Preference 클래스 적극 제거
-assumenosideeffects class * extends androidx.preference.Preference {
    public void setEnabled(boolean);
    public void setSelectable(boolean);
}

# 리플렉션 최적화 (Preference XML에서 사용)
-keepclassmembers class * extends androidx.preference.Preference {
    public <init>(android.content.Context, android.util.AttributeSet);
}

# DialogFragment 최적화
-keep class * extends androidx.preference.PreferenceDialogFragmentCompat {
    public <init>();
}

# Legacy 클래스 완전 제거 (제거 후 추가)
-dontwarn fr.neamar.kiss.preference.AddSearchProviderPreference
-dontwarn fr.neamar.kiss.preference.ColorPreference
# ... (나머지 legacy 클래스들)
```

### 2. gradle.properties 최적화

```properties
# R8 Full Mode 활성화 (더 적극적인 최적화)
android.enableR8.fullMode=true

# Incremental Compilation 활성화
android.enableIncrementalDesugaring=true

# Resource Shrinking 강화
android.enableResourceOptimizations=true
```

### 3. build.gradle 확인

```gradle
buildTypes {
    release {
        minifyEnabled true
        shrinkResources = true  // ✅ 이미 활성화됨
        proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 
                      'proguard-rules.pro'
    }
}
```

---

## 📝 작업 순서 (Step-by-Step)

### Step 1: 브랜치 생성

```bash
cd /Users/1001028/git/KISS
git checkout dev
git pull origin dev
git checkout -b feature/phase6-step8-cleanup
```

### Step 2: Legacy 파일 사용 여부 최종 확인

```bash
# Legacy 클래스 참조 검색
grep -r "import fr.neamar.kiss.preference.SwitchPreference[^C]" app/src/main/java
grep -r "import fr.neamar.kiss.preference.ColorPreference[^C]" app/src/main/java
grep -r "import fr.neamar.kiss.preference.ResetPreference[^C]" app/src/main/java
# ... (모든 Legacy 클래스 확인)

# XML 파일 확인
grep "fr.neamar.kiss.preference." app/src/main/res/xml/preferences.xml | grep -v "Compat"
```

### Step 3: SettingsActivity.java 처리 결정

#### Option A: 완전 제거 (권장)

```bash
# 1. AndroidManifest.xml에서 제거
# 2. SettingsActivity.java 삭제
# 3. 관련 import 정리
```

#### Option B: Deprecated 마킹

```java
@Deprecated
public class SettingsActivity extends PreferenceActivity {
    // ... 유지
}
```

#### Option C: 최소화

```java
// NewSettingsActivity로 리다이렉트만 하는 껍데기로 축소
```

### Step 4: Legacy Preference 파일 삭제

```bash
cd /Users/1001028/git/KISS/app/src/main/java/fr/neamar/kiss/preference

# SwitchPreference 계열
rm SwitchPreference.java
rm FreezeHistorySwitch.java
rm RootModeSwitch.java
rm ShizukuModeSwitch.java

# DialogPreference 계열
rm AddSearchProviderPreference.java
rm ColorPreference.java
rm DefaultLauncherPreference.java
rm ExportSettingsPreference.java
rm ImportSettingsPreference.java
rm NotificationPreference.java
rm ResetPreference.java
rm ResetExcludedAppShortcutsPreference.java
rm ResetExcludedAppsPreference.java
rm ResetExcludedFromHistoryAppsPreference.java
rm ResetFavoritesPreference.java
rm ResetSearchProvidersPreference.java
rm ResetShortcutsPreference.java
rm RestartPreference.java
```

### Step 5: ProGuard 규칙 추가

```bash
# proguard-rules.pro 수정
nano app/proguard-rules.pro

# 규칙 추가 (위의 내용 참고)
```

### Step 6: gradle.properties 최적화

```bash
nano gradle.properties

# 추가
android.enableR8.fullMode=true
```

### Step 7: 빌드 & 용량 확인

```bash
# Release APK 빌드
./scripts/build_release_apk.sh

# 용량 확인
ls -lh app/build/outputs/apk/release/*.apk

# 기대 결과: 3.8MB → 3.5MB 이하
```

### Step 8: 기능 테스트

```bash
# 설치
./scripts/install_and_test.sh

# 수동 테스트:
# 1. 설정 화면 열기 (NewSettingsActivity)
# 2. 모든 Preference 동작 확인
# 3. Dialog 열기 테스트
# 4. Switch 토글 테스트
```

### Step 9: 커밋 & 머지

```bash
git add .
git commit -m "Phase 6 Step 8: Remove legacy Preference classes and optimize build

- Remove 15 legacy Preference classes
- Add ProGuard optimization rules
- Enable R8 full mode
- Expected APK size reduction: 200-300KB

Removed files:
- SwitchPreference.java (+ 3 subclasses)
- 11 DialogPreference classes
- [SettingsActivity.java - if decided]

Build size:
- Before: 3.8MB
- After: ~3.5MB
"

git push origin feature/phase6-step8-cleanup

# GitHub에서 PR 생성 후 dev에 머지
```

### Step 10: 문서 작성

```bash
# phase6-step8-completion-report.md 작성
# README.md 업데이트
# phase6-progress-tracker.md 업데이트
```

---

## 🧪 테스트 체크리스트

### 빌드 테스트

- [ ] Debug APK 빌드 성공
- [ ] Release APK 빌드 성공
- [ ] APK 용량 측정 (3.5MB 이하 목표)
- [ ] ProGuard 에러 없음

### 기능 테스트

- [ ] 설정 화면 열기
- [ ] SwitchPreference 토글
- [ ] ColorPreference 다이얼로그
- [ ] Reset 다이얼로그들
- [ ] Import/Export 기능
- [ ] ShizukuMode/RootMode 스위치

### 회귀 테스트

- [ ] 앱 실행
- [ ] 검색 기능
- [ ] 즐겨찾기
- [ ] 위젯
- [ ] 히스토리

---

## 📊 예상 결과

### APK 용량 감소

```
현재:     3.8MB (v4.2.4)
목표:     3.5MB 이하
감소량:   300KB+

구성:
- Legacy 제거:       -150KB
- ProGuard 최적화:   -100KB
- Resource 최적화:    -50KB
```

### 코드 정리

```
제거:
- 15개 Legacy Preference 클래스
- ~2,000줄 중복 코드
- [SettingsActivity.java - if decided] ~860줄

유지:
- 33개 Compat 클래스 (최신 AndroidX 기반)
- NewSettingsActivity + SettingsFragment
```

---

## ⚠️ 주의사항

### 1. SettingsActivity 제거 시점

- **권장**: v4.2.5에서 제거 (현재)
- **보수적**: v4.3.0까지 유지 후 제거
- **이유**: NewSettingsActivity 안정성 검증 기간

### 2. ProGuard 설정

- R8 Full Mode는 앱 전체에 영향
- 충분한 테스트 필요
- 문제 발생 시 즉시 롤백 가능

### 3. 롤백 계획

```bash
# 문제 발생 시
git checkout dev
git branch -D feature/phase6-step8-cleanup

# Legacy 파일은 Git 히스토리에 남아있음
git show HEAD~1:app/src/main/java/fr/neamar/kiss/preference/SwitchPreference.java
```

---

## 🎯 성공 기준

### 필수 달성

- ✅ Legacy 파일 15개 제거
- ✅ APK 빌드 성공
- ✅ 모든 설정 기능 정상 동작

### 추가 달성

- ✅ APK 300KB 이상 감소
- ✅ ProGuard 최적화 적용
- ✅ SettingsActivity 제거 결정

### Phase 6 전체 완료 조건

- ✅ Step 1-7 완료 (이미 완료)
- ✅ Step 8 완료 (진행 중)
- ✅ 모든 Preference AndroidX 전환
- ✅ NewSettingsActivity 안정화
- ✅ 문서 완성

---

## 📚 참고 문서

- `phase6-step-by-step-guide.md` - 전체 계획
- `phase6-step7-implementation-plan.md` - Step 7 완료 보고
- `newsettings-day3-completion-report.md` - NewSettingsActivity 완료
- `warning-removal-phases1-5-completion-report.md` - Phase 1-5 완료

---

**준비 완료!** Step 8 시작하세요! 🚀
