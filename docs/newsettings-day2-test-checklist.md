# NewSettingsActivity Day 2 - 기능 테스트 체크리스트

**테스트 일자**: 2025-10-16  
**테스터**: AI Assistant  
**빌드**: feature/phase6-step7-fragment-conversion

---

## ✅ 1. 메인 설정 화면 (7개 카테고리)

### 1.1 History Settings (히스토리 설정)
- [ ] 카테고리 진입 가능
- [ ] Reset history 다이얼로그 표시
- [ ] Number of display elements 수정 가능
- [ ] History mode 변경 가능
- [ ] Freeze history 토글 작동
- [ ] Phone history 토글 작동 (권한 요청 확인)
- [ ] App history 토글 작동
- [ ] Notification history 토글 작동
- [ ] Excluded apps 버튼 동작
- [ ] Reset excluded apps 다이얼로그 표시

### 1.2 Favorites Settings (즐겨찾기 설정)
- [ ] 카테고리 진입 가능
- [ ] Reset favorites 다이얼로그 표시
- [ ] Enable favorites bar 토글 작동
- [ ] Large favorites bar 토글 작동 (dependency 확인)
- [ ] Exclude favorites apps 토글 작동
- [ ] Favorites on launch 토글 작동

### 1.3 User Interface (사용자 인터페이스)
- [ ] 카테고리 진입 가능
- [ ] 모든 색상 선택 (ColorPreferenceDialogFragmentCompat)
  - [ ] Primary color 변경
  - [ ] Notification bar color 변경
  - [ ] Theme bar color 변경
  - [ ] Result color 변경
- [ ] 테마 선택 (Light/Dark/System)
- [ ] Icon pack 선택
- [ ] 각종 UI 스위치 작동 확인

### 1.4 Search Settings (검색 설정)
- [ ] 카테고리 진입 가능
- [ ] Enable apps 토글
- [ ] Enable contacts 토글
- [ ] Enable web search providers 토글
- [ ] 하위 메뉴들 진입 확인:
  - [ ] Web providers 설정
  - [ ] Default search provider 선택
  - [ ] **AddSearchProvider 다이얼로그 작동** ⭐
    - [ ] 이름/URL 입력 폼 표시
    - [ ] URL 검증 작동
    - [ ] 검색 엔진 추가 성공
    - [ ] Snackbar 피드백 표시
  - [ ] 검색 엔진 삭제 (MultiSelectListPreference)
  - [ ] Reset search providers 다이얼로그

### 1.5 Advanced Settings (고급 설정)
- [ ] 카테고리 진입 가능
- [ ] 각종 제스처 설정 변경
- [ ] 키보드 설정
- [ ] 알림 설정
- [ ] 성능 설정

### 1.6 Import & Export (가져오기/내보내기)
- [ ] 카테고리 진입 가능
- [ ] **Export settings 실행** ⭐
  - [ ] 클립보드에 복사 확인
  - [ ] Snackbar 성공 메시지 표시
  - [ ] 에러 시 Retry 버튼 표시
- [ ] **Import settings 실행** ⭐
  - [ ] 클립보드에서 읽기 성공
  - [ ] JSON 검증 작동
  - [ ] 버전 체크 작동
  - [ ] 설정 적용 후 Activity recreate
  - [ ] Snackbar 피드백 표시
  - [ ] 에러 시 Retry 버튼 표시

### 1.7 Tags (태그)
- [ ] 카테고리 진입 가능
- [ ] Tag result sort mode 변경
- [ ] Tags visibility 토글

---

## ✅ 2. Fragment 네비게이션 테스트

### 2.1 PreferenceScreen 네비게이션
- [ ] 메인 → 하위 카테고리 진입 애니메이션
- [ ] ActionBar 타이틀 변경 확인
- [ ] Up button (←) 작동
- [ ] 백버튼 작동
- [ ] BackStack 관리 정상

### 2.2 Hierarchy 깊이 테스트
- [ ] 1단계 (메인)
- [ ] 2단계 (카테고리)
- [ ] 3단계 (서브 카테고리 - ExcludePreferenceScreen 등)
- [ ] 각 단계에서 뒤로가기 정상 작동

### 2.3 ExcludePreferenceScreen 특수 케이스
- [ ] Excluded apps 진입
- [ ] Excluded from history apps 진입
- [ ] Excluded shortcuts 진입
- [ ] MultiSelectListPreference 작동
- [ ] 앱 리스트 로딩 성공
- [ ] 앱 선택/해제 작동

---

## ✅ 3. Snackbar 에러 처리 테스트

### 3.1 권한 거부
- [ ] Phone history 권한 거부 → Snackbar 표시
- [ ] Notification history 권한 거부 → Snackbar 표시

### 3.2 Import 에러 시나리오
- [ ] 클립보드 비어있음 → Snackbar + Retry 버튼
- [ ] 잘못된 JSON → Snackbar + Retry 버튼
- [ ] 버전 불일치 → Snackbar (Retry 없음)
- [ ] 저장 실패 → Snackbar 표시

### 3.3 Export 에러 시나리오
- [ ] JSON 생성 실패 → Snackbar + Retry 버튼
- [ ] 클립보드 쓰기 실패 → Snackbar + Retry 버튼

### 3.4 Snackbar UX 확인
- [ ] 메시지 가독성
- [ ] 표시 위치 (화면 하단)
- [ ] Duration 적절성 (SHORT/LONG)
- [ ] Action 버튼 클릭 가능
- [ ] Retry 버튼 작동 확인

---

## ✅ 4. DialogPreference 테스트

### 4.1 ColorPreferenceDialogFragmentCompat
- [ ] Primary color 다이얼로그 표시
- [ ] 색상 팔레트 렌더링
- [ ] 미리 정의된 색상 버튼 작동
  - [ ] Transparent
  - [ ] Transparent White
  - [ ] System
- [ ] 팔레트에서 색상 선택
- [ ] 선택된 색상 체크마크 표시
- [ ] OK 버튼으로 저장
- [ ] Cancel 버튼으로 취소

### 4.2 AddSearchProviderPreferenceDialogFragmentCompat
- [ ] 다이얼로그 표시
- [ ] Name 입력 필드
- [ ] URI 입력 필드
- [ ] 빈 문자열 검증
- [ ] 파이프(|) 문자 검증
- [ ] 중복 이름 검증
- [ ] {q} placeholder 검증
- [ ] URL 형식 검증
- [ ] URI 형식 검증
- [ ] 앱이 URI 처리 가능한지 확인
- [ ] 성공 시 SharedPreferences 저장
- [ ] 실패 시 적절한 Toast 표시

### 4.3 Reset 다이얼로그들
- [ ] Reset history 확인 다이얼로그
- [ ] Reset favorites 확인 다이얼로그
- [ ] Reset excluded apps 확인 다이얼로그
- [ ] Reset excluded from history 확인 다이얼로그
- [ ] Reset excluded shortcuts 확인 다이얼로그
- [ ] Reset search providers 확인 다이얼로그

---

## ✅ 5. 엣지 케이스 & 회귀 테스트

### 5.1 빠른 연속 클릭
- [ ] PreferenceScreen 빠른 연속 클릭 시 중복 Fragment 생성 방지
- [ ] DialogPreference 빠른 연속 클릭 시 중복 다이얼로그 방지
- [ ] Action preference (Import/Export/Restart) 빠른 연속 클릭

### 5.2 화면 회전
- [ ] 메인 화면에서 회전
- [ ] 하위 카테고리에서 회전
- [ ] 다이얼로그 표시 중 회전
- [ ] ColorPicker 표시 중 회전
- [ ] 선택 상태 유지 확인

### 5.3 백그라운드 진입/복귀
- [ ] 설정 화면에서 홈 버튼 → 복귀
- [ ] 다이얼로그 표시 중 홈 버튼 → 복귀
- [ ] 최근 앱에서 복귀
- [ ] 상태 유지 확인

### 5.4 메모리 부족 시나리오
- [ ] 앱 리스트 로딩 중 메모리 부족
- [ ] 대량의 검색 엔진 로딩
- [ ] Activity recreate 시 상태 복원

### 5.5 권한 변경
- [ ] 설정 앱에서 권한 변경 후 복귀
- [ ] 런타임 권한 요청 중 취소
- [ ] 권한 재요청 시나리오

---

## ✅ 6. 성능 테스트

### 6.1 로딩 속도
- [ ] 메인 화면 로딩 시간 (< 500ms)
- [ ] PreferenceScreen 전환 시간 (< 300ms)
- [ ] ExcludePreferenceScreen 로딩 (< 1s)
- [ ] 색상 팔레트 렌더링 (< 500ms)

### 6.2 메모리 사용
- [ ] 메인 화면 메모리 사용량
- [ ] 깊은 hierarchy 네비게이션 시 메모리 증가
- [ ] Activity recreate 후 메모리 해제
- [ ] LeakCanary 경고 확인

### 6.3 반응성
- [ ] UI 스레드 블로킹 없음
- [ ] 긴 작업 시 프로그레스 표시
- [ ] ANR 발생 없음

---

## ✅ 7. 접근성 테스트

### 7.1 TalkBack 지원
- [ ] 모든 Preference 읽기 가능
- [ ] 다이얼로그 제목/메시지 읽기
- [ ] Action 버튼 포커스 가능
- [ ] 네비게이션 음성 안내

### 7.2 키보드 네비게이션
- [ ] Tab 키로 이동
- [ ] Enter 키로 선택
- [ ] Esc 키로 취소

---

## 📊 테스트 결과 요약

**테스트 항목 총 개수**: ~150개

**통과**: ___  
**실패**: ___  
**스킵**: ___  

### 발견된 이슈

1. **Critical** (즉시 수정 필요)
   - 

2. **Major** (우선 수정)
   - 

3. **Minor** (개선 사항)
   - 

4. **Enhancement** (향후 고려)
   - 

---

## 📝 테스트 노트

### 주요 발견 사항


### 개선 제안


### 다음 단계


---

**작성자**: AI Assistant  
**최종 업데이트**: 2025-10-16
