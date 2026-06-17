package io.vasim.glass

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import io.vasim.glass.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

/**
 * 글라스 앱의 단일 진입점이자 얇은 클라이언트.
 *
 * 화면·음성·바코드만 네이티브로 담당하고, 데이터/로직은 BODA.VMS.Web 서버를 호출한다.
 *
 * 음성 UX(§7): WearHF "보이는 대로 말하기"는 화면에 보이는 Button 의 text 를
 * 자동으로 음성 명령으로 등록한다. 따라서 손으로 버튼을 눌러도, 음성으로 불러도
 * 동일한 핸들러가 실행된다.
 *   - "스캔"      → [startScan]
 *   - "입고"      → [queryLocation] ("입고")
 *   - "입고제품"  → [queryLocation] ("입고제품")
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val api = GlassApi()

    /** 가장 최근 스캔된 바코드. 조회는 이 값을 사용한다. */
    private var lastBarcode: String? = null

    /** 내장 스캐너 Intent 결과 수신. */
    private val scanLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        when (result.resultCode) {
            Activity.RESULT_OK -> {
                val code = result.data?.getStringExtra(BarcodeScanner.EXTRA_RESULT)
                if (!code.isNullOrBlank()) {
                    onBarcodeScanned(code)
                } else {
                    showStatus("바코드를 읽지 못했습니다. 다시 ‘스캔’ 하세요")
                }
            }
            else -> showStatus("스캔이 취소되었습니다")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 버튼 라벨 = 음성 명령. 손/음성 모두 같은 핸들러로 수렴 → 디버깅 용이.
        binding.scanButton.setOnClickListener { startScan() }
        binding.inboundButton.setOnClickListener { queryLocation("입고") }
        binding.inboundItemButton.setOnClickListener { queryLocation("입고제품") }
        // 음성 "출고" → 출고 피킹 화면(주문 스캔 → 피킹 안내)
        binding.outboundButton.setOnClickListener {
            startActivity(Intent(this, PickingActivity::class.java))
        }

        resetToIdle()
    }

    /** 1) "스캔" — 내장 스캐너 호출. */
    private fun startScan() {
        try {
            scanLauncher.launch(Intent(BarcodeScanner.ACTION_SCAN))
        } catch (e: Exception) {
            // 기기에 해당 Intent 를 처리할 스캐너 앱이 없는 경우 (TODO #2: Intent 키 확인)
            showStatus("내장 스캐너를 호출할 수 없습니다.\n기기 문서의 스캐너 Intent 키를 확인하세요")
        }
    }

    /** 바코드 캡처 성공 시 화면에 표시하고 다음 음성 명령을 유도. */
    private fun onBarcodeScanned(code: String) {
        lastBarcode = code
        binding.barcodeText.text = code
        binding.locationText.text = ""
        showStatus("‘입고’ 또는 ‘입고제품’ 이라고 말하세요")
    }

    /** 2) "입고" / "입고제품" — 서버에서 입고 위치 조회. */
    private fun queryLocation(mode: String) {
        val barcode = lastBarcode
        if (barcode.isNullOrBlank()) {
            showStatus("먼저 ‘바코드 스캔’ 이라고 말하세요")
            return
        }

        setLoading(true)
        binding.locationText.text = ""
        showStatus("입고 위치 조회 중… ($mode)")

        lifecycleScope.launch {
            val result = api.queryInboundLocation(barcode, mode)
            setLoading(false)
            when (result) {
                is GlassResult.Success -> showResult(result.location)
                is GlassResult.NotFound -> showStatus(result.message)
                is GlassResult.Error -> showStatus(result.message)
            }
        }
    }

    /** 3) 결과 표시 — 입고 위치 텍스트를 대형 고대비로. (1차 목표) */
    private fun showResult(location: InboundLocation) {
        binding.locationText.text = location.locationText.ifBlank { "위치 정보 없음" }
        binding.statusText.text = location.itemName?.takeIf { it.isNotBlank() } ?: "조회 완료"
    }

    private fun resetToIdle() {
        lastBarcode = null
        binding.barcodeText.text = "—"
        binding.locationText.text = ""
        showStatus("’바코드 스캔’ 이라고 말하거나 버튼을 누르세요")
    }

    /** 조회 중에는 진행 표시를 켜고 버튼을 잠가 중복 호출을 막는다. */
    private fun setLoading(loading: Boolean) {
        binding.progress.isVisible = loading
        binding.scanButton.isEnabled = !loading
        binding.inboundButton.isEnabled = !loading
        binding.inboundItemButton.isEnabled = !loading
    }

    private fun showStatus(message: String) {
        binding.statusText.text = message
    }
}
