# Fastlane 배포 가이드

**작성일**: 2025-12-29  
**목적**: Fastlane을 사용한 KISS 자동 배포 절차  
**대상**: 프로젝트 관리자, 배포 담당자

---

## 📋 개요

Fastlane은 iOS/Android 앱 배포를 자동화하는 오픈소스 도구입니다.  
KISS 프로젝트에서는 Google Play Store 배포를 자동화하기 위해 사용됩니다.

**위치**: `/fastlane/` 디렉토리  
**주요 파일**:
- `Fastfile` - 배포 자동화 스크립트
- `README.md` - Fastlane 기본 설정
- `PUBLISHING.md` - Google Play 배포 상세 가이드
- `metadata/` - 앱 메타데이터 (앱명, 설명, 이미지 등)

---

## 🚀 빠른 시작

### 1. Fastlane 설치

```bash
# Homebrew (macOS)
brew install fastlane

# Ruby gems
gem install fastlane -NV
# or
sudo gem install fastlane -NV
```

### 2. 설정 확인

```bash
cd /Users/1001028/git/KISS

# Fastlane 초기화 (이미 완료됨)
fastlane init android

# 또는 기존 설정 검증
fastlane validate_credentials
```

### 3. 배포 실행

```bash
# Beta 배포 (Google Play 내부 테스트 트랙)
fastlane android beta

# Production 배포 (공개 릴리스)
fastlane android prod
```

---

## 📁 디렉토리 구조

```
fastlane/
├─ Fastfile                      # 배포 자동화 스크립트
├─ README.md                     # 기본 설정 및 시작 가이드
├─ PUBLISHING.md                 # Google Play 배포 상세 가이드
├─ generate_graphics.py          # 앱 스크린샷 자동 생성 스크립트
├─ metadata/
│  └─ android/
│     ├─ en-US/                  # 영문 메타데이터
│     │  ├─ title.txt            # 앱 제목
│     │  ├─ short_description.txt
│     │  ├─ full_description.txt
│     │  ├─ changelogs/          # 버전별 변경 로그
│     │  │  └─ default.txt
│     │  └─ images/              # 스크린샷 및 아이콘
│     │     ├─ icon.png
│     │     ├─ phoneScreenshots/
│     │     │  ├─ 1.png
│     │     │  └─ ... (최대 8개)
│     │     ├─ sevenInchScreenshots/
│     │     └─ tenInchScreenshots/
│     └─ ko/                     # 한국어 메타데이터
│        └─ (동일 구조)
└─ graphic_templates/            # 스크린샷 생성용 템플릿
   ├─ template_phone.psd
   ├─ template_tablet_7inch.psd
   └─ template_tablet_10inch.psd
```

---

## 🔐 Google Play 서비스 계정 설정

### 1. Google Play Console 접속

- https://play.google.com/console에 접속
- KISS 프로젝트 선택

### 2. 서비스 계정 키 생성

**경로**: Settings → API & Services → Service accounts

1. 새 서비스 계정 생성 또는 기존 계정 선택
2. 키 생성 (JSON 형식)
3. JSON 파일을 안전한 위치에 저장

```bash
# 예: ~/.fastlane/google-play-key.json
mkdir -p ~/.fastlane
# JSON 파일 복사
```

### 3. Fastlane 설정

`fastlane/Fastfile`에서 Google Play 키 경로 지정:

```ruby
desc "Production release to Google Play"
lane :prod do
  upload_to_play_store(
    json_key: "~/.fastlane/google-play-key.json",
    package_name: "kr.lum7671.kiss",
    ...
  )
end
```

**보안 주의:**
- ⚠️ JSON 키 파일을 절대 Git에 커밋하지 마세요
- ⚠️ `~/.gitignore`에 추가: `~/.fastlane/`
- ⚠️ 환경 변수로 관리 권장

---

## 🛠️ Fastfile 스크립트 상세

### Lane: beta

**목적**: Google Play 내부 테스트 트랙에 배포

```ruby
lane :beta do
  # 1. Release APK 빌드
  build_android_app(
    project_dir: "app/",
    task: "assembleRelease",
    build_type: "Release"
  )
  
  # 2. Google Play에 업로드 (Beta/Internal 트랙)
  upload_to_play_store(
    json_key: "~/.fastlane/google-play-key.json",
    package_name: "kr.lum7671.kiss",
    track: "internal",  # 또는 "beta"
    release_status: "draft"
  )
  
  # 3. Slack 알림 (선택)
  slack(
    message: "✅ KISS v4.3.0 beta uploaded successfully"
  )
end
```

### Lane: prod

**목적**: Google Play 공개 릴리스 (Production)

```ruby
lane :prod do
  # 1. Release APK 빌드
  build_android_app(
    project_dir: "app/",
    task: "assembleRelease",
    build_type: "Release"
  )
  
  # 2. Google Play에 업로드 (Production)
  upload_to_play_store(
    json_key: "~/.fastlane/google-play-key.json",
    package_name: "kr.lum7671.kiss",
    track: "production",
    release_status: "completed"
  )
  
  # 3. Release notes 자동 생성 (선택)
  set_github_release(
    repository_name: "lum7671/KISS",
    api_token: ENV["GITHUB_TOKEN"],
    name: "v4.3.0",
    description: "Phase 3: UX Improvements"
  )
end
```

---

## 📝 배포 워크플로우

### 단계 1: 로컬 빌드 검증

```bash
# Debug 빌드로 기본 동작 확인
./gradlew assembleDebug

# Release 빌드로 최종 검증
./gradlew assembleRelease
```

### 단계 2: 버전 및 메타데이터 업데이트

**app/build.gradle**:
```gradle
android {
    defaultConfig {
        versionCode 431  // 증가
        versionName "4.3.1"  // 버전명 업데이트
    }
}
```

**fastlane/metadata/android/en-US/changelogs/default.txt**:
```
v4.3.1 (2025-12-29)
- Fixed: Tag navigation crash during app update
- Improved: Search ranking for hibernated apps
- Added: NEW badge for newly installed apps
```

### 단계 3: Fastlane Beta 배포

```bash
# Beta 트랙에 업로드
fastlane android beta

# 또는 구체적 옵션 지정
fastlane android beta package_name:kr.lum7671.kiss
```

**검증 사항**:
- [x] Google Play Console에서 Beta 버전 업로드 확인
- [x] 내부 테스터에게 배포 알림 확인
- [x] 기본 기능 테스트 (테스터 기기에서)

### 단계 4: Fastlane Production 배포

```bash
# Production 릴리스 (공개)
fastlane android prod

# 출시 후 확인
# - Google Play Console에서 공개 상태 확인
# - 사용자 리뷰 모니터링
# - Amplitude 크래시 보고 확인
```

---

## 🔄 자동화 CI/CD 통합 (Optional)

### GitHub Actions를 통한 자동 배포

`.github/workflows/release.yml` 예시:

```yaml
name: Release to Play Store

on:
  push:
    tags:
      - 'v*'

jobs:
  release:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up JDK
        uses: actions/setup-java@v3
        with:
          java-version: '17'
      
      - name: Install Fastlane
        run: |
          sudo gem install fastlane -NV
      
      - name: Beta Deploy
        run: |
          cd /Users/1001028/git/KISS
          fastlane android beta
        env:
          GOOGLE_PLAY_KEY: ${{ secrets.GOOGLE_PLAY_KEY }}
          
      - name: Production Deploy (Manual Approval)
        # 수동 approval 후 실행
        run: |
          fastlane android prod
```

---

## ⚠️ 주의사항

### 1. APK 서명
- ✅ KISS는 `keystore/kiss-release.keystore`로 자동 서명됨
- ✅ Fastlane이 자동으로 인식하고 처리

### 2. 버전 코드 관리
- 🔴 **필수**: 매번 배포 시 versionCode를 증가시켜야 함
- 🔴 **필수**: 이전 버전과 같은 versionCode는 거부됨

### 3. Google Play 정책
- ⚠️ 충돌하는 권한 선언 확인 (Linting)
- ⚠️ 개인정보보호 정책 명시 필요
- ⚠️ 크래시 보고 (Amplitude) 통합 설명

### 4. 메타데이터 관리
- 📝 각 언어별 메타데이터 최신 유지 필요
- 📸 스크린샷 (최대 8개) 정기 업데이트
- 📝 Changelog 버전별 기록

---

## 🐛 문제 해결

### 1. "Authentication failed" 에러

```bash
# 구글 계정 재인증
fastlane auth google_play

# 또는 JSON 키 경로 확인
ls -la ~/.fastlane/google-play-key.json
```

### 2. "Version code already used" 에러

```bash
# app/build.gradle의 versionCode 증가 확인
grep "versionCode" app/build.gradle

# 현재 Play Store의 최신 versionCode 확인
# → Google Play Console 앱 정보 탭
```

### 3. Fastlane timeout

```bash
# gradle 캐시 초기화
./gradlew clean

# 재시도
fastlane android beta
```

---

## 📚 참고 문서

- **Fastlane 공식 가이드**: https://docs.fastlane.tools/
- **Google Play Console**: https://play.google.com/console
- **PUBLISHING.md**: [fastlane/PUBLISHING.md](../../fastlane/PUBLISHING.md)
- **README-dev.md**: [docs/README-dev.md](../README-dev.md)

---

## 📋 배포 체크리스트

배포 전 다음을 확인하세요:

- [ ] 모든 기능 수동 테스트 완료
- [ ] Detekt, Lint 경고 최소화
- [ ] versionCode 증가됨
- [ ] versionName 업데이트됨
- [ ] 메타데이터 (설명, 스크린샷) 최신 상태
- [ ] Changelog 작성됨
- [ ] Git 커밋 완료됨
- [ ] Beta 배포 후 내부 테스터 피드백 확인
- [ ] Production 배포 후 사용자 리뷰 모니터링

---

**Last Updated**: 2025-12-29  
**Next Review**: 배포 시마다 확인
