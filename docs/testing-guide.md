# KISS 런처 - Intel 맥북프로 테스트 가이드

## 🚀 빠른 시작

Intel CPU 맥북프로 2019에서 KISS 런처를 테스트하는 방법입니다.

### 1단계: 프로젝트 빌드
```bash
./gradlew assembleDebug
```

### 2단계: 에뮬레이터 실행
```bash
./run_emulator.sh
```

### 3단계: APK 설치 및 테스트
```bash
./install_and_test.sh
```

## 📱 에뮬레이터 실행 옵션

### 기본 실행 (Medium_Phone_API_36)
```bash
./run_emulator.sh
```

### 특정 AVD 실행
```bash
./run_emulator.sh Pixel_7
```

### 데이터 초기화 모드
```bash
./run_emulator.sh Medium_Phone_API_36 --clean
```

## 🧪 AsyncTask → Coroutines 테스트 포인트

### 변환 완료된 기능들
- ✅ **설정 화면 비동기 로딩** (`SettingsActivity`)
- ✅ **태그 아이콘 로딩** (`TagDummyResult`)
- ✅ **연락처 아이콘 로딩** (`ContactsResult`)
- ✅ **단축키 아이콘 로딩** (`ShortcutsResult`)
- ✅ **아이콘 팩 로딩** (`IconsHandler`)

### 테스트 시나리오

#### 1. 설정 화면 성능 테스트
```
설정 → 앱 제외 설정
→ 빠른 로딩 확인 (CoroutineUtils.execute)
→ UI 블로킹 없음 확인
```

#### 2. 아이콘 로딩 성능 테스트
```
앱 목록 스크롤
→ 부드러운 스크롤 확인
→ 아이콘 로딩 지연 최소화
→ 메모리 사용량 안정성
```

#### 3. 태그 기능 테스트
```
태그 생성 → 즐겨찾기 추가
→ 태그 아이콘 로딩 확인
→ Coroutines 기반 비동기 처리
```

#### 4. 메모리 누수 테스트
```
앱 전환 반복 (홈 → 다른 앱 → KISS)
→ 메모리 누수 없음 확인
→ Coroutines 자동 정리 확인
```

## 🔧 Intel CPU 최적화 설정

### 에뮬레이터 최적화 옵션
- **GPU 가속**: Intel 내장 그래픽 활용
- **메모리**: 4GB 할당
- **CPU 코어**: 4개 사용
- **ABI**: x86_64 (Intel 최적화)
- **캐시**: 1GB 할당

### 성능 모니터링
```bash
# CPU 사용률 모니터링
top -pid $(pgrep emulator64)

# 메모리 사용량 모니터링
adb shell dumpsys meminfo fr.neamar.kiss.lum7671

# 로그 실시간 모니터링
adb logcat | grep -i kiss
```

## 🐛 문제 해결

### 에뮬레이터가 느려요
1. Intel HAXM 설치 확인
2. 메모리 할당량 조정
3. GPU 가속화 설정 확인

### APK 설치 실패
1. 기존 앱 제거 후 재설치
2. adb 연결 상태 확인
3. 에뮬레이터 부팅 완료 대기

### Coroutines 관련 이슈
1. 로그에서 CancellationException 확인
2. Job.cancel() 호출 여부 확인
3. LifecycleScope 연동 상태 확인

## 📊 성능 비교

### Before (AsyncTask)
- 스레드 풀 사용으로 메모리 오버헤드
- 복잡한 취소 로직
- 메모리 누수 위험

### After (Coroutines)
- 경량 코루틴으로 성능 향상
- 구조화된 동시성
- 자동 생명주기 관리

## 🎯 추가 개발 계획

### Phase 3: 핵심 로더 시스템
- [ ] `LoadPojos` → Suspend 함수 변환
- [ ] `Provider` 시스템 Coroutines 적용

### Phase 4: 개별 AsyncTask 클래스
- [ ] `SaveSingleOreoShortcutAsync`
- [ ] `SaveAllOreoShortcutsAsync`
- [ ] `CustomIconDialog.AsyncLoad`

---

**Made with ❤️ for Intel MacBook Pro 2019**
