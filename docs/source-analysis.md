# KISS 프로젝트 소스 코드 분석

## 소스 코드 구조 

전체 소스 파일 수: 182개 (Java: 178개, Kotlin: 4개)

### 주요 패키지 구조

1. **fr.neamar.kiss** (코어)
   - 앱의 핵심 컴포넌트
   - 메인 액티비티, 설정, 데이터 핸들링

2. **fr.neamar.kiss.dataprovider**
   - 데이터 제공자 클래스들
   - 앱, 연락처, 바로가기 등의 데이터 관리

3. **fr.neamar.kiss.db**
   - 데이터베이스 관련 클래스
   - 히스토리, 앱 레코드, 바로가기 저장

4. **fr.neamar.kiss.ui**
   - 사용자 인터페이스 컴포넌트
   - 커스텀 뷰, 위젯 관련 클래스

5. **fr.neamar.kiss.utils**
   - 유틸리티 클래스들
   - 코루틴, 아이콘, 권한 등 처리

6. **fr.neamar.kiss.searcher**
   - 검색 관련 클래스들
   - 다양한 검색 알고리즘 구현

7. **fr.neamar.kiss.result**
   - 검색 결과 처리
   - 결과 표시 및 처리 로직

## 주요 패키지 및 클래스 상세

### 1. 비동기 처리 (`fr.neamar.kiss.utils`)

#### CoroutineUtils.kt

코루틴 기반 비동기 처리 유틸리티

- [ ] `runAsync()` - 기본 비동기 실행
- [ ] `runAsyncWithLifecycle()` - 라이프사이클 관리 비동기 실행
- [ ] `runAsyncWithResult()` - 결과 반환 비동기 실행
- [x] `execute()` - (제거 예정) 레거시 실행 메서드
- [x] `runAsyncWithWeakReference()` - (제거 예정) WeakReference 기반 비동기 실행

### 2. 데이터 로딩 (`fr.neamar.kiss.loader`)

#### LoadPojosCoroutine

기본 데이터 로딩 추상 클래스

- [ ] `doInBackground()` - 백그라운드 데이터 로딩
- [ ] `executeAsync()` - 비동기 실행 처리
- [x] `setProvider()` - (제거 예정) 레거시 프로바이더 설정

#### LoadAppPojosCoroutine

앱 데이터 로딩 구현

- [ ] `loadAppsForAllProfiles()` - 모든 프로필의 앱 로딩
- [ ] `loadAppsForProfile()` - 특정 프로필의 앱 로딩
- [x] `loadAppsLegacy()` - (제거 예정) 레거시 앱 로딩 방식

### 3. 바로가기 관리 (`fr.neamar.kiss.shortcut`)

#### SaveAllOreoShortcuts

Oreo 이상 버전의 바로가기 일괄 처리

- [ ] `execute()` - 바로가기 저장 실행
- [ ] `doInBackground()` - 백그라운드 저장 처리
- [x] `showSecurityErrorToast()` - (통합 예정) 보안 에러 표시
- [x] `showGeneralErrorToast()` - (통합 예정) 일반 에러 표시

#### SaveSingleOreoShortcut

단일 바로가기 처리

- [ ] `execute()` - 단일 바로가기 저장
- [ ] `doInBackground()` - 백그라운드 저장 처리
- [x] `showErrorToast()` - (통합 예정) 에러 표시

## 리팩토링 우선순위

### 1. 제거 대상 함수 (사용되지 않음)

- `execute()` (CoroutineUtils)
- `runAsyncWithWeakReference()` (CoroutineUtils)
- `loadAppsLegacy()` (LoadAppPojosCoroutine)
- `setProvider()` (LoadPojosCoroutine)

### 2. 통합 대상 기능

1. 에러 처리 통합
   - `showSecurityErrorToast()`
   - `showGeneralErrorToast()`
   - `showErrorToast()`
   → 통합된 에러 처리 시스템으로 마이그레이션

2. 바로가기 관리 통합
   - `SaveAllOreoShortcuts`
   - `SaveSingleOreoShortcut`
   → 단일 바로가기 관리 클래스로 통합

### 3. 현대화 필요 컴포넌트

1. Java에서 Kotlin으로의 마이그레이션
   - [ ] DataProvider 패키지 (9개 파일)
   - [ ] Result 패키지 (10개 파일)
   - [ ] Searcher 패키지 (9개 파일)
   - [ ] Utils 패키지 (20개 파일)

2. 코루틴 현대화
   - [ ] AsyncTask 제거 (`Provider.java` 등)
   - [ ] WeakReference 사용 제거
   - [ ] Flow 기반 데이터 스트림 도입
   - [ ] 코루틴 스코프 최적화

3. 데이터 로딩 개선
   - [ ] 캐시 메커니즘 개선 (`CachedPojoRepository`)
   - [ ] 백그라운드 작업 최적화
   - [ ] 메모리 사용량 개선
   - [ ] 데이터 프리페칭 구현

## 다음 단계

1. 제거 단계
   - 사용되지 않는 함수들 제거
   - 관련 테스트 코드 정리
   - 문서 업데이트

2. 통합 단계
   - 에러 처리 시스템 통합
   - 바로가기 관리 클래스 통합
   - 테스트 케이스 업데이트

3. 현대화 단계
   - 코루틴 기반 리팩토링
   - 캐싱 시스템 개선
   - 성능 최적화
