# KISS 코드 정리 계획 (2025-10-17)

## 개요

`run_all_analysis.sh` 실행 결과를 바탕으로 단순하고 명확한 것부터 수정하는 코드 정리 계획입니다.

**분석 실행 날짜**: 2025년 10월 17일  
**분석 도구**: Detekt, Android Lint, Error Prone, Dependency Updates

---

## 📊 분석 결과 요약

### Lint 결과

- **필터링된 이슈**: 8 errors, 234 warnings, 39 hints (baseline 파일에 의해 필터링)
- **해결된 이슈**: 33개의 baseline 이슈가 이미 해결됨 (DiscouragedApi, GradleDependency, ObsoleteSdkInt 등)
- **현재 Warning**: Kotlin stdlib 버전 업데이트 필요 (2.0.21 → 2.2.0)

### Detekt 결과 (주요 이슈)

- **Complexity 이슈**: 6건
  - CyclomaticComplexMethod (복잡도 15)
  - NestedBlockDepth (중첩 깊이 4~5)
  - TooManyFunctions (15/11)
  - LongParameterList (6~9개 파라미터)
  
- **Exception Handling**: 18건
  - TooGenericExceptionCaught (일반 Exception 캐치)
  - SwallowedException (예외 삼킴)

- **Formatting 이슈**: 81건
  - NoTrailingSpaces (줄 끝 공백)
  - NoBlankLineBeforeRbrace (닫는 괄호 전 빈 줄)

---

## 🎯 우선순위별 수정 계획

## Phase 1: 포매팅 정리 (가장 단순)

### 1.1 줄 끝 공백 제거 (81건)

**난이도**: ⭐ (매우 쉬움)  
**영향도**: 낮음  
**예상 소요**: 10분

**대상 파일**:

- `searcher/` 패키지 전체 (주요 대상)
  - NullSearcherCoroutine.kt
  - ApplicationsSearcherCoroutine.kt
  - HistorySearcherCoroutine.kt
  - TagsSearcherCoroutine.kt
  - QuerySearcherCoroutine.kt
  - UntaggedSearcherCoroutine.kt
  - SearcherCoroutine.kt
  - ISearchResultReceiver.kt

**작업 방법**:

```bash
# 자동화된 방법
find app/src/main/java/fr/neamar/kiss/searcher -name "*.kt" -exec sed -i '' 's/[[:space:]]*$//' {} \;
```

**검증**:

```bash
./gradlew detekt
```

### 1.2 닫는 괄호 전 빈 줄 제거 (2건)

**난이도**: ⭐ (매우 쉬움)  
**영향도**: 낮음  
**예상 소요**: 5분

**대상**:

- `SearcherCoroutine.kt:137` - catch 블록 내
- `SearcherCoroutine.kt:143` - catch 블록 내

---

## Phase 2: Lint Baseline 업데이트 (단순)

### 2.1 해결된 이슈 제거

**난이도**: ⭐⭐ (쉬움)  
**영향도**: 낮음  
**예상 소요**: 15분

**작업 내용**:

- `app/lint-baseline.xml`에서 더 이상 존재하지 않는 33개 이슈 제거
- Baseline 파일 정리 후 재생성

**작업 방법**:

```bash
# 기존 baseline 백업
cp app/lint-baseline.xml app/lint-baseline.xml.bak

# Baseline 업데이트
./gradlew lintDebug -PupdateLintBaseline=true
```

**제거 대상 이슈 타입**:

- DiscouragedApi
- GradleDependency (이미 해결됨)
- ObsoleteSdkInt (28건)
- UnusedResources (3건)

---

## Phase 3: 의존성 업데이트 (중간 난이도)

### 3.1 안전한 패치 버전 업데이트

**난이도**: ⭐⭐ (쉬움)  
**영향도**: 낮음  
**예상 소요**: 30분 (테스트 포함)

**즉시 업데이트 가능**:

```gradle
// 현재 → 업데이트
androidx.collection:collection [1.4.5 → 1.4.6] // 안정 버전만
com.squareup.leakcanary:leakcanary-android [2.14 → 2.15] // 패치 버전
io.gitlab.arturbosch.detekt:detekt-formatting [1.23.7 → 1.23.8] // 패치 버전
org.jetbrains:annotations [24.1.0 → 25.0.0] // 메이저 업그레이드이지만 안전
```

**Kotlin stdlib 업데이트** (Lint Warning 해결):

```gradle
// app/build.gradle line 123
implementation "org.jetbrains.kotlin:kotlin-stdlib:2.0.21"
// → 2.2.0 (주의: Kotlin 플러그인 버전도 함께 업데이트 필요)
```

### 3.2 신중한 업데이트 (별도 검토 필요)

**난이도**: ⭐⭐⭐ (중간)  
**영향도**: 중간~높음  
**예상 소요**: 2시간 (테스트 및 검증)

**검토 필요**:

- **androidx.lifecycle**: 2.8.5 → 2.10.0-alpha05 (alpha 버전 - 보류)
- **Material Design**: 1.12.0 → 1.14.0-alpha05 (alpha 버전 - 보류)
- **OkHttp**: 4.12.0 → 5.2.1 (메이저 버전 업그레이드 - 별도 작업)
- **Amplitude SDK**: 2.40.3 → 3.35.1 (메이저 버전 업그레이드 - 별도 작업)
- **JUnit Jupiter**: 5.11.4 → 6.0.0 (메이저 버전 업그레이드 - 별도 작업)

**Gradle 업데이트**: 8.13 → 9.2.0-rc-1 (RC 버전 - 안정 버전 대기)

### 3.3 업데이트 제외 (현재 버전 유지)

- alpha/beta 버전들은 안정 버전 출시 대기
- 메이저 버전 업그레이드는 별도 이슈로 관리

---

## Phase 4: 사용하지 않는 코드 정리 (중간 난이도)

### 4.1 사용하지 않는 변수/함수 찾기

**난이도**: ⭐⭐⭐ (중간)  
**영향도**: 중간  
**예상 소요**: 1시간

**작업 방법**:

```bash
# Android Studio Inspection 실행
# Analyze > Inspect Code > Whole Project
# "Unused declaration" 필터로 결과 확인
```

**예상 대상**:

- Dead code (도달 불가능한 코드)
- Unused private methods
- Unused parameters
- Unused imports

### 4.2 사용하지 않는 리소스 정리

**난이도**: ⭐⭐ (쉬움)  
**영향도**: 낮음 (APK 크기 감소)  
**예상 소요**: 30분

**작업 방법**:

```bash
# Lint를 통한 미사용 리소스 검출
./gradlew lintDebug
# app/build/reports/lint-results-debug.html에서 UnusedResources 확인
```

---

## Phase 5: 코드 복잡도 개선 (높은 난이도)

### 5.1 긴 파라미터 리스트 리팩토링 (LongParameterList)

**난이도**: ⭐⭐⭐⭐ (어려움)  
**영향도**: 중간  
**예상 소요**: 4시간

**대상**:

1. `LoadAppPojosCoroutine.kt:223` - createPojo (9개 파라미터)
2. `LoadAppPojosCoroutine.kt:89` - loadAppsForProfile (7개 파라미터)
3. `LoadShortcutsPojosCoroutine.kt:119` - createPojo (6개 파라미터)

**개선 방안**:

```kotlin
// Before: 9개 파라미터
fun createPojo(
    userHandle: UserHandle,
    packageName: String,
    activityName: String,
    label: CharSequence,
    disabled: Boolean,
    suspended: Boolean,
    excludedAppList: Set<String>,
    excludedFromHistoryAppList: Set<String>,
    excludedShortcutsAppList: Set<String>
): AppPojo

// After: Parameter Object 패턴
data class AppPojoParams(
    val userHandle: UserHandle,
    val packageName: String,
    val activityName: String,
    val label: CharSequence,
    val disabled: Boolean = false,
    val suspended: Boolean = false,
    val excludedAppList: Set<String> = emptySet(),
    val excludedFromHistoryAppList: Set<String> = emptySet(),
    val excludedShortcutsAppList: Set<String> = emptySet()
)

fun createPojo(params: AppPojoParams): AppPojo
```

### 5.2 복잡한 메서드 분해 (CyclomaticComplexMethod)

**난이도**: ⭐⭐⭐⭐ (어려움)  
**영향도**: 높음 (가독성, 유지보수성)  
**예상 소요**: 6시간

**대상**:

- `HistorySearcherCoroutine.kt:77` - doInBackground (복잡도 15, 중첩 깊이 5)

**개선 방안**:

- Extract Method 리팩토링
- Guard Clause 패턴으로 중첩 줄이기
- 조건문을 별도 함수로 분리

### 5.3 중첩 깊이 감소 (NestedBlockDepth)

**난이도**: ⭐⭐⭐⭐ (어려움)  
**영향도**: 중간~높음  
**예상 소요**: 4시간

**대상**:

1. `HistorySearcherCoroutine.kt:77` - doInBackground (중첩 5)
2. `SetImageCoroutine.kt:128` - applyDrawable (중첩 4)
3. `LoadShortcutsPojosCoroutine.kt:43` - fetchOreoPojos (중첩 5)
4. `LoadContactsPojosCoroutine.kt:49` - loadPhoneContacts (중첩 4)

**개선 전략**:

- Early return 패턴
- 중첩된 조건문을 Boolean 변수로 추출
- 람다 함수 분리

---

## Phase 6: Exception Handling 개선 (중간~높은 난이도)

### 6.1 일반 Exception 캐치 개선 (TooGenericExceptionCaught)

**난이도**: ⭐⭐⭐ (중간)  
**영향도**: 중간 (에러 처리 품질)  
**예상 소요**: 3시간

**대상**: 18개 파일 (CoroutineUtils, Loaders, Searchers)

**개선 방안**:

```kotlin
// Before
try {
    // some operation
} catch (e: Exception) {
    Log.e(TAG, "Error", e)
}

// After
try {
    // some operation
} catch (e: IOException) {
    Log.e(TAG, "IO Error", e)
} catch (e: SecurityException) {
    Log.e(TAG, "Permission Error", e)
} catch (e: IllegalStateException) {
    Log.e(TAG, "Invalid State", e)
}
```

### 6.2 삼켜진 예외 처리 (SwallowedException)

**난이도**: ⭐⭐ (쉬움)  
**영향도**: 중간  
**예상 소요**: 30분

**대상**:

- `SearcherCoroutine.kt:138` - CancellationException 처리

**개선 방안**:

```kotlin
// Before
catch (e: CancellationException) {
    // 아무것도 안 함 (삼킴)
}

// After
catch (e: CancellationException) {
    Log.d(TAG, "Search cancelled", e)
    throw e // 또는 적절한 처리
}
```

---

## 📋 실행 순서 및 체크리스트

### Week 1: 빠른 승리 (Quick Wins)

- [ ] **Day 1**: Phase 1 - 포매팅 정리 (15분)
- [ ] **Day 1**: Phase 2 - Lint Baseline 업데이트 (15분)
- [ ] **Day 2**: Phase 3.1 - 안전한 패치 버전 업데이트 (30분)
- [ ] **Day 2**: Phase 4.2 - 미사용 리소스 정리 (30분)
- [ ] **Day 3**: Phase 6.2 - 삼켜진 예외 처리 (30분)
- [ ] **Day 3**: Phase 4.1 - 미사용 코드 정리 (1시간)

**Week 1 예상 소요**: 3시간 30분  
**Week 1 예상 효과**: 코드베이스 정리, baseline 정리, 버전 업데이트

### Week 2: 중간 난이도

- [ ] **Day 4-5**: Phase 6.1 - Exception Handling 개선 (3시간)
- [ ] **Day 6-7**: Phase 5.1 - 긴 파라미터 리스트 리팩토링 (4시간)

**Week 2 예상 소요**: 7시간  
**Week 2 예상 효과**: 코드 품질 개선, 가독성 향상

### Week 3: 높은 난이도 (별도 계획)

- [ ] Phase 5.2 - 복잡한 메서드 분해 (6시간)
- [ ] Phase 5.3 - 중첩 깊이 감소 (4시간)
- [ ] Phase 3.2 - 메이저 버전 업데이트 검토 (별도 이슈)

**Week 3 예상 소요**: 10시간+  
**Week 3 예상 효과**: 코드 복잡도 감소, 유지보수성 향상

---

## 🛠 작업 전 준비사항

1. **브랜치 생성**:

```bash
git checkout -b cleanup/code-quality-improvements
```

1. **Baseline 백업**:

```bash
cp app/lint-baseline.xml app/lint-baseline.xml.backup
```

1. **빌드 성공 확인**:

```bash
./gradlew clean build
./gradlew test
```

1. **분석 도구 실행**:

```bash
./scripts/run_all_analysis.sh
```

---

## 📈 예상 효과

### 정량적 효과

- **Detekt 이슈 감소**: 391건 → 약 250건 (36% 감소 목표)
- **Lint Warning 감소**: 234건 → 약 200건 (15% 감소 목표)
- **코드 포매팅 이슈**: 83건 → 0건 (100% 해결)
- **APK 크기**: 미사용 리소스 제거로 약 100KB 감소 예상

### 정성적 효과

- ✅ 코드 가독성 향상
- ✅ 유지보수성 개선
- ✅ 신규 개발자 온보딩 용이성 증가
- ✅ 버그 발생 가능성 감소
- ✅ CI/CD 파이프라인 안정성 향상

---

## ⚠️ 주의사항

1. **각 Phase는 독립적으로 테스트**
   - 각 단계마다 빌드 및 테스트 실행
   - 실패 시 이전 커밋으로 롤백 가능

2. **Baseline 파일 관리**
   - Baseline 변경 시 별도 커밋
   - 새로운 이슈가 추가되지 않았는지 확인

3. **의존성 업데이트**
   - 패치 버전만 먼저 업데이트
   - 메이저 버전은 별도 이슈로 관리
   - 각 업데이트마다 회귀 테스트 수행

4. **리팩토링 시 동작 변경 금지**
   - 코드 구조만 변경, 동작은 동일하게 유지
   - 테스트 커버리지 확인
   - 수동 테스트 체크리스트 작성

---

## 📚 참고 문서

- [Android Lint 가이드](https://developer.android.com/studio/write/lint)
- [Detekt 규칙 문서](https://detekt.dev/docs/rules/complexity)
- [Kotlin 코딩 컨벤션](https://kotlinlang.org/docs/coding-conventions.html)
- [Refactoring 패턴](https://refactoring.guru/refactoring/techniques)

---

## 🔄 다음 단계

1. ✅ 이 문서 리뷰 및 우선순위 조정
2. ⏳ Phase 1 작업 시작
3. ⏳ 각 Phase별 진행 상황 업데이트
4. ⏳ Week 1 완료 후 회고 및 계획 조정

---

**작성자**: GitHub Copilot  
**작성일**: 2025-10-17  
**다음 업데이트**: Week 1 완료 후
