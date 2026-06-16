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

// 헬스 체크(앱 디버깅용).
app.MapGet("/api/glass/ping", () => Results.Ok(new { ok = true }));

app.Run();
