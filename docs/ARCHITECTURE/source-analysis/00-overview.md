# KISS 프로젝트 소스 코드 분석 개요

## 분석 목적

### 1. 코드 정리

- 불필요한 코드 식별 및 제거
- 중복 코드 통합
- 레거시 코드 현대화

### 2. 참고 자료

- 성능 개선 포인트 기록
- 리팩토링 가이드
- 아키텍처 문서화

## 소스 코드 구조

### 주요 패키지

1. **UI & Activities** (`fr.neamar.kiss.ui/`): 사용자 인터페이스 관련 코드
2. **Data Providers** (`fr.neamar.kiss.dataprovider/`): 데이터 제공자 구현
3. **Utils** (`fr.neamar.kiss.utils/`): 유틸리티 클래스들
4. **Loaders** (`fr.neamar.kiss.loader/`): 데이터 로딩 관련
5. **DB** (`fr.neamar.kiss.db/`): 데이터베이스 관련
6. **Shortcuts** (`fr.neamar.kiss.shortcut/`): 바로가기 처리
7. **Search** (`fr.neamar.kiss.search/`): 검색 기능
8. **Preference** (`fr.neamar.kiss.preference/`): 설정 관련

## 분석 문서 구조

1. [UI 컴포넌트](./01-ui-components.md)
2. [데이터 관리](./02-data-management.md)
3. [검색 시스템](./03-search-system.md)
4. [설정 시스템](./04-preferences.md)
5. [유틸리티](./05-utils.md)
6. [바로가기 관리](./06-shortcuts.md)
7. [데이터베이스](./07-database.md)
8. [백그라운드 작업](./08-background.md)

## 리팩토링 우선순위

1. AsyncTask → Coroutine 마이그레이션
2. 데이터 로딩 시스템 현대화
3. UI 컴포넌트 최적화
4. 검색 엔진 개선
5. 설정 시스템 단순화

## 코드 품질 개선 계획

1. Kotlin 마이그레이션 완료
2. 테스트 커버리지 향상
3. 성능 최적화
4. 메모리 사용량 개선
5. 코드 중복 제거
