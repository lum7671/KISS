# 배포 프로세스 (수동 및 자동화)

**작성일**: 2025-12-29  
**대상**: 프로젝트 관리자, 릴리스 엔지니어  
**목적**: 체계적인 배포 절차 표준화

---

## 📋 배포 프로세스 개요

KISS 프로젝트의 배포는 **3단계 검증** 후 진행됩니다:

```
개발 → Beta 테스트 → 내부 승인 → Production 릴리스
```

---

## 🚀 단계별 배포 절차

### Phase 0: 준비 (배포 3-5일 전)

#### 0.1 기능 완성 및 테스트
- [ ] 모든 기능 수동 테스트 완료
- [ ] 성능 이슈 없음 (스크롤, 검색 등)
- [ ] 크래시 없음 (LeakCanary 검증)
- [ ] 메모리 누수 없음

#### 0.2 코드 정리
- [ ] Lint 경고 최소화
- [ ] Detekt 규칙 준수
- [ ] 미사용 코드 제거
- [ ] 주석 정리

#### 0.3 문서 업데이트
- [ ] README.md 버전 정보 업데이트
- [ ] Changelog 작성
- [ ] API 변경사항 문서화
- [ ] 마이그레이션 가이드 (있으면)

**체크리스트**:
```bash
# Lint/Detekt 검사
./gradlew lint detekt

# 빌드 검증
./gradlew clean assembleDebug assembleRelease

# 에뮬레이터 테스트
# 주요 기능: 검색, 설정, 다크모드, 태그 필터링 등
```

---

### Phase 1: Beta 배포 (배포 2-3일 전)

#### 1.1 버전 업데이트

**파일**: `app/build.gradle`

```gradle
android {
    defaultConfig {
        applicationId "kr.lum7671.kiss"
        versionCode 431      // ← 증가
        versionName "4.3.1"  // ← 업데이트
    }
}
```

**규칙**:
- versionCode: 매번 1씩 증가 (필수)
- versionName: 의미있는 버전 번호 (semantic versioning)

#### 1.2 메타데이터 업데이트

**앱 설명**: `fastlane/metadata/android/en-US/full_description.txt`
```
KISS Android Launcher
- 간단하고 빠른 안드로이드 런처
- 최소한의 UI로 최대의 성능
- 완전 오픈소스

v4.3.1 업데이트:
- 태그 네비게이션 버그 수정
- Hibernated 앱 검색 개선
- NEW 배지 시스템 추가
```

**변경 로그**: `fastlane/metadata/android/en-US/changelogs/default.txt`
```
v4.3.1 (2025-12-29)

✅ 개선사항:
- 태그 필터링 중 홈 화면 이동 버그 완전 해결
- Hibernated 앱 스마트 랭킹 (최근 사용 여부 기반)
- NEW 배지로 신규 설치 앱 시각적 표시
- History 자동 추가 및 DB 동기화 안정화

🐛 버그 수정:
- 메모리 DB ↔ 디스크 DB 동기화 오류 제거
- State tracking 강화로 UX 버그 예방

⚡ 성능:
- 검색 성능 유지
- 메모리 사용량 최적화
- 배터리 효율 개선
```

#### 1.3 Fastlane Beta 배포

```bash
# 1. Release APK 빌드
./gradlew assembleRelease

# 2. Beta 배포 (Google Play)
fastlane android beta

# 또는 직접 관리
fastlane android beta \
  package_name:kr.lum7671.kiss \
  json_key:~/.fastlane/google-play-key.json
```

**검증**:
- [ ] Google Play Console 접속
- [ ] "Beta" 또는 "Internal Testing" 트랙 확인
- [ ] 최신 버전이 업로드됨
- [ ] 메타데이터 올바름

#### 1.4 내부 테스터 알림

**대상**: 3-5명의 내부 테스터  
**방법**: Slack, Email  
**메시지 템플릿**:

```
🚀 KISS v4.3.1 Beta 배포

Beta 버전이 Google Play에 업로드되었습니다.

📋 주요 변경사항:
- 태그 네비게이션 버그 수정
- Hibernated 앱 스마트 랭킹
- NEW 배지 시스템

📝 테스트 항목:
- [ ] 태그 필터링 중 앱 업데이트 시 화면 유지
- [ ] 검색 결과 순서 확인
- [ ] NEW 배지 표시 및 제거
- [ ] 다크모드, 설정 정상 동작
- [ ] 크래시 없음

⏰ 피드백 기한: 2025-12-31 23:59

🔗 Google Play Beta: https://play.google.com/apps/testing/kr.lum7671.kiss
```

---

### Phase 2: Beta 테스트 (2-3일)

#### 2.1 내부 테스터 피드백

**테스트 항목**:
- [ ] 기본 기능 동작 확인
- [ ] 특정 기기(이전 안드로이드, 특수 환경) 호환성
- [ ] 성능 및 배터리 소비
- [ ] UI 외관 및 사용성
- [ ] 크래시/ANR 발생 여부

**피드백 수집**:
- 테스터로부터 리포트 받기
- Google Play Console의 "Beta feedback" 확인
- Amplitude 크래시 리포트 모니터링

#### 2.2 문제 대응

**경우 1: 치명적 버그 발견**
```bash
# 1. 로컬에서 버그 재현 및 수정
# 2. versionCode 증가 (432)
# 3. Beta 재배포
./gradlew clean assembleRelease
fastlane android beta
```

**경우 2: 경미한 문제**
- 수정 후 다음 버전에 포함
- 사용자에게 workaround 제공

---

### Phase 3: Production 승인 (배포 전 1일)

#### 3.1 최종 검증

```
체크리스트:
- [ ] Beta 테스트 피드백 긍정적
- [ ] 크래시 없음 (Amplitude 확인)
- [ ] 성능 지표 정상
- [ ] Google Play 정책 준수
- [ ] 개인정보보호 정책 명시
```

#### 3.2 릴리스 노트 확정

GitHub Release 생성 예시:

```
Release: v4.3.1 - UX Bug Fixes
Date: 2025-12-29
versionCode: 431
versionName: 4.3.1

## 🎯 주요 개선사항

### Phase 3.1: 태그 네비게이션 버그 수정 ✅
- 사용자가 태그 필터링 중 앱 업데이트 시 예기치 않은 홈 화면 전환 문제 완전 해결
- Pending Updates Queue로 안전한 백그라운드 업데이트 처리
- 삭제 이벤트는 즉시 반영, 설치/업데이트는 연기

### Phase 3.2: Hibernated 앱 검색 개선 ⭐
- 최근 30일 내 1회 이상 사용한 hibernated 앱은 검색 상위 표시
- 스마트 패널티 로직으로 자주 쓰는 앱 접근성 향상
- History boost 적용으로 사용 패턴 기반 검색 제공

### Phase 3.3: NEW 배지 및 History 통합 🆕
- 새로 설치한 앱에 빨간 NEW 배지 표시 (앱 실행 시 자동 제거)
- 설치 앱 자동 History 추가로 사용 기록 누락 방지
- 메모리 DB ↔ 디스크 DB 동기화 안정화

## 📊 성과 지표

| 항목 | 개선율 |
|------|--------|
| UX 안정성 | 예기치 않은 화면 전환 0건 🎯 |
| 검색 정확도 | 자주 쓰는 앱 상위 표시 ⭐ |
| 신규 앱 인식도 | RED 배지로 즉시 인식 ✨ |
| History 완정성 | 설치 앱 100% 추가 🛡️ |

## 🔧 기술 세부사항

[docs/DEVELOPMENT/phase-3-progress.md](docs/DEVELOPMENT/phase-3-progress.md) 참고

## 🙏 감사의 말

이 버전에 기여한 모든 테스터와 커뮤니티에 감사합니다.
```

---

### Phase 4: Production 배포 (배포날)

#### 4.1 수동 Fastlane 배포 또는 CI/CD 승인

**수동 배포**:
```bash
# 1. 최종 확인
git tag v4.3.1
git push origin v4.3.1

# 2. GitHub Actions 자동 배포 (설정 후)
# → CI/CD가 Beta 업로드 완료

# 3. GitHub Actions에서 Production 승인
# → Manual approval in GitHub → Deploy to Prod

# 또는 직접 배포
fastlane android prod
```

**자동 배포** (CI/CD 구성 후):
```
Git Tag v4.3.1 → 
  Release Build → 
    Beta Upload (자동) → 
      [수동 승인] → 
        Production Upload (자동)
```

#### 4.2 배포 후 모니터링

**첫 24시간**:
- [ ] Google Play에 공개 릴리스되었는지 확인
- [ ] Amplitude 크래시 보고 모니터링
- [ ] 사용자 리뷰 모니터링
- [ ] DAU (Daily Active Users) 확인

**대시보드**:
```
Google Play Console:
  https://play.google.com/console/u/0/developers/[ID]/app/[APP_ID]/overview

Amplitude:
  https://analytics.amplitude.com/
```

#### 4.3 긴급 대응

**치명적 크래시 발견 시**:
```bash
# 1. 긴급 패치 개발
# 2. versionCode 증가 (432)
# 3. 배포
./gradlew clean assembleRelease
fastlane android prod
```

---

## 📋 배포 체크리스트

### 배포 1주일 전
- [ ] 개발 완료
- [ ] 모든 기능 수동 테스트
- [ ] Code review 완료
- [ ] Lint/Detekt 경고 최소화

### 배포 3일 전
- [ ] Version 업데이트
- [ ] Changelog 작성
- [ ] README.md 갱신
- [ ] Beta 메타데이터 확정

### 배포 2일 전
- [ ] Fastlane Beta 배포
- [ ] 내부 테스터 알림
- [ ] Beta 테스트 시작

### 배포 당일
- [ ] Beta 피드백 최종 확인
- [ ] Amplitude 크래시 없음 확인
- [ ] Release notes 확정
- [ ] GitHub Tag 생성
- [ ] Fastlane Prod 배포 (또는 CI/CD 승인)

### 배포 후
- [ ] Google Play 공개 확인
- [ ] 크래시 보고 모니터링 (24시간)
- [ ] 사용자 리뷰 모니터링
- [ ] 릴리스 문서화 완료

---

## 🔄 배포 자동화 (Future)

### 현재 상태
- ✅ Fastlane Beta/Prod lane 준비
- ✅ Local build scripts 완성
- ⏳ GitHub Actions Workflow (계획)

### 향후 개선 (2-4주)
```
PR 생성 → 자동 Lint/Build ✅
         ↓
Git Tag → 자동 Beta 배포 ⏳
         ↓
[수동 승인] → 자동 Prod 배포 ⏳
         ↓
Slack/Email 알림 ⏳
```

자세한 내용: [docs/DEPLOYMENT/ci-cd-pipeline.md](ci-cd-pipeline.md)

---

## 📚 관련 문서

- **Fastlane 가이드**: [docs/DEPLOYMENT/fastlane-guide.md](fastlane-guide.md)
- **CI/CD 파이프라인**: [docs/DEPLOYMENT/ci-cd-pipeline.md](ci-cd-pipeline.md)
- **개발 가이드**: [docs/README-dev.md](../README-dev.md)
- **프로젝트 관리 계획**: [docs/PROJECT-MANAGEMENT-IMPROVEMENT-PLAN.md](../PROJECT-MANAGEMENT-IMPROVEMENT-PLAN.md)

---

## 💡 팁

### 배포 전 빠른 체크
```bash
# 1. Clean build
./gradlew clean

# 2. Lint + Detekt
./gradlew lint detekt

# 3. Release 빌드
./gradlew assembleRelease

# 4. APK 확인
ls -lh app/build/outputs/apk/release/
```

### Google Play 버전 관리
```bash
# 현재 Play Store 최신 versionCode 확인
# Google Play Console → 앱 정보 → 버전 현황

# 로컬 versionCode 확인
grep versionCode app/build.gradle
```

### Fastlane 문제 해결
```bash
# 권한 확인
fastlane auth google_play

# JSON 키 검증
cat ~/.fastlane/google-play-key.json

# Dry run (실제 배포 없이 테스트)
fastlane android beta --dry_run
```

---

**Last Updated**: 2025-12-29  
**Next Review**: 각 배포 후
