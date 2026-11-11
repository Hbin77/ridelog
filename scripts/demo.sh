#!/usr/bin/env bash
# 시연 시나리오: 친구 3명 EV 여행 — 중간 하차 1명, 충전 1회, 음성 경비 1건
# 전제: Pleos Connect Emulator 실행 중, 라이드로그 설치됨. 사용: scripts/demo.sh [단계 간 대기초]
set -euo pipefail
cd "$(dirname "$0")/.."
V=scripts/vc.sh; T=scripts/tap.sh; W=${1:-2}

echo "[0] 차량 초기 상태: SOC 82%, 조수석·뒷좌석 왼쪽 탑승"
$V park; $V soc 82
$V seat passenger 1; $V seat rear_left 1; $V seat rear_right 0
adb shell am force-stop com.ridelog.app
adb shell am start -n com.ridelog.app/.MainActivity >/dev/null; sleep 4

echo "[1] 여행 시작 — 좌석 배정 (운전석 나, 조수석 민수, 뒷좌석 왼쪽 서연)"
$T "여행 시작"; sleep 1; $T "민수"; $T "서연"; sleep "$W"; $T "출발"; sleep 3

echo "[2] 40km 주행 (SOC 82→72%, 약 5km/kWh)"
$V drive; sleep "$W"; $V odo+ 20; sleep 1; $V odo+ 20; $V soc 72; sleep "$W"; $V park; sleep "$W"

echo "[3] 휴게소: 간식 경비 음성 입력(3명 분담) → 서연 하차"
$T "여행 기록 중"; sleep 1; $T "음성: 간식"; sleep 1; $T "패널 닫기"; sleep "$W"
$V seat rear_left 0; sleep "$W"

echo "[4] 30km 주행 (SOC 72→64%)"
$V drive; sleep "$W"; $V odo+ 30; $V soc 64; sleep "$W"; $V park; sleep "$W"

echo "[5] 급속 충전 64→90%"
$V charge 1; sleep 1; $V soc 90; sleep 1; $V charge 0; sleep "$W"

echo "[6] 여행 끝 → 정산표"
$T "여행 끝"; sleep 3
echo "완료"
