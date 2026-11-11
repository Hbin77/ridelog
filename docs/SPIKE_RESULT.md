# Pleos SDK 검증 결과 (2026-11-11, Pleos Connect Emulator v2.1.0 / Apple M3 Pro)

## 환경
| 항목 | 값 |
|---|---|
| 에뮬레이터 이미지 | `system-images;android-34;pleos_car_emulator;arm64-v8a` (Pleos Connect v2.1.0, sha1 `24d28eb2…49419`) |
| 화면 | 2560 × 1440, mdpi(160dpi) — 앱은 우측 창(약 1700 × 1300)에 표시 |
| AVD | Automotive, API 34, RAM 4GB, Cold boot, GPU host |
| Maven | `https://nexus-playground.pleos.ai/repository/maven-releases/` — 인증 없이 다운로드 |
| SDK | Vehicle 2.3.2, TextToSpeech 3.3.1, SpeechToText 3.3.1 |

## 신호별 결과
| 신호 | API | 결과 | 비고 |
|---|---|---|---|
| 좌석 점유 | `Seat.registerSeatOccupancy(SeatArea, SeatOccupancyListener)` | ✅ 5좌석 지원 (ROW_1_LEFT/RIGHT, ROW_2_LEFT/CENTER/RIGHT) | ⚠️ Virtual Console 값이 반대로 전달됨 (아래) |
| 안전벨트 | `Seat.registerSeatBeltBuckledState` | ✅ 5좌석 capability 지원 | Virtual Console에 벨트 조작 UI 없음 → 주행 시작 시 점유 좌석 자동 탑승 처리 |
| 주행거리 | `Odometer.registerDistanceDriven` (Float, km) | ✅ | VHAL이 감소 값을 무시 → 데모는 증가분만 사용 |
| 배터리 SOC | `EvBattery.registerEvBatteryStateOfCharge` (Int, %) | ✅ | 1% 해상도 |
| 배터리 용량 | `EvBattery.getEvBatteryCapacity` | ✅ `77400.0` = **Wh 단위** | kWh로 변환. deprecated(INFO_EV_BATTERY_CAPACITY 권장) |
| 순간 전비 | `EvBattery.registerInstantaneousEvEfficiency` | 등록 성공, 에뮬레이터에서 값 미발생 | 실차에서는 적분 방식 우선, 없으면 SOC 방식 |
| 주행 상태 | AAOS `CarUxRestrictionsManager` | ✅ | Virtual Console 기어 D + 속도 → MOVING, 속도 0 + P → PARKED |
| TTS | `TextToSpeech(ctx, ON_DEVICE)` | ✅ 온디바이스 동작 (앱 등록 불필요) | 매니페스트 `pleos.car.permission.TTS_SERVICE` 필수, **`initialize()` 성공 후 `addEventListener`** |
| STT | `SpeechToText(ctx, ON_DEVICE)` | ✅ 초기화 성공 | `pleos.car.permission.STT_SERVICE` 필수, 동일하게 initialize 후 addListener. 실제 인식은 마이크 입력 필요 |

## 알려진 차이·우회
1. **좌석 점유 반전**: Virtual Console에서 "Occupied"로 바꾸면 SDK 콜백은 `false`. 에뮬레이터(`Build.HARDWARE == "ranchu"`)에서만 값을 뒤집어 실차 의미와 맞춘다 (`PleosVehicleDataSource.invertOccupancy`). Pleos 측 확인 필요.
2. **Virtual Console 쓰기 조건**: 브라우저 세션 heartbeat(`POST /browser/session`)가 3초 내 있어야 하고 쓰기는 `PUT`. `scripts/vc.sh`가 처리.
3. **문서의 `hmg_car_service ... driving_regulation` 명령**: 이 이미지에는 서비스가 없어 동작하지 않음 → 기어·속도로 주행 상태 전환.
4. **Playground 콘솔 "준비 중" 표기**(FusedLocation, TTS, STT): 에뮬레이터에서 TTS/STT 온디바이스 모드는 정상 동작.

## 성능 (release APK, 정차 대기)
- CPU 0.0% (`top`), PSS 65MB (`dumpsys meminfo`) — 기준 CPU < 5%(주행), RAM < 400MB 충족
