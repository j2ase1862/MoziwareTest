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
