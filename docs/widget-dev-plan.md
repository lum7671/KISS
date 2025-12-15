# Widget Development Plan

## 목적
- 위젯 표시 안정성 향상(날씨 등 느린 RemoteViews 위젯 포함)
- 현대 기기(12L+/SDK35)에서 올바른 사이즈 네고 및 리사이즈 UX 확보
- 스타트/리스닝 레이스 및 라이프사이클 정합성 개선

## 범위
- 코드: forwarder/Widgets.java, ui/WidgetView.java, ui/WidgetHost.java
- 리소스: 기존 위젯 레이아웃/패딩 유지, 필요 시 margin/padding 조정
- 제외: 위젯 제공 앱 자체의 버그(3rd-party provider) 수정

## 현황 요약
- Widgets.java: 위젯 추가/복원 시 startListening()이 add/restore/onStart에서 중복 호출; 컨테이너 높이 0일 때 lineSize가 1로 축소 가능; 사이징이 고정 50dp 라인 기반, padding/sizeOptions 반영 없음; onStop 비어 있고 onDestroy에서만 stopListening.
- WidgetView.java: updateAppWidgetSize가 구버전 시그니처로만 호출, 12L+ sizeOptions 미반영; padding 고려 없음; 터치 인터셉트/롱프레스 처리만 유지.
- WidgetHost.java: start/stop에 try/catch 있으나 provider 변경 시 debounce 없음; stopListening 시 clearViews 호출.

## 주요 문제점
- startListening 중복 호출로 RemoteViews 수신 시점 레이스, 느린 위젯이 빈 화면으로 남을 수 있음.
- 컨테이너 미측정 상태에서 사이즈 계산 → 1라인/과소 사이즈 위젯 발생.
- 사이징 수식이 provider min/max, padding, targetCellHeight(API 12L+)를 반영하지 않음.
- sizeOptions 갱신을 provider에 전달하지 않아 리사이즈 후 레이아웃 반영 지연/실패 가능.
- provider 변경(onProvidersChanged) 시 전체 restore 호출 가능성 → 불필요한 churn.

## 업스트림(v3.24.2) 대비 차이
- INITIAL_WIDGET_LINE_SIZE=2 기본 도입으로 신규 위젯 기본 높이 개선.
- getMinHeight()가 targetCellHeight를 사용해 Android 12L+에서 올바른 최소 높이 산정.
- add/resize 후 post-layout에 updateAppWidgetSizeOptions 전달(12L+ sizeOptions, 이하 버전 legacy 4-int 호출).
- padding/resize bound 클램프 적용으로 과소/과대 리사이즈 방지.

## 단계별 실행 계획

### Phase 1: Lifecycle & Race 안정화 (완료)
- [x] startListening 호출 경량화 및 가드: restore/add/onStart 중 단일 지점으로 일원화, 중복 호출 시 IllegalStateException 무시 처리.
- [x] provider 변경 debounce: onProvidersChanged에서 500ms 지연 후 단일 restore/정리 실행.
- [x] add 시 컨테이너 높이 0이면 post { measure/layout } 후 size 적용 또는 합리적 기본 라인(2라인) 사용.

### Phase 2: 사이징 현대화 (업스트림 포트, 완료)
- [x] Widgets.java에 INITIAL_WIDGET_LINE_SIZE=2 도입, getMinHeight()/getLineSize()/setWidgetSize 수식 교체(targetCellHeight, provider min/max, padding 반영).
- [x] WidgetView.updateAppWidgetSize 현대화: SDK>=S_V2(12L+)에서 sizeOptions(float) 경로, 이하 버전은 legacy 4-int fallback.
- [x] 리사이즈 가능 여부 판단 로직을 새 min/max 계산 기준으로 갱신.

### Phase 3: 리사이즈 UX/저장 정합 (완료)
- [x] serializeState()/restoreWidgets()가 새 높이 계산과 일치하도록 검증(라인 수 계산 방식 점검).
- [x] 리사이즈 버튼(크기 증가/감소) 노출 조건 재검토: 최소/최대 라인 범위를 새 수식으로 제한.

### Phase 4: 정리 및 회귀 방지 (완료)
- [x] onStop/onDestroy 정합성 점검: 위젯 미사용 모드에서는 stopListening 수행, destroy 시 안전 정리.
- [x] zombie widget id 정리 로직 유지/검증.
- [x] 로그 레벨 정리: startListening 실패(프로바이더 미설치) 시 명확한 에러/경고 로그.

## 테스트 플랜
- 느린 RemoteViews(날씨) + 빠른 Tasks 위젯 혼합 추가 후 재시작/회전/앱 전환 반복: 빈 화면/깜빡임 없을 것.
- Android 12L+/foldable에서 리사이즈 후 레이아웃 반영 확인(sizeOptions 전달 여부).
- add 시점 컨테이너 높이 0 상황(런처 첫 로드)에서 기본 2라인 확보 여부 확인.
- provider 업데이트(앱 업데이트/enable-disable) 시 위젯 유지 여부 확인.
- serialize/restore 후 라인 수 일관성 검증.

## 메모
- 사이징 수식 포트 시 CJK/폰트 스케일에 대한 영향은 따로 확인.
- startListening 레이스 수정과 사이징 포트를 분리 커밋 권장(리뷰/롤백 용이성).
