# -*- coding: utf-8 -*-
"""BODA 글라스 PoC 데모용 코드 생성 — 입고/출고 라벨이 명시된 QR + 1D 바코드.

산출물 (D:\\Repo\\Android\\poc-codes):
  - inbound/  : 제품 카드(입고 조회용) PNG  — 파랑 '입고용' 배너
  - outbound/ : 주문 카드 + 피킹 검증 제품 카드 PNG — 초록 '출고용' 배너
  - BODA_PoC_바코드_카드.pdf : 인쇄용 통합 PDF (입고 → 출고 순)

데이터는 라이브 DB(C:\\ProgramData\\BODA\\VMS\\BodaVision.db) 시드와 일치.
각 카드: 동일 값의 QR + Code128 을 함께 실어 스캐너 모드와 무관하게 인식.
"""
import io
import os
import segno
from barcode import Code128
from barcode.writer import ImageWriter
from PIL import Image, ImageDraw, ImageFont

BASE = r"D:\Repo\Android"
OUT = os.path.join(BASE, "poc-codes")
OUT_IN = os.path.join(OUT, "inbound")
OUT_OUT = os.path.join(OUT, "outbound")
PDF = os.path.join(OUT, "BODA_PoC_바코드_카드.pdf")
FONT = r"C:\Windows\Fonts\malgun.ttf"
FONTB = r"C:\Windows\Fonts\malgunbd.ttf"

for d in (OUT, OUT_IN, OUT_OUT):
    os.makedirs(d, exist_ok=True)

# 팔레트
INK = (15, 23, 42)
MUTED = (90, 100, 115)
LINE = (210, 218, 228)
BLUE = (37, 99, 235)      # 입고
GREEN = (22, 132, 70)     # 출고 - 주문
AMBER = (180, 83, 9)      # 출고 - 피킹 검증
WHITE = (255, 255, 255)

# 카드 규격
CW, CH = 560, 400
BAND = 60


def font(sz, bold=False):
    return ImageFont.truetype(FONTB if bold else FONT, sz)


def qr_img(data, px=190):
    qr = segno.make(data, error="m")
    buf = io.BytesIO()
    qr.save(buf, kind="png", scale=10, border=2, dark="#0F172A", light="#FFFFFF")
    buf.seek(0)
    return Image.open(buf).convert("RGB").resize((px, px), Image.NEAREST)


def barcode_img(data, target_w=500, target_h=110):
    code = Code128(data, writer=ImageWriter())
    buf = io.BytesIO()
    code.write(buf, options={
        "write_text": False, "module_height": 14.0,
        "quiet_zone": 2.0, "dpi": 300,
    })
    buf.seek(0)
    img = Image.open(buf).convert("RGB")
    return img.resize((target_w, target_h), Image.NEAREST)


def card(value, band_color, band_label, title, lines, use_text, qr_only=False):
    """카드 1장 렌더 → PIL.Image"""
    im = Image.new("RGB", (CW, CH), WHITE)
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, CW - 1, CH - 1], outline=LINE, width=2)

    # 상단 배너
    d.rectangle([0, 0, CW, BAND], fill=band_color)
    d.text((20, BAND // 2), band_label, font=font(26, True), fill=WHITE, anchor="lm")
    d.text((CW - 18, BAND // 2), "BODA PoC", font=font(15), fill=(235, 240, 250), anchor="rm")

    # QR
    qpx = 190
    qx, qy = 22, BAND + 22
    im.paste(qr_img(value, qpx), (qx, qy))
    d.text((qx + qpx // 2, qy + qpx + 8), "QR", font=font(13), fill=MUTED, anchor="mm")

    # 텍스트 블록 (QR 우측)
    tx = qx + qpx + 26
    ty = BAND + 26
    d.text((tx, ty), title, font=font(26, True), fill=INK, anchor="lm")
    ty += 40
    for ln, big in lines:
        d.text((tx, ty), ln, font=font(20 if big else 16, big), fill=INK if big else MUTED, anchor="lm")
        ty += 32 if big else 26

    # 하단 1D 바코드 (옵션)
    if not qr_only:
        bw, bh = 500, 96
        bx = (CW - bw) // 2
        by = CH - bh - 46
        im.paste(barcode_img(value, bw, bh), (bx, by))
        d.text((CW // 2, CH - 28), use_text + "      " + value,
               font=font(16, True), fill=INK, anchor="mm")
    else:
        d.text((CW // 2, CH - 30), use_text + "      " + value,
               font=font(16, True), fill=INK, anchor="mm")
    return im


# ---------------------------------------------------------------- 데이터 (DB 시드와 일치)
PRODUCTS = [
    ("8801234567890", "P-1001", "알루미늄 브라켓 A",  "A-01-2-07"),
    ("8801234567891", "P-1002", "스테인리스 볼트 M6", "A-03-1-12"),
    ("8801234567892", "P-1003", "고무 개스킷 50mm",   "B-02-3-04"),
    ("8801234567893", "P-1004", "센서 하우징 PCB",    "B-05-2-09"),
    ("8801234567894", "P-1005", "전원 케이블 2m",     "C-01-1-01"),
    ("8801234567895", "P-1006", "방열판 60x60",       "C-04-4-15"),
    ("8801234567896", "P-1007", "LED 모듈 화이트",    "A-06-3-11"),
    ("8801234567897", "P-1008", "커넥터 하우징 8핀",  "B-01-1-03"),
    ("8801234567898", "P-1009", "실링 테이프 롤",     "C-07-2-06"),
    ("8801234567899", "P-1010", "베어링 6204",        "A-02-1-05"),
    ("8801116012435", "TEMP-001", "임시 등록 제품",   "임시-검수-1-01"),
]
PROD_BY_BC = {p[0]: p for p in PRODUCTS}

ORDERS = [
    ("SO-2026-001", "가나상사", "3번 도크 / 출고대 B",
     [("8801234567890", 3), ("8801234567892", 2), ("8801234567896", 1)]),
    ("SO-2026-002", "대한물산", "1번 도크 / 출고대 A",
     [("8801234567891", 5), ("8801234567899", 4)]),
]


def slug(s):
    return s.replace("/", "_").replace(" ", "")


# ---------------------------------------------------------------- 생성
def build():
    inbound_cards, outbound_cards = [], []

    # 1) 입고용 — 제품 카드 (전 제품)
    for bc, code, name, loc in PRODUCTS:
        im = card(
            bc, BLUE, "입고용",
            f"{code}",
            [(name, True), (f"입고 위치  {loc}", False)],
            use_text="입고 조회용",
        )
        path = os.path.join(OUT_IN, f"입고_{code}_{bc}.png")
        im.save(path)
        inbound_cards.append(im)

    # 2) 출고용 — 주문 카드 (QR 중심) + 피킹 검증 제품 카드
    for ono, cust, dest, plist in ORDERS:
        summary = ", ".join(f"{PROD_BY_BC[b][1]}×{q}" for b, q in plist)
        im = card(
            ono, GREEN, "출고용 · 주문",
            ono,
            [(cust, True), (f"목적지  {dest}", False), (f"라인  {summary}", False)],
            use_text="출고 주문(피킹 시작)",
        )
        im.save(os.path.join(OUT_OUT, f"출고주문_{ono}.png"))
        outbound_cards.append(im)

        # 해당 주문의 피킹 검증 제품들
        for b, q in plist:
            _, code, name, loc = PROD_BY_BC[b]
            im2 = card(
                b, AMBER, "출고용 · 피킹",
                code,
                [(name, True), (f"{ono}  ·  수량 {q}", False), (f"피킹 위치  {loc}", False)],
                use_text="출고 피킹 검증용",
            )
            im2.save(os.path.join(OUT_OUT, f"출고피킹_{ono}_{code}.png"))
            outbound_cards.append(im2)

    # 3) 인쇄용 통합 PDF (2열 그리드)
    pages = paginate(
        [("입고 데모용 — 제품 바코드 (스캔 → 입고 위치 조회)", inbound_cards),
         ("출고 데모용 — 주문 QR + 피킹 검증 제품 바코드", outbound_cards)]
    )
    if pages:
        pages[0].save(PDF, save_all=True, append_images=pages[1:], resolution=150.0)

    print("입고 카드:", len(inbound_cards), "장")
    print("출고 카드:", len(outbound_cards), "장")
    print("PDF:", PDF, f"({len(pages)} pages)")
    print("폴더:", OUT)


def paginate(sections):
    """섹션별 카드들을 A4 세로(1240x1754 @150dpi) 2열 그리드 페이지로."""
    PW, PH = 1240, 1754
    cols, rows = 2, 4
    mx, my = 40, 74
    gx = (PW - 2 * mx - cols * CW) // (cols - 1)
    gy = 12
    per = cols * rows
    pages = []
    for title, cards in sections:
        for start in range(0, len(cards), per):
            chunk = cards[start:start + per]
            page = Image.new("RGB", (PW, PH), WHITE)
            d = ImageDraw.Draw(page)
            d.text((mx, 28), title, font=font(24, True), fill=INK, anchor="lm")
            d.line([mx, 62, PW - mx, 62], fill=LINE, width=2)
            for i, cimg in enumerate(chunk):
                r, c = divmod(i, cols)
                x = mx + c * (CW + gx)
                y = my + r * (CH + gy)
                page.paste(cimg, (x, y))
            d.text((PW - mx, PH - 16),
                   "※ 제품 바코드는 실제 1개이며 입고 조회·출고 피킹에 공용입니다. 데모 편의를 위해 단계별로 분류했습니다.",
                   font=font(13), fill=MUTED, anchor="rm")
            pages.append(page)
    # 페이지 PNG 도 저장 (인쇄/미리보기 편의)
    for i, pg in enumerate(pages, 1):
        pg.save(os.path.join(OUT, f"시트-{i}.png"))
    return pages


build()
