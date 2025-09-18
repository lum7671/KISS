# KISS Launcher - AI Coding Agent Instructions

KISS is a customized Android launcher with advanced features like Shizuku integration, hibernation capabilities, and performance optimizations. This is a Korean-localized fork (v4.1.7) with modern Android API support and recent code cleanup optimizations.

## Architecture Overview

### Core Provider-POJO-Result Pattern
- **Providers** (`dataprovider/`) load data asynchronously using Kotlin Coroutines
- **POJOs** (`pojo/`) represent data objects (apps, contacts, shortcuts, etc.)
- **Results** (`result/`) handle UI display and user interactions for each POJO type
- **DataHandler** coordinates all providers and manages the broadcast system

### Provider Loading System
```kotlin
// Background loading with Coroutines and memory-safe references
LoadPojosCoroutine<T> -> executeAsync() -> doInBackground() -> Provider.loadOver(results) -> LOAD_OVER broadcast -> MainActivity updates UI
```

Key broadcasts: `START_LOAD`, `LOAD_OVER` (individual provider), `FULL_LOAD_OVER` (all providers ready)

### Search Architecture
- **Searcher** classes (`searcher/`) handle different search modes (query, history, tags)
- **FuzzyScore** performs fuzzy matching with highlighting
- **RecordAdapter** displays heterogeneous result types in a single ListView

## Critical Patterns

### Kotlin Coroutines Implementation
Complete migration from AsyncTask to Coroutines using `LoadPojosCoroutine<T>`:
```kotlin
abstract class LoadPojosCoroutine<T : Pojo>(context: Context, protected val pojoScheme: String) {
    protected val contextRef = WeakReference(context)
    
    @WorkerThread
    protected abstract fun doInBackground(): List<T>
    
    fun executeAsync(): Job = CoroutineUtils.runAsyncWithResult(...)
}
```

### CoroutineUtils Patterns
For simple background tasks, use these established patterns:
```kotlin
// Simple background execution
CoroutineUtils.execute(background: Runnable)

// Background + UI callback
CoroutineUtils.runAsync(background: AsyncRunnable, callback: AsyncRunnable?)

// With result return
CoroutineUtils.runAsyncWithResult<T>(background: AsyncCallable<T>, callback: AsyncCallback<T>)

// WeakReference pattern for UI components
CoroutineUtils.runAsyncWithWeakReference<T, R>(target, background, callback)
```

### Shizuku & Root Integration
- **ShizukuHandler**: Modern privilege escalation using Shizuku API for app hibernation
- **RootHandler**: Wrapper supporting both root and Shizuku methods
- App hibernation priority: Shizuku (safer) → Root (fallback)
- **AIDL Integration**: Never import AIDL interfaces directly (`import android.app.IActivityManager`), use reflection with Stub.asInterface
- **Critical Pattern**: Always check `isShizukuReady()` before attempting system service access

### Memory Management
- Use `WeakReference` for Context in background tasks: `WeakReference(context)`
- Providers clean up via `Provider.onDestroy()` and `removeShizukuListeners()`
- Job cancellation in `cancelInitialize()` prevents memory leaks

## Build System (v4.1.7)

### Gradle Configuration
- **Current**: Target SDK 35, Min SDK 33 (Android 13+), Version 4.1.7
- **Kotlin**: 2.0.21 with Java 17 compatibility (`-Xjvm-default=all`)
- **Build Types**: `debug`, `release`, `profile` (for performance analysis with `debuggable=true`)
- **Static Analysis**: Error Prone 2.42.0, Detekt for code quality

### Key Dependencies (Post-Cleanup)
- **Coroutines**: kotlinx-coroutines-android 1.10.2 (replacing AsyncTask patterns)
- **Image Loading**: Coil 2.7.0 (replaced Glide, Kotlin-first and lightweight)
- **Privilege Escalation**: Shizuku API 13.1.5
- **Analytics**: Amplitude 2.40.3 for performance tracking
- **Memory Debugging**: LeakCanary 2.14 (debug only)

### Removed Dependencies (Code Cleanup)
- Legacy benchmark, startup, profileinstaller libraries
- Flipper debugging tools (replaced with Chrome DevTools + Android Studio Profiler)
- Glide image loading (replaced with Coil)

## Development Workflows

### Building & Testing
```bash
# Build scripts in scripts/ directory
./scripts/build_release_apk.sh
./scripts/build_profile_apk.sh
./scripts/install_and_test.sh

# Fastlane deployment
fastlane android beta  # Upload to beta
fastlane android prod  # Production release
```

### Profile Build for Performance Analysis
- **Profile APK**: Includes debuggable flag and performance tracking
- **Custom Signing**: Use `apksigner` (not `jarsigner`) for Android 13+ compatibility
- **Performance Logging**: ProfileManager and ActionPerformanceTracker enabled
- **Log Location**: `/storage/emulated/0/Android/data/.../files/kiss_profile_logs/`

### Performance Optimization
- Use `ProfileManager` for performance tracking
- `BuildConfig.BUILD_TYPE` switches for debug/profile features
- Amplitude events track provider loading times and user patterns

## Project-Specific Conventions

### Package Structure
- `fr.neamar.kiss` namespace (historical) 
- Application ID: `kr.lum7671.kiss` (Korean fork identifier)
- Each provider lives in `dataprovider/` with corresponding POJO and Result classes

### Korean Localization Features
- Enhanced CJK text processing in `StringNormalizer`
- Korean-specific fuzzy matching optimizations
- Localized error messages for Shizuku/root operations

### Android 15+ Compatibility
- Edge-to-edge display support with transparent status bars
- Automatic settings UI adjustment for policy changes
- Enhanced security with Context.RECEIVER_EXPORTED flags

## Critical Files to Understand

- `MainActivity.java`: Central activity managing UI and provider coordination
- `DataHandler.java`: Provider registry and search coordination
- `KissApplication.java`: Singleton holding global state (DataHandler, RootHandler)
- `LoadPojosCoroutine.kt`: Base class for all background data loading
- `ShizukuHandler.java`: Modern privilege escalation system
- `ForwarderManager.java`: UI event delegation system

## Integration Points

### Adding New Providers
1. Extend `Provider<YourPojo>` in `dataprovider/`
2. Create corresponding `YourPojo` class implementing `Pojo`
3. Create `YourResult` extending `Result<YourPojo>`
4. Register in `DataHandler.providers` map
5. Add factory method in `Result.fromPojo()`

### Working with System Services
Always check Shizuku availability before attempting privilege operations:
```java
if (shizukuHandler.isShizukuReady()) {
    String result = shizukuHandler.hibernateAppWithReason(packageName);
    if (result == null) { /* success */ }
}
```

## Testing & Debugging

- Use Android emulator scripts in `scripts/`
- Profile builds enable detailed performance tracking
- Amplitude events provide production telemetry
- `git` branch strategy: `dev` for features, upstream merges via `cleanup/` branches

Focus on the Provider-POJO-Result data flow and Coroutines-based async patterns when making changes. The codebase prioritizes performance and memory efficiency while maintaining backwards compatibility with the KISS launcher ecosystem.

## Recent v4.1.7 Code Cleanup

The v4.1.7 release (2025-09-17) included major code cleanup and modernization:
- **Dead Code Removal**: Eliminated legacy Java files, experimental Controller/Repository/Action systems, unused methods
- **Dependency Cleanup**: Removed 8 unused libraries, modernized image loading (Glide → Coil)
- **Performance Optimization**: Reduced APK size, build time, and memory usage through static analysis
- **Stability Improvements**: All core features preserved (Coroutines, Shizuku, Performance Profiler) while simplifying codebase