#!/usr/bin/env bash
# 화면에서 텍스트(또는 content-desc)를 포함한 요소의 중심을 탭한다. 사용: scripts/tap.sh "여행 시작"
set -euo pipefail
adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
adb exec-out cat /sdcard/ui.xml | python3 -I -c '
import sys,re,xml.etree.ElementTree as ET
want=sys.argv[1]; root=ET.fromstring(sys.stdin.read())
for n in root.iter("node"):
    if want in (n.get("text") or "") or want in (n.get("content-desc") or ""):
        x1,y1,x2,y2=map(int,re.findall(r"\d+",n.get("bounds"))); print((x1+x2)//2,(y1+y2)//2); break
else: sys.exit("not found: "+want)
' "$1" | { read x y; adb shell input tap "$x" "$y"; echo "tap $1 @ $x,$y"; }
