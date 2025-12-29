# CI/CD 파이프라인 설정 가이드

**작성일**: 2025-12-29  
**상태**: 📋 계획 (부분 구현)  
**목표**: GitHub Actions 및 Fastlane을 통한 자동화 배포 파이프라인 구축

---

## 📊 현재 상태

### ✅ 기존 구성 요소

| 항목 | 상태 | 위치 | 설명 |
|------|------|------|------|
| **Local Build Scripts** | ✅ 완료 | `scripts/` | build_release_apk.sh 등 |
| **Fastlane Setup** | ✅ 완료 | `fastlane/` | Beta/Prod lane 준비 |
| **Gradle Build System** | ✅ 완료 | `app/build.gradle` | Release/Debug/Profile 설정 |
| **Keystore** | ✅ 완료 | `keystore/` | APK 자동 서명 |
| **Amplitude Monitoring** | ✅ 완료 | Google Analytics | 크래시 보고 통합 |

### ⏳ 미구현 항목

| 항목 | 상태 | 우선순위 | 예상 소요 |
|------|------|----------|---------|
| **GitHub Actions Workflow** | 📋 계획 | 🔴 높음 | 2-3일 |
| **Auto Build on PR** | 📋 계획 | 🟡 중간 | 1일 |
| **Auto Deploy on Release** | 📋 계획 | 🟡 중간 | 1-2일 |
| **Slack/Email Notifications** | 📋 계획 | 🟢 낮음 | 4시간 |

---

## 🎯 파이프라인 설계

### Phase 1: Pull Request 검증

**트리거**: PR 생성 또는 커밋 push  
**작업**:
1. Lint/Detekt 정적 분석
2. Debug APK 빌드
3. 기본 테스트 실행 (선택)
4. 빌드 결과 PR 코멘트 작성

```
PR 생성 → Lint 검사 → Debug 빌드 → 결과 리포트
         (✅/❌)    (✅/❌)
```

### Phase 2: Release 빌드 및 배포

**트리거**: `v*` 태그 푸시 (예: v4.3.1)  
**작업**:
1. Release APK 빌드
2. Google Play Beta 트랙에 업로드 (자동)
3. 내부 테스터 배포 알림
4. 수동 승인 후 Production 배포

```
Git Tag v4.3.1 → Release 빌드 → Beta Upload → [수동 승인] → Prod Upload
                                              ↓
                                        Slack 알림
```

### Phase 3: 모니터링 및 알림

**지속적 모니터링**:
1. Amplitude 크래시 보고
2. Google Play 사용자 리뷰
3. 성능 지표 대시보드

---

## 📋 구현 계획

### Step 1: PR 검증 Workflow (2-3일)

**파일**: `.github/workflows/lint-and-build.yml`

```yaml
name: Lint & Build on PR

on:
  pull_request:
    branches: [ dev, main ]
  push:
    branches: [ dev ]

jobs:
  lint:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '17'
      
      # Lint 검사
      - name: Run Lint
        run: ./gradlew lint
      
      # Detekt 분석
      - name: Run Detekt
        run: ./gradlew detekt
      
      # 결과를 artifacts로 저장
      - name: Upload Lint Results
        if: always()
        uses: actions/upload-artifact@v3
        with:
          name: lint-results
          path: |
            app/build/reports/lint-*.html
            app/build/reports/detekt/

  build:
    runs-on: ubuntu-latest
    needs: lint
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '17'
      
      # Debug APK 빌드
      - name: Build Debug APK
        run: ./gradlew assembleDebug
      
      # 빌드 결과 업로드
      - name: Upload APK
        uses: actions/upload-artifact@v3
        with:
          name: app-debug.apk
          path: app/build/outputs/apk/debug/app-debug.apk
      
      # PR 코멘트로 결과 리포트
      - name: Comment on PR
        if: github.event_name == 'pull_request'
        uses: actions/github-script@v6
        with:
          script: |
            github.rest.issues.createComment({
              issue_number: context.issue.number,
              owner: context.repo.owner,
              repo: context.repo.repo,
              body: '✅ Build successful! Debug APK ready.'
            })
```

**장점**:
- 모든 PR에 대해 자동으로 빌드 검증
- Lint 경고 조기 감지
- 개발자 피드백 빠름

---

### Step 2: Release 배포 Workflow (1-2일)

**파일**: `.github/workflows/release.yml`

```yaml
name: Release to Play Store

on:
  push:
    tags:
      - 'v[0-9]+.[0-9]+.[0-9]+'

jobs:
  release:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '17'
      
      # Fastlane 설치
      - name: Install Fastlane
        run: |
          sudo gem install fastlane -NV
      
      # Release APK 빌드
      - name: Build Release APK
        run: ./gradlew assembleRelease
      
      # Beta 배포 (자동)
      - name: Upload to Google Play Beta
        run: fastlane android beta
        env:
          GOOGLE_PLAY_KEY: ${{ secrets.GOOGLE_PLAY_KEY }}
      
      # Slack 알림
      - name: Notify Slack
        if: success()
        uses: 8398a7/action-slack@v3
        with:
          status: ${{ job.status }}
          text: '✅ KISS ${{ github.ref }} beta uploaded to Play Store'
          webhook_url: ${{ secrets.SLACK_WEBHOOK }}
      
      # GitHub Release 생성
      - name: Create GitHub Release
        uses: actions/create-release@v1
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        with:
          tag_name: ${{ github.ref }}
          release_name: Release ${{ github.ref }}
          body: 'Beta version uploaded to Play Store'
```

**수동 승인 추가** (Production 배포 전):

```yaml
  production-deployment:
    runs-on: ubuntu-latest
    needs: release
    environment: production  # ← 수동 승인 필요
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '17'
      
      - name: Install Fastlane
        run: sudo gem install fastlane -NV
      
      - name: Deploy to Production
        run: fastlane android prod
        env:
          GOOGLE_PLAY_KEY: ${{ secrets.GOOGLE_PLAY_KEY }}
```

---

### Step 3: 환경 변수 및 시크릿 설정 (2시간)

GitHub Settings에서 다음 시크릿 추가:

| 시크릿 | 값 | 설명 |
|--------|-----|------|
| `GOOGLE_PLAY_KEY` | JSON 파일 내용 | Google Play Console 서비스 계정 키 |
| `SLACK_WEBHOOK` | Webhook URL | Slack 알림용 (선택) |
| `GITHUB_TOKEN` | Auto (GitHub 제공) | Release 생성용 |

**GitHub Settings 경로**:
```
Repository → Settings → Secrets and variables → Actions
```

---

## 🔄 배포 워크플로우 (최종)

### 개발자 관점

```
1. 코드 작성 및 커밋
   ↓
2. PR 생성
   ↓ (자동 Lint + Build)
3. PR 검토 및 승인
   ↓
4. dev 브랜치에 merge
   ↓
5. 테스트 및 검증 (1-2일)
   ↓
6. Git 태그 생성: git tag v4.3.1
   ↓
7. 태그 푸시: git push origin v4.3.1
   ↓ (자동 Beta 배포)
8. Beta 트랙 테스트 (2-3일)
   ↓
9. GitHub Actions에서 수동 승인
   ↓ (자동 Production 배포)
10. Google Play 공개 릴리스
```

### 자동화된 작업들

| 작업 | 트리거 | 자동화 여부 |
|------|--------|-----------|
| Lint/Build | PR 생성 | ✅ 자동 |
| Beta 배포 | v* 태그 푸시 | ✅ 자동 |
| 내부 테스터 알림 | Beta 배포 완료 | ✅ 자동 |
| Prod 배포 | 수동 승인 | 🔘 반자동 |
| 모니터링 | 배포 후 지속 | ✅ 자동 |

---

## 🛠️ 로컬 개발 가이드

### PR 생성 전 확인

```bash
# 1. Local lint 검사
./gradlew lint detekt

# 2. Local debug 빌드
./gradlew assembleDebug

# 3. 결과 확인
# app/build/reports/lint-results-debug.html
# app/build/reports/detekt/detekt.html
```

### Release 준비

```bash
# 1. Version 업데이트
# app/build.gradle에서 versionCode, versionName 변경

# 2. Changelog 작성
# fastlane/metadata/android/en-US/changelogs/default.txt

# 3. Local release 빌드
./gradlew assembleRelease

# 4. Git 태그 생성
git tag v4.3.1
git push origin v4.3.1

# 5. GitHub Actions 상태 모니터링
# https://github.com/lum7671/KISS/actions
```

---

## 📊 모니터링 대시보드

### Google Play Console

**체크 항목**:
- 사용자 리뷰 (⭐ 평점)
- 크래시 통계 (ANR, Exception)
- 일일 활성 사용자 (DAU)

**링크**: https://play.google.com/console

### Amplitude Analytics

**모니터링**:
- 앱 시작 시간 (Performance)
- 검색 성능
- 크래시 발생률

**링크**: Amplitude Dashboard (설정 필요)

---

## ⚠️ 주의사항

### 1. Git 태그 생성

```bash
# ✅ 올바른 형식
git tag v4.3.0
git tag v4.3.1

# ❌ 잘못된 형식 (CI/CD 트리거 안 됨)
git tag release-4.3.0
git tag 4.3.0
```

### 2. 시크릿 관리

- 🔴 Google Play 키를 Git에 커밋하지 마세요
- 🔴 `~/.gitignore`에 시크릿 파일 추가
- ✅ GitHub Secrets에만 저장

### 3. 권한 관리

- ⚠️ Google Play Console에서 "Release Manager" 권한 필요
- ⚠️ GitHub Actions 환경에서 "deploy" 권한 설정

---

## 📚 참고 문서

- **Fastlane 가이드**: [docs/DEPLOYMENT/fastlane-guide.md](fastlane-guide.md)
- **GitHub Actions 공식 문서**: https://docs.github.com/en/actions
- **Google Play Console**: https://play.google.com/console
- **배포 프로세스**: [docs/DEPLOYMENT/deployment-process.md](deployment-process.md)

---

## 🗓️ 구현 로드맵

### Phase 1 (즉시, 1-2주)
- [ ] PR 검증 Workflow 구현
- [ ] 로컬 테스트 및 검증

### Phase 2 (단기, 2-4주)
- [ ] Release 배포 Workflow 구현
- [ ] 시크릿 설정 및 테스트
- [ ] Beta 배포 검증

### Phase 3 (중기, 1-3개월)
- [ ] Slack 알림 통합
- [ ] Production 승인 프로세스
- [ ] 모니터링 대시보드 구축

---

**Last Updated**: 2025-12-29  
**Next Review**: 1개월 후 (구현 진행 확인)
