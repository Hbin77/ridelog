"""demo.sh 단계 시각 → 자막 PNG + ffmpeg overlay 필터 생성. 사용: captions.py steps.txt rec_start_ms workdir"""
import os
import sys

from PIL import Image, ImageDraw, ImageFont

CAPTIONS = {
    "[0]": "라이드로그 — 함께 탄 이동의 비용을, 차가 기록하고 나눠줍니다",
    "[1]": "① 출발 전: 좌석에 이름만 배정 (자주 타는 멤버는 저장)",
    "[2]": "② 주행 중: 화면은 음성 버튼 하나 — 좌석·계기판·배터리를 차가 자동 기록",
    "[3]": "③ 휴게소: \"간식 3만 2천원 내가 냈어\" 음성 경비 → 서연 하차를 좌석 센서가 감지",
    "[4]": "④ 남은 2명의 구간은 따로 계산",
    "[5]": "⑤ 충전으로 늘어난 배터리는 소비량에서 제외",
    "[6]": "⑥ 도착: 구간별 실측 에너지 비용 + 경비 → 최소 송금 목록",
    "[7]": "⑦ QR로 동승자 폰에 정산 내용 전달 — 공동 경비 분담",
}
FOOTER = "Pleos Connect Emulator v2.1.0 · 차량 신호는 Virtual Console 시뮬레이션 입력"
FONT = "/System/Library/Fonts/AppleSDGothicNeo.ttc"
W, H = 1280, 720


def banner(text: str, path: str, size: int, height: int, alpha: int) -> None:
    img = Image.new("RGBA", (W, height), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, W, height], fill=(14, 17, 22, alpha))
    font = ImageFont.truetype(FONT, size, index=6 if size > 20 else 0)
    tw = d.textlength(text, font=font)
    d.text(((W - tw) / 2, (height - size) / 2 - 2), text, font=font, fill=(242, 244, 247, 255))
    img.save(path)


def main() -> None:
    steps_file, rec_start, work = sys.argv[1], int(sys.argv[2]), sys.argv[3]
    steps = []
    for line in open(steps_file, encoding="utf-8"):
        ts, label = line.rstrip("\n").split("|", 1)
        steps.append(((int(ts) - rec_start) / 1000.0, label[:3]))
    inputs, filters, last = [], [], "[0:v]"
    for i, (t, key) in enumerate(steps):
        end = steps[i + 1][0] if i + 1 < len(steps) else t + 12
        png = os.path.join(work, f"cap{i}.png")
        banner(CAPTIONS.get(key, key), png, 30, 64, 225)
        inputs += ["-i", png]
        out = f"[c{i}]"
        filters.append(f"{last}[{i + 1}:v]overlay=0:{H - 64}:enable='between(t,{max(t, 0):.2f},{end:.2f})'{out}")
        last = out
    foot = os.path.join(work, "footer.png")
    banner(FOOTER, foot, 18, 30, 170)
    inputs += ["-i", foot]
    filters.append(f"{last}[{len(steps) + 1}:v]overlay=0:0[v]")
    open(os.path.join(work, "inputs.txt"), "w").write(" ".join(inputs))
    open(os.path.join(work, "filter.txt"), "w").write(";".join(filters))


if __name__ == "__main__":
    main()
