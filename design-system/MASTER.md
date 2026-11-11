# RideLog Design System — MASTER

> 소스 오브 트루스. 코드의 색·크기는 `app/src/main/java/com/ridelog/app/ui/theme/` 토큰만 사용 (raw hex는 `Color.kt` 한 곳에만).
> 근거: Pleos Connect UI Design Guide(대비 4.5:1↑, 최소 12sp, 주요 텍스트 5mm↑, 응답 0.25s), AAOS Driver Distraction 가이드.

## 1. 제품 맥락
- 유형: 차량 인포테인먼트 유틸리티 (B2C, 정산·기록). 밀도: 낮음, 한눈에 읽기.
- 사용 상태 2개: **주행(Driving)** = 음성·상태 한 줄만 / **정차(Parked)** = 전체 UI.
- 스타일: Flat + 고대비 다크 기본(야간·주간 자동 전환 대비), 장식 없음.

## 2. 색 토큰 (Dark 기본 / Light)
| 토큰 | Dark | Light | 용도 |
|---|---|---|---|
| background | #0E1116 | #F6F7F9 | 화면 배경 |
| surface | #1A1F27 | #FFFFFF | 카드·패널 |
| surfaceVariant | #252B35 | #E9ECF1 | 좌석 칸, 구분 영역 |
| onBackground | #F2F4F7 | #12161C | 본문 (대비 17.2:1 / 16.9:1) |
| onSurfaceMuted | #A9B1BD | #4A5361 | 보조 텍스트 (대비 8.7:1 / 7.3:1) |
| primary | #4DA3FF | #0B63CE | 주요 버튼·강조 (onPrimary 대비 7.1:1 / 5.7:1) |
| onPrimary | #0B1220 | #FFFFFF | primary 위 텍스트 |
| success | #3DD68C | #11804B | 탑승 확정, 정산 완료 |
| warning | #F5B544 | #8A5A00 | 추정치 배지, 미등록 좌석 |
| error | #FF6B6B | #B42318 | 오류 |
| outline | #3A4250 | #C9CFD8 | 테두리 |

## 3. 타이포 (Pleos 서체 Asta Sans 번들 불가 시 시스템 기본)
| 토큰 | 크기 | 굵기 | 용도 |
|---|---|---|---|
| display | 64sp | Bold | 주행 화면 상태, 총액 |
| title | 44sp | SemiBold | 화면 제목 |
| body | 34sp | Regular | 본문 (약 5mm — Pleos 주요 텍스트 기준) |
| label | 26sp | Medium | 배지·보조 (약 3.8mm — 보조 텍스트 3mm 기준 상회) |

## 4. 간격·모양
- spacing: 8 / 16 / 24 / 32 / 48 dp (8pt 리듬)
- radius: 12dp(카드), 20dp(주요 버튼)
- 터치 타겟: 정차 **≥ 104dp**, 주행 **≥ 160dp** (주행 중 버튼은 마이크 1개뿐)
- 기준 화면: Pleos Connect 에뮬레이터 2560×1440, mdpi(160dpi) — 1dp ≈ 0.145mm(17인치 기준)

## 5. 모션
- 주행 중: 애니메이션·자동 스크롤·화면 전환 효과 **0**
- 정차 중: 상태 변화 페이드 150ms 이하만

## 6. 컴포넌트
- PrimaryButton: 높이 104dp, radius 20, primary/onPrimary, 아이콘(머티리얼 아웃라인) + 텍스트
- SeatMap: 2×3 좌석 그리드, 칸 최소 150dp, 점유=success 테두리, 미등록=warning
- AmountRow: 이름(body) ↔ 금액(title, 우측 정렬, 천 단위 콤마)
- Badge: label, 추정=warning, 실측=success

## 7. Anti-patterns (금지)
- 주행 중 금액·리스트·키보드·QR 표시
- 개인별 분담액 음성 발화 (공개 장소에서 돈 얘기 → 원래 문제를 악화)
- "청구/요금" 표현 → "공동 경비 분담"
- 이모지를 아이콘으로 사용, raw hex, 장식 이미지, 반복 움직임
- 광고성 알림
