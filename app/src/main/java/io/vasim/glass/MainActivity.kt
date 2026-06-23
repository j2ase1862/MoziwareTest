package io.vasim.glass

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import io.vasim.glass.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

/**
 * 글라스 앱의 단일 진입점이자 얇은 클라이언트.
 *
 * 화면·음성·바코드만 네이티브로 담당하고, 데이터/로직은 BODA.VMS.Web 서버를 호출한다.
 *
 * 음성 UX: WearHF "보이는 대로 말하기"가 화면 Button 의 text 를 음성 명령으로 등록한다.
 *   "바코드 스캔" · "입고" · "입고제품" · "출고" · "처음으로"
 */
class MainActivity : AppCompatActivity() {

    private enum class Status { NEUTRAL, SUCCESS, ERROR }

    private lateinit var binding: ActivityMainBinding
    private val api = GlassApi()
    private lateinit var speaker: Speaker

    /** 가장 최근 스캔된 바코드. 조회는 이 값을 사용한다. */
    private var lastBarcode: String? = null

    /** 입고 위치가 조회·표시된 상태인가. '입고 확정'은 이 상태에서만 활성. */
    private var locationShown = false

    private val scanLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        when (result.resultCode) {
            Activity.RESULT_OK -> {
                val code = BarcodeScanner.extractResult(result.data)
                if (!code.isNullOrBlank()) onBarcodeScanned(code)
                else showStatus("바코드를 읽지 못했습니다. 다시 ‘바코드 스캔’", Status.ERROR)
            }
            else -> showStatus("스캔이 취소되었습니다", Status.NEUTRAL)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        speaker = Speaker(this)   // resetToIdle() 의 showStatus 보다 먼저 준비

        binding.scanButton.setOnClickListener { startScan() }
        binding.inboundButton.setOnClickListener { queryLocation("입고") }
        binding.inboundItemButton.setOnClickListener { queryLocation("입고제품") }
        binding.inboundConfirmButton.setOnClickListener { confirmInbound() }   // 음성 "입고 확정"
        binding.outboundButton.setOnClickListener {
            startActivity(Intent(this, OrderListActivity::class.java))   // 음성 "출고" → 출고 목록
        }
        binding.homeButton.setOnClickListener { resetToIdle() }   // 음성 "처음으로"
        binding.exitButton.setOnClickListener { finishAffinity() } // 음성 "종료" → 앱 종료

        resetToIdle()
    }

    override fun onDestroy() {
        speaker.shutdown()
        super.onDestroy()
    }

    /** "바코드 스캔" — 내장 스캐너 호출. */
    private fun startScan() {
        try {
            scanLauncher.launch(BarcodeScanner.scanIntent(this))
        } catch (e: Exception) {
            showStatus("내장 스캐너를 호출할 수 없습니다 · 기기 Intent 키 확인", Status.ERROR)
        }
    }

    private fun onBarcodeScanned(code: String) {
        lastBarcode = code
        locationShown = false
        updateConfirmEnabled()
        binding.barcodeText.text = code
        binding.locationText.text = ""
        binding.hintText.text = ""
        binding.warehouseMap.setLocation(null)
        showStatus("‘입고’ 또는 ‘입고제품’ 이라고 말하세요", Status.NEUTRAL)
    }

    /** "입고" / "입고제품" — 서버에서 입고 위치 조회. */
    private fun queryLocation(mode: String) {
        val barcode = lastBarcode
        if (barcode.isNullOrBlank()) {
            showStatus("먼저 ‘바코드 스캔’ 이라고 말하세요", Status.NEUTRAL)
            return
        }

        setLoading(true)
        locationShown = false
        updateConfirmEnabled()
        binding.locationText.text = ""
        binding.hintText.text = ""
        binding.warehouseMap.setLocation(null)
        showStatus("입고 위치 조회 중… ($mode)", Status.NEUTRAL, speak = false)

        lifecycleScope.launch {
            val result = api.queryInboundLocation(barcode, mode)
            setLoading(false)
            when (result) {
                is GlassResult.Success -> showResult(result.location)
                is GlassResult.NotFound -> showStatus(result.message, Status.ERROR)
                is GlassResult.Error -> showStatus(result.message, Status.ERROR)
            }
        }
    }

    /** 결과 표시 — 미니맵 핀 + 코드 + 방향 힌트, 상태는 성공(초록). */
    private fun showResult(location: InboundLocation) {
        val text = location.locationText
        val p = LocationFormat.parse(text)
        binding.warehouseMap.setLocation(p)
        binding.locationText.text = text.ifBlank { "위치 정보 없음" }
        binding.hintText.text = if (p.ok) LocationFormat.hint(p) else ""
        locationShown = true
        updateConfirmEnabled()
        val name = location.itemName?.takeIf { it.isNotBlank() }
        showStatus(name?.let { "✓ $it · ‘입고 확정’ 가능" } ?: "✓ 조회 완료 · ‘입고 확정’ 가능", Status.SUCCESS)
    }

    /** "입고 확정" — 스캔·조회한 제품의 적치를 확정(재고 누적 + 웹 실시간 반영). */
    private fun confirmInbound() {
        val barcode = lastBarcode
        if (barcode.isNullOrBlank() || !locationShown) {
            showStatus("먼저 ‘바코드 스캔’ 후 ‘입고’로 위치를 확인하세요", Status.NEUTRAL)
            return
        }
        setLoading(true)
        showStatus("입고 확정 중…", Status.NEUTRAL, speak = false)
        lifecycleScope.launch {
            val res = api.confirmInbound(barcode)
            setLoading(false)
            if (res == null || !res.confirmed) {
                showStatus("입고 확정 실패(네트워크/미등록 바코드) · 다시 시도", Status.ERROR)
                return@launch
            }
            locationShown = false           // 한 번 확정하면 비활성(중복 확정 방지)
            updateConfirmEnabled()
            val name = res.itemName.ifBlank { res.itemCode }
            showStatus("✓ $name 입고 확정 · 재고 ${res.stockAfter}", Status.SUCCESS)
        }
    }

    /** '입고 확정' 버튼 가용 상태(위치 조회 후에만) 시각 반영. */
    private fun updateConfirmEnabled() {
        binding.inboundConfirmButton.isEnabled = locationShown
        binding.inboundConfirmButton.alpha = if (locationShown) 1f else 0.45f
    }

    private fun resetToIdle() {
        lastBarcode = null
        locationShown = false
        updateConfirmEnabled()
        binding.barcodeText.text = "—"
        binding.locationText.text = ""
        binding.hintText.text = ""
        binding.warehouseMap.setLocation(null)
        showStatus("‘바코드 스캔’ 이라고 말하거나 버튼을 누르세요", Status.NEUTRAL)
    }

    private fun setLoading(loading: Boolean) {
        binding.progress.isVisible = loading
        binding.scanButton.isEnabled = !loading
        binding.inboundButton.isEnabled = !loading
        binding.inboundItemButton.isEnabled = !loading
        binding.inboundConfirmButton.isEnabled = !loading && locationShown
    }

    private fun showStatus(message: String, kind: Status = Status.NEUTRAL, speak: Boolean = true) {
        binding.statusText.text = message
        if (speak) speaker.speak(message)
        val (bg, fg) = when (kind) {
            Status.SUCCESS -> R.color.success to R.color.bg
            Status.ERROR -> R.color.danger to R.color.bg
            Status.NEUTRAL -> R.color.surface_variant to R.color.fg
        }
        binding.statusText.backgroundTintList =
            ColorStateList.valueOf(ContextCompat.getColor(this, bg))
        binding.statusText.setTextColor(ContextCompat.getColor(this, fg))
    }
}
