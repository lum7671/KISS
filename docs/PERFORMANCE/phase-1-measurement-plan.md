# Phase 1.3: 성능 측정 계획 (Performance Measurement Plan)

**생성 일시**: 2025-12-15 14:45  
**Phase**: 1.3 (성능 측정)  
**목표**: Phase 1.1 (Throttling) + Phase 1.2 (Lazy Init) 성능 개선 검증

---

## 📊 측정 목표

### 주요 KPI (Key Performance Indicators)

| 지표 | 현재 (Before) | 목표 (After) | 개선율 |
|------|----------|----------|--------|
| **콜드 스타트** | 3000-4000ms | 1500-2000ms | **50-62%** |
| **UI 표시 시간** | 2500-3000ms | 1000-1500ms | **50-60%** |
| **초기 메모리** | 300-350MB | 250-300MB | **15-25%** |
| **onResume 리로드 스킵율** | 0% | 60-90% | **60-90%** |
| **배터리 효율** | 기준선 | +10-15% | **10-15%** |

### 측정 항목

**Phase 1.1 (Throttling) 검증**:
- onResume 호출 빈도 vs 실제 리로드 수행 비율
- 2초 throttle 윈도우 내 스킵된 리로드 수
- RELOAD_THROTTLED 이벤트 로깅

**Phase 1.2 (Lazy Init) 검증**:
- 콜드 스타트 시간 (앱 시작 → UI 표시)
- 초기 메모리 사용량
- 첫 검색 vs 후속 검색 응답시간
- LAZY_LOAD_TRIGGERED 이벤트 로깅

---

## 🛠️ 측정 환경 설정

### Profile APK 빌드 완료 ✅

```
파일: KISS_v4.2.7_b427_20251215_144404_profile_signed.apk
크기: 16M
경로: app/build/outputs/apk/profile/

✅ 빌드 성공 (0 errors, 39 warnings)
✅ 포함 사항:
   - Phase 1.1 Throttling 구현
   - Phase 1.2 Lazy Initialization 구현
   - ProfileManager 통합
   - ActionPerformanceTracker 통합
```

### 측정 방법

#### 1. ProfileManager CSV 로그 수집

**위치**: `/storage/emulated/0/Android/data/kr.lum7671.kiss/files/kiss_profile_logs/`

**로그 형식**:
```
timestamp,event,provider,duration_ms,memory_mb
2025-12-15 14:50:00,COLD_START,main,1200,280
2025-12-15 14:50:00,LAZY_LOAD_TRIGGERED,contacts,300,290
2025-12-15 14:50:05,RELOAD_THROTTLED,data_handler,0,280
2025-12-15 14:50:10,SEARCH_LAZY_LOADING,search,250,295
2025-12-15 14:51:00,RELOAD_THROTTLED,data_handler,0,280
```

**분석 포인트**:
- COLD_START: 초기 시작 시간
- LAZY_LOAD_TRIGGERED: Lazy 로드 트리거 횟수 및 시간
- RELOAD_THROTTLED: Throttle된 리로드 수
- SEARCH_LAZY_LOADING: 첫 검색 오버헤드

#### 2. ActionPerformanceTracker 측정

**측정 항목**:
```
RELOAD
├─ startAction("RELOAD") @ MainActivity.handleDataUpdateOnResume()
├─ endAction("RELOAD") 또는 early return (throttled)
└─ Duration: RELOAD_THROTTLE_MS 내 차이 기록

SEARCH
├─ startAction("SEARCH") @ MainActivity.updateSearchRecords()
├─ endAction("SEARCH")
└─ Duration: 첫 검색(Lazy overhead) vs 후속 검색 비교
```

#### 3. LogCat 모니터링

```bash
# 실시간 로그 수집
adb logcat | grep -E "(ProfileManager|LAZY_LOAD|THROTTLED|PERF)"
```

**예상 로그**:
```
I/ProfileManager: Event: LAZY_LOAD_TRIGGERED, provider: ContactsProvider, duration: 250ms
I/ProfileManager: Event: RELOAD_THROTTLED, skipped: 5, duration: 0ms
I/MainActivity: Search lazy loading triggered: 280ms
```

#### 4. 앱 메트릭 수집

**Amplitude 이벤트** (클라우드 분석):
- `app_start`: 콜드 스타트 트리거
- `reload_requested`: onResume 리로드 요청
- `reload_throttled`: Throttle된 리로드
- `lazy_init_triggered`: Lazy 초기화 트리거
- `search_performed`: 검색 수행 (첫 vs 후속)

---

## 📅 수집 계획

### Phase 1: Baseline 수집 (2025-12-16 ~ 2025-12-17, 2-3일)

**목표**: Phase 1.1-1.2 최적화 전후 성능 데이터 비교

**수집 항목**:
1. 프로파일 APK 설치 (수동)
2. 앱 시작 (콜드 스타트) - 10회
3. onResume 리로드 반복 (앱 백그라운드/포그라운드) - 20회
4. 검색 수행 (첫 검색 + 후속 검색) - 10회
5. 메모리 스냅샷 - 10회 (초기, 검색 후, 100개 앱 로드 후)

**로그 수집**:
```bash
# 디바이스에서 로그 추출
adb pull /storage/emulated/0/Android/data/kr.lum7671.kiss/files/kiss_profile_logs/ ./logs/

# CSV 분석 준비
ls -la logs/ | wc -l  # 파일 개수 확인
wc -l logs/*.csv       # 라인 수 확인
```

### Phase 2: 데이터 분석 (2025-12-18, 1일)

**분석 항목**:
1. 콜드 스타트 평균/중간값/표준편차
2. onResume 리로드 스킵율 (RELOAD_THROTTLED / 전체)
3. 초기 메모리 사용량
4. 첫 검색 vs 후속 검색 응답 시간
5. Lazy 초기화 오버헤드 (첫 검색 추가 시간)

**분석 스크립트** (Python):
```python
import csv
import statistics
from collections import defaultdict

# CSV 읽기
cold_starts = []
reloads_throttled = 0
reloads_total = 0
first_search_times = []
subsequent_search_times = []
memory_usage = []

with open('profile_log.csv') as f:
    reader = csv.DictReader(f)
    for row in reader:
        event = row['event']
        duration = int(row['duration_ms'])
        
        if event == 'COLD_START':
            cold_starts.append(duration)
        elif event == 'RELOAD_THROTTLED':
            reloads_throttled += 1
        elif event == 'RELOAD':
            reloads_total += 1
        elif event == 'SEARCH_LAZY_LOADING':
            first_search_times.append(duration)
        elif event == 'SEARCH':
            subsequent_search_times.append(duration)
        
        memory = int(row['memory_mb'])
        memory_usage.append(memory)

# 통계 계산
print("=== Cold Start ===")
print(f"평균: {statistics.mean(cold_starts):.0f}ms")
print(f"중간값: {statistics.median(cold_starts):.0f}ms")
print(f"표준편차: {statistics.stdev(cold_starts):.0f}ms")
print(f"최소/최대: {min(cold_starts):.0f}ms / {max(cold_starts):.0f}ms")

print("\n=== Throttling 효율 ===")
throttle_ratio = reloads_throttled / (reloads_throttled + reloads_total) * 100
print(f"스킵율: {throttle_ratio:.1f}% ({reloads_throttled}/{reloads_throttled + reloads_total})")

print("\n=== 메모리 ===")
print(f"초기: {memory_usage[0]}MB")
print(f"평균: {statistics.mean(memory_usage):.0f}MB")
print(f"최대: {max(memory_usage)}MB")

print("\n=== 검색 응답 시간 ===")
print(f"첫 검색 (Lazy): {statistics.mean(first_search_times):.0f}ms ± {statistics.stdev(first_search_times):.0f}ms")
print(f"후속 검색: {statistics.mean(subsequent_search_times):.0f}ms ± {statistics.stdev(subsequent_search_times):.0f}ms")
print(f"오버헤드: {statistics.mean(first_search_times) - statistics.mean(subsequent_search_times):.0f}ms")
```

### Phase 3: 보고서 작성 (2025-12-19, 1일)

**보고서 내용**:
- 측정 환경 설명
- 수집된 데이터 통계
- 성능 개선 결과 (예: 50% 콜드 스타트 단축)
- 각 최적화의 기여도 분석
  - Throttling: 리로드 스킵율 60-90% 달성 시 배터리 10-15% 개선
  - Lazy Init: 콜드 스타트 50-62% 단축 (ContactsProvider 지연 로드 효과)
- 권장 사항 (Phase 2로의 진행, 추가 최적화)

---

## 📋 체크리스트

### 설정 단계
- [x] Profile APK 빌드 완료
- [ ] APK 다운로드 (app/build/outputs/apk/profile/)
- [ ] ADB 디바이스 연결 및 APK 설치
- [ ] 앱 실행 확인 (프로파일 로그 경로 존재 확인)

### 데이터 수집 단계
- [ ] 콜드 스타트 10회 기록
- [ ] onResume 리로드 20회 반복
- [ ] 검색 수행 10회 (첫 + 후속)
- [ ] 메모리 스냅샷 10회
- [ ] ProfileManager CSV 로그 확인
- [ ] LogCat 로그 백업

### 분석 단계
- [ ] CSV 파일 파싱 및 통계 계산
- [ ] 성능 개선율 검증 (기대값 달성 확인)
- [ ] 각 최적화별 기여도 분석
- [ ] 이상치 제거 및 재계산

### 보고서 단계
- [ ] docs/phase-1-performance-results.md 작성
- [ ] 그래프 및 표 작성
- [ ] 결론 및 권장 사항 작성
- [ ] Phase 2 진행 여부 결정

---

## 🎯 성공 기준

### 필수 (MUST)
- ✅ Phase 1.1 (Throttling): 리로드 스킵율 **60% 이상** 달성
- ✅ Phase 1.2 (Lazy Init): 콜드 스타트 **40% 이상** 단축 (1500-2000ms 미만)
- ✅ 안정성: 앱 크래시 없음, 메모리 누수 없음

### 선택 (SHOULD)
- 배터리 효율 10% 이상 개선
- 검색 응답 시간 개선 (캐시 효과 50% 이상)
- Lazy Init 오버헤드 300ms 이하 (첫 검색 추가 시간)

### 추가 (NICE-TO-HAVE)
- UI 프레임 드롭 감소 (ProfileManager 측정)
- 메모리 사용량 안정화 (변동 20% 이내)
- Amplitude 클라우드 분석 확인

---

## 📌 주의사항

1. **디바이스 환경**: 모든 측정은 동일 디바이스 + 동일 네트워크 환경에서 수행
   - 배경 앱 최소화 (불필요한 서비스 종료)
   - 네트워크 상태 일정 유지 (WiFi 권장)
   
2. **측정 반복성**: 각 측정마다 2-3분 간격으로 앱 재시작 (메모리 정리)

3. **데이터 유효성**: 
   - 첫 1-2회 측정은 제외 (warmup 제거)
   - 이상치(outlier) 제거 후 통계 계산 (3σ 규칙)

4. **프라이버시**: 개인정보는 포함되지 않음 (프로파일 로그는 메트릭만 기록)

---

## 📂 파일 구조

```
/Users/1001028/git/KISS/
├── app/build/outputs/apk/profile/
│   └── KISS_v4.2.7_b427_20251215_144404_profile_signed.apk  (빌드됨)
│
├── docs/
│   ├── v4.1.7-to-v4.1.9-optimization-plan.md  (전체 계획)
│   ├── IMPLEMENTATION-PROGRESS.md               (진행 상황)
│   ├── phase-1-measurement-plan.md             (현재 문서)
│   └── phase-1-performance-results.md          (작성 예정)
│
└── measurements/
    ├── baseline_logs/                          (수집할 디렉토리)
    ├── analyzed_data.csv                       (분석 데이터)
    └── performance_analysis.py                 (분석 스크립트)
```

---

## 🔗 관련 파일

- [v4.1.7-to-v4.1.9-optimization-plan.md](v4.1.7-to-v4.1.9-optimization-plan.md) - Phase 전체 계획
- [IMPLEMENTATION-PROGRESS.md](IMPLEMENTATION-PROGRESS.md) - 구현 진행 현황
- [app/src/main/java/fr/neamar/kiss/DataHandler.java](../app/src/main/java/fr/neamar/kiss/DataHandler.java#L520) - Throttling 구현
- [app/src/main/java/fr/neamar/kiss/dataprovider/Provider.java](../app/src/main/java/fr/neamar/kiss/dataprovider/Provider.java) - Lazy Init 구현

---

**다음 단계**: APK 디바이스 설치 → 데이터 수집 → 성능 분석 → 보고서 작성
