# Phase 2 계획: 검색/리스트 성능 최적화 및 계측 강화

최종 업데이트: 2025-12-16

## 🎯 목표(KPIs)
- 검색 대기시간: p50 ≤ 4ms, p95 ≤ 8ms, p99 ≤ 15ms
- 콜드스타트 TTFB(첫 UI 상호작용 가능): ≤ 250ms (측정 이벤트 기준)
- 스크롤 잔뜩/프레임 드랍: 체감 불가 수준(목표 0에 근접)
- 메모리: 입력/스크롤 중 추가 할당 최소화(steady-state 유지)
- 리로드 스로틀: onResume 중 리로드 차단율 ≥ 60%

## 🔭 범위(Scope)
Phase 2는 코드 구조를 크게 바꾸지 않고, 검색 경로와 리스트 바인딩 경로의 미세 최적화 및 계측 보강에 집중한다. Settings 크래시 수정은 별도 트랙에서 처리한다.

### S1. 검색기 구성 리팩터링(즉시 참조)
- 내용: Searcher들이 설정값을 정적 캐시에 두지 않고, 인스턴스 단위로 즉시 읽도록 정비.
- 경로: app/src/main/java/fr/neamar/kiss/searcher/*
- 관련: `number-of-display-elements` 캐시 제거 방향과 일치(설명 주석 존재, Settings 쪽).
- 기대효과: 설정 변경 시 앱 재시작 없이 즉시 반영, 코드 단순화.

### S2. 입력 중 성능/할당 최적화
- 내용: `RecordAdapter` 및 각 `Result` 뷰 바인딩 경로의 불필요 할당/작업 제거.
- 경로: 
  - app/src/main/java/fr/neamar/kiss/adapter/RecordAdapter.java
  - app/src/main/java/fr/neamar/kiss/result/*
- 포인트:
  - 안정적인 `getItemId()` 유지, `notifyDataSetChanged()`의 영향 최소화
  - 하이라이트/FuzzyScore 재사용 전략 점검
  - 빈번 경로에서의 `String`/`List` 재할당 억제

### S3. Provider 질의 경로 효율 유지 + 지연 초기화 보완
- 내용: `ensureLoaded()` 타이밍 점검, 첫 검색 시 오버헤드 최소화. (필요 시 안전한 소량 프리웜)
- 경로:
  - app/src/main/java/fr/neamar/kiss/dataprovider/Provider.java
  - app/src/main/java/fr/neamar/kiss/dataprovider/*Provider.java
- 기대효과: 첫 검색 지연 최소화, 이후 검색은 캐시 경로 고정.

### S4. 계측 확장(p95/p99, 스로틀 비율)
- 내용: `COLD_START`, `RELOAD_REQUESTED` 이벤트 추가 및 분석 스크립트 확장.
- 경로:
  - app/src/main/java/fr/neamar/kiss/MainActivity.java (시작/첫 상호작용)
  - app/src/main/java/fr/neamar/kiss/DataHandler.java (리로드 요청)
  - utils/analyze_profile_custom_events.py (p95/p99, 스로틀 비율)
- 기대효과: 체감 성능을 더 정확히 수치화, 회귀 조기 탐지.

### S5. UI 비동기 안전성(전역 가이드)
- 내용: UI 연계 비동기는 `CoroutineUtils.runAsyncWithLifecycle()` 우선 적용 가이드 정립.
- 경로:
  - app/src/main/java/fr/neamar/kiss/MainActivity.java
  - app/src/main/java/fr/neamar/kiss/utils/CoroutineUtils.kt
- 비고: Settings 크래시 수정은 별도 트랙에서 처리(본 Phase 범위 외).

## 🧪 검증(Validation)
- 로그: ProfileManager + ActionPerformanceTracker(CSV/Logcat)
- 스크립트: utils/analyze_profile_custom_events.py 확장으로 p50/p95/p99, 스로틀 비율 산출
- 시나리오: 콜드스타트 10회, onResume 20회, 검색(첫/후속) 20회, 스크롤 상하 왕복 5회

## 📅 마일스톤
1) S1-S2 구현(4h) → 단위 측정
2) S3 보완(2h) → 단위 측정
3) S4 계측 확장 + 리포트(2h)
4) S5 가이드 반영(1h) → 최종 측정/문서화

## 🧩 변경 예상(코드 미반영, 제안 목록)
- `RecordAdapter.updateResults(...)`: FuzzyScore 재사용/지연 생성 옵션 검토
- `Result.display(...)` 전반: View 재사용·할당 최소화 점검
- `Provider.ensureLoaded()`: 최초 트리거 시 UI 경로 영향 최소화(콜백 분리/계측 추가)
- `MainActivity`/`DataHandler`: `COLD_START`, `RELOAD_REQUESTED` 이벤트 삽입
- `analyze_profile_custom_events.py`: p95/p99·스로틀 비율 계산 루틴 추가

## 🚧 리스크/완화
- 사용자 체감 변화가 생기지 않도록 UI 동작/순서 유지
- 계측 추가로 로그량 증가 가능 → 프로파일 빌드 한정
- 프리웜 도입 시 과도한 I/O 방지(보수적 적용)

## ⛔ 비범위(Out-of-scope)
- SettingsFragment 크래시 수정(별도 트랙)
- 위젯/대규모 UI 개편, 업스트림 대형 PR 병합

---
작성자: 개발자
검토/승인: (미정)
버전: v0.1 (초안)
