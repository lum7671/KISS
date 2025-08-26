# AndroidX Preference 마이그레이션 성공 전략 요약 (AI/팀 협업용)

## 1. AI 마이그레이션 실패 원인

- **의존성 복잡성**: 설정 간 상호작용이 많아 AI가 전체 맥락을 놓치기 쉽다.
- **비즈니스 로직 손실**: 단순 변환 시 커스텀 동작/로직이 누락될 수 있다.
- **레거시 코드 패턴**: 오래된 커스텀 구현이 표준 패턴과 달라 자동 변환이 어렵다.

## 2. 성공 전략

### (1) 점진적 청킹(Chunking)

- 한 번에 전체를 변환하지 말고, **100~200줄 단위** 또는 **설정 그룹별**로 쪼개서 진행
- 의존성 적은 부분부터, 단일 책임 원칙에 따라 순차적으로 변환

### (2) 구체적이고 맥락 중심의 AI 프롬프트

- "모두 변환" 대신, **구체적 코드 블록**과 **변환 목적**을 명확히 제시
- 예: "이 PreferenceFragment를 PreferenceFragmentCompat으로 변환해줘. 클래스 선언과 필수 메소드 오버라이드에만 집중하고, 기존 비즈니스 로직은 그대로 보존해줘: [코드]"

### (3) 인간-AI 협업 워크플로우

- **분석 → AI 변환 → 수동 검증/테스트 → 점진적 통합**의 4단계 반복
- 작은 단위로 커밋, 문제 발생 시 롤백 용이

### (4) 반복 검증과 통합

- 각 단계마다 코드 리뷰, 단위/수동 테스트, 성능 영향 검토
- 사용자 데이터(SharedPreferences) 호환성, 접근성, 성능도 반드시 확인

## 3. 실전 체크리스트

- [ ] 마이그레이션 전 기존 동작 테스트 코드 작성
- [ ] 각 Preference/Fragment를 독립적으로 변환 및 테스트
- [ ] 커스텀 Preference는 AndroidX 패턴에 맞게 직접 구현
- [ ] 기존 SharedPreferences 데이터 호환성 보장
- [ ] 성능/접근성/UX 변화 검증
- [ ] 작은 단위로 자주 커밋, 문제 발생 시 빠른 롤백

## 4. AI 프롬프트 예시

- **Import 변환**:  
    `"이 import 문들을 분석하고 android.preference에서 androidx.preference로 변경해야 하는 것들만 식별해서 androidx 대응 항목과 함께 나열해줘: [import 목록]"`
- **Fragment 변환**:  
    `"이 PreferenceFragment를 PreferenceFragmentCompat으로 변환해줘. 클래스 선언과 필수 메소드 오버라이드에만 집중하고 기존 비즈니스 로직은 보존해줘: [Fragment 코드]"`

## 5. 복잡한 설정 구조 대응

- Nested PreferenceScreen은 화면별로 분리 변환
- 동적 생성 Preference는 로직 분석 → 변환 → 검증의 3단계로 분리
- 의존성 매핑 후, 의존성 적은 것부터 순차 변환

---
이 전략과 체크리스트는 마이그레이션 기간 동안 팀/AI 모두의 나침판입니다. 각 단계마다 이 문서를 참고하며, 필요시 바로 업데이트/보완하세요!

## AndroidX Migration Guide for KISS Launcher

## 개요

KISS 런처를 deprecated `android.preference` 패키지에서 AndroidX `androidx.preference` 패키지로 마이그레이션하는 전략적 가이드입니다.

## 현재 상황 분석

### 성공적으로 완료된 작업

- ✅ Java 17 LTS 업그레이드
- ✅ Firebase Crashlytics 통합
- ✅ 패키지명 변경 (`kr.lum7671.kiss`)
- ✅ `ResetPreference` 부분 마이그레이션 (AndroidX Preference 상속)
- ✅ `FreezeHistorySwitch` 부분 마이그레이션 (AndroidX SwitchPreference 상속)

### 발견된 문제점

1. **Type Incompatibility**: AndroidX와 deprecated preference 클래스 간 타입 호환성 문제
2. **Method Removal**: `onBindView()` 등 일부 메서드가 AndroidX에서 제거됨
3. **XML Namespace**: XML에서 preference 클래스 참조 방식 차이
4. **Legacy Code Dependencies**: 여러 클래스가 deprecated preference에 의존

### 영향받는 파일들

#### Preference 클래스 (난이도 순 정렬)

1. `/app/src/main/java/fr/neamar/kiss/preference/ResetPreference.java` ⚠️ 부분 완료 (쉬움)
2. `/app/src/main/java/fr/neamar/kiss/preference/FreezeHistorySwitch.java` ⚠️ 부분 완료 (쉬움)
3. `/app/src/main/java/fr/neamar/kiss/preference/SwitchPreference.java` ❌ 미완료 (쉬움)
4. `/app/src/main/java/fr/neamar/kiss/preference/ColorPreference.java` ❌ 미완료 (중간)
5. `/app/src/main/java/fr/neamar/kiss/preference/AddSearchProviderPreference.java` ❌ 미완료 (중간)
6. `/app/src/main/java/fr/neamar/kiss/preference/ResetExcludedAppShortcutsPreference.java` ❌ 미완료 (어려움)
7. `/app/src/main/java/fr/neamar/kiss/preference/ExcludePreferenceScreen.java` ❌ 미완료 (어려움)

#### Activity 클래스

- `/app/src/main/java/fr/neamar/kiss/SettingsActivity.java` ✅ 완료
    (PreferenceActivity → AppCompatActivity + Fragment, 구조만 적용)

#### XML 리소스

- `/app/src/main/res/xml/preferences.xml` ⚠️ 부분 수정 (임시 주석 처리, 메뉴별 점진적 추가 권장)

## 마이그레이션 전략

### Phase 1: 핵심 앱 기능 안정화 (현재 우선순위)

**목표**: Firebase Crashlytics 테스트가 가능한 최소 기능 앱 구성

#### 1.1 임시 해결책 구현

- [x] 문제되는 preference 클래스들을 XML에서 일시적으로 표준 AndroidX preference로 교체
- [ ] 빌드 성공 확인
- [ ] 기본 런처 기능 동작 확인
- [ ] Firebase Crashlytics "crashtest" 기능 검증

#### 1.2 갤럭시 노트20 울트라 테스트

- [ ] APK 설치 및 기본 동작 확인
- [ ] 실제 크래시 발생 시 Firebase 보고 테스트

### Phase 2: PreferenceActivity → PreferenceFragmentCompat 마이그레이션

**목표**: 현대적인 preference 아키텍처로 전환

#### 2.1 SettingsActivity 마이그레이션

- [ ] `PreferenceActivity` → `AppCompatActivity` + `PreferenceFragmentCompat` 변경
- [ ] Fragment 기반 preference 관리 구조 구현
- [ ] Navigation 및 lifecycle 관리 업데이트

#### 2.2 XML 구조 업데이트

- [ ] `preferences.xml` AndroidX 네임스페이스로 완전 전환
- [ ] Preference 키 및 속성 호환성 확인

### Phase 3: 커스텀 Preference 클래스 마이그레이션 (난이도 순 점진적 진행)

**목표**: 모든 커스텀 preference 클래스를 AndroidX로 완전 마이그레이션

#### 3.1 난이도 기반 단계별 마이그레이션

1. ResetPreference (쉬움, 부분 완료) → 점진적 완성 및 테스트
2. FreezeHistorySwitch (쉬움, 부분 완료) → 점진적 완성 및 테스트
3. SwitchPreference (쉬움, 미완료) → AndroidX로 교체, summary 등 최소 기능 구현
4. ColorPreference (중간) → AndroidX + 커스텀 다이얼로그 적용
5. AddSearchProviderPreference (중간) → AndroidX + 커스텀 입력 UI 적용
6. ResetExcludedAppShortcutsPreference (어려움) → AndroidX + 복잡 로직 마이그레이션
7. ExcludePreferenceScreen (어려움) → AndroidX + PreferenceScreen 구조화

각 단계별로 **하나씩만 추가/마이그레이션 후 빌드 및 동작 확인**을 권장합니다.

#### 3.2 구현 순서 예시

1. preferences.xml에 ResetPreference만 추가 → 빌드/동작 확인
2. FreezeHistorySwitch 추가 → 빌드/동작 확인
3. SwitchPreference 추가 → 빌드/동작 확인
4. 이후 메뉴도 동일 방식 반복

이 가이드에 따라 실제 구현도 가장 쉬운 항목부터 순차적으로 진행합니다.

#### 3.2 각 클래스별 마이그레이션 계획

##### SwitchPreference 마이그레이션

```java
// Before: extends android.preference.SwitchPreference
// After: extends androidx.preference.SwitchPreference
// 특별 요구사항: onBindView() 대체 방안 필요 (summary 최대 줄 수 제한)
```

##### ResetPreference 마이그레이션

```java
// Before: extends android.preference.DialogPreference  
// After: extends androidx.preference.Preference + AlertDialog 직접 구현
// 상태: 부분 완료, XML 속성 정리 필요
```

##### ColorPreference 마이그레이션

```java
// Before: extends android.preference.DialogPreference
// After: extends androidx.preference.Preference + 커스텀 다이얼로그
// 복잡도: High (색상 선택 UI 포함)
```

### Phase 4: 테스트 및 검증

**목표**: 마이그레이션 완료 후 전체 기능 검증

#### 4.1 기능 테스트

- [ ] 모든 preference 설정 동작 확인
- [ ] 설정 저장/복원 테스트
- [ ] UI/UX 일관성 검증

#### 4.2 성능 및 안정성 테스트

- [ ] 메모리 사용량 확인
- [ ] 앱 시작 시간 측정
- [ ] 크래시 테스트 (Firebase Crashlytics)

## 구현 세부사항

### AndroidX Preference 핵심 개념

#### 1. PreferenceFragmentCompat 사용

```java
public class SettingsFragment extends PreferenceFragmentCompat {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);
    }
}
```

#### 2. 커스텀 Preference 구현 패턴

```java
public class CustomPreference extends androidx.preference.Preference {
    public CustomPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }
    
    @Override
    protected void onClick() {
        // 커스텀 동작 구현
    }
}
```

#### 3. 다이얼로그 기반 Preference 대체

```java
// DialogPreference 대신 Preference + AlertDialog 사용
@Override
protected void onClick() {
    new AlertDialog.Builder(getContext())
        .setTitle("Title")
        .setMessage("Message")
        .setPositiveButton("OK", (dialog, which) -> {
            // 확인 동작
        })
        .setNegativeButton("Cancel", null)
        .show();
}
```

## 위험 요소 및 대응 방안

### 1. 호환성 문제

- **위험**: 기존 설정 값 손실
- **대응**: SharedPreferences 키 매핑 테이블 작성

### 2. UI 변경

- **위험**: 사용자 경험 저하
- **대응**: UI 일관성 유지, 점진적 변경

### 3. 성능 영향

- **위험**: Fragment 기반 구조로 인한 성능 저하
- **대응**: 지연 로딩, 최적화 구현

## 체크리스트

### Phase 1 완료 조건

- [ ] 앱이 크래시 없이 시작됨
- [ ] 기본 런처 기능 동작 (앱 검색, 실행)
- [ ] Firebase Crashlytics "crashtest" 동작
- [ ] 갤럭시 노트20 울트라에서 안정 동작

### Phase 2 완료 조건

- [ ] SettingsActivity Fragment 기반으로 전환
- [ ] 모든 기본 설정 항목 접근 가능
- [ ] 설정 변경 시 즉시 반영

### Phase 3 완료 조건

- [ ] 모든 커스텀 preference AndroidX 마이그레이션
- [ ] 기존 기능 100% 동등성 보장
- [ ] deprecated API 사용 0개

### Phase 4 완료 조건

- [ ] 전체 기능 테스트 통과
- [ ] 성능 기준선 대비 동등 이상
- [ ] 사용자 설정 데이터 무결성 보장

## 다음 단계

1. **즉시**: Phase 1.1 임시 해결책으로 빌드 성공시키기
2. **단기 (1-2일)**: Phase 1 완료 및 실기기 테스트
3. **중기 (1주)**: Phase 2 PreferenceFragment 마이그레이션
4. **장기 (2-3주)**: Phase 3-4 전체 마이그레이션 완료

---
*최종 업데이트: 2025년 8월 25일*
*작성자: GitHub Copilot & lum7671*
