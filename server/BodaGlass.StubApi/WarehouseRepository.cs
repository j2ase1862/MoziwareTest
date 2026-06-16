namespace BodaGlass.StubApi;

public interface IWarehouseRepository
{
    /// <summary>바코드로 입고 위치를 조회. 없으면 null.</summary>
    WarehouseItem? FindByBarcode(string barcode);
}

/// <summary>
/// PoC 인메모리 저장소. 바코드를 키로 인덱싱하여 실 서버의 "바코드 인덱스"에 대응시킨다.
///
/// 실제 BODA.VMS.Web 으로 옮길 때는 이 구현을 EF Core DbContext 조회로 교체:
///   <code>_db.WarehouseItems.AsNoTracking().FirstOrDefault(w => w.Barcode == barcode)</code>
/// </summary>
public sealed class InMemoryWarehouseRepository : IWarehouseRepository
{
    private readonly Dictionary<string, WarehouseItem> _byBarcode;

    public InMemoryWarehouseRepository()
    {
        _byBarcode = SeedData.Items.ToDictionary(i => i.Barcode, StringComparer.Ordinal);
    }

    public WarehouseItem? FindByBarcode(string barcode) =>
        _byBarcode.TryGetValue(barcode, out var item) ? item : null;
}
