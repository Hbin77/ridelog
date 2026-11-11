#!/usr/bin/env bash
# 시연 영상 제작: 에뮬레이터 화면 녹화 + demo.sh 실행 + 단계별 한국어 자막 합성
# 결과: docs/media/ridelog_demo.mp4   (필요: adb, ffmpeg, python3 + Pillow)
set -euo pipefail
cd "$(dirname "$0")/.."
OUT=docs/media; mkdir -p "$OUT"
WORK=$(mktemp -d)
STEPS="$WORK/steps.txt"

adb shell rm -f /sdcard/demo.mp4
adb shell screenrecord --size 1280x720 --bit-rate 6000000 --time-limit 175 /sdcard/demo.mp4 &
REC=$!
sleep 2
REC_START=$(python3 -I -c "import time;print(int(time.time()*1000))")
STEPS_FILE="$STEPS" scripts/demo.sh "${1:-3}"
sleep 3
adb shell pkill -INT screenrecord || true
wait $REC || true
sleep 2
adb pull /sdcard/demo.mp4 "$WORK/raw.mp4" >/dev/null

python3 -I scripts/captions.py "$STEPS" "$REC_START" "$WORK"
ffmpeg -hide_banner -loglevel error -y -i "$WORK/raw.mp4" $(cat "$WORK/inputs.txt") \
  -filter_complex "$(cat "$WORK/filter.txt")" -map "[v]" -c:v libx264 -pix_fmt yuv420p -crf 23 "$OUT/ridelog_demo.mp4"
echo "saved $OUT/ridelog_demo.mp4"
rm -rf "$WORK"
