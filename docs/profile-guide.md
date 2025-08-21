# KISS Profile 모드 성능 분석 가이드

## 🎯 개요

KISS 앱의 **Profile 빌드 모드**는 실제 사용 환경에서 앱의 성능을 모니터링하고 분석하기 위해 설계되었습니다. 1일 정도 Profile 모드로 설치하여 사용하면서 성능 로그를 수집하고 분석할 수 있습니다.

## 🔧 Profile 모드 특징

### 빌드 설정

- **앱 ID**: `fr.neamar.kiss.lum7671` (일반 빌드와 병행 설치 가능)
- **디버깅**: 활성화 (`debuggable = true`)
- **최적화**: 비활성화 (성능 분석을 위해)
- **프로파일링 도구**: 포함된 라이브러리들

### 포함된 성능 모니터링 도구

```gradle
// 벤치마크 및 추적
androidx.benchmark:benchmark-macro-junit4:1.3.3
androidx.tracing:tracing:1.2.0

// 프로파일링 최적화
androidx.startup:startup-runtime:1.1.1
androidx.profileinstaller:profileinstaller:1.3.1

// 메모리 & 동시성 모니터링
androidx.work:work-runtime:2.9.1
androidx.concurrent:concurrent-futures:1.1.0
androidx.lifecycle:lifecycle-process:2.7.0

// 네트워크 & I/O 분석
com.squareup.okhttp3:logging-interceptor:4.12.0
androidx.datastore:datastore-preferences:1.0.0
```

## 📱 Profile 빌드 설치 및 사용

### 1. Profile APK 빌드

```bash
./gradlew assembleProfile
```

### 2. 기기에 설치

```bash
adb install app/build/outputs/apk/profile/app-profile-unsigned.apk
```

### 3. 앱 사용

- 평소처럼 KISS 런처를 1일 정도 사용
- 자동으로 성능 로그가 5초마다 기록됨
- 다음 이벤트들이 특별히 추적됨:
  - 앱 시작/종료
  - 액티비티 생명주기 (onCreate, onResume, onPause, onStop)
  - 검색 성능 (검색어, 소요시간, 결과 개수)
  - 메모리 압박 상황

## 📊 성능 로그 수집 위치

### 안드로이드 기기 내 저장 경로

```plaintext
/storage/emulated/0/Android/data/fr.neamar.kiss.lum7671/files/kiss_profile_logs/
```

### 로그 파일 형식

- **파일명**: `performance_YYYY-MM-DD_HH-mm-ss.csv`
- **형식**: CSV (콤마로 구분된 값)

### 로그 데이터 구조

| 항목 | 설명 |
|------|------|
| `timestamp` | Unix 타임스탬프 (밀리초) |
| `uptime_ms` | 앱 실행 시간 (밀리초) |
| `heap_used_mb` | 사용 중인 힙 메모리 (MB) |
| `heap_max_mb` | 최대 힙 메모리 (MB) |
| `native_heap_mb` | 네이티브 힙 메모리 (MB) |
| `cpu_usage_percent` | CPU 사용률 (%) |
| `gc_count` | GC 실행 시간 (추정) |
| `thread_count` | 활성 스레드 수 |
| `memory_class_mb` | 앱 메모리 클래스 |
| `large_memory_class_mb` | 대용량 메모리 클래스 |
| `available_memory_mb` | 사용 가능한 시스템 메모리 (MB) |
| `total_memory_mb` | 총 시스템 메모리 (MB) |
| `is_low_memory` | 낮은 메모리 상태 여부 |
| `app_startup_time_ms` | 앱 시작 후 경과 시간 |
| `method_trace_info` | 메서드 추적 정보 |

## 📥 로그 파일 PC로 가져오기

### ADB를 사용한 로그 다운로드

```bash
# 로그 디렉토리 전체 다운로드
adb pull /storage/emulated/0/Android/data/fr.neamar.kiss.lum7671/files/kiss_profile_logs/ ./kiss_logs/

# 특정 날짜 로그만 다운로드
adb pull /storage/emulated/0/Android/data/fr.neamar.kiss.lum7671/files/kiss_profile_logs/performance_2025-08-13_*.csv ./
```

### 파일 관리자 앱 사용

1. 기기에서 파일 관리자 앱 실행
2. `Android/data/fr.neamar.kiss.lum7671/files/kiss_profile_logs/` 이동
3. 로그 파일들을 클라우드 저장소나 이메일로 전송

## 📈 로그 분석 도구 사용법

### 전제 조건

```bash
pip install pandas matplotlib seaborn
```

### 분석 실행

```bash
# 기본 분석 (현재 디렉토리의 로그 파일들)
python3 analyze_profile_logs.py

# 특정 디렉토리 분석
python3 analyze_profile_logs.py ./kiss_logs/

# 출력 디렉토리 지정
python3 analyze_profile_logs.py ./kiss_logs/ --output ./analysis_results/
```

### 생성되는 분석 결과

#### 1. 터미널 출력

- 📊 메모리 사용량 통계 (평균, 최대, 표준편차)
- ⚡ CPU 성능 분석 (평균, 최대, 고사용률 구간)
- 🔄 앱 생명주기 이벤트 카운트
- 🔍 검색 성능 분석 (평균/최대 검색 시간)

#### 2. 시각화 파일

- `performance_overview.png`: 전체 성능 개요 (4개 차트)
- `memory_analysis.png`: 상세 메모리 분석

#### 3. HTML 보고서

- `profile_report.html`: 종합 분석 보고서

## 🔍 분석 지표 해석

### 메모리 관련

- **정상 범위**: 힙 메모리 < 100MB
- **주의 필요**: 메모리 사용량이 지속적으로 증가하는 추세
- **문제 상황**: 사용 가능 메모리 < 200MB

### CPU 관련

- **정상 범위**: 평균 CPU 사용률 < 30%
- **주의 필요**: 평균 > 50% 또는 70% 이상이 자주 발생
- **문제 상황**: 지속적으로 높은 CPU 사용률

### 검색 성능

- **우수**: 평균 검색 시간 < 50ms
- **양호**: 평균 검색 시간 < 100ms  
- **개선 필요**: 평균 검색 시간 > 100ms

### 스레드 관리

- **정상**: 스레드 수 < 15개
- **주의**: 스레드 수 > 20개 (스레드 풀 관리 검토)

## 💡 성능 최적화 권장사항

### 메모리 최적화

1. **메모리 누수 확인**: 지속적인 메모리 증가 추세 모니터링
2. **아이콘 캐시 관리**: 메모리 압박 시 캐시 정리 최적화
3. **백그라운드 정리**: 불필요한 백그라운드 작업 최소화

### CPU 최적화

1. **검색 알고리즘**: 검색 시간이 긴 경우 알고리즘 개선
2. **UI 업데이트**: 메인 스레드 차단 최소화
3. **백그라운드 작업**: 무거운 작업을 백그라운드 스레드로 이동

### 배터리 최적화

1. **주기적 작업**: 불필요한 주기적 업데이트 줄이기
2. **네트워크 사용**: 효율적인 네트워크 요청 관리
3. **센서 사용**: 사용하지 않는 센서 비활성화

## 🚀 실제 사용 시나리오

### 1일차 - 설치 및 초기 설정

- Profile 빌드 설치
- 기본 런처로 설정
- 평소처럼 사용 시작

### 2-7일차 - 데이터 수집

- 다양한 사용 패턴으로 앱 사용
- 특히 검색 기능 활발히 사용
- 메모리 부족 상황도 의도적으로 테스트

### 분석일 - 데이터 분석

- 로그 파일 수집
- 분석 도구 실행
- 성능 병목 지점 파악
- 최적화 우선순위 결정

## 🛠️ 문제 해결

### 로그 파일이 생성되지 않는 경우

1. 외부 저장소 권한 확인
2. 앱이 Profile 빌드인지 확인 (`fr.neamar.kiss.lum7671`)
3. 로그 디렉토리 수동 생성 시도

### 분석 도구 실행 오류

1. Python 패키지 설치 확인: `pip install pandas matplotlib seaborn`
2. CSV 파일 형식 확인
3. 파일 경로 및 권한 확인

### 성능 영향 최소화

- Profile 모드는 프로덕션용이 아님
- 일상 사용 후 일반 빌드로 복구
- 로그 파일은 주기적으로 정리

## 📞 지원

문제가 발생하거나 추가 기능이 필요한 경우:

1. GitHub 이슈 등록
2. 로그 파일 샘플 첨부
3. 기기 정보 및 사용 환경 명시
