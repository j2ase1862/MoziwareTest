# -*- coding: utf-8 -*-
"""BODA 글라스 + BODA.VMS.Web PoC 소개서/매뉴얼 — 자체 포함 단일 HTML 생성.

make_doc.py 와 동일한 내용을 사용하되, 이미지를 base64 data URI 로 임베드해
파일 하나(.html)로 오프라인 열람·전달·브라우저 인쇄(PDF)가 모두 가능하게 한다.
"""
import os
import base64
import html as _html
from PIL import Image, ImageDraw, ImageFont

BASE = r"D:\Repo\Android"
OUT  = r"D:\BODA_글라스_PoC_소개서_매뉴얼.html"
ARCH = os.path.join(BASE, "doc-architecture.png")
FONT = r"C:\Windows\Fonts\malgun.ttf"
FONTB = r"C:\Windows\Fonts\malgunbd.ttf"


# ---------------------------------------------------------------- 아키텍처 다이어그램
def make_arch():
    W, H = 1240, 660
    img = Image.new("RGB", (W, H), "#FFFFFF")
    d = ImageDraw.Draw(img)

    def f(sz, bold=False):
        return ImageFont.truetype(FONTB if bold else FONT, sz)

    def box(x, y, w, h, title, sub, fill, border, tcol="#0A0C10", dash=False):
        d.rounded_rectangle([x, y, x + w, y + h], radius=14, fill=fill, outline=border, width=3)
        d.text((x + w / 2, y + h / 2 - 16), title, font=f(22, True), fill=tcol, anchor="mm")
        if sub:
            d.text((x + w / 2, y + h / 2 + 14), sub, font=f(15), fill="#334155", anchor="mm")

    def arrow(x1, y1, x2, y2, label=""):
        d.line([x1, y1, x2, y2], fill="#64748B", width=3)
        import math
        ang = math.atan2(y2 - y1, x2 - x1)
        for s in (-0.4, 0.4):
            d.line([x2, y2, x2 - 14 * math.cos(ang - s), y2 - 14 * math.sin(ang - s)], fill="#64748B", width=3)
        if label:
            d.text(((x1 + x2) / 2, (y1 + y2) / 2 - 14), label, font=f(14), fill="#1E40AF", anchor="mm")

    d.text((40, 28), "시스템 구성도", font=f(26, True), fill="#0A0C10")

    y = 250
    box(40, y, 250, 110, "스마트 글라스", "Moziware Cimo · BODA 글라스 앱", "#DBEAFE", "#3B82F6")
    box(360, y, 220, 110, "게이트웨이", "PoC: Cloudflare · 운영: 고객 환경", "#FEF3C7", "#F59E0B")
    box(650, y, 250, 110, "BODA 서버", "PoC: boda-vms.com · 운영: 고객 인프라", "#DCFCE7", "#22C55E")
    box(970, y, 230, 110, "데이터베이스", "SQLite · WarehouseItem/OutboundOrder", "#F1F5F9", "#64748B")
    arrow(290, y + 55, 360, y + 55, "REST/HTTPS")
    arrow(580, y + 55, 650, y + 55, "")
    arrow(900, y + 55, 970, y + 55, "EF Core")

    box(650, 70, 250, 90, "웹 관리자(브라우저)", "입고/출고 관리 · 실시간", "#EDE9FE", "#8B5CF6")
    arrow(775, 160, 775, y, "")
    arrow(775, y, 775, 162, "SignalR 실시간")

    box(650, 470, 250, 90, "고객 ERP / WMS", "주문·재고 연동 (운영)", "#FFFFFF", "#94A3B8", tcol="#475569")
    d.line([775, 360, 775, 470], fill="#CBD5E1", width=3)
    d.text((790, 415), "연동/맞춤개발", font=f(13), fill="#94A3B8", anchor="lm")

    d.text((40, H - 34),
           "※ Cloudflare·boda-vms.com은 PoC 데모 환경입니다. 운영은 고객사 인프라(온프렘/클라우드)와 "
           "ERP·WMS에 맞춰 구성·연동·맞춤개발합니다.",
           font=f(14), fill="#64748B")
    img.save(ARCH)


# ---------------------------------------------------------------- HTML 빌더
def esc(s):
    return _html.escape(str(s))


def data_uri(path):
    if not os.path.exists(path):
        return None
    with open(path, "rb") as fh:
        b64 = base64.b64encode(fh.read()).decode("ascii")
    return "data:image/png;base64," + b64


def img(name):
    return os.path.join(BASE, name)


class Doc:
    def __init__(self):
        self.parts = []
        self.toc = []   # (level, anchor, text)
        self._h1 = 0

    def h1(self, text):
        anchor = "sec%d" % (len(self.toc) + 1)
        self.toc.append((1, anchor, text))
        self.parts.append('<h1 id="%s">%s</h1>' % (anchor, esc(text)))

    def h2(self, text, cls=None):
        c = ' class="%s"' % cls if cls else ""
        self.parts.append("<h2%s>%s</h2>" % (c, esc(text)))

    def p(self, text, cls=None):
        c = ' class="%s"' % cls if cls else ""
        self.parts.append("<p%s>%s</p>" % (c, esc(text)))

    def note(self, text):
        self.p(text, cls="note")

    def lead(self, text):
        self.parts.append('<p class="lead-label">%s</p>' % esc(text))

    def ul(self, items, cls=None):
        c = ' class="%s"' % cls if cls else ""
        lis = "".join("<li>%s</li>" % esc(it) for it in items)
        self.parts.append("<ul%s>%s</ul>" % (c, lis))

    def table(self, headers, rows):
        thead = "".join("<th>%s</th>" % esc(h) for h in headers)
        body = ""
        for row in rows:
            body += "<tr>" + "".join("<td>%s</td>" % esc(v) for v in row) + "</tr>"
        self.parts.append(
            '<table><thead><tr>%s</tr></thead><tbody>%s</tbody></table>' % (thead, body)
        )

    def image(self, path, width_cm=14.5, caption=None):
        uri = data_uri(path)
        if uri is None:
            self.p("[이미지 없음: %s]" % os.path.basename(path), cls="missing")
            return
        px = int(round(width_cm / 2.54 * 96))
        cap = ('<figcaption>▲ %s</figcaption>' % esc(caption)) if caption else ""
        self.parts.append(
            '<figure><img src="%s" style="max-width:min(100%%,%dpx)" alt="%s">%s</figure>'
            % (uri, px, esc(caption or ""), cap)
        )

    def render(self):
        toc_links = "".join(
            '<li><a href="#%s">%s</a></li>' % (a, esc(t)) for (lv, a, t) in self.toc
        )
        body = "\n".join(self.parts)
        return PAGE.format(toc=toc_links, body=body)


# ---------------------------------------------------------------- 스타일/셸
CSS = """
:root{
  --ink:#0F172A; --muted:#5B6472; --line:#E2E8F0; --soft:#F8FAFC;
  --blue:#1E40AF; --blue2:#3B82F6; --accent:#B45309;
  --ok:#15803D; --okbg:#ECFDF5; --cond:#B45309; --condbg:#FFFBEB; --no:#B91C1C; --nobg:#FEF2F2;
}
*{box-sizing:border-box}
html{-webkit-print-color-adjust:exact; print-color-adjust:exact}
body{
  margin:0; color:var(--ink); background:#EEF2F7;
  font-family:"맑은 고딕","Malgun Gothic","Apple SD Gothic Neo","Noto Sans KR",
              system-ui,-apple-system,Segoe UI,sans-serif;
  font-size:15px; line-height:1.7;
}
.page{max-width:900px; margin:24px auto; background:#fff; padding:0 56px 64px;
  box-shadow:0 10px 40px rgba(15,23,42,.10); border-radius:12px; overflow:hidden}

/* 표지 */
.cover{margin:0 -56px 8px; padding:56px 56px 48px; text-align:center; color:#E6ECFF;
  background:radial-gradient(120% 140% at 50% 0%,#1D4ED8 0%,#0B1220 70%)}
.cover .brand{font-size:30px; font-weight:800; letter-spacing:.5px; color:#fff; margin:6px 0}
.cover .sub{font-size:18px; font-weight:700; color:#DBEAFE; margin:4px 0}
.cover .kind{font-size:14px; color:#93B4F5; margin:2px 0 22px}
.cover img{max-width:min(100%,460px); border-radius:10px; box-shadow:0 8px 30px rgba(0,0,0,.45)}
.cover figcaption{color:#93B4F5; font-size:12px; margin-top:8px; font-style:italic}
.cover .date{margin-top:18px; font-size:13px; color:#9FB7E8}

/* 목차 */
nav.toc{background:var(--soft); border:1px solid var(--line); border-radius:10px;
  padding:16px 22px; margin:26px 0}
nav.toc h3{margin:0 0 8px; font-size:14px; color:var(--muted); letter-spacing:.04em; text-transform:uppercase}
nav.toc ol{margin:0; padding-left:20px; columns:2; column-gap:32px}
nav.toc li{margin:3px 0}
nav.toc a{color:var(--blue); text-decoration:none}
nav.toc a:hover{text-decoration:underline}

h1{font-size:23px; color:var(--blue); margin:38px 0 12px; padding-bottom:8px;
  border-bottom:3px solid var(--blue2)}
h2{font-size:17px; color:#0B2A6B; margin:24px 0 8px}
p{margin:8px 0}
.lead-label{font-weight:700; margin:14px 0 4px}
.note{font-size:13px; color:var(--muted); background:var(--soft);
  border-left:3px solid #CBD5E1; padding:8px 12px; border-radius:0 6px 6px 0}
.missing{color:#B91C1C; font-size:13px}

ul{margin:8px 0; padding-left:22px}
li{margin:5px 0}

table{border-collapse:collapse; width:100%; margin:12px 0; font-size:14px}
th,td{border:1px solid var(--line); padding:8px 11px; text-align:left; vertical-align:top}
thead th{background:#EAF1FF; color:#0B2A6B; font-weight:700}
tbody tr:nth-child(even){background:#FAFCFF}

figure{margin:16px 0; text-align:center}
figure img{border:1px solid var(--line); border-radius:8px}
figcaption{font-size:12px; color:var(--muted); font-style:italic; margin-top:6px}

/* 가능/조건부/불가능 */
h2.ok{color:var(--ok)} h2.cond{color:var(--cond)} h2.no{color:var(--no)}
ul.ok,ul.cond,ul.no{list-style:none; padding:14px 16px 14px 18px; border-radius:10px; border:1px solid}
ul.ok{background:var(--okbg); border-color:#A7F3D0}
ul.cond{background:var(--condbg); border-color:#FDE68A}
ul.no{background:var(--nobg); border-color:#FECACA}
ul.ok li::before{content:"✅ "} ul.cond li::before{content:"△ "} ul.no li::before{content:"✕ "}
ul.ok li,ul.cond li,ul.no li{margin:7px 0}

footer{margin-top:40px; padding-top:14px; border-top:1px solid var(--line);
  font-size:12px; color:var(--muted); text-align:center}

@media print{
  body{background:#fff; font-size:12px}
  .page{max-width:none; margin:0; padding:0 8mm; box-shadow:none; border-radius:0}
  nav.toc{display:none}
  .cover{margin:0 -8mm 0; border-radius:0; break-after:page}
  h1{break-before:page; break-after:avoid}
  h2{break-after:avoid}
  figure,table{break-inside:avoid}
  a{color:inherit; text-decoration:none}
}
@media (max-width:640px){
  .page{padding:0 18px 40px}
  .cover{margin:0 -18px 8px; padding:40px 18px}
  nav.toc ol{columns:1}
}
"""

PAGE = """<!DOCTYPE html>
<html lang="ko">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>BODA 글라스 × BODA.VMS.Web — PoC 소개서 & 매뉴얼</title>
<style>__CSS__</style>
</head>
<body>
<div class="page">
  <header class="cover">
    <div class="brand">BODA 글라스 &times; BODA.VMS.Web</div>
    <div class="sub">스마트 글라스 입출고 관리 솔루션</div>
    <div class="kind">제품 소개서 &amp; 사용 매뉴얼</div>
    __COVER_IMG__
    <div class="date">고객 PoC 자료 · 2026-06-17</div>
  </header>

  <nav class="toc">
    <h3>목차</h3>
    <ol>{toc}</ol>
  </nav>

  {body}

  <footer>BODA 글라스 × BODA.VMS.Web · PoC 소개서 &amp; 매뉴얼 · 2026-06-17</footer>
</div>
</body>
</html>
"""


# ---------------------------------------------------------------- 문서 내용 (make_doc.py 와 동일)
def build():
    make_arch()
    doc = Doc()

    # 1. 개요
    doc.h1("1. 개요")
    doc.p("BODA 글라스는 단안 스마트 글라스(Moziware Cimo)에서 바코드 스캔과 음성 명령만으로 "
          "물류 입고 위치를 조회하고 출고 주문을 핸즈프리로 피킹하는 네이티브 Android 앱입니다. "
          "글라스는 화면·음성·스캔만 담당하는 얇은 클라이언트로, 데이터/로직은 백엔드 서버가 처리하고 "
          "작업 결과는 웹 관리 화면에 실시간으로 반영됩니다.")
    doc.note("※ 본 PoC는 데모 환경(서버 boda-vms.com, Cloudflare)에서 검증한 것이며, 실제 운영은 "
             "고객사 인프라(온프렘/클라우드)와 ERP·WMS에 맞춰 구성·연동·맞춤개발됩니다.")
    doc.lead("핵심 가치")
    doc.ul([
        "핸즈프리(음성 전용): 화면 터치 없이, 음성 명령과 내장 스캐너만으로 모든 작업 수행 — 단안 HUD라 터치 입력은 사용하지 않음",
        "현장 즉시성: 입고 위치를 대형 고대비 화면에 즉시 표시, 출고는 위치로 직접 안내",
        "검증된 피킹: 제품 바코드 스캔으로 오피킹 방지 + 수량 카운트 + 출하 확정",
        "유연한 연동: 신규 서버 없이 백엔드 확장으로 구현 — 고객사 VMS/ERP/WMS에 맞춰 연동·맞춤개발 가능",
        "실시간 가시성: 글라스 작업이 웹 관리 대시보드에 새로고침 없이 즉시 반영",
    ])

    # 2. 시스템 구성
    doc.h1("2. 시스템 구성")
    doc.image(ARCH, width_cm=16, caption="전체 시스템 구성도")
    doc.lead("구성 요소")
    doc.table(["구성 요소", "역할/기술"], [
        ["스마트 글라스", "Moziware Cimo (RealWear 기반, Android 10) · 내장 바코드 스캐너 · WearHF 음성"],
        ["BODA 글라스 앱", "네이티브 Android(Kotlin) · 얇은 클라이언트(화면·음성·스캔만)"],
        ["백엔드 서버", "ASP.NET Core + Blazor · PoC: boda-vms.com / 운영: 고객 인프라 배포 또는 고객 시스템 연동"],
        ["게이트웨이", "PoC: Cloudflare(HTTPS/Tunnel) / 운영: 고객 네트워크·보안 정책에 맞춤"],
        ["데이터베이스", "PoC: SQLite · 운영: 고객 표준 DB / ERP·WMS 데이터 연동"],
        ["실시간", "SignalR — 글라스 작업 → 웹 관리 화면 즉시 반영"],
    ])
    doc.lead("데이터 흐름")
    doc.ul([
        "글라스 → 서버: HTTPS REST 호출(/api/glass/...) — 익명 + X-API-Key 보호",
        "서버 → DB: 입고 위치 조회 / 출고 주문·피킹 상태 저장",
        "서버 → 웹 관리자: SignalR로 출고 상태 변경 실시간 푸시",
        "(운영) 고객 ERP/WMS ↔ 서버: 주문·재고 연동(맞춤개발)",
    ])
    doc.note("※ PoC 환경 안내: boda-vms.com·Cloudflare·SQLite는 시연용 데모 구성입니다. "
             "운영 도입 시 백엔드는 고객사 인프라(온프렘/클라우드)에 배포하거나 고객사 ERP/WMS와 연동하며, "
             "네트워크·보안·데이터 표준은 고객 환경에 맞춰 구성합니다.")

    # 3. 주요 기능
    doc.h1("3. 주요 기능")
    doc.table(["기능", "설명"], [
        ["입고 위치 조회", "제품 바코드 스캔 → '입고/입고제품' → 보관 위치를 대형 표시"],
        ["입고 확정(적치)", "(글라스) 위치 확인 후 '입고 확정' → 재고 누적 + 입고 이력 기록"],
        ["입고 위치 등록", "(웹) 바코드별 위치(Zone-Rack-Level-Bin)·좌표 등록/수정, 재고 표시"],
        ["출고 목록", "(글라스) 활성 출고 주문을 보고 음성으로 선택 — 커서(다음/이전/열기)·번호"],
        ["출고 피킹", "주문 선택 → 라인별 위치 안내 → 제품 스캔 검증 + 수량 → 출하 확정"],
        ["출고 오더 관리", "(웹) 출고 주문·라인·목적지 등록/수정, 진행 상태 확인"],
        ["실시간 반영", "글라스 입고 확정·피킹·출하 확정이 웹 관리 화면에 즉시 표시"],
    ])

    # 4. 스마트 글라스 앱 매뉴얼
    doc.h1("4. 스마트 글라스 앱(BODA 글라스) 매뉴얼")

    doc.h2("4.1 설치 및 실행")
    doc.ul([
        "APK를 기기에 사이드로드(설치) — 기기 저장소에 영구 설치(USB 분리·재부팅 후에도 유지)",
        "런처(앱 목록)에서 'BODA 글라스' 아이콘 실행, 또는 음성으로 앱 실행",
        "내 프로그램(My Programs)에서 'BODA 글라스'라고 말해 실행 — 항목 번호는 앱 추가/삭제 시 바뀌므로 이름 호출을 권장",
        "Wi-Fi 인터넷이 boda-vms.com(443)에 연결되면 어디서든 사용 가능",
    ])
    doc.image(img("screenshot-icon.png"), width_cm=13, caption="앱 아이콘 'BODA 글라스' (앱 정보 화면)")

    doc.h2("4.2 메인 화면")
    doc.image(img("screenshot-newcimo.png"), width_cm=14.5, caption="메인 화면 — 버튼 텍스트가 곧 음성 명령")
    doc.note("모든 화면은 음성 명령으로만 조작합니다(단안 HUD라 화면 터치 입력 없음). "
             "RealWear/WearHF의 '보이는 대로 말하기' 방식으로, 화면에 보이는 버튼 텍스트가 곧 음성 명령입니다.")
    doc.lead("버튼(=음성 명령)")
    doc.table(["명령", "동작"], [
        ["바코드 스캔", "내장 스캐너로 제품 바코드 스캔"],
        ["입고 / 입고제품", "스캔한 바코드의 입고(보관) 위치 조회·표시"],
        ["입고 확정", "위치 확인 후 적치 확정 — 재고 누적 + 웹 실시간 반영(위치 조회 후 활성)"],
        ["출고", "출고 목록 화면으로 이동"],
        ["처음으로", "대기 상태로 초기화"],
        ["종료", "앱 완전 종료"],
    ])

    doc.h2("4.3 입고 위치 조회")
    doc.image(img("screenshot-inbound-map.png"), width_cm=14.5,
              caption="입고 위치 조회 — 2D 미니맵(구역/랙 핀) + 대형 위치 코드 + 방향 힌트")
    doc.p("① '바코드 스캔' → 제품 바코드 스캔  ② '입고' 또는 '입고제품'  ③ 보관 위치가 대형 고대비로 표시되고, "
          "왼쪽 2D 미니맵에 구역/랙 핀과 '○구역 · ○랙 · ○단 · ○칸' 힌트가 함께 표시됩니다. "
          "등록되지 않은 바코드는 빨간색으로 '등록되지 않은 바코드'를 안내합니다.")
    doc.p("④ 그 자리에 적치한 뒤 '입고 확정'이라고 말하면 입고가 확정됩니다 — 해당 품목의 "
          "재고가 1 증가하고 입고 이력이 기록되며, 웹 관리 화면(입고 위치 등록)의 '재고'가 새로고침 없이 즉시 반영됩니다. "
          "'입고 확정'은 위치를 조회한 뒤에만 활성화되어 오확정을 방지합니다. (출고의 '출하 확정'과 대칭)")
    doc.note("※ 설계 의도 — 입고는 '현품 주도'입니다. 도착한 물건을 바로 스캔→위치 확인→확정하므로 별도의 '입고 목록'이 "
             "없습니다. 반면 출고는 '오더 주도'라 처리할 주문이 미리 정해져 있어 '출고 목록'에서 선택합니다. "
             "사전 계획 입고가 필요하면 '입고 예정(ASN) 목록 → 스캔 검증 적치'를 출고와 대칭 구조로 확장할 수 있습니다(§8.2).")

    doc.h2("4.4 출고 — 주문 목록")
    doc.image(img("doc-orderlist.png"), width_cm=14.5, caption="출고 목록 — 활성 주문 선택")
    doc.lead("주문 선택 방법 (모두 음성·핸즈프리)")
    doc.table(["방법", "사용법"], [
        ["커서(권장)", "'다음'/'이전'으로 강조 이동 → '열기'"],
        ["번호", "각 행 배지 번호로 '항목 1 열기'처럼 발화"],
        ["주문 스캔", "주문/송장 바코드를 스캔해 바로 진입"],
    ])

    doc.h2("4.5 출고 — 피킹(스캔 검증)")
    doc.image(img("screenshot-minimap.png"), width_cm=14.5,
              caption="피킹 화면 — 2D 미니맵·위치·품목·수량 / 라인별 제품 스캔 검증")
    doc.ul([
        "각 라인의 '위치'로 이동 → 그 자리에서 '스캔'으로 제품 바코드 스캔",
        "맞는 품목이면 수량이 1씩 증가(예: 0/3 → 3/3), 다 채우면 자동으로 다음 라인",
        "다른 품목을 스캔하면 빨간색 '이 주문에 없는 품목'으로 차단(오피킹 방지)",
        "'이전'/'다음'으로 라인 이동, 상단 '취소'로 언제든 홈 복귀",
    ])

    doc.h2("4.6 출고 — 출하 확정")
    doc.image(img("screenshot-pick-dest.png"), width_cm=14.5,
              caption="출하 확정 화면 — 전 라인 피킹 시 '출하 확정' 활성, 출고 목적지 표시")
    doc.p("전 라인 피킹이 끝나면 '출하 확정' 버튼이 활성화되고, 확정 시 주문 상태가 '완료(Done)'로 바뀌며 "
          "출고 목적지(도크/출고대)가 표시됩니다. 미완료 상태에서는 확정이 비활성으로 보호됩니다.")

    doc.h2("4.7 음성 명령 빠른 참조")
    doc.table(["화면", "음성 명령"], [
        ["메인", "바코드 스캔 · 입고 · 입고제품 · 입고 확정 · 출고 · 처음으로 · 종료"],
        ["출고 목록", "이전 · 다음 · 열기 · 항목 N 열기 · 주문 스캔 · 새로고침 · 취소"],
        ["피킹", "스캔 · 이전 · 다음 · 출하 확정 · 닫기 · 취소"],
        ["전역(WearHF)", "뒤로 가기 · 명령어"],
    ])

    # 5. 웹 관리
    doc.h1("5. 웹 관리 (BODA.VMS.Web)")
    doc.p("boda-vms.com에 로그인 후, 좌측 메뉴에서 입출고 마스터 데이터를 관리합니다. "
          "글라스의 작업은 이 화면에 실시간으로 반영됩니다.")
    doc.h2("5.1 입고 위치 등록")
    doc.ul([
        "메뉴: '입고 위치 등록'",
        "바코드 · 품목코드/명 · 위치(Zone-Rack-Level-Bin) · 3D 좌표 등록/수정/삭제",
        "여기 등록된 바코드를 글라스에서 스캔하면 즉시 위치가 조회됨",
        "'재고' 열: 글라스 '입고 확정' 시 해당 품목 재고가 실시간으로 증가(새로고침 불필요)",
    ])
    doc.h2("5.2 출고 오더 관리")
    doc.ul([
        "메뉴: '출고 피킹'",
        "출고 주문(주문번호·고객·배송지·출고 목적지) + 피킹 라인(바코드·수량) 등록/수정",
        "상태(대기 → 피킹중 → 완료)가 글라스 작업에 따라 실시간 갱신",
    ])
    doc.h2("5.3 실시간(SignalR)")
    doc.p("글라스에서 제품을 스캔(피킹)하거나 출하 확정하면, 열려 있는 웹 관리 화면의 해당 주문 상태가 "
          "새로고침 없이 '대기 → 피킹중 → 완료'로 즉시 바뀝니다. 마찬가지로 '입고 확정' 시에는 입고 위치 등록 화면의 "
          "'재고'가 즉시 증가합니다. 운영자는 입고·출고 현장 진행 상황을 실시간으로 모니터링할 수 있습니다.")
    doc.note("※ 웹 관리 화면 캡처는 시연 환경(로그인 계정)에서 추가 첨부 예정.")

    # 6. PoC 시연 시나리오
    doc.h1("6. PoC 시연 시나리오")
    doc.lead("준비물")
    doc.ul([
        "BODA 글라스 설치된 Moziware Cimo (Wi-Fi 연결)",
        "시연용 주문 QR / 제품 바코드(QR), 웹 관리 화면(브라우저, 로그인)",
    ])
    doc.lead("시연 순서")
    doc.table(["단계", "내용"], [
        ["① 입고 조회", "제품 바코드 스캔 → '입고' → 보관 위치 대형 표시"],
        ["② 입고 확정", "'입고 확정' → 웹 '입고 위치 등록' 화면의 재고가 새로고침 없이 +1 되는지 확인"],
        ["③ 출고 진입", "'출고' → 출고 목록에서 음성으로 주문 선택(커서/번호) 또는 '주문 스캔'"],
        ["④ 피킹", "라인별 위치 안내 → 제품 스캔으로 수량 채움(오피킹 차단 시연)"],
        ["⑤ 출하 확정", "전 라인 완료 → '출하 확정' → 목적지 표시(상태 '완료')"],
        ["⑥ 실시간 확인", "웹 '출고 피킹' 화면이 새로고침 없이 '피킹중 → 완료'로 변하는지 확인"],
    ])
    doc.lead("시연용 주문 QR 예시 (SO-2026-001)")
    doc.image(img("qr-SO-2026-001.png"), width_cm=4.5, caption="주문 QR: SO-2026-001 (가나상사, 3개 라인)")
    doc.lead("시드 데이터(예시)")
    doc.table(["주문", "고객", "목적지", "라인"], [
        ["SO-2026-001", "가나상사", "3번 도크 / 출고대 B", "P-1001×3, P-1003×2, P-1007×1"],
        ["SO-2026-002", "대한물산", "1번 도크 / 출고대 A", "P-1002×5, P-1010×4"],
    ])

    # 7. 운영/도입 요건
    doc.h1("7. 운영 / 도입 요건")
    doc.ul([
        "기기: Moziware Cimo(Android 10) — 내장 바코드 스캐너 / WearHF 음성",
        "네트워크: PoC는 Wi-Fi로 boda-vms.com(HTTPS) 접속. 운영은 고객 네트워크 정책에 맞춰 "
        "사내망(온프렘) 또는 고객 클라우드로 구성(앱 서버 주소만 교체)",
        "백엔드 배포: PoC는 데모 서버. 운영은 고객 인프라에 배포하거나 고객 기존 시스템(VMS/ERP/WMS)과 연동",
        "보안: 핸즈프리 익명 + X-API-Key(운영 시 키 강제). HTTPS 종단·게이트웨이는 고객 보안 정책에 맞춤",
        "데이터 출처: 입고 위치·출고 주문 공급(고객 ERP/WMS 연동·CSV·수기) 방식 확정이 도입 1순위 과제",
    ])

    # 8. 기능 범위 (가능/조건부/불가능)
    doc.h1("8. 기능 범위 — 가능 / 조건부 / 불가능")
    doc.p("고객 기대치를 정확히 맞추기 위해, 현재 PoC에서 동작하는 범위 · 추가 데이터/개발이 필요한 범위 · "
          "기기(단안 글라스) 한계로 불가능한 범위를 구분합니다.")
    doc.h2("8.1 가능 — PoC에서 동작", cls="ok")
    doc.ul([
        "바코드/QR 스캔 → 입고 위치 즉시 조회(대형 표시 + 2D 미니맵 + 방향 힌트)",
        "입고 확정(적치): 위치 확인 후 '입고 확정' → 재고 누적 + 입고 이력 + 웹 실시간 반영(출고 출하확정과 대칭)",
        "음성·핸즈프리 조작(화면 버튼 텍스트가 곧 음성 명령)",
        "출고 피킹: 주문 목록 음성 선택 → 위치 안내 → 제품 스캔 검증 + 수량 카운트 → 출하 확정",
        "오피킹 방지(잘못된 품목 스캔 차단) · 라인별 진행률",
        "웹 관리(입고 위치·출고 오더 등록/수정) + 실시간(SignalR) 상태 반영",
        "앱 내 위치 시각화 — 구역/랙 도식 미니맵",
    ], cls="ok")
    doc.h2("8.2 조건부 가능 — 추가 데이터/개발 필요", cls="cond")
    doc.ul([
        "실제 창고 배치도 미니맵: 2D 평면도 이미지(또는 구역/랙 좌표) + 각 위치 실측 좌표 필요",
        "입고 예정(ASN) 기반 입고: 입고예정 목록 → 선택 → 라인별 스캔 검증 적치(출고 오더/피킹과 대칭) — 추가 데이터/개발",
        "고객 ERP/WMS 연동(주문·재고 동기화): 연동 인터페이스 개발",
        "체크인(웨이포인트) 길안내: 통로/랙에 QR 부착 + 앱 보강 — 추가 HW 없이, 스캔 시점마다 다음 위치 안내",
        "재고 차감 · 작업자 배정(내 작업) · 피킹 리스트 인쇄/출고 리포트",
        "한국어 음성 인식 정확도: 명령어·소음 환경 튜닝(짧은 단어 → 또렷한 문구)",
    ], cls="cond")
    doc.h2("8.3 현재 불가능 — 기기(단안 HUD) 한계", cls="no")
    doc.ul([
        "실시간 연속 실내 길안내(이동에 따른 위치 추적): 실내는 GPS 불가 + 단안 HUD라 공간추적(SLAM) 없음 "
        "→ BLE/UWB 등 실내 측위 인프라 구축 시에만 가능(별도 프로젝트)",
        "XR/AR 오버레이(실제 선반에 화살표·하이라이트를 공간 고정): 공간 AR 기기(HoloLens/Magic Leap 등) 필요 "
        "— Moziware Cimo(단안 보조 디스플레이)로는 불가",
        "몰입형 VR · 정밀 3D 뷰: 단안 · 좁은 FOV(20°)로 가독성이 떨어져 부적합",
    ], cls="no")
    doc.note("※ 위 '불가능' 항목은 Moziware Cimo(단안 HUD) 기준입니다. 공간 AR 기기/실내 측위 인프라를 "
             "도입하면 일부는 향후 구현 가능합니다.")

    # 9. 향후 확장
    doc.h1("9. 향후 확장 로드맵")
    doc.ul([
        "고객 ERP/WMS 연동: 판매오더·재고 자동 동기화(시드/수기 → 운영 데이터)",
        "재고 차감: 출하 확정 시 보관 위치 재고 반영",
        "작업자 배정: 로그인/배정 기반 '내 작업' 목록",
        "2차 목표(3D): 입고/피킹 위치를 공장 도면에 3D 하이라이트",
        "라벨/리포트: 피킹 리스트 인쇄, 출고 실적 리포트",
    ])

    cover_uri = data_uri(img("screenshot-newcimo.png"))
    cover_html = (
        '<figure><img src="%s" alt="BODA 글라스 메인 화면">'
        '<figcaption>BODA 글라스 메인 화면 (Moziware Cimo, 854×480)</figcaption></figure>'
        % cover_uri if cover_uri else ""
    )
    page = doc.render().replace("__CSS__", CSS).replace("__COVER_IMG__", cover_html)
    with open(OUT, "w", encoding="utf-8") as fh:
        fh.write(page)
    size_kb = round(os.path.getsize(OUT) / 1024)
    print("저장:", OUT, "(%d KB)" % size_kb)


build()
