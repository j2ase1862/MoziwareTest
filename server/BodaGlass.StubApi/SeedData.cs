namespace BodaGlass.StubApi;

/// <summary>
/// PoC 시드 데이터(아키텍처 문서 §13-1). 글라스 앱 흐름 검증용 샘플 바코드.
/// 실 서버에서는 WMS/ERP 동기화·관리 CRUD·CSV 등으로 채운다(§13-4).
/// </summary>
public static class SeedData
{
    public static readonly List<WarehouseItem> Items =
    [
        new() { Id = 1, Barcode = "8801234567890", ItemCode = "A-1001", ItemName = "포장박스(대)",  LocationText = "A동 3층 R-12-04", X = 12, Y = 4, Z = 3 },
        new() { Id = 2, Barcode = "8809876543210", ItemCode = "B-2002", ItemName = "완충재 롤",     LocationText = "B동 1층 R-02-09", X = 2,  Y = 9, Z = 1 },
        new() { Id = 3, Barcode = "1234567890128", ItemCode = "C-3003", ItemName = "라벨 스티커",   LocationText = "C동 2층 R-07-01", X = 7,  Y = 1, Z = 2 },
        new() { Id = 4, Barcode = "0000000000017", ItemCode = "D-4004", ItemName = "테스트 샘플",   LocationText = "검수대 01",       X = 0,  Y = 0, Z = 0 },
    ];
}
