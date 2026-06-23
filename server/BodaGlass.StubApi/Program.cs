using BodaGlass.StubApi;

var builder = WebApplication.CreateBuilder(args);

builder.Services.AddSingleton<IWarehouseRepository, InMemoryWarehouseRepository>();

// 핸즈프리 글라스용 보호 키. 설정돼 있으면 X-API-Key 헤더를 강제하고,
// 비어 있으면(기본) 검사를 건너뛴다(개발 편의). 운영에서는 반드시 설정할 것.
//   설정: appsettings.json 의 "Glass:ApiKey", 또는 env  Glass__ApiKey=xxxx
var apiKey = builder.Configuration["Glass:ApiKey"] ?? "";

var app = builder.Build();

// 글라스 전용 그룹. (Andon/키오스크 패턴 — 익명 허용 + X-API-Key 필터)
var glass = app.MapGroup("/api/glass");

glass.AddEndpointFilter(async (ctx, next) =>
{
    if (!string.IsNullOrEmpty(apiKey))
    {
        var provided = ctx.HttpContext.Request.Headers["X-API-Key"].ToString();
        if (!string.Equals(provided, apiKey, StringComparison.Ordinal))
            return Results.Json(new { message = "인증 실패" }, statusCode: StatusCodes.Status401Unauthorized);
    }
    return await next(ctx);
});

// GET /api/glass/inbound-location?barcode={바코드}&mode={입고|입고제품}
glass.MapGet("/inbound-location", (string? barcode, string? mode, IWarehouseRepository repo) =>
{
    if (string.IsNullOrWhiteSpace(barcode))
        return Results.BadRequest(new { message = "barcode 파라미터가 필요합니다" });

    // mode(입고/입고제품)는 계약상 받되, PoC 에서는 분기하지 않는다.
    // 실 서버에서 모드별 조회 규칙이 갈리면 여기서 분기한다.
    _ = mode;

    var item = repo.FindByBarcode(barcode.Trim());
    if (item is null)
        return Results.NotFound(new { message = "등록되지 않은 바코드" });

    var dto = new InboundLocationDto(
        item.ItemCode,
        item.ItemName,
        item.LocationText,
        new CoordDto(item.X, item.Y, item.Z));

    return Results.Ok(dto);
});

// GET /api/glass/orders — 활성 출고 목록(요약). Kotlin List<GlassOrder>.
glass.MapGet("/orders", (IWarehouseRepository repo) =>
{
    var dto = repo.AllOrders()
        .Select(o => new GlassOrderDto(o.OrderNo, o.CustomerName, o.Destination, o.LineCount, o.Status))
        .ToList();
    return Results.Ok(dto);
});

// GET /api/glass/summary — 홈 대시보드 집계(입고 대기 / 출고 대기 / 재고 SKU / 임박 건수).
glass.MapGet("/summary", (IWarehouseRepository repo) =>
{
    var today = DateOnly.FromDateTime(DateTime.Today);
    var stock = repo.AllStock();
    var orders = repo.AllOrders();

    var summary = new SummaryDto(
        InboundWaiting: 12, // 입고 대기는 별도 소스(입고예정) — 데모 고정값
        OutboundWaiting: orders.Count,
        StockSku: stock.Select(s => s.ItemCode).Distinct().Count(),
        ExpiringCount: stock.Count(s => DaysToExpiry(s, today) <= SeedData.ExpiringDays));

    return Results.Ok(summary);
});

// GET /api/glass/inventory?coldChain=&expiringOnly=&query= — 재고 목록.
// daysToExpiry/fefoFlag 는 서버가 계산해 내려준다(임박 기준은 서버 정책).
glass.MapGet("/inventory", (string? coldChain, bool? expiringOnly, string? query, IWarehouseRepository repo) =>
{
    var today = DateOnly.FromDateTime(DateTime.Today);
    IEnumerable<StockItem> rows = repo.AllStock();

    if (!string.IsNullOrWhiteSpace(coldChain))
        rows = rows.Where(s => string.Equals(s.ColdChain, coldChain, StringComparison.OrdinalIgnoreCase));

    if (!string.IsNullOrWhiteSpace(query))
    {
        var q = query.Trim();
        rows = rows.Where(s =>
            s.ItemCode.Contains(q, StringComparison.OrdinalIgnoreCase) ||
            s.ItemName.Contains(q, StringComparison.OrdinalIgnoreCase));
    }

    if (expiringOnly == true)
        rows = rows.Where(s => DaysToExpiry(s, today) <= SeedData.ExpiringDays);

    // 임박(유통기한 가까운) 순 → FEFO 우선 노출.
    var dto = rows
        .OrderBy(s => s.ExpiryDate)
        .Select(s =>
        {
            var d = DaysToExpiry(s, today);
            return new StockItemDto(
                s.ItemCode, s.ItemName, s.Qty, s.Unit, s.LocationText, s.ColdChain,
                s.LotNo, s.MfgDate.ToString("yyyy-MM-dd"), s.ExpiryDate.ToString("yyyy-MM-dd"),
                d, d <= SeedData.ExpiringDays);
        })
        .ToList();

    return Results.Ok(dto);
});

// 헬스 체크(앱 디버깅용).
app.MapGet("/api/glass/ping", () => Results.Ok(new { ok = true }));

app.Run();

// 유통기한까지 남은 일수(음수면 만료). 서버가 임박/FEFO 판단의 기준으로 사용.
static int DaysToExpiry(StockItem s, DateOnly today) => s.ExpiryDate.DayNumber - today.DayNumber;
