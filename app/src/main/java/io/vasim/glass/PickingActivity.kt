package io.vasim.glass

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import io.vasim.glass.databinding.ActivityPickingBinding
import kotlinx.coroutines.launch

/**
 * 출고 피킹(2차 — 스캔 검증) 화면.
 *
 * 흐름: 주문 바코드 스캔 → 피킹 목록 조회 → 각 라인에서 **제품 바코드를 스캔해 검증**하며
 * 수량을 채우고(서버 pick-confirm), 전 라인 완료 후 **출하 확정**(ship-confirm) → 목적지 표시.
 *
 * 버튼(음성): [이전] · [스캔/출하 확정] · [다음/닫기] — 상태에 따라 가운데·오른쪽 버튼이 바뀐다.
 */
class PickingActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ORDER_NO = "io.vasim.glass.ORDER_NO"
    }

    private lateinit var binding: ActivityPickingBinding
    private val api = GlassApi()

    private var pickList: PickList? = null
    private var index = 0                 // 0..lines.size — lines.size 이면 목적지/출하 화면
    private var orderNo: String = ""
    private var awaitingOrder = true      // true=주문 스캔 대기, false=제품 스캔 대기
    private var shipped = false

    private val scanLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val code = if (result.resultCode == Activity.RESULT_OK)
            result.data?.getStringExtra(BarcodeScanner.EXTRA_RESULT) else null

        if (awaitingOrder) {
            if (!code.isNullOrBlank()) loadOrder(code)
            else showStatus("주문 바코드를 읽지 못했습니다. ‘닫기’ 후 다시 시도")
        } else {
            if (!code.isNullOrBlank()) onProductScanned(code)
            else showStatus("바코드를 읽지 못했습니다. ‘스캔’ 다시")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPickingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.prevButton.setOnClickListener { step(-1) }
        // actionButton / nextButton 핸들러는 render()에서 상태별로 지정

        val preset = intent.getStringExtra(EXTRA_ORDER_NO)
        if (!preset.isNullOrBlank()) loadOrder(preset) else startOrderScan()
    }

    private fun startOrderScan() {
        awaitingOrder = true
        showStatus("출고 주문 바코드를 스캔하세요")
        launchScanner()
    }

    private fun scanProduct() {
        awaitingOrder = false
        launchScanner()
    }

    private fun launchScanner() {
        try {
            scanLauncher.launch(Intent(BarcodeScanner.ACTION_SCAN))
        } catch (e: Exception) {
            showStatus("내장 스캐너를 호출할 수 없습니다.\n기기 문서의 스캐너 Intent 키를 확인하세요")
        }
    }

    private fun loadOrder(scanned: String) {
        orderNo = scanned
        setLoading(true)
        binding.orderText.text = scanned
        binding.locationText.text = ""
        showStatus("피킹 목록 조회 중…")
        lifecycleScope.launch {
            val result = api.queryPickList(scanned)
            setLoading(false)
            when (result) {
                is PickResult.Success -> {
                    pickList = result.pickList
                    index = 0
                    shipped = false
                    render()
                }
                is PickResult.NotFound -> showStatus(result.message + " · ‘닫기’로 종료")
                is PickResult.Error -> showStatus(result.message + " · ‘닫기’로 종료")
            }
        }
    }

    /** 제품 스캔 → 서버 검증/수량 증가. */
    private fun onProductScanned(barcode: String) {
        val order = orderNo
        if (order.isBlank()) return
        setLoading(true)
        showStatus("확정 중…")
        lifecycleScope.launch {
            val res = api.confirmPick(order, barcode)
            setLoading(false)
            if (res == null) {
                showStatus("확정 실패(네트워크/서버). ‘스캔’ 다시")
                return@launch
            }
            pickList = res.pickList
            val total = res.pickList.lines.size
            when {
                res.allPicked -> {
                    index = total          // 목적지/출하 화면으로
                    render()
                    showStatus("전 라인 피킹 완료 · ‘출하 확정’")
                }
                res.matched -> {
                    // 현재 라인이 완료됐으면 자동으로 다음 라인
                    val line = res.pickList.lines.getOrNull(index)
                    if (line != null && line.pickedQty >= line.qty) {
                        index = (index + 1).coerceAtMost(total)
                    }
                    render()
                    showStatus(res.message)
                }
                else -> {
                    render()                // alreadyComplete 또는 미일치
                    showStatus("❌ " + res.message)
                }
            }
        }
    }

    private fun confirmShip() {
        val order = orderNo
        if (order.isBlank()) return
        setLoading(true)
        showStatus("출하 확정 중…")
        lifecycleScope.launch {
            val updated = api.confirmShip(order)
            setLoading(false)
            if (updated == null) {
                showStatus("출하 확정 실패(네트워크/서버)")
                return@launch
            }
            pickList = updated
            shipped = true
            render()
            showStatus("출하 완료! · ‘닫기’로 종료")
        }
    }

    private fun step(delta: Int) {
        val list = pickList ?: return
        index = (index + delta).coerceIn(0, list.lines.size)
        render()
    }

    private fun render() {
        val list = pickList ?: return
        val total = list.lines.size
        val allPicked = total > 0 && list.lines.all { it.pickedQty >= it.qty }

        binding.orderText.text = buildString {
            append(list.orderNo)
            list.customerName?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
        }

        if (index < total) {
            // === 피킹 라인 (스캔 검증) ===
            val line = list.lines[index]
            binding.progressText.text = "${index + 1} / $total"
            binding.locationText.text = line.locationText.ifBlank { "위치 미등록" }
            binding.itemText.text = listOf(line.itemCode, line.itemName)
                .filter { it.isNotBlank() }.joinToString("  ")
            binding.qtyText.text = "수량 ${line.pickedQty} / ${line.qty}"

            binding.actionButton.text = getString(R.string.btn_pick_scan)   // 스캔
            binding.actionButton.isEnabled = true
            binding.actionButton.setOnClickListener { scanProduct() }
            binding.nextButton.text = getString(R.string.btn_next)          // 다음
            binding.nextButton.setOnClickListener { step(+1) }
            showStatus("이 위치에서 제품을 ‘스캔’ 하세요")
        } else {
            // === 출고 목적지 / 출하 확정 ===
            binding.progressText.text = if (shipped) "출하완료" else "완료"
            binding.locationText.text = list.destination?.ifBlank { "목적지 미지정" } ?: "목적지 미지정"
            binding.itemText.text = "출고 목적지"
            binding.qtyText.text = when {
                shipped -> "출하 확정됨"
                allPicked -> "전 라인 피킹 완료 ($total)"
                else -> "⚠ 미완료 라인 있음"
            }

            binding.actionButton.text = getString(R.string.btn_ship)        // 출하 확정
            binding.actionButton.isEnabled = allPicked && !shipped
            binding.actionButton.setOnClickListener { confirmShip() }
            binding.nextButton.text = getString(R.string.btn_close)         // 닫기
            binding.nextButton.setOnClickListener { finish() }
            showStatus(
                when {
                    shipped -> "출하 완료! · ‘닫기’로 종료"
                    allPicked -> "‘출하 확정’ 하세요"
                    else -> "미완료 라인이 있습니다 · ‘이전’으로 확인"
                }
            )
        }

        binding.prevButton.isEnabled = index > 0
    }

    private fun setLoading(loading: Boolean) {
        binding.progress.isVisible = loading
        binding.prevButton.isEnabled = !loading
        binding.actionButton.isEnabled = !loading
        binding.nextButton.isEnabled = !loading
    }

    private fun showStatus(message: String) {
        binding.statusText.text = message
    }
}
