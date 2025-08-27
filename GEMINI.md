# Gemini 분석용 프로젝트 요약: KISS Launcher (개인 포크)

## 1. 프로젝트 개요

- **이름**: KISS Launcher (Keep It Simple, Stupid)
- **종류**: 안드로이드 런처 애플리케이션
- **핵심 컨셉**: UI 요소를 최소화하고, 텍스트 검색을 통해 앱, 연락처, 설정 등에 빠르게 접근하는 것을 목표로 하는 미니멀리즘 런처.
- **특징**: `neamar/KISS` 원본 프로젝트의 개인 포크 버전 (`kr.lum7671.kiss`)으로, 원본에 더해 현대화, 성능 최적화, 신규 기능 추가 등 적극적인 개발이 이루어지고 있음.

## 2. 아키텍처 및 기술 스택

- **구조**: 표준 Gradle 기반의 멀티-모듈 안드로이드 프로젝트
  - `:app`: 메인 런처 애플리케이션 모듈
  - `:ai-tag-library`: AI 기반 태그 추천 기능을 위한 라이브러리 모듈
- **언어**: Java와 Kotlin 혼용 (Kotlin으로 점진적 마이그레이션 진행 중)
- **핵심 기술**:
  - **Android**: minSdkVersion 33 (Android 13) 이상 타겟
  - **비동기 처리**: `AsyncTask`를 `Kotlin Coroutines`로 **완전 전환 완료**.
  - **UI**: Android 표준 UI 컴포넌트 및 커스텀 뷰 사용.
  - **데이터베이스**: SQLite를 사용하여 히스토리, 설정 등 관리.
  - **빌드 시스템**: 최신 Android Gradle Plugin 및 Gradle 사용.

## 3. 주요 기능 및 개발 동향

### 3.1. 성능 최적화 (매우 중요)
- **아이콘 캐싱**: 3단계(frequent, recent, memory) LruCache와 Glide를 결합한 고성능 아이콘 캐싱 시스템을 구현하여 아이콘 로딩 속도를 최적화.
- **UI 렌더링**: 화면 상태(UIState) 추적 시스템을 도입하여 불필요한 UI 새로고침을 방지하고 사용자 작업 흐름이 중단되지 않도록 개선.
- **데이터 로딩**: Coroutines 기반의 비동기 데이터 로딩으로 UI 응답성 향상.
- **코드 정리**: `minSdkVersion` 상승에 맞춰 불필요한 하위 호환성 코드를 제거하고, Lint 분석을 통해 코드 품질을 지속적으로 관리.

### 3.2. 신규 기능 개발
- **Shizuku 통합**: 루트(Root) 권한 없이 시스템 API를 사용하여 앱을 '절전(hibernate)' 상태로 만드는 기능을 구현.
- **AI 태그 추천**: `ai-tag-library` 모듈에서 볼 수 있듯, Google Gemini API를 활용하여 Play Store의 앱 메타데이터를 분석하고, 앱에 적합한 태그를 자동으로 추천/생성하는 기능을 개발 중.

### 3.3. 코드 현대화
- **Java 17 & Kotlin 2.0**: 최신 LTS 버전의 Java와 안정화된 Kotlin 버전을 사용하여 프로젝트 전체를 현대화.
- **AndroidX 마이그레이션**: `android.preference` 등 오래된 라이브러리를 최신 AndroidX 라이브러리로 전환하는 작업을 진행.

## 4. 문서화

- `docs/` 디렉토리에 사용자 도움말 웹사이트(Jekyll 기반)와 함께, 개발 과정에서 작성된 방대한 양의 내부 분석/설계 문서가 존재함.
- **주요 문서**:
  - `asynctask-to-coroutines-migration.md`: AsyncTask 마이그레이션 전략.
  - `screen-refresh-optimization-analysis.md`: UI 새로고침 문제 분석 및 해결 전략.
  - `icon-refresh-optimization-analysis.md`: 아이콘 캐싱 및 로딩 최적화 분석.
  - `shizuku-guide.md`: Shizuku 연동 가이드.
  - `tag-feature-enhancement.md`: AI 태그 기능 고도화 제안.
  - `source-analysis/`: 주요 소스 코드 구조 분석.

## 5. 상호작용 가이드

- 이 프로젝트는 **체계적인 문서화**와 **단계적 개발**을 매우 중시함.
- 새로운 기능을 추가하거나 리팩토링을 할 때는, 기존의 분석 문서 (`docs/*.md`)를 먼저 참고하여 설계 의도와 기존 구조를 파악하는 것이 중요함.
- 코드 변경 시, 성능(메모리, CPU, 응답성)에 미치는 영향을 항상 고려해야 함.
- 이슈 해결 시, 관련 로그와 분석 데이터를 기반으로 접근하는 것이 바람직함.
