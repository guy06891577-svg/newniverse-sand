# 뉴니버스 전화 — 실제로 만들려면 (v1 시안 메모)

이 폴더의 `index.html`은 **디자인 시안**이에요. 전화를 실제로 걸거나 받지 않고, 데이터는 모두 가짜입니다 (010-0000-xxxx).

## 1. 기본 전화 앱 자격 (Android 10+ RoleManager)
- `RoleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)`로 사용자에게 "기본 전화 앱으로 설정할까요?"를 물어봐야 해요. 사용자가 허용해야만 이 역할을 받습니다.
- 역할을 받으려면 앱이 아래 두 가지를 모두 갖춰야 해요.
  - `ACTION_DIAL`을 처리하는 Activity: `tel:` 스킴이 있는 것과 데이터가 없는 것, 두 가지 intent-filter.
  - `InCallService` 구현: `android.permission.BIND_INCALL_SERVICE` 권한, meta-data `android.telecom.IN_CALL_SERVICE_UI=true`, intent-filter `android.telecom.InCallService`.
- `InCallService`가 받는 `Call` 객체로 할 수 있는 것: 받기/거절(`answer`/`reject`, 문자 거절 포함), 대기(`hold`), DTMF(`playDtmfTone`), 회의 통화(`conference`).
  - 음소거와 스피커는 `setMuted`와 `setAudioRoute`로 해요.
  - 통화 추가는 `TelecomManager.placeCall`로 해요.
- 걸려오는 전화 화면은 잠금 화면 위에 떠야 해서 아래 설정이 필요해요.
  - full-screen intent 알림 (`USE_FULL_SCREEN_INTENT`. Android 14+에서는 통화 앱만 허용).
  - `showWhenLocked` / `turnScreenOn`.
  - 통화 중 포그라운드 서비스 유형 `phoneCall`.

## 2. 권한
- `CALL_PHONE`: 바로 전화 걸기
- `READ_CALL_LOG`: 최근 통화와 '최근 15일 N통' 집계
- `WRITE_CALL_LOG`: 묶음 삭제
- `READ_CONTACTS`: 이름과 초성 검색
- `READ_PHONE_STATE` / `READ_PHONE_NUMBERS`: 심 정보
- `POST_NOTIFICATIONS`: 부재중·수신 알림
- 지금까지 하던 대로, 권한은 **기능을 켤 때만** 요청해요.
- 최근 검색 목록은 앱 안에만 저장해요 (시스템 권한 필요 없음).

## 3. Play 정책
- Play 정책에서 통화 기록 권한(`READ_CALL_LOG`, `WRITE_CALL_LOG`, `PROCESS_OUTGOING_CALLS`)은 '제한된 권한'이에요.
  - 기본 전화 앱으로 설정을 요청하는 앱처럼 핵심 기능에 꼭 필요한 경우에만 허용돼요.
  - Play Console에서 권한 선언서를 내고 심사를 받아야 해요.
- 기본 전화 앱이 아닐 때 통화 기록을 읽으면 거부 사유가 됩니다.
- **통화 녹음**은 사실상 막혀 있어요.
  - Android 10+에서 일반 앱은 통화 음성(VOICE_CALL)을 녹음할 수 없어요.
  - 접근성 API로 녹음하는 방식은 2022년부터 Play에서 금지됐어요.
  - 시안의 '녹음' 버튼은 실제로는 '삼성 기본 녹음 열기' 안내로 바꾸거나 빼야 해요.
- 지금처럼 APK를 직접 배포(사이드로드)하면 Play 심사는 없어요. 하지만 통화 기록 권한이 붙으면 Play Protect 경고가 더 강해질 수 있어요.

## 4. 런처 APK와의 관계
- 한 앱이 HOME 역할과 DIALER 역할을 동시에 가질 수는 있어요.
- **권장: 별도 앱 "뉴니버스 전화"** (패키지 분리, 같은 서명 키, 같은 디자인).
  - 런처 독의 전화 아이콘은 이 앱을 엽니다.
  - 전화 앱에 문제가 생겨도 홈 화면은 멀쩡해요.
  - 통화 기록과 연락처 권한이 런처에 붙지 않아요.
  - 런처는 지금처럼 가볍게 유지하고 업데이트도 따로 할 수 있어요.
- 다음 단계인 **문자**(`ROLE_SMS`)는 전화와 같은 앱("뉴니버스 전화·문자")에 넣는 쪽이 자연스러워요.
  - 다만 기본 문자 앱은 SMS/MMS 수신·저장·발송 전체를 구현해야 해서 일이 더 큽니다.
