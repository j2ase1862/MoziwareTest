namespace BodaGlass.StubApi;

/// <summary>
/// 입고 위치 마스터 — 신규 엔티티(아키텍처 문서 §5, §10).
/// 실제 BODA.VMS.Web 에서는 EF Core 엔티티로 두고 <see cref="Barcode"/> 에 인덱스를 건다.
/// </summary>
public class WarehouseItem
{
    public int Id { get; set; }
    public string Barcode { get; set; } = "";
    public string ItemCode { get; set; } = "";
    public string ItemName { get; set; } = "";
    public string LocationText { get; set; } = "";

    // 2차 목표(3D 도면 하이라이트)용 좌표.
    public double X { get; set; }
    public double Y { get; set; }
    public double Z { get; set; }
}

/// <summary>
/// 글라스 앱 응답 DTO. Kotlin <c>InboundLocation</c> 과 1:1.
/// ASP.NET 기본 System.Text.Json 직렬화가 camelCase 로 내보내므로
/// (itemCode, itemName, locationText, coord{x,y,z}) Kotlin 필드명과 자동 매칭된다.
/// </summary>
public record InboundLocationDto(
    string ItemCode,
    string ItemName,
    string LocationText,
    CoordDto Coord);

public record CoordDto(double X, double Y, double Z);

// ───────────────────────── 재고(인벤토리) — 식품 콜드체인 운용 ─────────────────────────

/// <summary>
/// 재고 항목. 식품 물류 창고 운용 반영:
///  · 온도대(콜드체인) 구분  · 로트/유통기한(FEFO)  · 로케이션(구역-랙-단-칸)
/// 실제 BODA.VMS.Web 에서는 EF Core 엔티티(로트 단위 재고 테이블)로 둔다.
/// </summary>
public class StockItem
{
    public string ItemCode { get; set; } = "";
    public string ItemName { get; set; } = "";
    public int Qty { get; set; }                       // 현재고
    public string Unit { get; set; } = "EA";
    public string LocationText { get; set; } = "";
    public string ColdChain { get; set; } = "AMBIENT"; // FROZEN | CHILLED | AMBIENT
    public string LotNo { get; set; } = "";
    public DateOnly MfgDate { get; set; }
    public DateOnly ExpiryDate { get; set; }
}

/// <summary>
/// 재고 응답 DTO — Kotlin <c>StockItem</c> 과 1:1.
/// <c>DaysToExpiry</c>/<c>FefoFlag</c> 는 임박 기준이 품목·거래처별로 다를 수 있어
/// 반드시 서버가 계산해 내려준다(클라가 판단하지 않음).
/// </summary>
public record StockItemDto(
    string ItemCode,
    string ItemName,
    int Qty,
    string Unit,
    string LocationText,
    string ColdChain,
    string LotNo,
    string MfgDate,
    string ExpiryDate,
    int DaysToExpiry,
    bool FefoFlag);

/// <summary>홈 대시보드 집계 — Kotlin <c>GlassSummary</c> 와 1:1.</summary>
public record SummaryDto(
    int InboundWaiting,
    int OutboundWaiting,
    int StockSku,
    int ExpiringCount);

/// <summary>출고 목록 요약 — Kotlin <c>GlassOrder</c> 와 1:1.</summary>
public record GlassOrderDto(
    string OrderNo,
    string? CustomerName,
    string? Destination,
    int LineCount,
    string Status);

/// <summary>출고 주문(스텁 엔티티).</summary>
public class OutboundOrder
{
    public string OrderNo { get; set; } = "";
    public string CustomerName { get; set; } = "";
    public string Destination { get; set; } = "";
    public int LineCount { get; set; }
    public string Status { get; set; } = "Ready"; // Ready | Picking
}
