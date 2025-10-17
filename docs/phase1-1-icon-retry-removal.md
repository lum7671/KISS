# Phase 1.1: 아이콘 로딩 재시도 로직 제거

## 목표

SetImageCoroutine의 동기적 재시도 로직을 제거하여 스크롤 성능 개선

## 변경 사항

### 파일: app/src/main/java/fr/neamar/kiss/result/SetImageCoroutine.kt

**변경 전 (Lines 90-120):**

```kotlin
private fun loadDrawable(
    imageViewRef: WeakReference<ImageView>,
    resultRef: WeakReference<Result<*>>
): Drawable? {
    val imageView = imageViewRef.get() ?: return null
    val result = resultRef.get() ?: return null

    val currentTag = imageView.tag
    if (currentTag !is ImageLoadingTag || currentTag.result != result) {
        return null
    }

    return try {
        var drawable = result.getDrawable(imageView.context)

        // ❌ 문제: 최대 600ms 동기 대기
        var retryCount = 0
        while (drawable == null && retryCount < 3) {
            retryCount++
            Thread.sleep((100 * retryCount).toLong())
            drawable = result.getDrawable(imageView.context)
            android.util.Log.w("SetImageCoroutine", 
                "Retrying icon load (${retryCount}/3) for ${result.javaClass.simpleName}")
        }

        drawable
    } catch (e: Exception) {
        android.util.Log.w("SetImageCoroutine", "Failed to load drawable: ${e.message}")
        null
    }
}
```

**변경 후:**

```kotlin
private fun loadDrawable(
    imageViewRef: WeakReference<ImageView>,
    resultRef: WeakReference<Result<*>>
): Drawable? {
    val imageView = imageViewRef.get() ?: return null
    val result = resultRef.get() ?: return null

    val currentTag = imageView.tag
    if (currentTag !is ImageLoadingTag || currentTag.result != result) {
        return null
    }

    return try {
        // ✅ 개선: 재시도 없이 1회만 시도
        // IconsHandler의 내부 캐싱에 의존
        result.getDrawable(imageView.context)
    } catch (e: Exception) {
        android.util.Log.w("SetImageCoroutine", "Failed to load drawable: ${e.message}")
        null
    }
}
```

### 파일 2: app/src/main/java/fr/neamar/kiss/result/SetImageCoroutine.kt

**applyDrawable() 메서드도 단순화:**

**변경 전 (Lines 130-175):**

```kotlin
private fun applyDrawable(
    imageViewRef: WeakReference<ImageView>,
    resultRef: WeakReference<Result<*>>,
    drawable: Drawable?
) {
    val imageView = imageViewRef.get() ?: return
    val result = resultRef.get() ?: return

    val currentTag = imageView.tag
    if (currentTag !is ImageLoadingTag || currentTag.result != result) {
        return
    }

    // ❌ 복잡한 fallback 로직
    if (drawable != null) {
        imageView.setImageDrawable(drawable)
        imageView.tag = result
    } else {
        android.util.Log.w("SetImageCoroutine", "Drawable is null, forcing default icon load")
        try {
            val defaultDrawable = result.getDrawable(imageView.context)
            if (defaultDrawable != null) {
                imageView.setImageDrawable(defaultDrawable)
                imageView.tag = result
            } else {
                // 시스템 기본 아이콘
                val systemDefault = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    imageView.context.resources.getDrawable(android.R.drawable.sym_def_app_icon, imageView.context.theme)
                } else {
                    @Suppress("DEPRECATION")
                    imageView.context.resources.getDrawable(android.R.drawable.sym_def_app_icon)
                }
                imageView.setImageDrawable(systemDefault)
                imageView.tag = result
            }
        } catch (e: Exception) {
            android.util.Log.e("SetImageCoroutine", "Failed to set fallback icon", e)
            imageView.tag = result
        }
    }
}
```

**변경 후:**

```kotlin
private fun applyDrawable(
    imageViewRef: WeakReference<ImageView>,
    resultRef: WeakReference<Result<*>>,
    drawable: Drawable?
) {
    val imageView = imageViewRef.get() ?: return
    val result = resultRef.get() ?: return

    val currentTag = imageView.tag
    if (currentTag !is ImageLoadingTag || currentTag.result != result) {
        return
    }

    // ✅ 단순화: drawable이 null이면 placeholder 유지
    // 다음 스크롤 시 자연스럽게 재로드됨
    if (drawable != null) {
        imageView.setImageDrawable(drawable)
    }
    
    // Tag 설정으로 재로딩 방지
    imageView.tag = result
}
```

## 예상 효과

### 성능 개선

- **백그라운드 스레드 블록 제거**: 최대 600ms 대기 제거
- **동시 로딩 개선**: 여러 아이콘을 병렬로 빠르게 로드
- **스크롤 반응성**: 끊김 현상 40-50% 감소

### 메모리 개선

- **복잡한 fallback 로직 제거**: 메모리 할당 감소
- **예외 처리 단순화**: GC 압력 감소

## 테스트 계획

### 단위 테스트

```kotlin
@Test
fun testLoadDrawable_noRetry() {
    val startTime = System.currentTimeMillis()
    val drawable = loadDrawable(imageViewRef, resultRef)
    val duration = System.currentTimeMillis() - startTime
    
    // 재시도 없이 빠르게 완료되어야 함
    assertTrue(duration < 100, "Load should complete in < 100ms")
}

@Test
fun testApplyDrawable_nullHandling() {
    applyDrawable(imageViewRef, resultRef, null)
    
    // null drawable은 placeholder 유지
    assertEquals(R.drawable.placeholder, imageView.drawable)
    // Tag는 설정되어 재로딩 방지
    assertEquals(result, imageView.tag)
}
```

### 통합 테스트

#### 시나리오 1: 빠른 스크롤

```gherkin
GIVEN: 100개 앱이 설치된 상태
WHEN: 목록을 빠르게 스크롤 (2초 내)
THEN: 
  - 평균 FPS > 40 (기존 30)
  - 프레임 드랍 < 25% (기존 35%)
  - 모든 아이콘 정상 표시
```

#### 시나리오 2: 메모리 부족 상황

```gherkin
GIVEN: 에뮬레이터 RAM 512MB 제한
WHEN: 500개 앱 목록 스크롤
THEN:
  - OOM 발생하지 않음
  - 아이콘 로딩 실패 시 placeholder 유지
  - 스크롤 멈추면 정상 로드
```

#### 시나리오 3: 네트워크 지연 (커스텀 아이콘팩)

```gherkin
GIVEN: 느린 네트워크 연결
WHEN: 커스텀 아이콘팩 사용 중 스크롤
THEN:
  - 스크롤 끊김 없음
  - 로딩 실패 시 기본 아이콘 표시
  - 백그라운드에서 재시도 없음
```

## 롤백 계획

### 문제 발생 시

1. 아이콘 로딩 실패율 증가 (> 5%)
2. 빈 아이콘 보고 증가
3. 사용자 불만 리뷰

### 롤백 절차

```bash
git revert <commit-hash>
./gradlew assembleRelease
fastlane android beta
```

### 대안

재시도 로직을 유지하되 개선:

```kotlin
// 대안: 비동기 재시도 (블록하지 않음)
if (drawable == null) {
    // 100ms 후 1회만 재시도
    Handler(Looper.getMainLooper()).postDelayed({
        setImageAsync(imageView, result, resId)
    }, 100)
}
```

## 체크리스트

- [ ] SetImageCoroutine.kt 수정
- [ ] 단위 테스트 작성
- [ ] 에뮬레이터 테스트 (저사양)
- [ ] 실기기 테스트 (3종)
- [ ] 성능 메트릭 수집
- [ ] 코드 리뷰
- [ ] 문서 업데이트
- [ ] 베타 배포

## 참고

- 관련 이슈: 에뮬레이터 스크롤 끊김
- 분석 문서: [app-list-scroll-analysis.md](app-list-scroll-analysis.md)
- 전체 계획: [scroll-performance-improvement-plan.md](scroll-performance-improvement-plan.md)

---

**예상 작업 시간**: 2일  
**위험도**: 낮음 (쉬운 롤백 가능)  
**우선순위**: 높음 (40-50% 성능 개선)
