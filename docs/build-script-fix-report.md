# build_release_apk.sh 수정 보고서

**날짜**: 2025-10-14  
**문제**: `adb: command not found` 에러  
**상태**: ✅ 수정 완료

---

## 🐛 발견된 문제

### 1. ADB 경로 문제
`build_release_apk.sh` 스크립트가 `adb` 명령을 직접 호출하여 PATH에 설정되지 않은 경우 실패함.

**에러 메시지**:
```
./scripts/build_release_apk.sh: 줄 69: adb: 명령을 찾을 수 없음
```

**원인**:
- `ANDROID_HOME` 환경변수는 설정되어 있음
- 하지만 `adb` 명령을 직접 호출 시 `$ANDROID_HOME/platform-tools`가 PATH에 없으면 실패

---

## ✅ 적용된 수정

### 변경 사항

#### 1. ADB 변수 추가
```bash
# Before
APKSIGNER="$ANDROID_HOME/build-tools/34.0.0/apksigner"
if [ ! -f "$APKSIGNER" ]; then
    ...
fi

# After
# ADB 경로 설정
ADB="$ANDROID_HOME/platform-tools/adb"
if [ ! -f "$ADB" ]; then
    echo -e "${RED}❌ adb를 찾을 수 없습니다: $ADB${NC}"
    exit 1
fi

APKSIGNER="$ANDROID_HOME/build-tools/34.0.0/apksigner"
if [ ! -f "$APKSIGNER" ]; then
    ...
fi
```

#### 2. 모든 adb 호출을 $ADB로 변경

**변경된 위치** (4곳):

1. **ADB 연결 확인** (줄 73):
```bash
# Before
if ! adb devices | grep -q "device$"; then

# After  
if ! "$ADB" devices | grep -q "device$"; then
```

2. **기존 앱 확인** (줄 148):
```bash
# Before
if adb shell pm list packages | grep -q "kr.lum7671.kiss"; then

# After
if "$ADB" shell pm list packages | grep -q "kr.lum7671.kiss"; then
```

3. **앱 제거** (줄 153):
```bash
# Before
adb uninstall kr.lum7671.kiss || true

# After
"$ADB" uninstall kr.lum7671.kiss || true
```

4. **APK 설치** (줄 158):
```bash
# Before
adb install "$APK_SIGNED"

# After
"$ADB" install "$APK_SIGNED"
```

5. **런처 화면 표시** (줄 162):
```bash
# Before
adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME

# After
"$ADB" shell am start -a android.intent.action.MAIN -c android.intent.category.HOME
```

---

## 📋 테스트 결과

### 수정 전
```bash
$ ./scripts/build_release_apk.sh
🚀 KISS Release APK 빌드 시작...
⚠️  릴리즈 키스토어가 없습니다. 디버그 키스토어를 사용합니다
📱 ADB 연결 확인...
./scripts/build_release_apk.sh: 줄 69: adb: 명령을 찾을 수 없음
⚠️  에뮬레이터 또는 디바이스가 연결되지 않았습니다
```

### 수정 후
```bash
$ ./scripts/build_release_apk.sh
🚀 KISS Release APK 빌드 시작...
⚠️  릴리즈 키스토어가 없습니다. 디버그 키스토어를 사용합니다
📱 ADB 연결 확인...
List of devices attached
emulator-5554   device

✅ 에뮬레이터가 연결되었습니다
```

---

## 🔍 추가 발견 사항

### 손상된 파일들

다음 스크립트 파일들도 Git 저장소에서 손상된 상태로 발견됨:
1. `scripts/build_profile_apk.sh` - 첫 줄이 깨짐
2. `scripts/install_and_test.sh` - 동일한 ADB 경로 문제 가능성

**손상 내용**:
```bash
# 정상:
echo "🚀 KISS Profile APK 빌드 시작..."

# 실제:
echo "🚀 echo -e "${GREEN}✅ 빌드 완료!${NC}"
echo -e "${GREEN}📱 앱이 설치되었습니다. 런처로 설정해주세요.${NC}"
...file APK 빌드 시작..."
```

**원인**: Git 저장소의 특정 커밋에서 이미 손상되어 저장됨 (2667e51d4)

---

## 📝 권장 사항

### 1. 즉시 수정 필요
- [ ] `build_profile_apk.sh` 재생성 또는 복구
- [ ] `install_and_test.sh`에 ADB 경로 수정 적용
- [ ] `run_emulator.sh` 확인

### 2. 장기 개선
- [ ] 모든 스크립트에 일관된 환경변수 사용 패턴 적용
- [ ] 스크립트 검증 테스트 추가
- [ ] CI/CD에서 스크립트 무결성 검사

### 3. 문서화
- [ ] 스크립트 사용법 README 작성
- [ ] 환경변수 설정 가이드 추가
- [ ] 트러블슈팅 섹션 추가

---

## ✅ 커밋 정보

```
Commit: bbd3d88d0
Message: Fix build_release_apk.sh: Add proper ADB path

- Set ADB variable with full path ($ANDROID_HOME/platform-tools/adb)
- Replace all 'adb' commands with '$ADB' variable  
- Fixes 'adb: command not found' error

Issue: Script was calling 'adb' directly without PATH configuration
```

---

## 🎯 결론

`build_release_apk.sh` 스크립트의 ADB 경로 문제가 수정되었습니다.

**수정 전**: `adb` 명령이 PATH에 없으면 실패  
**수정 후**: `$ANDROID_HOME/platform-tools/adb` 전체 경로 사용

다른 손상된 스크립트 파일들은 별도 수정 필요.
