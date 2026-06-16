# BodaGlass.StubApi — `/api/glass` PoC 스텁

글라스 앱(`io.vasim.glass`)을 붙여 흐름을 검증하기 위한 **독립 실행 .NET Minimal API 스텁**.
실제로는 이 엔드포인트/엔티티를 기존 **BODA.VMS.Web** 에 추가한다(아키텍처 문서 §10, §13).

- 외부 NuGet 패키지 **0개**(프레임워크 참조만) → 오프라인에서도 `dotnet run` 가능
- 데이터는 인메모리 시드(`SeedData.cs`). 실 서버에서는 EF Core `WarehouseItem` 으로 교체

## 실행

```powershell
dotnet run --project server/BodaGlass.StubApi
# 기본 http://0.0.0.0:5000 (launchSettings)
```

- **에뮬레이터**에서 호스트 PC = `10.0.2.2` → 앱 기본 `BASE_URL=http://10.0.2.2:5000` 그대로 동작
- **실기(같은 Wi-Fi)** 에서는 PC의 LAN IP(예: `http://192.168.0.x:5000`)로 앱 `BASE_URL` 변경.
  방화벽에서 5000 인바운드 허용 필요

## 엔드포인트 계약

```
GET /api/glass/inbound-location?barcode={바코드}&mode={입고|입고제품}
→ 200 { itemCode, itemName, locationText, coord{ x, y, z } }   # camelCase = Kotlin DTO 와 자동 매칭
→ 400 { message: "barcode 파라미터가 필요합니다" }
→ 404 { message: "등록되지 않은 바코드" }

GET /api/glass/ping → 200 { ok: true }   # 앱/네트워크 디버깅용
```

검증된 응답 예:

```
GET /api/glass/inbound-location?barcode=8801234567890&mode=입고
200 {"itemCode":"A-1001","itemName":"포장박스(대)","locationText":"A동 3층 R-12-04","coord":{"x":12,"y":4,"z":3}}
```

시드 바코드: `8801234567890`, `8809876543210`, `1234567890128`, `0000000000017`

## 인증 (X-API-Key)

핸즈프리라 익명 호출을 허용하되 `X-API-Key` 헤더로 보호한다.

- `appsettings.json` 의 `Glass:ApiKey` (또는 env `Glass__ApiKey`) 설정 시 헤더 강제
- 비어 있으면(기본) 검사 비활성 — 개발 편의
- 운영: 강한 키 설정 + 앱 `BuildConfig.API_KEY` 와 일치

## 실 서버(BODA.VMS.Web) 이식 시

1. `WarehouseItem` 을 EF Core 엔티티로, `Barcode` 에 인덱스
2. `InMemoryWarehouseRepository` → DbContext 조회로 교체
   (`_db.WarehouseItems.AsNoTracking().FirstOrDefault(w => w.Barcode == barcode)`)
3. `Program.cs` 의 `MapGroup("/api/glass")` 블록을 기존 앱 라우팅에 합치기
4. 데이터 출처(WMS/ERP 동기화·CRUD·CSV) 확정 — 기술보다 선행하는 1순위 과제(§13-4)
