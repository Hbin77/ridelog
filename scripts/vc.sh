#!/usr/bin/env bash
# Pleos Connect Virtual Console HTTP 헬퍼 (데모·테스트용)
# 사용: scripts/vc.sh <명령> [값]
#   odo <km>            계기판 누적 주행거리 (VHAL은 감소를 무시하므로 증가만 의미 있음)
#   odo+ <km>           현재 주행거리에 km 더하기
#   soc <percent>       배터리 충전율
#   seat <area> <0|1>   좌석 점유 (driver|passenger|rear_left|rear_right)
#   charge <0|1>        충전 스위치
#   gear <P|R|N|D>      기어
#   drive | park        기어 D + 60km/h / 0km/h + 기어 P (AAOS 주행 상태 MOVING/PARKED)
set -euo pipefail
VC=http://127.0.0.1:8088
adb forward tcp:8088 tcp:8088 >/dev/null
# Virtual Console은 브라우저 세션 heartbeat가 살아 있어야 쓰기를 받는다 (timeout 3s)
beat() { curl -s -m 3 -X POST "$VC/browser/session?client=ridelog-cli" >/dev/null; }
req() { beat; curl -s -m 5 -X PUT "$VC$1" >/dev/null; }
case "$1" in
  odo)    req "/vehicle/odometer?traveled_distance_km=$2" ;;
  odo+)   cur=$(beat; curl -s -m 5 "$VC/vehicle/state" | python3 -I -c 'import sys,json;print(json.load(sys.stdin)["odometer"]["traveled_distance_km"])')
          req "/vehicle/odometer?traveled_distance_km=$(python3 -I -c "print($cur+$2)")" ;;
  soc)    req "/vehicle/ev-battery?field=soc&value=$2" ;;
  seat)   v=$([ "$3" = 1 ] && echo true || echo false); req "/vehicle/seat?kind=occupied&area=$2&value=$v" ;;
  charge) v=$([ "$2" = 1 ] && echo true || echo false); req "/vehicle/ev-battery?field=charge_switch&value=$v" ;;
  gear)   case "$2" in P) g=Park;; R) g=Reverse;; N) g=Neutral;; D) g=Drive;; esac; req "/vehicle/gear?gear=$g" ;;
  drive)  req "/vehicle/gear?gear=Drive"; req "/vehicle/controls?speed_kph=60" ;;
  park)   req "/vehicle/controls?speed_kph=0"; req "/vehicle/gear?gear=Park" ;;
  *) echo "unknown: $1" >&2; exit 1 ;;
esac
