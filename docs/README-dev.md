# KISS Launcher - 개발자 가이드 (v4.2.6)

> 한국화된 KISS Launcher 개발을 위한 종합 개발 가이드입니다. 이 문서는 프로젝트 구조, 핵심 아키텍처 패턴, 개발 워크플로우, 성능 최적화 방법을 포함합니다.

**현재 버전:** 4.2.6 (v4.1.7 코드 정리 완료)  
**대상 SDK:** 35 (Android 15)  
**최소 SDK:** 33 (Android 13)  
**주요 특징:** Shizuku 통합, Kotlin Coroutines 기반 비동기 처리, 성능 프로파일링, 한국화

---

## 📑 목차

1. [프로젝트 개요](#프로젝트-개요)
2. [빠른 시작](#빠른-시작)
3. [핵심 아키텍처](#핵심-아키텍처)
4. [개발 가이드](#개발-가이드)
5. [성능 최적화](#성능-최적화)
6. [문제 해결](#문제-해결)
7. [유용한 리소스](#유용한-리소스)

---

## 프로젝트 개요

### 프로젝트 구조

```
KISS Launcher (kr.lum7671.kiss)
├─ 원본: Neamar/KISS v3.23.0 기반
├─ 한국화: 완전 한글 UI 및 최적화
└─ 최신화: Android 15(SDK 35), Java 17, Kotlin 2.0.21
```

### 주요 특징

- **Shizuku 통합**: 루트 권한 없이 앱 동작 중지 및 시스템 기능 제어
- **성능 최적화**: 스크롤 성능 40-50% 개선, 아이콘 로딩 최적화
- **Kotlin Coroutines**: 모든 비동기 작업을 Coroutines으로 통합 (AsyncTask 제거 완료)
- **Code Cleanup v4.1.7**: Detekt 73% 감소, APK 크기 10% 축소
- **한국화**: CJK 텍스트 처리 최적화, 한글 입력기 지원

### 패키지 조직

```
app/src/main/java/fr/neamar/kiss/
├─ dataprovider/        # 데이터 로드 프로바이더 (AppProvider, ContactsProvider 등)
├─ pojo/                # 데이터 객체 (AppPojo, ContactsPojo 등)
├─ searcher/            # 검색 연산 (Kotlin Coroutines 기반)
├─ loader/              # 비동기 데이터 로딩 (Coroutines)
├─ result/              # UI 렌더링 및 상호작용 처리
├─ adapter/             # ListView 어댑터
├─ db/                  # 데이터베이스 레이어
├─ MainActivity.java    # ⭐ 중앙 Activity
├─ DataHandler.java     # 프로바이더 레지스트리 및 검색 조정
├─ KissApplication.java # 글로벌 상태 보유
├─ ShizukuHandler.java  # Shizuku API 통합
└─ utils/               # CoroutineUtils, StringNormalizer 등
```

---

## 빠른 시작

### 환경 설정

#### 필수 요구사항

- **Java:** OpenJDK 17 LTS
  ```bash
  # macOS (Homebrew)
  brew install openjdk@17
  ```
- **Android SDK:** API 35 (Android 15)
- **Android Studio:** 2025.1.2+
- **Gradle:** 8.13 (프로젝트에 포함됨)

#### 초기 설정

```bash
# 1. 프로젝트 클론
git clone https://github.com/lum7671/KISS.git
cd KISS

# 2. 로컬 속성 설정 (필요시)
echo "sdk.dir=/path/to/android/sdk" > local.properties

# 3. Gradle 동기화 (Android Studio 자동 실행 또는)
./gradlew sync
```

### 빌드 방법

#### Debug 빌드 (개발/테스팅)

```bash
# 빌드
./gradlew assembleDebug

# 설치 및 실행
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n kr.lum7671.kiss/.MainActivity

# 또는 한 번에
./gradlew installDebug
```

**특징:**
- 빠른 빌드 (≈3초)
- 전체 로깅 활성화
- LeakCanary 메모리 누수 감지 포함
- Detekt/Lint 검사

#### Release 빌드 (배포)

```bash
# APK 서명과 함께 빌드
./gradlew assembleRelease

# 결과: app/build/outputs/apk/release/app-release.apk
adb install -r app/build/outputs/apk/release/app-release.apk
```

**특징:**
- R8 최소화 적용
- ProGuard 규칙 적용
- APK 크기: ≈1.2MB
- Android 15 호환성 검증

#### Profile 빌드 (성능 분석)

```bash
# 프로파일링 활성화된 빌드
./gradlew assembleProfile

# 디바이스에 설치
adb install -r app/build/outputs/apk/profile/app-profile.apk
```

**특징:**
- 성능 로깅 활성화
- Debuggable 플래그 포함
- ProfileManager 및 ActionPerformanceTracker 활성화

### 테스트 및 분석

#### 정적 분석 실행

```bash
# Detekt - Kotlin 코드 품질 검사
./gradlew detekt

# Android Lint - Android 특화 검사
./gradlew lint

# Error Prone - 정적 분석 (자동 수정 포함)
./gradlew build  # 빌드 시 포함됨

# 모든 분석 실행
./gradlew check
```

#### 유닛 테스트

```bash
# 테스트 실행
./gradlew testDebugUnitTest

# 테스트 결과 보기
open build/reports/tests/testDebugUnitTest/index.html
```

#### 성능 프로파일링

1. **Profile APK 설치**
   ```bash
   ./gradlew assembleProfile
   adb install -r app/build/outputs/apk/profile/app-profile.apk
   ```

2. **사용 패턴 수집** - 디바이스에서 1일 사용

3. **로그 수집**
   ```bash
   adb pull /storage/emulated/0/Android/data/kr.lum7671.kiss/files/kiss_profile_logs/
   ```

4. **분석** - ProfileManager 및 ActionPerformanceTracker 로그 검토

---

## 핵심 아키텍처

### 1. Provider-POJO-Result 패턴

KISS의 모든 데이터 흐름은 이 패턴을 따릅니다:

```
┌──────────────────────────────────────────────────────────┐
│                   데이터 로딩 흐름                        │
└──────────────────────────────────────────────────────────┘

LoadPojosCoroutine<T>        (백그라운드 로딩, WeakReference)
         │ executeAsync()
         ↓
    doInBackground()          (워커 스레드)
         │
         ↓
Provider.loadPojosOver()      (POJO 리스트 반환)
         │
         ↓
POJO<T>                       (데이터 객체: AppPojo, ContactsPojo...)
         │
         ↓
Result<T>                     (UI 렌더링 및 상호작용)
         │
         ↓
RecordAdapter                 (ListVie 어댑터)
         │
         ↓
MainActivity UI               (사용자에게 표시)
```

#### 주요 컴포넌트

**Provider** - 데이터 로드
- `AppProvider` - 설치된 앱
- `ContactsProvider` - 연락처
- `ShortcutsProvider` - 바로가기
- `HistoryProvider` - 검색 기록

**POJO** - 데이터 표현
```kotlin
interface Pojo {
    fun getName(context: Context): String
    fun getIcon(context: Context): Drawable?
    fun launch(context: Context, forTarget: String?)
}

// 구현체: AppPojo, ContactsPojo, ShortcutPojo
```

**Result** - UI 처리
```java
abstract class Result<T : Pojo> {
    fun getIcon(context: Context): Drawable? { }
    fun getDescription(context: Context): String? { }
    fun launch(context: Context, launchAction: String) { }
}
```

### 2. Kotlin Coroutines 아키텍처

모든 비동기 작업은 **Kotlin Coroutines**을 사용합니다 (AsyncTask 제거 완료).

#### 기본 패턴

```kotlin
// 1. 백그라운드 작업 + UI 업데이트
CoroutineUtils.runAsync(
    background = AsyncRunnable {
        // 백그라운드 스레드에서 실행
        loadDataFromDatabase()
    },
    callback = AsyncRunnable {
        // UI 스레드에서 실행
        updateUI()
    }
)

// 2. 결과 반환이 필요한 경우
CoroutineUtils.runAsyncWithResult(
    background = AsyncCallable<List<AppPojo>> {
        provider.loadApps()  // 결과 반환
    },
    callback = AsyncCallback<List<AppPojo>> { result ->
        adapter.setItems(result)  // UI 업데이트
    }
)

// 3. WeakReference를 통한 메모리 안전
CoroutineUtils.runAsyncWithWeakReference(
    target = this,  // Activity 또는 Fragment
    background = { context -> 
        context?.let { loadData(it) }
    },
    callback = { result ->
        updateUI(result)
    }
)
```

#### LoadPojosCoroutine 구현

```kotlin
// 기본 클래스
abstract class LoadPojosCoroutine<T : Pojo>(
    context: Context, 
    protected val pojoScheme: String
) {
    protected val contextRef = WeakReference(context)
    
    @WorkerThread
    protected abstract fun doInBackground(): List<T>
    
    fun executeAsync(): Job = CoroutineUtils.runAsyncWithResult(
        background = AsyncCallable { doInBackground() },
        callback = AsyncCallback { results ->
            provider.loadPojosOver(results)
            MainActivityMessaging.sendFullLoadOver()
        }
    )
}

// 구현 예: AppProvider 로딩
class LoadAppPojosCoroutine(context: Context) : LoadPojosCoroutine<AppPojo>(...) {
    override fun doInBackground(): List<AppPojo> {
        return packageManager.getInstalledApplications(0)
            .map { AppPojo(it) }
            .sortedBy { it.name }
    }
}
```

#### CoroutineUtils 유틸리티

```kotlin
object CoroutineUtils {
    // 1. 단순 백그라운드 실행
    fun execute(background: Runnable) { }
    
    // 2. 백그라운드 + UI 콜백
    fun runAsync(background: AsyncRunnable, callback: AsyncRunnable? = null) { }
    
    // 3. 결과 반환
    fun <T> runAsyncWithResult(
        background: AsyncCallable<T>,
        callback: AsyncCallback<T>
    ): Job { }
    
    // 4. 메모리 안전 (WeakReference)
    fun <T, R> runAsyncWithWeakReference(
        target: T,
        background: suspend (T?) -> R,
        callback: (R) -> Unit
    ): Job { }
}

// 디스패처 설정
val ioDispatcher = Dispatchers.IO.limitedParallelism(1)  // 순차 실행
```

### 3. 검색 아키텍처

검색은 8개의 `SearcherCoroutine` 클래스로 처리됩니다.

```
SearcherCoroutine (기본 클래스)
├─ QuerySearcherCoroutine      ⭐ 메인 검색
├─ HistorySearcherCoroutine    # 검색 기록
├─ ApplicationsSearcherCoroutine # 앱 필터링
├─ PojoWithTagSearcherCoroutine # 태그 기반 (추상)
├─ TagsSearcherCoroutine        # 태그 검색
├─ UntaggedSearcherCoroutine    # 태그 없음
└─ NullSearcherCoroutine        # 빈 상태
```

**QuerySearcherCoroutine** - 가장 중요한 검색 엔진

```kotlin
class QuerySearcherCoroutine(
    context: Context,
    private val query: String
) : SearcherCoroutine(context) {
    
    override fun doInBackground(): List<Result<*>> {
        // 1. 모든 프로바이더에서 데이터 조회
        val allResults = mutableListOf<Result<*>>()
        DataHandler.providers.forEach { provider ->
            allResults.addAll(provider.search(query))
        }
        
        // 2. Fuzzy 매칭으로 순위 지정
        return allResults
            .sortedByDescending { FuzzyScore.score(it.name, query) }
            .take(MAX_RESULTS)
    }
}
```

**FuzzyScore** - 검색 매칭

```kotlin
object FuzzyScore {
    fun score(text: String, query: String): Int {
        // 한글 최적화 포함
        // 완전 매칭 > 시작 매칭 > 부분 매칭 순서
    }
}
```

### 4. 메모리 관리

#### WeakReference 패턴

```kotlin
// Context 안전성 보장
protected val contextRef = WeakReference(context)

fun doWork() {
    val context = contextRef.get()
    context?.let { 
        // Context가 존재할 때만 실행
        loadData(it)
    }
}
```

#### Job 취소 및 정리

```kotlin
class LoadAppPojosCoroutine : LoadPojosCoroutine<AppPojo> {
    private var job: Job? = null
    
    fun executeAsync(): Job {
        job = CoroutineUtils.runAsyncWithResult(...)
        return job!!
    }
    
    fun cancel() {
        job?.cancel()
    }
}

// MainActivity에서
override fun onDestroy() {
    DataHandler.providers.forEach { it.onDestroy() }
    ShizukuHandler.destroy()
    super.onDestroy()
}
```

#### Shizuku 리소스 정리

```java
public class ShizukuHandler {
    public void destroy() {
        // Shizuku 리스너 제거
        Shizuku.removeRequestPermissionResultListener(...)
        Shizuku.addBinderReceivedListener(null)
    }
}
```

### 5. Shizuku & Root 통합

#### Shizuku를 통한 루트 없는 권한 상승

```java
public class ShizukuHandler {
    
    // Shizuku 준비 확인
    public boolean isShizukuReady() {
        try {
            return Shizuku.pingBinder();
        } catch (Exception e) {
            return false;
        }
    }
    
    // 앱 동작 중지 (Shizuku)
    public String hibernateAppWithReason(String packageName) {
        if (!isShizukuReady()) return "Shizuku not available";
        
        try {
            // Reflection을 통한 IActivityManager 접근
            IActivityManager am = Stub.asInterface(
                SystemServiceManager.getService("activity")
            );
            am.forceStopPackage(packageName, USER_ID);
            return null;  // 성공
        } catch (Exception e) {
            return e.getMessage();
        }
    }
}
```

#### 중요: AIDL 통합 패턴

```java
// ❌ 절대 금지
import android.app.IActivityManager;  // 직접 import 금지

// ✅ 올바른 방법
// Reflection 사용
Class<?> iActivityManagerClass = Class.forName("android.app.IActivityManager");
Method asInterfaceMethod = iActivityManagerClass
    .getMethod("Stub.asInterface", IBinder.class);
Object am = asInterfaceMethod.invoke(null, binder);
```

#### Fallback 구조

```
시스템 함수 요청
    ↓
┌─────────────────────┐
│ Shizuku 사용 가능?  │
└─────────────────────┘
    ↙ 예           아래 ↘
[ShizukuHandler]    [RootHandler]
    ↓                   ↓
(안전한 방식)        (Root 필요)
    └─────────────────┬──────────────┘
                      ↓
                    결과 반환
```

---

## 개발 가이드

### 1. 새 Provider 추가하기

예: 설치된 폰트 목록을 표시하는 `FontProvider` 추가

**Step 1: POJO 클래스 생성**

```kotlin
// app/src/main/java/fr/neamar/kiss/pojo/FontPojo.kt
data class FontPojo(
    val fontName: String,
    val fontPath: String
) : Pojo {
    override fun getName(context: Context): String = fontName
    
    override fun getIcon(context: Context): Drawable? = 
        // 폰트 미리보기 아이콘 반환
        null
    
    override fun launch(context: Context, forTarget: String?) {
        // 폰트 선택 시 동작 (Intent 실행 등)
    }
}
```

**Step 2: Provider 클래스 생성**

```kotlin
// app/src/main/java/fr/neamar/kiss/dataprovider/FontProvider.kt
class FontProvider(context: Context) : Provider<FontPojo>(context) {
    
    override fun load(results: MutableList<FontPojo>) {
        val fontDir = File("/system/fonts")
        fontDir.listFiles()?.forEach { fontFile ->
            results.add(FontPojo(fontFile.name, fontFile.path))
        }
    }
    
    override fun onDestroy() {
        // 정리 작업
    }
}
```

**Step 3: LoadPojosCoroutine 클래스 생성**

```kotlin
// app/src/main/java/fr/neamar/kiss/loader/LoadFontPojosCoroutine.kt
class LoadFontPojosCoroutine(context: Context) : 
    LoadPojosCoroutine<FontPojo>(context, "font") {
    
    override fun doInBackground(): List<FontPojo> {
        val provider = FontProvider(contextRef.get() ?: return emptyList())
        val results = mutableListOf<FontPojo>()
        provider.load(results)
        return results
    }
}
```

**Step 4: Result 클래스 생성**

```kotlin
// app/src/main/java/fr/neamar/kiss/result/FontResult.kt
class FontResult(val pojo: FontPojo) : Result<FontPojo>(pojo) {
    override fun getIcon(context: Context): Drawable? = null
    override fun getDescription(context: Context): String = pojo.fontPath
    override fun launch(context: Context, launchAction: String) {
        pojo.launch(context, launchAction)
    }
}
```

**Step 5: DataHandler에 등록**

```java
// DataHandler.java의 initializeProviders() 메서드에 추가
providers.add(new FontProvider(context));
```

**Step 6: 로드 및 검색 통합**

```java
// DataHandler의 startLoader 메서드에서
LoadFontPojosCoroutine fontLoader = new LoadFontPojosCoroutine(context);
fontLoader.executeAsync();
```

### 2. 검색 기능 확장하기

**예: 폰트 검색 추가**

```kotlin
// app/src/main/java/fr/neamar/kiss/searcher/FontSearcherCoroutine.kt
class FontSearcherCoroutine(
    context: Context,
    private val query: String
) : SearcherCoroutine(context) {
    
    override fun doInBackground(): List<Result<*>> {
        val results = mutableListOf<Result<*>>()
        
        // FontProvider에서 데이터 가져오기
        val fontProvider = DataHandler.getProvider("font")
        fontProvider?.let { provider ->
            val allFonts = provider.getAllFonts()  // 임의의 메서드
            
            // 쿼리와 매칭되는 폰트 필터링
            allFonts
                .filter { FuzzyScore.score(it.name, query) > 0 }
                .sortedByDescending { FuzzyScore.score(it.name, query) }
                .forEach { font ->
                    results.add(FontResult(font))
                }
        }
        
        return results
    }
}
```

### 3. UI 컴포넌트 수정하기

**예: 리스트 항목 스타일 변경**

**RecordAdapter 수정**

```java
// app/src/main/java/fr/neamar/kiss/adapter/RecordAdapter.java
public class RecordAdapter extends ArrayAdapter<Result<?>> {
    
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Result<?> record = getItem(position);
        
        // 폰트 결과 특별 처리
        if (record instanceof FontResult) {
            // 커스텀 레이아웃 적용
            return createFontView((FontResult) record, parent);
        }
        
        // 기본 처리
        return super.getView(position, convertView, parent);
    }
    
    private View createFontView(FontResult record, ViewGroup parent) {
        // 폰트 미리보기를 보여주는 커스텀 뷰
        return inflater.inflate(R.layout.record_font, parent, false);
    }
}
```

### 4. Shizuku 기능 추가하기

**예: 앱 동작 중지 버튼 추가**

```java
// AppResult.java에 메서드 추가
public void hibernateApp(Context context) {
    ShizukuHandler handler = KissApplication.getInstance().getShizukuHandler();
    
    if (!handler.isShizukuReady()) {
        Toast.makeText(context, "Shizuku가 준비되지 않았습니다", 
                      Toast.LENGTH_SHORT).show();
        return;
    }
    
    new Thread(() -> {
        String result = handler.hibernateAppWithReason(pojo.getComponentName().getPackageName());
        if (result == null) {
            // 성공
            Toast.makeText(context, "앱이 동작 중지되었습니다", Toast.LENGTH_SHORT).show();
        } else {
            // 실패
            Toast.makeText(context, "오류: " + result, Toast.LENGTH_SHORT).show();
        }
    }).start();
}
```

---

## 성능 최적화

### 1. Profile 빌드를 통한 성능 분석

#### 프로파일링 프로세스

**Step 1: Profile APK 빌드 및 설치**

```bash
./gradlew assembleProfile
adb install -r app/build/outputs/apk/profile/app-profile.apk
```

**Step 2: 일반적인 사용 시뮬레이션 (1일)**

- 앱 검색 50회
- 앱 실행 20회
- 설정 변경 5회
- 일반 스크롤 테스트

**Step 3: 로그 수집**

```bash
adb pull /storage/emulated/0/Android/data/kr.lum7671.kiss/files/kiss_profile_logs/
```

**Step 4: 분석**

- `ProfileManager`의 로그 검토
- `ActionPerformanceTracker`의 타이밍 데이터 분석
- 병목 지점 식별

### 2. 스크롤 성능 최적화 (이미 적용됨)

현재 구현된 최적화:

- **아이콘 로딩 지연** - 스크롤 중 이미콘 로드 지연 (40-50% 성능 개선)
- **ListView 뷰 재사용** - convertView 재사용으로 메모리 압박 감소
- **배경 작업 제한** - 스크롤 중 백그라운드 작업 일시 중지
- **3단계 아이콘 캐시** - 메모리/디스크/네트워크 계층식 캐싱

[상세 가이드](./scroll-performance-improvement-summary.md)

### 3. Detekt 코드 품질 검사

```bash
# Detekt 실행
./gradlew detekt

# 결과 확인
open build/reports/detekt/detekt.html

# 최근 개선: 391 → 106 문제 (73% 감소)
```

**주요 검사 항목:**
- 복잡도 분석
- 명명 규칙
- 성능 문제
- 코드 냄새

### 4. Lint 검사 및 자동 수정

```bash
# Android Lint 실행
./gradlew lint

# 리포트 확인
open app/build/reports/lint-results.html

# Lint 기준선 업데이트 (필요시)
./gradlew updateLintBaseline
```

### 5. 메모리 누수 감지 (Debug 빌드)

Debug 빌드에 LeakCanary가 포함되어 있습니다:

```kotlin
// debugImplementation 'com.squareup.leakcanary:leakcanary-android:2.14'
// 자동 감지 및 알림 표시
```

---

## 문제 해결

### 문제 1: "Shizuku가 준비되지 않았습니다" 오류

**원인:**
- Shizuku 앱이 설치되지 않음
- Shizuku 권한이 부여되지 않음
- Shizuku 서비스가 실행 중이 아님

**해결:**

1. Shizuku 앱 설치
   ```bash
   # GitHub에서 다운로드
   https://github.com/RikkaApps/Shizuku/releases
   ```

2. Shizuku 권한 부여
   ```bash
   adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh
   ```

3. 앱 재시작

### 문제 2: 메모리 누수 의심

**확인 방법:**

1. **LeakCanary 알림 확인** (Debug 빌드)
   - 알림이 표시되면 상세 정보 확인

2. **WeakReference 패턴 검토**
   ```kotlin
   // ✅ 올바른 패턴
   protected val contextRef = WeakReference(context)
   val context = contextRef.get() ?: return
   
   // ❌ 잘못된 패턴
   protected val context = context  // 강한 참조 (메모리 누수)
   ```

3. **Job 취소 확인**
   ```kotlin
   // ✅ onDestroy에서 Job 취소
   override fun onDestroy() {
       job?.cancel()
       super.onDestroy()
   }
   ```

### 문제 3: 앱 크래시 "java.lang.IllegalStateException: Provider not initialized"

**원인:**
- Provider가 초기화되기 전에 접근

**해결:**

```java
// DataHandler.java
public void startLoad() {
    // 모든 Provider 초기화 완료 후 로드 시작
    for (Provider<?> provider : providers) {
        new LoadPojosCoroutine<>(provider).executeAsync();
    }
}

// MainActivity에서 올바른 순서
@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    DataHandler.initialize(this);  // 먼저 초기화
    DataHandler.startLoad();        // 그 다음 로드
}
```

### 문제 4: "Build error: Detekt found issues"

**해결:**

```bash
# Option 1: Detekt 자동 수정 시도
./gradlew detektFormat

# Option 2: 특정 규칙 비활성화 (detekt.yml)
detekt:
  - # 규칙 추가
    excludeRules:
      - ComplexMethod  # 복잡한 메서드

# Option 3: 재분석
./gradlew detekt --rerun
```

### 문제 5: APK 설치 실패 "Signature mismatch"

**원인:**
- 다른 서명으로 빌드된 APK가 설치되어 있음

**해결:**

```bash
# 기존 앱 제거
adb uninstall kr.lum7671.kiss

# 재설치
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 문제 6: Gradle 동기화 실패

**해결:**

```bash
# 1. Gradle 캐시 정리
./gradlew clean

# 2. 의존성 재다운로드
./gradlew build --refresh-dependencies

# 3. Android Studio 캐시 정리
# Android Studio → Preferences → System Cache → Clear Caches
```

---

## 유용한 리소스

### 📚 필수 개발 문서

| 문서 | 설명 | 용도 |
|------|------|------|
| [AsyncTask 마이그레이션 마스터 플랜](./asynctask-migration-master-plan.md) | Coroutines 마이그레이션 전략 및 체크리스트 | 비동기 작업 구현 |
| [코드 정리 최종 요약](./code-cleanup-summary-2025-10-17.md) | v4.1.7 정리된 아키텍처 개요 | 코드베이스 이해 |
| [리팩토링 가이드](./refactoring-guide.md) | 코드 개선 및 리팩토링 베스트 프랙티스 | 코드 품질 개선 |

### 🎯 기능별 구현 가이드

| 문서 | 설명 | 대상 |
|------|------|------|
| [Shizuku 통합 가이드](./shizuku-guide.md) | 루트 없는 권한상승 및 앱 제어 | Shizuku 기능 개발 |
| [앱 비활성화 가이드](./disabled-app-icon-guide.md) | 비활성 앱 처리 및 UI | 앱 상태 관리 |
| [검색 기록 정렬](./history-sorting-guide.md) | 검색 기록 순위 알고리즘 | 검색 기능 개선 |
| [설정 UI 개선](./newsettings-cleanup-and-improvements.md) | 설정 화면 아키텍처 및 최적화 | 설정 화면 개발 |

### 🚀 성능 및 테스트

| 문서 | 설명 | 용도 |
|------|------|------|
| [스크롤 성능 최적화](./scroll-performance-improvement-summary.md) | ListView 성능 40-50% 개선 결과 | 성능 최적화 참고 |
| [프로파일 빌드 가이드](./profile-build-guide.md) | 성능 분석 및 프로파일링 방법 | 성능 측정 |
| [프로파일 가이드](./profile-guide.md) | Profile APK 사용 가이드 | 디버깅 및 분석 |
| [QA 테스트 스위트](./qa-suite.md) | 종합 테스트 케이스 및 절차 | 배포 전 검증 |
| [테스트 가이드](./testing-guide.md) | 유닛 테스트 및 통합 테스트 | 자동화 테스트 |

### 📖 기술 참고 문서

| 문서 | 설명 | 참고 용도 |
|------|------|----------|
| [Java 17 업그레이드 요약](./JAVA17_UPGRADE_SUMMARY.md) | Java 17 LTS 마이그레이션 내용 | Java 버전 관련 |
| [라이브러리 분석](./library-analysis-summary.md) | 사용 중인 의존성 상태 | 의존성 관리 |
| [라이브러리 업데이트 계획](./library-update-recommendations-v4.2.7.md) | 향후 라이브러리 업그레이드 계획 | 버전 업데이트 |
| [ListView vs RecyclerView 결정](./listview-vs-recyclerview-decision.md) | 아키텍처 선택 근거 | 설계 이해 |
| [Crash Reporting 설정](./crash-reporting-setup.md) | 크래시 리포팅 통합 | 모니터링 구축 |

### 🎁 추가 리소스

- [기부 및 후원](./donate.md) - 프로젝트 지원 방법

### 🌐 외부 리소스

- **Android Developers:** https://developer.android.com/
- **Kotlin Coroutines:** https://kotlinlang.org/docs/coroutines-overview.html
- **Shizuku API:** https://github.com/RikkaApps/Shizuku
- **Android Studio:** https://developer.android.com/studio
- **Kotlin 공식 문서:** https://kotlinlang.org/docs/

### 주요 코드 파일

```
# 가장 먼저 읽을 파일
1. MainActivity.java           - 엔트리 포인트
2. DataHandler.java            - 프로바이더 조정
3. KissApplication.java        - 전역 상태

# 데이터 흐름 이해
4. dataprovider/Provider.java
5. pojo/Pojo.java
6. result/Result.java
7. adapter/RecordAdapter.java

# 검색 및 비동기 처리
8. searcher/SearcherCoroutine.kt
9. loader/LoadPojosCoroutine.kt
10. utils/CoroutineUtils.kt

# Shizuku 통합
11. ShizukuHandler.java
12. RootHandler.java
```

### Git 워크플로우

```bash
# 기본 흐름
git checkout dev               # dev 브랜치에서 시작
git pull origin dev            # 최신 상태 동기화
git checkout -b feature/my-feature  # 기능 브랜치 생성
git commit -am "feat: 설명"    # 커밋
git push origin feature/my-feature  # 푸시
# GitHub에서 Pull Request 생성

# 업스트림 동기화 (필요시)
git remote add upstream https://github.com/Neamar/KISS.git
git pull upstream master       # 원본 저장소에서 가져오기
git rebase dev                 # dev에 기반
```

---

## 핵심 요점 체크리스트

새 기능을 추가할 때 다음을 확인하세요:

- [ ] WeakReference로 Context 참조 (메모리 누수 방지)
- [ ] Coroutines 사용 (AsyncTask 사용 금지)
- [ ] Provider-POJO-Result 패턴 준수
- [ ] onDestroy()에서 Job 취소
- [ ] Shizuku 사용 시 `isShizukuReady()` 확인
- [ ] Detekt/Lint 검사 통과
- [ ] 한국어 문자 처리 (StringNormalizer 사용)
- [ ] 테스트 실행 및 성공 확인

---

**마지막 업데이트:** 2025년 12월 11일  
**현재 버전:** 4.2.6  
**유지보수자:** 개발팀

