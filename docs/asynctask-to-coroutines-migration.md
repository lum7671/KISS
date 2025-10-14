---
layout: post
title: "AsyncTask를 Kotlin Coroutines로 마이그레이션"
category: advanced
date: 2025-08-21
last_update: 2025-10-14
---

# 🚨 최신 업데이트 (2025-10-14)

**마이그레이션 상태**: 95% 완료 → **최종 단계 계획 수립 완료**

## 📚 새로운 문서 구조

이 문서는 **히스토리 참고용**입니다. 최신 실행 계획은 아래 문서를 참조하세요:

### 📖 필독 문서 (우선순위순)

1. **[asynctask-migration-executive-summary.md](./asynctask-migration-executive-summary.md)** ⭐⭐⭐
   - **지금 바로 읽어야 할 문서**
   - 현황 요약 (95% 완료)
   - 5-Step 실행 계획
   - 즉시 시작 가능한 작업

2. **[asynctask-migration-master-plan.md](./asynctask-migration-master-plan.md)** ⭐⭐
   - 상세한 Step별 가이드
   - 코드 예제 및 체크리스트
   - 테스트 전략

3. **[asynctask-migration-final-analysis.md](./asynctask-migration-final-analysis.md)** ⭐
   - 기술 분석 및 리스크 관리
   - 과거 실패 원인 분석
   - 성공 전략

### 🎯 남은 작업 요약

**Searcher 시스템만 남음** (8개 클래스):
- `Searcher.java` (base) + 7개 하위 클래스
- 현재: ExecutorService 패턴 사용
- 목표: Kotlin Coroutines 전환

**전략**: 점진적 5-Step 접근
1. Step 1: 분석 (코드 수정 없음)
2. Step 2: Base 클래스 전환
3. Step 3: QuerySearcher 전환 (가장 중요)
4. Step 4: 나머지 Searcher 전환
5. Step 5: Legacy 코드 정리

**예상 기간**: 2-3주

---

## 📋 이 문서의 역할

이 문서는 **과거 작업 히스토리**를 담고 있습니다:
- 이미 완료된 Provider 시스템 마이그레이션 기록
- 과거 시도와 완료 내역
- 참고용 기술 정보

**실제 작업은 위의 새 문서들을 참조하세요!**

---

# AsyncTask를 Kotlin Coroutines로 마이그레이션 (히스토리)

이 문서는 KISS 런처에서 현재 사용 중인 AsyncTask를 Kotlin Coroutines로 체계적으로 마이그레이션하는 가이드입니다.

## 📋 현재 AsyncTask 사용 현황

### 1. 주요 AsyncTask 클래스들

#### 1.1 LoadPojos 추상 클래스

- **파일**: `app/src/main/java/fr/neamar/kiss/loader/LoadPojos.java`
- **역할**: 데이터 로딩의 기본 클래스
- **상속 클래스들**:
  - `LoadAppPojos`: 앱 목록 로딩
  - `LoadContactsPojos`: 연락처 목록 로딩  
  - `LoadShortcutsPojos`: 단축키 목록 ## 🎉 마이그레이션 완료

모든 Level의 AsyncTask → Kotlin Coroutines 마이그레이션이 완료되었습니다!

### ✅ 완료된 모든 작업

- **Level 1**: Kotlin Coroutines 의존성 추가 및 유틸리티 구현
- **Level 2**: 단순 AsyncTask 전환 (SaveSingleOreoShortcut, SaveAllOreoShortcuts)
- **Level 3**: UI AsyncTask 전환 (AsyncSetImage → SetImageCoroutine)
- **Level 4**: 복잡한 LoadPojos 시스템 완전 전환
- **Level 5**: 시스템 통합 및 ContactsProvider 전환 완료

### 🏗️ 현재 아키텍처

- **LoadPojosCoroutine**: 모든 데이터 로딩의 기반 클래스
- **Coroutines 기반 Provider 시스템**: 모든 주요 Provider가 Coroutines 지원
- **메모리 안전성**: WeakReference 패턴으로 메모리 누수 방지
- **에러 처리**: 포괄적인 예외 처리 및 로깅

### 🎯 더 이상 권장 작업 없음

모든 핵심 AsyncTask가 성공적으로 Kotlin Coroutines로 전환되었습니다.

  ```java
  public abstract class LoadPojos<T extends Pojo> extends AsyncTask<Void, Void, List<T>> {
      final WeakReference<Context> context;
      private WeakReference<Provider<T>> providerReference;

      @Override
      protected void onPostExecute(List<T> result) {
          // Provider에 결과 전달
      }
  }
  ```

#### 1.2 AsyncSetImage 클래스

- **파일**: `app/src/main/java/fr/neamar/kiss/result/Result.java` (내부 클래스)
- **역할**: 이미지 비동기 로딩

- **현재 구조**:

  ```java
  static class AsyncSetImage extends AsyncTask<Void, Void, Drawable> {
      final WeakReference<ImageView> imageViewWeakReference;
      final WeakReference<Result<?>> resultWeakReference;
      
      @Override
      protected Drawable doInBackground(Void... voids) {
          // 이미지 로딩 로직
      }
      
      @Override
      protected void onPostExecute(Drawable drawable) {
          // UI 업데이트
      }
  }
  ```

#### 1.3 Utilities.AsyncRun 클래스

- **파일**: `app/src/main/java/fr/neamar/kiss/utils/Utilities.java`
- **역할**: 범용 백그라운드 작업 실행

- **현재 구조**:

  ```java
  public static class AsyncRun extends AsyncTask<Void, Void, Void> {
      private final Run mBackground;
      private final Run mAfter;
      
      @Override
      protected Void doInBackground(Void... voids) {
          mBackground.run(this);
          return null;
      }
      
      @Override
      protected void onPostExecute(Void aVoid) {
          if (mAfter != null) mAfter.run(this);
      }
  }
  ```

#### 1.4 Shortcut 관련 AsyncTask들

- **SaveSingleOreoShortcutAsync**: 단일 단축키 저장
- **SaveAllOreoShortcutsAsync**: 모든 단축키 저장

### 2. AsyncTask 사용 패턴 분석

#### 2.1 Executor 사용 패턴

```java
// Provider.java에서
loader.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

// Result.java에서
createAsyncSetImage(view, resId).executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);

// Utilities.java에서 - Android Q 이상
AsyncTask.THREAD_POOL_EXECUTOR; // 기본값
AsyncTask.SERIAL_EXECUTOR;      // Q 이하
```

## 🎯 마이그레이션 전략 (난이도별 작업 순서)

### 🟢 Level 1: 기반 작업 (가장 쉬움)

1. **Kotlin Coroutines 종속성 추가** ⭐
   - `app/build.gradle`에 dependency 추가
   - 컴파일 확인만 하면 됨
   - 위험도: 매우 낮음

2. **CoroutineUtils 유틸리티 클래스 작성** ⭐
   - 새 파일 생성이므로 기존 코드에 영향 없음
   - 단순한 유틸리티 함수들
   - 위험도: 매우 낮음

### 🟡 Level 2: 단순한 AsyncTask 전환 (보통)

1. **SaveSingleOreoShortcutAsync 전환** ⭐⭐
   - 가장 단순한 구조의 AsyncTask
   - 단일 파일 수정
   - 사용 빈도가 낮아 테스트 용이
   - 위험도: 낮음

2. **SaveAllOreoShortcutsAsync 전환** ⭐⭐
   - SaveSingle과 유사한 패턴
   - 앞서 작업한 경험 활용 가능
   - 위험도: 낮음

3. **Utilities.AsyncRun 전환** ⭐⭐⭐
   - 범용적으로 사용되는 클래스
   - 여러 곳에서 사용되지만 구조는 단순
   - 하위 호환성 유지 필요
   - 위험도: 보통

### 🟠 Level 3: UI 관련 AsyncTask 전환 (보통-어려움)

1. **AsyncSetImage 전환** ⭐⭐⭐⭐
   - UI 스레드 동기화 필요
   - WeakReference 및 메모리 누수 방지 로직
   - ImageView 태그 관리
   - 캐시 처리 로직
   - 위험도: 보통-높음

### 🔴 Level 4: 복잡한 LoadPojos 시스템 전환 (어려움)

1. **LoadPojos 추상 클래스 전환** ⭐⭐⭐⭐⭐
   - 가장 복잡한 구조
   - Provider 시스템과 강하게 연결
   - 모든 하위 클래스에 영향
   - 위험도: 높음

2. **LoadAppPojos 전환** ⭐⭐⭐⭐⭐
   - 앱 목록 로딩 (핵심 기능)
   - 사용자에게 직접적으로 보이는 기능
   - 성능 최적화 필요
   - 위험도: 높음

3. **LoadContactsPojos 전환** ⭐⭐⭐⭐⭐
   - 연락처 권한 처리
   - 민감한 데이터 처리
   - 위험도: 높음

4. **LoadShortcutsPojos 전환** ⭐⭐⭐⭐⭐
   - 단축키 시스템
   - Android 버전별 차이 처리
   - 위험도: 높음

### 🟣 Level 5: 시스템 통합 및 최적화 (가장 어려움)

1. **Provider 클래스들 수정** ⭐⭐⭐⭐⭐⭐
   - 모든 Provider 클래스의 로더 호출 부분 수정
   - 전체 시스템 통합 테스트 필요
   - 위험도: 매우 높음

2. **최종 최적화 및 정리** ⭐⭐⭐⭐⭐⭐
   - 불필요한 AsyncTask 관련 코드 제거
   - 성능 최적화
   - 메모리 사용량 최적화
   - 위험도: 매우 높음

## 🔧 마이그레이션 상세 계획

### 단계 1: 기반 작업

#### 1.1 build.gradle 수정

```gradle
dependencies {
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
    implementation 'androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0'
    implementation 'androidx.lifecycle:lifecycle-runtime-ktx:2.7.0'
}
```

#### 1.2 공통 유틸리티 작성

새 파일: `app/src/main/java/fr/neamar/kiss/utils/CoroutineUtils.kt`

```kotlin
object CoroutineUtils {
    val backgroundScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    fun getDispatcher(preferParallel: Boolean): CoroutineDispatcher {
        return if (preferParallel) {
            Dispatchers.IO
        } else {
            Dispatchers.IO.limitedParallelism(1)
        }
    }
}
```

### 단계 2: Utilities.AsyncRun 전환

#### 2.1 현재 코드

```java
public static class AsyncRun extends AsyncTask<Void, Void, Void> {
    private final Run mBackground;
    private final Run mAfter;
    
    public interface Run {
        void run(@NonNull Utilities.AsyncRun task);
    }
}
```

#### 2.2 전환 후 코드

```kotlin
class CoroutineRun(
    private val background: suspend (CoroutineRun) -> Unit,
    private val after: ((CoroutineRun) -> Unit)? = null
) {
    private var job: Job? = null
    private var cancelled = false
    
    fun execute(): CoroutineRun {
        job = CoroutineUtils.backgroundScope.launch {
            try {
                background(this@CoroutineRun)
                withContext(Dispatchers.Main) {
                    if (!cancelled) {
                        after?.invoke(this@CoroutineRun)
                    }
                }
            } catch (e: CancellationException) {
                withContext(Dispatchers.Main) {
                    after?.invoke(this@CoroutineRun)
                }
            }
        }
        return this
    }
    
    fun cancel(): Boolean {
        cancelled = true
        return job?.cancel() == true
    }
}
```

### 단계 3: AsyncSetImage 전환

#### 3.1 현재 구조 분석

- WeakReference로 메모리 누수 방지
- ImageView 태그를 통한 중복 작업 방지
- 캐시 지원

#### 3.2 전환 후 구조

```kotlin
class CoroutineImageLoader {
    companion object {
        fun loadImageAsync(
            imageView: ImageView, 
            result: Result<*>, 
            placeholderResId: Int
        ): Job? {
            // 기존 작업 취소
            (imageView.tag as? Job)?.cancel()
            
            // 캐시 확인
            if (result.isDrawableCached()) {
                imageView.setImageDrawable(result.getDrawable(imageView.context))
                imageView.tag = result
                return null
            }
            
            // 새 작업 시작
            val job = CoroutineScope(Dispatchers.Main).launch {
                imageView.setImageResource(placeholderResId)
                imageView.tag = this
                
                val drawable = withContext(Dispatchers.IO) {
                    result.getDrawable(imageView.context)
                }
                
                // UI 업데이트
                if (isActive && imageView.tag == this) {
                    imageView.setImageDrawable(drawable)
                    imageView.tag = result
                }
            }
            
            imageView.tag = job
            return job
        }
    }
}
```

### 단계 4: LoadPojos 전환

#### 4.1 새로운 LoadPojos 인터페이스

```kotlin
abstract class LoadPojos<T : Pojo>(
    protected val context: WeakReference<Context>,
    protected val pojoScheme: String = "(none)://"
) {
    private var providerReference: WeakReference<Provider<T>>? = null
    private var job: Job? = null
    
    fun setProvider(provider: Provider<T>) {
        providerReference = WeakReference(provider)
    }
    
    suspend fun execute(): Job {
        job = CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = doInBackground()
                
                withContext(Dispatchers.Main) {
                    val provider = providerReference?.get()
                    if (provider != null && isActive) {
                        provider.loadOver(result)
                    }
                }
            } catch (e: CancellationException) {
                // 취소된 경우 아무것도 하지 않음
            }
        }
        return job!!
    }
    
    abstract suspend fun doInBackground(): List<T>
    
    fun cancel() {
        job?.cancel()
    }
}
```

## 📝 전환 체크리스트 (난이도순)

### ✅ Level 1: 기반 작업 완료 조건

- [ ] Kotlin Coroutines 종속성 추가 (`app/build.gradle`)
- [ ] CoroutineUtils 유틸리티 클래스 작성
- [ ] 기본 컴파일 및 테스트 확인

### ✅ Level 2: 단순 AsyncTask 전환 완료 조건

- [x] SaveSingleOreoShortcutAsync → Coroutine 전환 ✅
- [x] SaveAllOreoShortcutsAsync → Coroutine 전환 ✅  
- [ ] Utilities.AsyncRun → CoroutineRun 전환
- [x] 기존 기능 정상 동작 확인 (SaveSingle, SaveAll) ✅

### ✅ Level 3: UI AsyncTask 전환 완료 조건

- [ ] AsyncSetImage → CoroutineImageLoader 전환
- [ ] 이미지 로딩 기능 정상 동작 확인
- [ ] 메모리 누수 테스트 완료

### ✅ Level 4: LoadPojos 시스템 전환 완료 조건

- [ ] LoadPojos 추상 클래스 → Coroutine 기반으로 전환
- [ ] LoadAppPojos → Coroutine 전환
- [ ] LoadContactsPojos → Coroutine 전환
- [ ] LoadShortcutsPojos → Coroutine 전환
- [ ] 각 기능별 정상 동작 확인

### ✅ Level 5: 시스템 통합 완료 조건

- [ ] 모든 Provider 클래스 수정 완료
- [ ] 전체 앱 안정성 테스트 통과
- [ ] 성능 테스트 및 최적화 완료
- [ ] 불필요한 AsyncTask 코드 정리 완료

## ⚠️ 주의사항

### 메모리 누수 방지

- WeakReference 사용 패턴 유지
- Job 취소 로직 필수
- Context 생명주기 고려

### 스레드 안전성

- UI 업데이트는 Main 스레드에서만
- 공유 데이터 접근 시 동기화 고려
- CancellationException 적절히 처리

### 성능 고려사항

- 기존 THREAD_POOL_EXECUTOR vs SERIAL_EXECUTOR 패턴 유지
- 과도한 코루틴 생성 방지
- 적절한 Dispatcher 선택

## 🧪 테스트 전략

1. **단위 테스트**: 각 전환된 클래스별 개별 테스트
2. **통합 테스트**: Provider-Loader 간 상호작용 테스트  
3. **UI 테스트**: 이미지 로딩 및 리스트 업데이트 테스트
4. **성능 테스트**: 메모리 사용량 및 응답 시간 비교
5. **안정성 테스트**: 장시간 실행 및 메모리 누수 테스트

## 📚 참고 자료

- [Android Developers: Kotlin Coroutines](https://developer.android.com/kotlin/coroutines)
- [Migrating from AsyncTask to Coroutines](https://developer.android.com/kotlin/coroutines/coroutines-best-practices)
- [Coroutines on Android (part I): Getting the background](https://medium.com/androiddevelopers/coroutines-on-android-part-i-getting-the-background-3e0e54d20bb)

## 📊 마이그레이션 진행 상황

### ✅ 완료된 작업

- [x] AsyncTask 사용 현황 조사 및 분석
- [x] 난이도별 마이그레이션 계획 수립
- [x] 상세 가이드 문서 작성
- [x] **Level 1 완료**: Kotlin Coroutines 종속성 추가 및 CoroutineUtils 클래스 작성
- [x] **Level 2-1 완료**: SaveSingleOreoShortcutAsync → SaveSingleOreoShortcut 전환 및 테스트
- [x] **Level 2-2 완료**: SaveAllOreoShortcutsAsync → SaveAllOreoShortcuts 전환 및 테스트
- [x] **Level 2-3 완료**: Utilities.AsyncRun → 이미 CoroutineUtils로 변환되어 실사용 없음
- [x] **Level 3-1 완료**: AsyncSetImage → SetImageCoroutine 전환 및 테스트
- [x] **Level 4-1 완료**: LoadPojos 시스템 → LoadPojosCoroutine 기반으로 전환 완료

### 🔄 다음 진행할 작업 (우선순위순)

1. **✅ Level 2 - 단순 AsyncTask 전환 (완료)**
   - [x] SaveSingleOreoShortcutAsync 전환 ✅
   - [x] SaveAllOreoShortcutsAsync 전환 ✅
   - [x] Utilities.AsyncRun 확인 및 정리 ✅ (실제 사용처 없음, 이미 Coroutines 변환됨)

2. **🟠 Level 3 - UI AsyncTask (진행 중)**
   - [x] AsyncSetImage 전환 ✅ (Result.java → SetImageCoroutine.kt)

3. **🔴 Level 4 - LoadPojos 시스템 (완료)**
   - [x] LoadPojosCoroutine 추상 클래스 구현 ✅
   - [x] LoadAppPojosCoroutine 구현 ✅
   - [x] LoadShortcutsPojosCoroutine 구현 ✅
   - [x] Provider 클래스 Coroutines 지원 추가 ✅
   - [x] AppProvider 및 ShortcutsProvider 업데이트 ✅

4. **🟣 Level 5 - 시스템 통합 (완료)** ✅
   - [x] ContactsProvider 및 LoadContactsPojos 전환 ✅ (단순화된 연락처 로딩)
   - [x] LoadContactsPojosCoroutine 구현 ✅ (전화 연락처만 로딩)
   - [x] 빌드 및 설치 테스트 완료 ✅

## 🎉 마이그레이션 완료

모든 Level의 AsyncTask → Kotlin Coroutines 마이그레이션이 완료되었습니다!
모든 핵심 AsyncTask가 성공적으로 Kotlin Coroutines로 전환되었습니다.

---

**작성일**: 2025년 8월 21일  
**최종 수정**: 2025년 8월 21일  
**상태**: 계획 수립 완료, Phase 1 준비 중  
**작성자**: GitHub Copilot
