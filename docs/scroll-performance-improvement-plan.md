# 스크롤 성능 개선 계획

## 🎯 목표

에뮬레이터 환경에서 발생하는 앱 목록 스크롤 끊김 현상 해결

## 📊 현재 문제 분석

### 측정된 증상

- 에뮬레이터에서 스크롤 시 끊김 현상
- 빠른 타이핑 시 UI 반응 지연
- 메모리 부족 환경에서 더 심각

### 발견된 병목 지점

#### 1. SetImageCoroutine 재시도 로직 (심각도: 높음)

**파일**: `app/src/main/java/fr/neamar/kiss/result/SetImageCoroutine.kt`
**라인**: 112-119

```kotlin
// 문제: 최대 600ms 동기 대기
while (drawable == null && retryCount < 3) {
    retryCount++
    Thread.sleep((100 * retryCount).toLong()) 
    drawable = result.getDrawable(imageView.context)
}
```

**영향**: 스크롤 중 여러 아이콘 로딩이 동시에 재시도하면 백그라운드 스레드 풀 고갈

#### 2. 뷰포트 체크 재시도 (심각도: 중간)

**파일**: `app/src/main/java/fr/neamar/kiss/result/Result.java`
**라인**: 387-395

```java
// 문제: view.post() 큐에 작업 누적
view.post(() -> {
    if (isViewInViewport(view)) {
        setAsyncDrawable(view, resId, false);
    }
});
```

**영향**: 빠른 스크롤 시 메인 스레드 큐 포화

#### 3. 타이핑마다 애니메이션 (심각도: 중간)

**파일**: `app/src/main/java/fr/neamar/kiss/MainActivity.java`
**라인**: 1461-1465

```java
// 문제: 검색 결과 업데이트마다 애니메이션
list.prepareChangeAnim();
adapter.updateResults(...);
list.animateChange();
```

**영향**: GPU 가속이 약한 에뮬레이터에서 프레임 드랍

## ✅ 단계별 개선 계획

### Phase 1: 즉시 수정 (예상 개선: 40-50%)

#### 1.1 아이콘 로딩 재시도 제거

- [ ] `SetImageCoroutine.kt` 수정
- [ ] 재시도 로직 완전 제거
- [ ] 에러 핸들링만 유지

#### 1.2 뷰포트 재시도 제거

- [ ] `Result.java` 수정
- [ ] view.post() 재귀 제거
- [ ] 스크롤 멈추면 자연스럽게 로드되도록

#### 1.3 타이핑 중 애니메이션 비활성화

- [ ] `AnimatedListView.java`에 enable/disable 추가
- [ ] `MainActivity.java` TextWatcher 수정
- [ ] 300ms 디바운스 적용

### Phase 2: 캐싱 개선 (예상 개선: 20-30%)

#### 2.1 알림 도트 상태 캐싱

- [ ] `AppResult.java`에 static 캐시 추가
- [ ] SharedPreferences 읽기 최소화
- [ ] 알림 변경 시 캐시 무효화

#### 2.2 FuzzyScore 결과 캐싱

- [ ] `Result.java`에 캐시 필드 추가
- [ ] 동일 쿼리 재사용
- [ ] 메모리 사용량 모니터링

### Phase 3: 에뮬레이터 최적화 (예상 개선: 10-20%)

#### 3.1 에뮬레이터 감지

- [ ] Build.FINGERPRINT 체크
- [ ] 성능 모드 플래그 추가

#### 3.2 성능 모드 적용

- [ ] 애니메이션 완전 비활성화
- [ ] 아이콘 해상도 낮추기
- [ ] 성능 추적 비활성화

## 📝 구현 체크리스트

### Week 1: Phase 1 구현

#### Day 1-2: SetImageCoroutine 수정

```kotlin
// Before (문제)
var retryCount = 0
while (drawable == null && retryCount < 3) {
    retryCount++
    Thread.sleep((100 * retryCount).toLong())
    drawable = result.getDrawable(imageView.context)
}

// After (개선)
return try {
    result.getDrawable(imageView.context)
} catch (e: Exception) {
    Log.w(TAG, "Icon load failed: ${e.message}")
    null
}
```

- [ ] 코드 수정
- [ ] 단위 테스트
- [ ] 에뮬레이터 테스트
- [ ] 실기기 회귀 테스트

#### Day 3: Result.java 수정

```java
// Before (문제)
view.post(() -> {
    if (isViewInViewport(view)) {
        setAsyncDrawable(view, resId, false);
    }
});

// After (개선)
// 재시도 로직 완전 제거 - 스크롤 멈추면 getView()에서 자연스럽게 재호출됨
return;
```

- [ ] 코드 수정
- [ ] 스크롤 동작 확인
- [ ] 메모리 프로파일링

#### Day 4-5: MainActivity 애니메이션 제어

```java
// AnimatedListView.java 추가
private boolean animationsEnabled = true;

public void setAnimationsEnabled(boolean enabled) {
    this.animationsEnabled = enabled;
}

public void animateChange() {
    if (!animationsEnabled) return;
    // ... 기존 로직
}

// MainActivity.java 수정
searchEditText.addTextChangedListener(new TextWatcher() {
    private Handler handler = new Handler();
    private Runnable enableAnim = () -> list.setAnimationsEnabled(true);
    
    public void beforeTextChanged(...) {
        list.setAnimationsEnabled(false);
        handler.removeCallbacks(enableAnim);
    }
    
    public void afterTextChanged(...) {
        handler.postDelayed(enableAnim, 300);
    }
});
```

- [ ] AnimatedListView 수정
- [ ] MainActivity 수정
- [ ] 타이핑 반응성 테스트
- [ ] 애니메이션 품질 확인

### Week 2: Phase 2 구현

#### Day 6-7: 알림 도트 캐싱

- [ ] AppResult static 캐시 추가
- [ ] NotificationListener 연동
- [ ] 메모리 사용량 측정

#### Day 8-9: FuzzyScore 캐싱

- [ ] Result 베이스 클래스 수정
- [ ] 캐시 히트율 측정
- [ ] 메모리 누수 확인

#### Day 10: 통합 테스트

- [ ] 전체 시나리오 테스트
- [ ] 성능 메트릭 수집
- [ ] 개선율 계산

### Week 3: Phase 3 구현

#### Day 11-12: 에뮬레이터 감지

```java
public static boolean isEmulator() {
    return Build.FINGERPRINT.contains("generic")
        || Build.FINGERPRINT.contains("unknown")
        || Build.MODEL.contains("google_sdk")
        || Build.MODEL.contains("Emulator")
        || Build.MODEL.contains("Android SDK built for x86")
        || Build.MANUFACTURER.contains("Genymotion")
        || (Build.BRAND.startsWith("generic") 
            && Build.DEVICE.startsWith("generic"));
}
```

- [ ] DeviceUtils 클래스 생성
- [ ] 감지 로직 구현
- [ ] 다양한 에뮬레이터에서 테스트

#### Day 13-14: 성능 모드 구현

```java
// MainActivity.onCreate()
if (DeviceUtils.isEmulator() || DeviceUtils.isLowEndDevice()) {
    // 애니메이션 비활성화
    list.setAnimationsEnabled(false);
    
    // 아이콘 해상도 낮추기
    prefs.edit().putString("icon-size", "small").apply();
    
    // 성능 추적 비활성화
    ActionPerformanceTracker.getInstance().setEnabled(false);
    
    Log.i(TAG, "Performance mode enabled");
}
```

- [ ] 성능 모드 구현
- [ ] 설정 UI 추가 (선택사항)
- [ ] 성능 비교 테스트

#### Day 15: 최종 검증

- [ ] 전체 개선 효과 측정
- [ ] 문서화
- [ ] 릴리스 노트 작성

## 📈 성능 측정 기준

### 측정 도구

```java
public class ScrollPerformanceMonitor {
    private long frameCount = 0;
    private long droppedFrames = 0;
    private long totalFrameTime = 0;
    
    public void onFrame(long frameTimeNanos) {
        frameCount++;
        long frameTimeMs = frameTimeNanos / 1_000_000;
        totalFrameTime += frameTimeMs;
        
        if (frameTimeMs > 16) { // 60fps threshold
            droppedFrames++;
        }
    }
    
    public double getAverageFPS() {
        return frameCount * 1000.0 / totalFrameTime;
    }
    
    public double getDropRate() {
        return (double) droppedFrames / frameCount * 100;
    }
}
```

### 테스트 시나리오

#### 시나리오 1: 빠른 스크롤

- 100개 앱 설치
- 상단→하단 2초 스크롤
- **목표**: 평균 FPS 45+ (개선 전 30)

#### 시나리오 2: 빠른 타이핑

- "google chrome" 빠르게 입력
- 키 입력 간격 100ms
- **목표**: 입력 지연 < 50ms (개선 전 150ms)

#### 시나리오 3: 메모리 부하

- 500개 앱 설치
- 10분간 랜덤 스크롤
- **목표**: 메모리 사용량 < 200MB (개선 전 300MB)

### 개선 목표

| 지표 | 개선 전 | 목표 | Phase 1 | Phase 2 | Phase 3 |
|-----|--------|------|---------|---------|---------|
| 평균 FPS | 30 | 45 | 40 | 43 | 45 |
| 프레임 드랍률 | 35% | 15% | 22% | 18% | 15% |
| 타이핑 지연 | 150ms | 50ms | 80ms | 60ms | 50ms |
| 메모리 사용 | 300MB | 200MB | 280MB | 240MB | 200MB |

## 🧪 테스트 환경

### 에뮬레이터 설정

```bash
# 저사양 에뮬레이터 (문제 재현용)
avdmanager create avd -n test_low \
  -k "system-images;android-33;google_apis;x86_64" \
  -d pixel_5

# config.ini 수정
hw.ramSize=512
hw.cpu.ncore=1
hw.gpu.enabled=yes
hw.gpu.mode=swiftshader_indirect
```

### 실기기 테스트

- Pixel 5 (Android 13)
- Galaxy A32 (Android 12)
- 저사양 중국 폰 (RAM 2GB)

## 📦 배포 계획

### 베타 테스트

- [ ] 내부 테스트 (개발팀)
- [ ] 클로즈 베타 (10명)
- [ ] 오픈 베타 (100명)

### 릴리스

- [ ] 버전 업데이트: v4.1.8
- [ ] 릴리스 노트 작성
- [ ] Play Store 배포

### 모니터링

- [ ] Crashlytics 모니터링
- [ ] 성능 메트릭 수집
- [ ] 사용자 피드백 수집

## 🔄 롤백 계획

### 문제 발생 시

1. 즉시 이전 버전으로 롤백
2. 로그 분석
3. 핫픽스 준비
4. 재배포

### 기준

- 크래시율 > 1%
- 평균 FPS < 개선 전
- 사용자 불만 리뷰 증가

## 📚 참고 자료

- [app-list-scroll-analysis.md](app-list-scroll-analysis.md) - 상세 분석
- [Android Performance Patterns](https://www.youtube.com/playlist?list=PLWz5rJ2EKKc9CBxr3BVjPTPoDPLdPIFCE)
- [Coil Performance Guide](https://coil-kt.github.io/coil/performance/)

---

**작성일**: 2025-10-17  
**작성자**: GitHub Copilot  
**상태**: 계획 단계
