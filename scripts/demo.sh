#!/usr/bin/env bash
# 시연 시나리오: 친구 3명 EV 여행 — 중간 하차 1명, 충전 1회, 음성 경비 1건
# 전제: Pleos Connect Emulator 실행 중, 라이드로그 디버그 빌드 설치됨(음성 입력 시뮬레이션). 사용: scripts/demo.sh [단계 간 대기초]
set -euo pipefail
cd "$(dirname "$0")/.."
V=scripts/vc.sh; T=scripts/tap.sh; W=${1:-2}
# 단계별 시작 시각(ms)을 남겨 자막 생성에 쓴다
STEPS=${STEPS_FILE:-/dev/null}; : > "$STEPS"
step() { echo "$1"; echo "$(python3 -I -c "import time;print(int(time.time()*1000))")|$1" >> "$STEPS"; }

step "[0] 차량 초기 상태: SOC 82%, 조수석·뒷좌석 왼쪽 탑승"
$V park; $V soc 82
$V seat passenger 1; $V seat rear_left 1; $V seat rear_right 0
adb shell am force-stop com.ridelog.app
# 이전 시연 기록을 지우고 처음 상태로 시작
adb shell pm clear --user current com.ridelog.app >/dev/null
adb shell pm grant --user current com.ridelog.app android.permission.RECORD_AUDIO
# 시연용 운전자·동승자 이름 (실사용에서는 첫 실행 때 운전자 이름만 입력)
adb shell am start -n com.ridelog.app/.MainActivity --es crew "지훈,민수,서연" >/dev/null; sleep 4

step "[1] 여행 시작 — 좌석 센서가 감지한 자리에 이름 배정 (운전석 지훈, 조수석 민수, 뒷좌석 왼쪽 서연)"
$T "좌석 배정하고 출발"; sleep 1; $T "민수"; $T "서연"; sleep "$W"; $T "출발"; sleep 3

step "[2] 40km 주행 (SOC 82→72%, 약 5km/kWh)"
$V drive; sleep "$W"; $V odo+ 20; sleep 1; $V odo+ 20; $V soc 72; sleep "$W"; $V park; sleep "$W"

step "[3] 휴게소: 간식 경비 음성 입력(3명 분담) → 서연 하차"
# 음성 입력: 디버그 빌드에서 인식 결과 문장을 그대로 전달 (에뮬레이터 마이크 대신)
adb shell am start -n com.ridelog.app/.MainActivity --es utterance "'휴게소 간식 3만 2천원 내가 냈어'" >/dev/null; sleep "$W"; sleep 2
$V seat rear_left 0; sleep "$W"

step "[4] 30km 주행 (SOC 72→64%)"
$V drive; sleep "$W"; $V odo+ 30; $V soc 64; sleep "$W"; $V park; sleep "$W"

step "[5] 급속 충전 64→90%"
$V charge 1; sleep 1; $V soc 90; sleep 1; $V charge 0; sleep "$W"

step "[6] 여행 끝 → 정산표"
$T "도착"; sleep 4

step "[7] 정산표를 QR로 동승자 폰에 전달"
$T "보내기"; sleep 4
echo "완료"
