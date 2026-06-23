namespace BodaGlass.StubApi;

/// <summary>
/// PoC 시드 데이터(아키텍처 문서 §13-1). 글라스 앱 흐름 검증용 샘플 바코드.
/// 실 서버에서는 WMS/ERP 동기화·관리 CRUD·CSV 등으로 채운다(§13-4).
/// </summary>
public static class SeedData
{
    /// <summary>유통기한 임박(FEFO) 기준 일수. 이 이내면 임박으로 본다.</summary>
    public const int ExpiringDays = 7;

    private static readonly DateOnly Today = DateOnly.FromDateTime(DateTime.Today);

    public static readonly List<WarehouseItem> Items =
    [
        new() { Id = 1, Barcode = "8801234567890", ItemCode = "A-1001", ItemName = "포장박스(대)",  LocationText = "A동 3층 R-12-04", X = 12, Y = 4, Z = 3 },
        new() { Id = 2, Barcode = "8809876543210", ItemCode = "B-2002", ItemName = "완충재 롤",     LocationText = "B동 1층 R-02-09", X = 2,  Y = 9, Z = 1 },
        new() { Id = 3, Barcode = "1234567890128", ItemCode = "C-3003", ItemName = "라벨 스티커",   LocationText = "C동 2층 R-07-01", X = 7,  Y = 1, Z = 2 },
        new() { Id = 4, Barcode = "0000000000017", ItemCode = "D-4004", ItemName = "테스트 샘플",   LocationText = "검수대 01",       X = 0,  Y = 0, Z = 0 },
    ];

    /// <summary>
    /// 재고 시드 — 식품 콜드체인 샘플. 유통기한은 startup 의 오늘 기준 상대일로 시드해
    /// 임박(FEFO) 데모가 항상 재현되게 한다. 일부 항목을 의도적으로 임박/만료 임박으로 둠.
    /// </summary>
    public static readonly List<StockItem> Stock =
    [
        new() { ItemCode = "F-1001", ItemName = "냉동 만두 1kg",   Qty = 320, Unit = "BOX", LocationText = "F구역 02랙 1단 03칸", ColdChain = "FROZEN",  LotNo = "L240501", MfgDate = Today.AddDays(-40), ExpiryDate = Today.AddDays(180) },
        new() { ItemCode = "F-1002", ItemName = "냉동 새우 2kg",   Qty = 96,  Unit = "BOX", LocationText = "F구역 05랙 2단 08칸", ColdChain = "FROZEN",  LotNo = "L240412", MfgDate = Today.AddDays(-60), ExpiryDate = Today.AddDays(90)  },
        new() { ItemCode = "C-2001", ItemName = "우유 1L",         Qty = 48,  Unit = "EA",  LocationText = "C구역 04랙 2단 11칸", ColdChain = "CHILLED", LotNo = "L240620", MfgDate = Today.AddDays(-3),  ExpiryDate = Today.AddDays(4)   }, // 임박
        new() { ItemCode = "C-2002", ItemName = "요거트 90g×4",    Qty = 120, Unit = "PACK",LocationText = "C구역 06랙 1단 02칸", ColdChain = "CHILLED", LotNo = "L240610", MfgDate = Today.AddDays(-8),  ExpiryDate = Today.AddDays(2)   }, // 임박
        new() { ItemCode = "C-2003", ItemName = "훈제 닭가슴살",   Qty = 64,  Unit = "EA",  LocationText = "C구역 02랙 3단 07칸", ColdChain = "CHILLED", LotNo = "L240601", MfgDate = Today.AddDays(-15), ExpiryDate = Today.AddDays(20)  },
        new() { ItemCode = "A-3001", ItemName = "생수 2L×6",       Qty = 540, Unit = "PACK",LocationText = "A구역 10랙 1단 01칸", ColdChain = "AMBIENT", LotNo = "L240301", MfgDate = Today.AddDays(-90), ExpiryDate = Today.AddDays(300) },
        new() { ItemCode = "A-3002", ItemName = "라면 멀티팩",     Qty = 210, Unit = "BOX", LocationText = "A구역 12랙 2단 05칸", ColdChain = "AMBIENT", LotNo = "L240520", MfgDate = Today.AddDays(-20), ExpiryDate = Today.AddDays(150) },
        new() { ItemCode = "A-3003", ItemName = "통조림 햄",       Qty = 88,  Unit = "BOX", LocationText = "A구역 08랙 3단 09칸", ColdChain = "AMBIENT", LotNo = "L231101", MfgDate = Today.AddDays(-200),ExpiryDate = Today.AddDays(6)   }, // 임박
    ];

    /// <summary>출고 주문 시드 — 출고 목록/집계 데모용.</summary>
    public static readonly List<OutboundOrder> Orders =
    [
        new() { OrderNo = "OUT-1182", CustomerName = "이마트 죽전",   Destination = "죽전점",   LineCount = 12, Status = "Picking" },
        new() { OrderNo = "OUT-1184", CustomerName = "롯데마트 수지", Destination = "수지점",   LineCount = 5,  Status = "Ready" },
        new() { OrderNo = "OUT-1183", CustomerName = "홈플러스 동수원", Destination = "동수원점", LineCount = 9,  Status = "Ready" },
    ];
}
