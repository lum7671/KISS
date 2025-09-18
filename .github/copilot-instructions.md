# KISS Launcher - AI Coding Agent Instructions

KISS is a customized Android launcher with advanced features like Shizuku integration, hibernation capabilities, and performance optimizations. This is a fork with significant Korean localization and modern Android API support.

## Architecture Overview

### Core Provider-POJO-Result Pattern
- **Providers** (`dataprovider/`) load data asynchronously using Kotlin Coroutines
- **POJOs** (`pojo/`) represent data objects (apps, contacts, shortcuts, etc.)
- **Results** (`result/`) handle UI display and user interactions for each POJO type
- **DataHandler** coordinates all providers and manages the broadcast system

### Provider Loading System
```kotlin
// Providers use Coroutines (not AsyncTask) for background loading
LoadPojosCoroutine<T> -> Provider.loadOver(results) -> LOAD_OVER broadcast -> MainActivity updates UI
```

Key broadcasts: `START_LOAD`, `LOAD_OVER` (individual provider), `FULL_LOAD_OVER` (all providers ready)

### Search Architecture
- **Searcher** classes (`searcher/`) handle different search modes (query, history, tags)
- **FuzzyScore** performs fuzzy matching with highlighting
- **RecordAdapter** displays heterogeneous result types in a single ListView

## Critical Patterns

### Kotlin Coroutines Migration
This codebase has migrated from AsyncTask to Kotlin Coroutines. Always use:
```kotlin
class LoadAppPojosCoroutine(context: Context) : LoadPojosCoroutine<AppPojo>(context, "app://") {
    @WorkerThread
    override fun doInBackground(): List<AppPojo> { /* background work */ }
}
```

### Shizuku & Root Integration
- **ShizukuHandler**: Modern privilege escalation using Shizuku API for app hibernation
- **RootHandler**: Wrapper supporting both root and Shizuku methods
- App hibernation priority: Shizuku (safer) → Root (fallback)

### Memory Management
- Use `WeakReference` for Context in background tasks
- Providers clean up via `Provider.onDestroy()` and `removeShizukuListeners()`
- Job cancellation in `cancelInitialize()` prevents memory leaks

## Build System

### Gradle Configuration
- Target SDK 35, Min SDK 33 (Android 13+)
- Kotlin 2.0.21 with Java 17 compatibility
- Three build types: `debug`, `release`, `profile` (for performance analysis)
- Uses Error Prone for static analysis

### Key Dependencies
- Kotlin Coroutines (replacing AsyncTask patterns)
- Coil (image loading, replaced Glide)
- Shizuku (privilege escalation)
- Amplitude (analytics/performance tracking)

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