# KISS Profile APK 빌드 가이드

## 📋 개요

KISS 런처의 성능 프로파일링용 APK를 빌드하고 설치하는 방법을 설명합니다.

## 🔧 환경 설정

### Android SDK 설정

```bash
# Android SDK 환경변수 설정 (~/.zshrc에 추가)
export ANDROID_HOME=/Users/1001028/Library/Android/sdk
export PATH=$PATH:$ANDROID_HOME/build-tools/36.0.0

# PATH 적용
source ~/.zshrc
```

### 도구 확인

```bash
# SDK 도구들이 제대로 설치되었는지 확인
aapt version
adb devices
```

## 🏗️ 빌드 프로세스

### 1. 프로파일 APK 빌드

```bash
cd /Users/1001028/git/KISS

# 클린 빌드 (권장)
./gradlew clean assembleProfile

# 빌드 결과 확인
ls -lh app/build/outputs/apk/profile/
```

### 2. APK 서명 (중요!)

```bash
# apksigner 사용 (jarsigner보다 안정적)
$ANDROID_HOME/build-tools/36.0.0/apksigner sign \
    --ks ~/.android/debug.keystore \
    --ks-pass pass:android \
    --key-pass pass:android \
    --out app/build/outputs/apk/profile/app-profile-signed.apk \
    app/build/outputs/apk/profile/app-profile-unsigned.apk
```

**⚠️ 주의사항**:

- `jarsigner` 대신 `apksigner` 사용 필수
- Android 13+ 에뮬레이터에서 서명 호환성 문제 해결

## 📱 설치 및 테스트

### Android Studio 에뮬레이터

```bash
# 에뮬레이터 연결 확인
adb devices

# APK 설치
adb install app/build/outputs/apk/profile/app-profile-signed.apk

# 앱 실행
adb shell am start -n fr.neamar.kiss.optimized.profile/fr.neamar.kiss.MainActivity

# 설치 확인
adb shell pm list packages | grep kiss
```

### 실제 디바이스 (Galaxy Note 20 Ultra 등)

1. USB 디버깅 활성화
2. 개발자 모드 활성화
3. 위와 동일한 adb 명령어 사용

## 🔍 프로파일 빌드 특징

### 빌드 타입별 구분

| 빌드 타입 | 패키지명 | 앱 이름 | 용도 |
|-----------|----------|---------|------|
| **Release** | `fr.neamar.kiss.optimized` | "KISS Optimized" | 일반 사용자 |
| **Debug** | `fr.neamar.kiss.optimized.debug` | "KISS Debug v4.0.1" | 개발/테스트 |
| **Profile** | `fr.neamar.kiss.optimized.profile` | "KISS Profile v4.0.1" | 성능 분석 |

### 프로파일 빌드만의 특징

- 📊 **성능 로깅 활성화**: ProfileManager, ActionPerformanceTracker
- 🔍 **빌드 타입 표시**: 설정 → About에서 "🔍 PERFORMANCE PROFILING BUILD" 표시
- 📁 **로그 디렉토리**: `/Android/data/com.hqwisen.kiss.profile/logs/`
- 📈 **50MB 크기**: 프로파일링 라이브러리 포함으로 일반 빌드보다 큼

## 🛠️ 트러블슈팅

### 일반적인 문제와 해결책

#### 1. 네이티브 라이브러리 추출 오류

```text
INSTALL_FAILED_INVALID_APK: Failed to extract native libraries, res=-2
```

**해결**: `app/build.gradle`에 다음 설정 확인

```gradle
packagingOptions {
    doNotStrip "*/x86/*.so"
    doNotStrip "*/x86_64/*.so" 
    doNotStrip "*/arm64-v8a/*.so"
    doNotStrip "*/armeabi-v7a/*.so"
}
```

#### 2. 서명 인증서 오류

```text
INSTALL_PARSE_FAILED_NO_CERTIFICATES: Failed to collect certificates
```

**해결**:

1. 기존 서명 제거: `zip -d app.apk "META-INF/*"`
2. `apksigner` 사용 (jarsigner 대신)

#### 3. 앱 이름이 숫자로 표시

**원인**: resValue 설정 오류
**해결**: `strings.xml`에서 중복된 리소스 제거

### 빌드 설정 확인사항

#### app/build.gradle 주요 설정

```gradle
buildTypes {
    profile {
        initWith debug
        manifestPlaceholders = [appLabel: "@string/app_name_profile"]
        resValue "string", "app_name", "KISS Profile v4.0.1"
        buildConfigField "boolean", "PROFILE_BUILD", "true"
        // 프로파일링 라이브러리 의존성
    }
}
```

## 📝 성능 분석 사용법

### 1. 로그 수집 기간

- **권장**: 1일 정상 사용
- **최소**: 4-6시간 연속 사용

### 2. 분석 대상

- 앱 실행 시간
- 검색 응답 시간  
- UI 렌더링 성능
- 메모리 사용량

### 3. 로그 파일 위치

```bash
# 에뮬레이터에서 로그 추출
adb shell run-as fr.neamar.kiss.optimized.profile ls /data/data/fr.neamar.kiss.optimized.profile/logs/

# 실제 디바이스 (루팅 필요할 수 있음)
adb shell ls /Android/data/fr.neamar.kiss.optimized.profile/logs/
```

## 📋 체크리스트

### 빌드 전 확인사항

- [ ] Android SDK 36.0.0+ 설치
- [ ] 환경변수 PATH 설정
- [ ] 디버그 키스토어 존재 (`~/.android/debug.keystore`)

### 빌드 후 확인사항

- [ ] APK 파일 크기 ~50MB
- [ ] 서명 상태 정상
- [ ] 패키지명 `fr.neamar.kiss.optimized.profile`

### 설치 후 확인사항

- [ ] 앱 이름 "KISS Profile v4.0.1" 표시
- [ ] 설정 → About에서 프로파일 빌드 정보 표시
- [ ] 검색, 연락처 등 기본 기능 정상 작동

## 🔄 버전 관리

### 버전 업데이트 시

1. `app/build.gradle`에서 `versionName`, `versionCode` 수정
2. `strings.xml`에서 앱 이름 업데이트
3. `README.md` 변경사항 기록
4. 새 프로파일 APK 빌드

---

## 📚 참고 문서

- [Android APK 서명 가이드](https://developer.android.com/studio/publish/app-signing)
- [Gradle 빌드 설정](https://developer.android.com/studio/build)
- [ADB 명령어 참조](https://developer.android.com/studio/command-line/adb)

---

*작성일: 2025년 8월 13일*  
*최종 업데이트: v4.0.1 프로파일 빌드*
