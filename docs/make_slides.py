# -*- coding: utf-8 -*-
"""BODA 글라스 PoC 소개 영상 — 슬라이드(1280x720) + 내레이션 대본(manifest.json) 생성.

기존 화면 캡처/다이어그램 자산을 브랜드 디자인 위에 배치한 슬라이드 PNG 를 만들고,
슬라이드별 한국어 내레이션 텍스트를 manifest.json 으로 내보낸다.
영상 합성(내레이션 TTS + ffmpeg)은 make_video.ps1 이 담당.
"""
import os
import json
from PIL import Image, ImageDraw, ImageFont

BASE = r"D:\Repo\Android"
OUT = os.path.join(BASE, "poc-video")
SLIDES = os.path.join(OUT, "slides")
FONT = r"C:\Windows\Fonts\malgun.ttf"
FONTB = r"C:\Windows\Fonts\malgunbd.ttf"
os.makedirs(SLIDES, exist_ok=True)

W, H = 1280, 720
INK = (233, 238, 250)
MUTED = (150, 165, 195)
ACCENT = (255, 209, 102)     # 노랑 강조
BLUE = (59, 130, 246)
TOP = (16, 30, 60)           # 상단 그라데이션
BOT = (8, 11, 20)            # 하단


def font(sz, bold=True):
    return ImageFont.truetype(FONTB if bold else FONT, sz)


def gradient_bg():
    img = Image.new("RGB", (W, H), BOT)
    d = ImageDraw.Draw(img)
    for y in range(H):
        t = y / (H - 1)
        c = tuple(int(TOP[i] + (BOT[i] - TOP[i]) * t) for i in range(3))
        d.line([(0, y), (W, y)], fill=c)
    return img


def fit(im, maxw, maxh):
    r = min(maxw / im.width, maxh / im.height)
    return im.resize((int(im.width * r), int(im.height * r)), Image.LANCZOS)


def wrap(d, text, fnt, maxw):
    words, lines, cur = text.split(" "), [], ""
    for w in words:
        t = (cur + " " + w).strip()
        if d.textlength(t, font=fnt) <= maxw:
            cur = t
        else:
            if cur:
                lines.append(cur)
            cur = w
    if cur:
        lines.append(cur)
    return lines


def render(idx, total, title, image, caption, subtitle=None, bullets=None):
    img = gradient_bg()
    d = ImageDraw.Draw(img)

    # 상단 브랜드 바
    d.text((56, 40), "BODA 글라스", font=font(26), fill=ACCENT)
    d.text((56, 74), "× BODA.VMS.Web", font=font(16, False), fill=MUTED)
    d.text((W - 56, 48), f"{idx} / {total}", font=font(18), fill=MUTED, anchor="ra")
    d.line([56, 108, W - 56, 108], fill=(40, 52, 78), width=2)

    # 제목
    d.text((56, 132), title, font=font(40), fill=INK)
    y_top = 200
    if subtitle:
        d.text((56, 188), subtitle, font=font(22, False), fill=MUTED)
        y_top = 240

    if image and os.path.exists(image):
        im = fit(Image.open(image).convert("RGB"), 1000, H - y_top - 110)
        x = (W - im.width) // 2
        y = y_top + ((H - y_top - 110) - im.height) // 2
        # 테두리
        d.rectangle([x - 3, y - 3, x + im.width + 2, y + im.height + 2], outline=(60, 74, 104), width=3)
        img.paste(im, (x, y))
    elif bullets:
        by = y_top + 10
        for b in bullets:
            d.ellipse([60, by + 14, 74, by + 28], fill=ACCENT)
            for i, ln in enumerate(wrap(d, b, font(28, False), W - 140)):
                d.text((92, by + i * 40), ln, font=font(28, False), fill=INK)
                by += 40
            by += 26

    # 캡션(하단 강조)
    if caption:
        d.rectangle([0, H - 78, W, H], fill=(13, 20, 38))
        d.rectangle([0, H - 78, 8, H], fill=ACCENT)
        d.text((56, H - 56), caption, font=font(24), fill=ACCENT, anchor="lm")

    path = os.path.join(SLIDES, f"slide-{idx:02d}.png")
    img.save(path)
    return path


def im(name):
    return os.path.join(BASE, name)


# ---------------------------------------------------------------- 스토리보드
SLIDES_DEF = [
    dict(
        title="스마트 글라스 입출고 관리",
        subtitle="음성 명령 핸즈프리 · 바코드 — PoC 소개",
        image=im("screenshot-newcimo.png"),
        caption="BODA 글라스 — Moziware Cimo (음성 전용 조작)",
        narration="보다 글라스를 소개할게요. 두 손이 자유로운 스마트 글라스 위에서, 음성과 바코드만으로 입고부터 출고까지 손쉽게 처리할 수 있는 솔루션이에요. 모든 조작은 화면 터치 없이, 음성 명령으로 이루어진답니다.",
    ),
    dict(
        title="시스템 구성",
        image=im("doc-architecture.png"),
        caption="얇은 클라이언트 + 백엔드 + 실시간 웹 관리",
        narration="글라스는 화면과 음성, 스캔만 맡는 가벼운 클라이언트예요. 데이터와 처리는 서버가 담당하고, 작업 결과는 웹 관리 화면에 실시간으로 나타난답니다.",
    ),
    dict(
        title="입고 — 위치 조회",
        image=im("screenshot-inbound-map.png"),
        caption="바코드 스캔 → ‘입고’ → 보관 위치 + 2D 미니맵",
        narration="먼저 입고예요. 바코드를 스캔하고 입고라고 말하면, 보관 위치가 큰 글씨와 미니맵으로 한눈에 표시돼요.",
    ),
    dict(
        title="입고 — 입고 확정",
        image=im("screenshot-inbound-map.png"),
        caption="‘입고 확정’ → 재고 누적 · 웹 재고 실시간 반영",
        narration="물건을 제자리에 둔 다음 입고 확정이라고 말하면, 재고가 자동으로 쌓이고 웹 화면의 재고 수량도 새로고침 없이 곧바로 올라간답니다.",
    ),
    dict(
        title="출고 — 주문 목록",
        image=im("doc-orderlist.png"),
        caption="음성 명령으로 주문 선택 (커서 · 번호)",
        narration="출고는 주문 목록에서 시작해요. 다음, 이전, 열기 같은 음성 명령으로, 처리할 주문을 가볍게 골라 줍니다.",
    ),
    dict(
        title="출고 — 피킹(스캔 검증)",
        image=im("screenshot-minimap.png"),
        caption="위치 안내 → 제품 스캔 → 수량 채움 · 오피킹 차단",
        narration="안내된 위치로 가서 제품을 스캔하면 수량이 채워져요. 혹시 다른 품목을 스캔하면 자동으로 막아 주니, 잘못 집을 걱정이 없답니다.",
    ),
    dict(
        title="출고 — 출하 확정",
        image=im("screenshot-pick-dest.png"),
        caption="전 라인 완료 → 출하 확정 → 출고 목적지 표시",
        narration="모든 라인을 다 담으면 출하 확정으로 마무리해요. 출고 목적지가 표시되고, 주문이 완료 상태로 바뀝니다.",
    ),
    dict(
        title="실시간 가시성",
        bullets=[
            "글라스 작업이 웹 관리 대시보드에 즉시 반영(SignalR)",
            "입고 확정 → 재고 증가 / 피킹·출하 → 주문 상태 변경",
            "현장 진행 상황을 새로고침 없이 실시간 모니터링",
        ],
        caption="글라스 작업 → 웹 대시보드 즉시 반영",
        narration="이렇게 글라스에서 한 모든 작업은 웹 대시보드에 실시간으로 전해져요. 현장이 지금 어떻게 돌아가는지, 새로고침 없이 바로 확인할 수 있습니다.",
    ),
    dict(
        title="BODA 글라스",
        bullets=[
            "입고: 위치 조회 → 입고 확정(재고·이력)",
            "출고: 주문 선택 → 스캔 검증 피킹 → 출하 확정",
            "고객 ERP / WMS 환경에 맞춘 연동·확장 가능",
        ],
        caption="핸즈프리 입출고 · 고객 환경 맞춤 확장",
        narration="보다 글라스는 입고 조회와 확정, 출고 피킹과 출하까지 두 손 자유롭게 도와드려요. 고객사의 이알피나 더블유엠에스 환경에 맞춰, 폭넓게 확장할 수도 있답니다. 감사합니다.",
    ),
]


def build():
    total = len(SLIDES_DEF)
    manifest = []
    for i, s in enumerate(SLIDES_DEF, 1):
        path = render(
            i, total, s["title"], s.get("image"), s.get("caption"),
            subtitle=s.get("subtitle"), bullets=s.get("bullets"),
        )
        manifest.append({"index": i, "file": path, "narration": s["narration"]})
    with open(os.path.join(OUT, "manifest.json"), "w", encoding="utf-8") as f:
        json.dump(manifest, f, ensure_ascii=False, indent=2)
    print(f"슬라이드 {total}장 생성:", SLIDES)
    print("manifest:", os.path.join(OUT, "manifest.json"))


build()
