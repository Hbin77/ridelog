# RideLog Design System — MASTER v2 "밝고 친근한 여행"

> 소스 오브 트루스. 코드 색·크기는 `app/src/main/java/com/ridelog/app/ui/theme/Theme.kt` 토큰만 사용 (raw hex는 Theme.kt 한 곳).
> 기준: Pleos Connect UI Design Guide(대비 4.5:1↑, 주요 텍스트 5mm↑, 큰 터치 영역), AAOS Driver Distraction.
> 기준 화면: Pleos Connect Emulator 2560×1440, mdpi(160dpi), 앱 창 약 1700×1300 — 1dp ≈ 0.145mm.

## 1. 방향
- 성격: 친구·가족과 떠나는 여행의 "같이 탄" 기분 — 따뜻한 크림 바탕, 민트(신뢰·차량)와 코랄(돈·포인트), 둥근 형태, 사람 아바타.
- 상징: **위에서 내려다본 차 일러스트 안의 좌석** — 모든 핵심 화면(배정·기록 중)의 중심. 좌석에는 사람 아바타(이름 첫 글자)가 앉는다.
- 레이아웃: 가로로 넓은 창 → **2단**(왼쪽 차, 오른쪽 정보), 하단 고정 버튼 줄.
- 서체: Pleos 시스템 서체 Asta Sans(기기 기본 sans-serif) 그대로 사용.

## 2. 색 토큰
| 토큰 | 주간(Light) | 야간(Dark) | 용도 / 대비 |
|---|---|---|---|
| background | #FFF9F3 | #121A1F | 화면 바탕 (크림 / 깊은 청록흑) |
| surface | #FFFFFF | #1B252B | 카드 |
| surfaceVariant | #F4EEE7 | #24313A | 좌석 바탕, 보조 영역 |
| onBackground | #1F2430 | #F1F5F4 | 본문 14.9:1 / 16.0:1 |
| onSurfaceMuted | #5E6573 | #A6B3B6 | 보조 5.6:1 / 8.2:1 (surfaceVariant 위 5.1 / 6.2) |
| primary (민트) | #0A7A6D | #4FD1BC | 주요 버튼·선택 — onPrimary 대비 5.0 / 8.1 |
| onPrimary | #FFFFFF | #062B26 | |
| primaryContainer | #D7F4EE | #1F4B44 | 탑승 좌석, 선택된 사람 |
| onPrimaryContainer | #0B5E54 | #F1F5F4 | 6.6 / 8.9 |
| accent (코랄) | #C23E2B | #FF8A76 | 금액·강조 글자 5.2 / 6.8 |
| accentContainer | #FFE4DD | #3A2420 | (예약) |
| error | #B3261E | #FFB4AB | 오류 전용 — 돈 색과 분리 6.3 / 10.4 |
| warning | #9A5B00 | #FFC266 | 미등록 탑승·추정 배지 |
| outline | #E2D8CC | #33424A | 테두리·구분선·차체 선 |
| carBody | #FFFFFF | #1F2B32 | 차 일러스트 차체 |
| carGlass | #CFE3FF | #26405F | 차 유리 |
| tire | #2B3038 | #0A0F12 | 바퀴 |
| lamp | #FFE9A8 | #F2E3A6 | 헤드램프 |
| avatar 1~5 | #BFEDE3 #FFD3C9 #CFE3FF #FFE3A8 #E4D7FF | #1F4B44 #5A2E27 #26405F #574516 #3E3360 | 사람별 고정 색 (등록 순서), 글자 대비 11:1 이상 |

## 3. 타이포
| 토큰 | 크기 | 굵기 | 용도 |
|---|---|---|---|
| headline | 52sp | Bold | 화면 제목 |
| display | 64sp | Bold, tnum | 주행 거리, 송금 금액 (자리 맞춤 숫자) |
| figure | 40sp | SemiBold, tnum | 목록 오른쪽 금액·거리 |
| title | 40sp | SemiBold | 카드 제목, 좌석 이름 |
| button | 36sp | SemiBold | 버튼 |
| body | 32sp | Regular | 본문 (약 4.6mm) |
| label | 26sp | Medium | 보조 (약 3.8mm) |

## 4. 형태·간격
- radius: **둥근 형태는 차와 사람(아바타)에게만.** 카드 16dp, 버튼 20dp, 좌석 20dp — 알약형 금지
- 그림자 없음: 카드는 1.5dp outline 또는 배경 단차. 목록은 카드 대신 구분선 행
- spacing: 8 / 16 / 24 / 32 / 48
- 터치: 정차 ≥ 104dp, 주행 ≥ 160dp(마이크 240dp)

## 5. 컴포넌트
- **CarView**: 위에서 본 차 — 앞이 둥글게 좁아지는 차체, 휠 아치 밖 바퀴, A필러 미러, 헤드램프(lamp)·테일램프(accent), 앞·뒷유리, 파노라마 루프 너머 실내(carGlass 28%), 센터 콘솔, B필러 이음새 + 좌석 5개(방석 + 아래 등받이 띠). 좌석 상태 — 빈자리, 선택(민트 굵은 링), 탑승(민트 바탕+아바타), 내림/대기(흐린 아바타), 감지됨·미등록(앰버)
- **Avatar**: 원 + 이름 첫 글자, 사람별 고정 색
- **Button**: 주요(민트 채움) / 보조(흰 바탕+테두리), 20dp
- **Metric**: 큰 숫자 + 단위 + 한 줄 설명 ("70.0 km · 계기판 기준") — 차가 기록한 증거를 화면 첫머리에
- **LogRow**: 시각 · 사건 · 값, 구분선 — 좌석 센서가 만든 탑승/하차·충전·경비 기록
- **RideBars**: 사람별 탑승 막대(가로축 = 주행 거리, 구간 경계 2dp 틈) — 정산의 근거
- 송금 행: "민수 → 지훈 12,620원" 한 줄, 금액은 onBackground display(자리 맞춤 숫자). 코랄은 돈 전용이 아니라 강조 포인트로만

## 6. 모션
- 주행 중: 애니메이션·자동 스크롤 0 / 정차 중: 150ms 이하 페이드만

## 7. Anti-patterns (금지)
- 템플릿 흔적: 알약 모든 곳, 상태 알약 반복, 회색 머리표(eyebrow), 1·2·3 번호 안내, 그림자 카드 쌓기, 분홍 카드 위 금액, 아바타+이름 중복 표기, "반가워요"류 인사말
- 사람 색은 등록 순서로 배정(LocalCrewOrder) — 해시로 정하면 같은 여행에서 색이 겹친다
- 주행 중 금액·목록·키보드·QR 표시, 개인별 분담액 음성 발화
- 예시 이름 미리 채우기 — 첫 실행은 운전자 이름만 묻고 동승자는 빈 목록
- "청구/요금" 표현 → "공동 경비 분담"
- 이모지 아이콘, raw hex, 장식용 사진, 반복 움직임, 광고성 알림
- 체크 아이콘 남용 — 확정 동작("출발", "도착")에만
