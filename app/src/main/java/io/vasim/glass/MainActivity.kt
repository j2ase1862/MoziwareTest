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

    /** 가장 최근 스캔된 바코드. 조회는 이 값을 사용한다. */
    private var lastBarcode: String? = null

    private val scanLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        when (result.resultCode) {
            Activity.RESULT_OK -> {
                val code = result.data?.getStringExtra(BarcodeScanner.EXTRA_RESULT)
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

        binding.scanButton.setOnClickListener { startScan() }
        binding.inboundButton.setOnClickListener { queryLocation("입고") }
        binding.inboundItemButton.setOnClickListener { queryLocation("입고제품") }
        binding.outboundButton.setOnClickListener {
            startActivity(Intent(this, PickingActivity::class.java))
        }
        binding.homeButton.setOnClickListener { resetToIdle() }   // 음성 "처음으로"

        resetToIdle()
    }

    /** "바코드 스캔" — 내장 스캐너 호출. */
    private fun startScan() {
        try {
            scanLauncher.launch(Intent(BarcodeScanner.ACTION_SCAN))
        } catch (e: Exception) {
            showStatus("내장 스캐너를 호출할 수 없습니다 · 기기 Intent 키 확인", Status.ERROR)
        }
    }

    private fun onBarcodeScanned(code: String) {
        lastBarcode = code
        binding.barcodeText.text = code
        binding.locationText.text = ""
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
        binding.locationText.text = ""
        showStatus("입고 위치 조회 중… ($mode)", Status.NEUTRAL)

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

    /** 결과 표시 — 입고 위치를 대형 강조, 상태는 성공(초록). */
    private fun showResult(location: InboundLocation) {
        binding.locationText.text = location.locationText.ifBlank { "위치 정보 없음" }
        val name = location.itemName?.takeIf { it.isNotBlank() }
        showStatus(name?.let { "✓ $it" } ?: "✓ 조회 완료", Status.SUCCESS)
    }

    private fun resetToIdle() {
        lastBarcode = null
        binding.barcodeText.text = "—"
        binding.locationText.text = ""
        showStatus("‘바코드 스캔’ 이라고 말하거나 버튼을 누르세요", Status.NEUTRAL)
    }

    private fun setLoading(loading: Boolean) {
        binding.progress.isVisible = loading
        binding.scanButton.isEnabled = !loading
        binding.inboundButton.isEnabled = !loading
        binding.inboundItemButton.isEnabled = !loading
    }

    private fun showStatus(message: String, kind: Status = Status.NEUTRAL) {
        binding.statusText.text = message
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
