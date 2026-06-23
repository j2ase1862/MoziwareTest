# `/api/glass` API 계약서 — 운영팀(BODA.VMS.Web) 전달용

스마트 글라스(Moziware cımō) 앱 `io.vasim.glass` 가 호출하는 **HTTP API 계약**입니다.
앱은 화면·음성·바코드만 담당하는 얇은 클라이언트이고, 데이터·로직은 전부 이 API에 의존합니다.
운영 서버 **BODA.VMS.Web(.NET)** 에 아래 엔드포인트를 **이 계약 그대로** 구현하면 됩니다.

> 레퍼런스 구현: `server/BodaGlass.StubApi/` (인메모리 스텁, NuGet 0개).
> 스텁으로 앱 동작이 검증돼 있으며, 운영은 동일 응답 형태를 EF Core/WMS 연동으로 제공하면 됩니다.

---

## 1. 공통 규약

| 항목 | 내용 |
|---|---|
| Base URL | 운영 HTTPS (예: `https://boda-vms.com`). 앱 `BuildConfig.BASE_URL` 로 주입 |
| 경로 접두 | 모든 엔드포인트 `**/api/glass**` 하위 |
| 직렬화 | JSON, **camelCase**. (.NET `System.Text.Json` 기본 ↔ Kotlin `kotlinx.serialization`) |
| 관용성 | 앱은 `ignoreUnknownKeys=true` — **응답에 필드가 더 있어도 무시**, **없으면 기본값**(문자열 `""`, 숫자 `0`, bool `false`, 객체 `null`) 사용. 따라서 필드 추가는 하위호환 |
| 인코딩 | UTF-8. 한글 값 그대로 |
| 메서드 | 조회 = `GET`(쿼리스트링), 변경 = `POST`(JSON 바디) |

### 인증 — `X-API-Key`

핸즈프리 특성상 익명 호출을 허용하되 헤더 키로 보호합니다.

- 서버에 키가 설정돼 있으면 모든 `/api/glass/*` 요청에 `X-API-Key: {키}` 헤더 **강제**
- 불일치 시 **401** `{ "message": "인증 실패" }`
- 앱은 `BuildConfig.API_KEY` 값을 헤더로 전송(비어 있으면 미전송). **운영 키와 일치**시킬 것

### 오류 모델

| 코드 | 의미 | 바디 |
|---|---|---|
| 200 | 성공 | 각 엔드포인트 DTO |
| 400 | 잘못된 요청(필수 파라미터 누락) | `{ "message": "..." }` |
| 401 | 인증 실패 | `{ "message": "인증 실패" }` |
| 404 | 대상 없음(미등록 바코드/주문) | `{ "message": "..." }` |
| 5xx | 서버 오류 | 앱은 "서버 오류 (HTTP {code})" 로 처리 |

### 공통 enum / 포맷

- **콜드체인 `coldChain`**: `FROZEN`(냉동) · `CHILLED`(냉장) · `AMBIENT`(상온)
- **출고 상태 `status`**: `Ready`(대기) · `Picking`(피킹중) *(그 외 값은 무시·대기 취급)*
- **날짜**: `yyyy-MM-dd` 문자열 (예: `2026-06-25`)

---

## 2. 엔드포인트 목록

| # | 메서드 · 경로 | 용도 | 스텁 구현 |
|---|---|---|---|
| 1 | `GET /api/glass/inbound-location` | 바코드 → 입고 위치 | ✅ |
| 2 | `GET /api/glass/orders` | 출고 목록(요약) | ✅ |
| 3 | `GET /api/glass/pick-list` | 출고 주문 피킹 목록 | ⛔ 미구현(계약만) |
| 4 | `GET /api/glass/summary` | 홈 대시보드 집계 | ✅ |
| 5 | `GET /api/glass/inventory` | 재고 목록(콜드체인·FEFO) | ✅ |
| 6 | `POST /api/glass/inbound-confirm` | 입고(적치) 확정 | ⛔ 미구현(계약만) |
| 7 | `POST /api/glass/pick-confirm` | 피킹 스캔 검증 확정 | ⛔ 미구현(계약만) |
| 8 | `POST /api/glass/ship-confirm` | 출하 확정 | ⛔ 미구현(계약만) |
| 9 | `GET /api/glass/ping` | 헬스 체크 | ✅ |

> "스텁 구현" = 레퍼런스 스텁에 이미 있는지 여부. ⛔ 항목도 앱은 **이미 호출**하므로 운영 구현이 필요합니다.

---

## 3. 조회(GET) 상세

### 1) `GET /api/glass/inbound-location`

바코드로 입고 적치 위치를 조회.

**쿼리**: `barcode` (필수, 스캔 원문) · `mode` (`입고` | `입고제품` — 모드별 조회규칙이 갈리면 서버가 분기)

**200** — `InboundLocation`
```jsonc
{
  "itemCode":    "A-1001",        // string | null
  "itemName":    "포장박스(대)",   // string | null
  "locationText":"A동 3층 R-12-04",// string (대형 표시 + 미니맵 파싱 대상)
  "coord": { "x": 12, "y": 4, "z": 3 }   // object | null (3D 하이라이트용, 1차에선 미사용)
}
```
**400** `{ "message": "barcode 파라미터가 필요합니다" }` · **404** `{ "message": "등록되지 않은 바코드" }`

---

### 2) `GET /api/glass/orders`

활성 출고 주문 목록(요약). 출고 목록 화면.

**200** — `GlassOrder[]`
```jsonc
[
  {
    "orderNo":     "OUT-1182",     // string
    "customerName":"이마트 죽전",   // string | null
    "destination": "죽전점",        // string | null
    "lineCount":   12,             // int (품목 수)
    "status":      "Picking"       // "Ready" | "Picking"
  }
]
```

---

### 3) `GET /api/glass/pick-list`

출고 주문 1건의 피킹 목록(어디서 무엇을 몇 개).

**쿼리**: `orderNo` (필수)

**200** — `PickList`
```jsonc
{
  "orderNo":     "OUT-1182",
  "customerName":"이마트 죽전",     // string | null
  "shipTo":      "...",            // string | null
  "destination": "죽전점",          // string | null
  "status":      "Picking",
  "lines": [
    {
      "seq":         1,            // int (순번 = 음성 "항목 N")
      "barcode":     "8801234567890",
      "itemCode":    "A-1001",
      "itemName":    "포장박스(대)",
      "qty":         24,           // int (지시 수량)
      "pickedQty":   0,            // int (집은 수량 → 진행률 = pickedQty/qty)
      "locationText":"A동 3층 R-12-04"
    }
  ]
}
```
**404** `{ "message": "등록되지 않은 주문번호" }`

---

### 4) `GET /api/glass/summary`

홈 대시보드 집계 4종.

**200** — `GlassSummary`
```jsonc
{
  "inboundWaiting":  12,   // int  입고 대기(예정) 건수
  "outboundWaiting": 8,    // int  출고 대기 건수
  "stockSku":        1240, // int  재고 SKU 수(품목코드 distinct)
  "expiringCount":   3     // int  유통기한 임박 건수(아래 FEFO 기준)
}
```

---

### 5) `GET /api/glass/inventory`

재고 목록. **식품 콜드체인 운용 핵심.**

**쿼리** (모두 선택):
- `coldChain` = `FROZEN` | `CHILLED` | `AMBIENT` — 온도대 필터
- `expiringOnly` = `true` | `false` — 유통기한 임박(FEFO)만
- `query` = 품목코드/품명 부분일치 검색

**200** — `StockItem[]` *(서버가 유통기한 오름차순=FEFO 정렬 권장)*
```jsonc
[
  {
    "itemCode":    "C-2002",
    "itemName":    "요거트 90g×4",
    "qty":         120,            // int 현재고
    "unit":        "PACK",         // string 단위(EA/BOX/PACK…)
    "locationText":"C구역 06랙 1단 02칸",
    "coldChain":   "CHILLED",      // FROZEN|CHILLED|AMBIENT
    "lotNo":       "L240610",      // string 로트번호(추적성)
    "mfgDate":     "2026-06-15",   // yyyy-MM-dd 제조일
    "expiryDate":  "2026-06-25",   // yyyy-MM-dd 유통기한
    "daysToExpiry":2,              // int  ★서버 계산 (음수=만료)
    "fefoFlag":    true            // bool ★서버 계산 (임박 여부)
  }
]
```

> ★ **`daysToExpiry` / `fefoFlag` 는 반드시 서버가 계산**해 내려줍니다.
> 임박 기준이 품목·거래처별로 다를 수 있어(예: 유통기한 1/3 룰) 클라이언트가 판단하지 않습니다.
> 스텁 기준은 `daysToExpiry ≤ 7` → `fefoFlag=true`. 운영은 자체 정책으로 대체하세요.

---

### 9) `GET /api/glass/ping`

**200** `{ "ok": true }` — 앱/네트워크 디버깅용.

---

## 4. 변경(POST) 상세

모두 `Content-Type: application/json`. 실패(네트워크/미등록 등) 시 앱은 null 처리 → 재시도 안내.

### 6) `POST /api/glass/inbound-confirm` — 입고(적치) 확정

**요청**
```jsonc
{ "barcode": "8801234567890", "qty": 1 }   // qty 기본 1
```
**200** — `InboundConfirmResult`
```jsonc
{
  "confirmed":   true,            // bool 확정 성공
  "message":     "",             // string 안내
  "itemCode":    "A-1001",
  "itemName":    "포장박스(대)",
  "locationText":"A동 3층 R-12-04",
  "qty":         1,
  "stockAfter":  124             // int 확정 후 재고 수량(화면 표기)
}
```

### 7) `POST /api/glass/pick-confirm` — 피킹 스캔 검증 확정

**요청**
```jsonc
{ "orderNo": "OUT-1182", "barcode": "8801234567890", "qty": 1 }
```
**200** — `PickConfirmResult`
```jsonc
{
  "matched":        true,         // bool 스캔 바코드가 지시 라인과 일치
  "alreadyComplete":false,        // bool 이미 완료된 라인
  "allPicked":      false,        // bool 주문 전체 피킹 완료
  "message":        "",
  "pickList":       { /* 갱신된 PickList (위 3번 형태) */ }
}
```

### 8) `POST /api/glass/ship-confirm` — 출하 확정

**요청** *(앱은 빈 barcode/0 qty 로 전송)*
```jsonc
{ "orderNo": "OUT-1182", "barcode": "", "qty": 0 }
```
**200** — `PickList` (위 3번 형태, 갱신본)

---

## 5. 운영 구현 시 체크리스트

1. **데이터 출처 확정(최우선)** — 재고는 **로트 단위**(유통기한·로트번호)로 관리되어야 `inventory`/FEFO가 의미를 가짐. WMS/ERP 동기화 방식 결정.
2. **FEFO 계산을 서버에서** — `daysToExpiry`/`fefoFlag`/정렬을 서버가 책임. 임박 기준(일수, 1/3 룰 등) 정책화.
3. **콜드체인 필드** — 품목·로케이션 마스터에 온도대(`coldChain`) 부여.
4. **집계(summary)** — 입고예정/출고대기/재고SKU/임박 카운트의 산출 쿼리 정의.
5. **인증** — `X-API-Key` 강한 키 설정 + 앱 `BuildConfig.API_KEY` 동기화. HTTPS 강제.
6. **상태 enum 합의** — `status`(Ready/Picking), `coldChain`(FROZEN/CHILLED/AMBIENT) 값 고정.
7. **하위호환** — 필드 추가는 안전(앱이 무시). 필드 **이름 변경/삭제는 파괴적** → 사전 합의.

---

*레퍼런스 스텁: `server/BodaGlass.StubApi/` · 앱 DTO: `app/src/main/java/io/vasim/glass/{InboundLocation,PickList,Inbound,Stock}.kt`*
