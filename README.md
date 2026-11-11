# 라이드로그 (RideLog)

**좌석이 기억하고 계기판이 증명하는, 함께 탄 이동의 공동 경비 자동 정산 앱** — Pleos Connect 차량용 앱

친구 여행·출퇴근 카풀에서 "누가 어디서 타고 내렸는지"를 좌석 센서로, "얼마나 썼는지"를 계기판 주행거리와 배터리 충전율로 자동 기록하고, 정차 후 정산표를 폰으로 보내 줍니다. 사용자가 하는 일은 **출발 전 좌석 배정**과 **도착 후 보내기** 두 번뿐입니다.

## 핵심 기능
- 좌석 점유·안전벨트로 승하차 자동 감지 → 탑승자가 바뀔 때마다 구간 분할
- 구간별 실측 에너지(SOC 감소 × 배터리 용량, 전비 적분) × 전력 단가 → 그 구간 탑승자끼리 분담
- 운전 중 음성으로 경비 추가: "간식 3만 2천원 내가 냈어"
- 주행 중에는 마이크 버튼과 상태 한 줄만 표시 (금액·목록 표시 없음), 정차 시 정산표·QR
- 최소 송금 목록(인원−1건 이하, 10원 단위)

## 사용 SDK
| SDK | 버전 | 용도 |
|---|---|---|
| Pleos Vehicle SDK | 2.3.2 | Seat(점유·벨트), Odometer, EvBattery(SOC·용량·전비) — 조회 권한만 |
| Pleos TextToSpeech SDK | 3.3.1 | 온디바이스 음성 안내 (실패 시 Android TTS) |
| Pleos SpeechToText SDK | 3.3.1 | 온디바이스 음성 경비 입력 (실패 시 Android SpeechRecognizer) |
| AAOS CarUxRestrictions | API 34 | 주행/정차 판단 |

## 테스트 환경
- **Pleos Connect Emulator v2.1.0** (`system-images;android-34;pleos_car_emulator;arm64-v8a`)
- **화면 규격: 2560 × 1440, mdpi(160dpi)**, 앱은 기본 세로 창에 표시
- AVD: Automotive / API 34 / RAM 4GB / Cold boot / GPU host
- Android Studio JBR 21, Gradle 8.13, AGP 8.7.3, Kotlin 2.0.21, minSdk 28, targetSdk 34

## 실행 방법
1. APK 설치: `adb install -g apk/TEAM_RideLog_release.apk`
2. 앱 실행 → **여행 시작** → 좌석에 이름 배정 → **출발**
3. Virtual Console(`adb forward tcp:8088 tcp:8088` → http://127.0.0.1:8088)에서 기어 D·속도, 좌석 점유, 주행거리, SOC를 바꾸면 기록이 따라갑니다.
4. 자동 시연: `scripts/demo.sh` — 3명 EV 여행(중간 하차 1명, 음성 경비 1건, 충전 1회)을 Virtual Console과 화면 탭으로 재생
5. 개발자 패널: 정차 화면 제목을 누르면 Pleos 신호 로그와 가상 신호 버튼이 열립니다.

## 빌드
```bash
./gradlew :core:test            # 정산 엔진 단위 테스트 30개
./gradlew :app:assembleRelease  # local.properties에 서명 정보(ridelog.storeFile 등)가 없으면 debug 키로 서명
```

## APK
- `apk/TEAM_RideLog_release.apk` (release 빌드)

## 구조
- `core/` — 순수 Kotlin 정산 엔진 (TripEngine 리듀서, Fare/Settlement, 한국어 금액 음성 파서). Android 의존 없음
- `app/` — Pleos 데이터 소스, 음성, Compose UI (`design-system/MASTER.md` 토큰)
- `docs/SPIKE_RESULT.md` — Pleos SDK 에뮬레이터 검증 결과와 알려진 차이
- `scripts/` — Virtual Console 제어(`vc.sh`), 화면 탭(`tap.sh`), 시연(`demo.sh`)

## 참고
- 에뮬레이터 시연 수치(주행거리·SOC)는 Virtual Console에 입력한 시뮬레이션 값입니다.
- 정산 표기는 운송 대가가 아닌 "공동 경비 분담"입니다.
