# 프로젝트 문서 및 관리 개선 마스터 플랜

**작성일**: 2025-12-29  
**상태**: 진행 중 🔄  
**목표**: docs 폴더 체계화 및 문서 관리 최적화

---

## 📊 현황 분석

### 문제점 요약
- **93개 마크다운 파일** 중 74%가 완료된 보고서/역사 기록
- 문서 구조가 체계적이지 않아 신규 개발자 온보딩 어려움
- Phase 3 진행 현황 추적 문서 부재
- CI/CD 및 배포 프로세스 문서화 부재

### 개선 목표
- ✅ 문서 폴더 체계화 (8개 주제별 폴더)
- ✅ 필수 신규 문서 작성 (4개)
- ✅ 완료된 보고서 아카이빙 (69개 파일)
- ✅ 활동적 문서만 root 폴더에 유지

---

## 🎯 실행 로드맵

### Phase 1: 즉시 조치 (1주일 이내) ⏱️

#### 1.1 폴더 구조 생성 및 기존 파일 분류
**소요 시간**: 0.5일  
**작업 내용**:
- [ ] `docs/` 루트 폴더 정리
- [ ] 8개 카테고리 폴더 생성
  - `ARCHITECTURE/` - 아키텍처 및 디자인
  - `DEVELOPMENT/` - 개발 가이드
  - `PERFORMANCE/` - 성능 최적화
  - `DEPLOYMENT/` - 배포 및 CI/CD
  - `TESTING/` - 테스트 및 QA
  - `DECISIONS/` - 아키텍처 결정
  - `FEATURES/` - 기능 가이드
  - `archive/` - 완료된 문서
- [ ] 기존 문서 분류 이동

**참고**:
```
docs/
├─ README.md (유지)
├─ README-dev.md (유지)
├─ ARCHITECTURE/ (신규)
├─ DEVELOPMENT/ (신규)
├─ ...
└─ archive/ (신규)
```

#### 1.2 ARCHIVE-INDEX 작성
**소요 시간**: 1시간  
**작업 내용**:
- [ ] `archive/ARCHIVE-INDEX.md` 생성
- [ ] 아카이브된 69개 파일 목록화
- [ ] 각 파일의 생성 시기 및 목적 정리
- [ ] 언제 참고할지 가이드 제공

**구조**:
```markdown
# Archive Index

## Phase 2 Searcher 최적화 (완료: 2025-10)
- phase2-completion-report.md
- phase2-test-results.md
- ...

## Phase 1 성능 최적화 (완료: 2025-09)
- phase-1-measurement-plan.md
- phase-1-performance-results.md
- ...
```

#### 1.3 Phase 3 진행 추적 문서
**소요 시간**: 30분 초기설정, 일일 5분 유지  
**작업 내용**:
- [ ] `docs/DEVELOPMENT/phase-3-progress.md` 생성
- [ ] Phase 3.1 ~ 3.3 진행도 추적
- [ ] 담당자 및 예상 완료 시기 명시
- [ ] 일일 업데이트 (진행 중)

**초기 템플릿**:
```markdown
# Phase 3: UX 개선 - 진행 현황

## 진행률
[████████░░] 80% (2025-12-29)

### Phase 3.1: 태그 네비게이션 버그
- 상태: 개발 중
- 완료율: 60%
- 예상 완료: 2025-12-31

### Phase 3.2: Hibernated 앱 검색순위
- 상태: 계획
- 완료율: 0%
- 예상 완료: 2026-01-05

### Phase 3.3: 새 앱 배지
- 상태: ✅ 완료
- 완료율: 100%
```

---

### Phase 2: 단기 개선 (2-4주)

#### 2.1 배포/운영 프로세스 문서화
**소요 시간**: 2일  
**작업 내용**:
- [ ] `docs/DEPLOYMENT/ci-cd-pipeline.md` 작성
  - GitHub Actions 현황 및 구성 계획
  - 빌드 자동화 프로세스
- [ ] `docs/DEPLOYMENT/deployment-process.md` 작성
  - 단계별 배포 절차
  - 수동 배포 가이드
  - 베타 vs 프로덕션
- [ ] `docs/DEPLOYMENT/fastlane-guide.md` 작성
  - Fastlane 설정 및 사용법
  - 자동 배포 스크립트
- [ ] `docs/DEPLOYMENT/crash-reporting-operations.md` 작성
  - Amplitude 모니터링 운영
  - 일일 체크리스트

#### 2.2 개발자 온보딩 개선
**소요 시간**: 1-2일  
**작업 내용**:
- [ ] `docs/ONBOARDING.md` 작성 (환경 설정 체크리스트)
- [ ] README-dev.md 구성 재정렬 (빠른 시작 상단으로)
- [ ] `docs/DEVELOPMENT/first-contribution.md` 작성 (튜토리얼)

#### 2.3 마이그레이션 문서 통합
**소요 시간**: 2시간  
**작업 내용**:
- [ ] AsyncTask 마이그레이션 3개 문서 관계도 명시
- [ ] 메인 문서 지정 (master-plan으로 통일)
- [ ] "다음 작업" 섹션에 Searcher 마이그레이션 링크

---

### Phase 3: 중기 개선 (1-3개월)

#### 3.1 CI/CD 자동화 구축
**소요 시간**: 3-5일  
**작업 내용**:
- [ ] GitHub Actions 워크플로우 구현
- [ ] Fastlane 자동 배포 설정

#### 3.2 성능 모니터링 운영 가이드
**소요 시간**: 1-2일  
**작업 내용**:
- [ ] Amplitude 대시보드 사용법
- [ ] 일일 모니터링 절차

---

## 📋 구현 진행 상황

### Phase 1 체크리스트

#### 1.1 폴더 구조화 ✅
- [x] `docs/ARCHITECTURE` 폴더 생성
- [x] `docs/DEVELOPMENT` 폴더 생성
- [x] `docs/PERFORMANCE` 폴더 생성
- [x] `docs/DEPLOYMENT` 폴더 생성
- [x] `docs/TESTING` 폴더 생성
- [x] `docs/DECISIONS` 폴더 생성
- [x] `docs/FEATURES` 폴더 생성
- [x] `docs/archive` 폴더 생성
- [x] 기존 파일 분류 이동 완료

#### 1.2 ARCHIVE-INDEX ✅
- [x] `archive/ARCHIVE-INDEX.md` 작성 완료
- [x] 아카이브 파일 목록화 완료
- [x] 색인 기능 완료

#### 1.3 Phase 3 진행 추적 ✅
- [x] `DEVELOPMENT/phase-3-progress.md` 작성 완료
- [x] Phase 3 완료 상태 문서화

### Phase 2 체크리스트

#### 2.1 배포/운영 프로세스 문서화 ✅
- [x] `DEPLOYMENT/ci-cd-pipeline.md` 작성 (GitHub Actions 계획)
- [x] `DEPLOYMENT/deployment-process.md` 작성 (단계별 배포)
- [x] `DEPLOYMENT/fastlane-guide.md` 작성 (Fastlane 사용법)

#### 2.2 README 업데이트 ✅
- [x] README.md 문서 구조 개선 안내 추가

#### 2.3 마이그레이션 문서 정리 ⏳
- [ ] AsyncTask 마이그레이션 3개 문서 정리 (optional)

---

## 📁 최종 문서 구조 (예상)

```
docs/
├─ README.md                          (프로젝트 개요)
├─ README-dev.md                      (개발 가이드)
├─ ONBOARDING.md                      (환경 설정, 신규 개발자용)
│
├─ ARCHITECTURE/
│  ├─ provider-pojo-result-pattern.md
│  ├─ coroutines-pattern.md
│  └─ shizuku-integration.md
│
├─ DEVELOPMENT/
│  ├─ development-guide.md
│  ├─ first-contribution.md
│  ├─ refactoring-guide.md
│  ├─ asynctask-migration.md
│  └─ phase-3-progress.md
│
├─ PERFORMANCE/
│  ├─ profile-build-guide.md
│  └─ optimization-phases/
│     ├─ phase-0-build.md
│     ├─ phase-1-throttling.md
│     ├─ phase-2-search.md
│     └─ phase-3-ux.md
│
├─ DEPLOYMENT/
│  ├─ ci-cd-pipeline.md
│  ├─ deployment-process.md
│  ├─ fastlane-guide.md
│  └─ crash-reporting-operations.md
│
├─ TESTING/
│  ├─ testing-guide.md
│  └─ qa-suite.md
│
├─ DECISIONS/
│  ├─ listview-vs-recyclerview.md
│  └─ library-update-recommendations.md
│
├─ FEATURES/
│  ├─ disabled-app-icon-guide.md
│  ├─ history-sorting-guide.md
│  └─ shizuku-guide.md
│
└─ archive/
   ├─ ARCHIVE-INDEX.md
   └─ completed-reports/
      ├─ phase-1-completion-report.md
      ├─ phase-2-completion-report.md
      └─ ... (69개 파일)
```

---

## ✅ 완료 체크리스트

### Phase 1 즉시 조치
- [ ] 폴더 구조화
- [ ] ARCHIVE-INDEX 작성
- [ ] Phase 3 진행 추적 문서
- [ ] README.md 문서화

### Phase 2 단기 개선
- [ ] 배포 프로세스 문서화
- [ ] 개발자 온보딩 개선
- [ ] 마이그레이션 문서 통합
- [ ] README.md 문서화

### Phase 3 중기 개선
- [ ] CI/CD 자동화
- [ ] 모니터링 운영 가이드

---

## 🔗 관련 문서

- 분석 결과: 이전 자에이전트 분석 리포트 참고
- 진행 상황: 이 문서의 "진행 상황" 섹션 실시간 업데이트

---

**마지막 업데이트**: 2025-12-29  
**다음 업데이트**: 구현 진행 시 일일 업데이트
