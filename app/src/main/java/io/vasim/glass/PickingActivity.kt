package io.vasim.glass

import android.app.Activity
import android.content.res.ColorStateList
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import io.vasim.glass.databinding.ActivityPickingBinding
import kotlinx.coroutines.launch

/**
 * 출고 피킹(스캔 검증) 화면.
 *
 * 주문 바코드 스캔 → 피킹 목록 → 라인별 제품 바코드 스캔 검증 + 수량 채움 →
 * 전 라인 완료 후 출하 확정 → 출고 목적지.
 *
 * 상태 판별: "주문이 로드됐는가"([pickList] != null)로 스캔을 주문/제품으로 구분한다.
 * 액티비티가 재생성돼도 [orderNo]를 저장해 자동 재로딩하므로 흐름이 꼬이지 않는다.
 *
 * 네비게이션: 상단 [취소] 상시(앱 홈 복귀), 하단 [이전]·[스캔/출하확정]·[다음/닫기].
 * 모든 버튼 text = WearHF 음성 명령.
 */
class PickingActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ORDER_NO = "io.vasim.glass.ORDER_NO"
        private const val STATE_ORDER_NO = "state.orderNo"
    }

    private enum class Status { NEUTRAL, SUCCESS, ERROR }

    private lateinit var binding: ActivityPickingBinding
    private val api = GlassApi()
    private lateinit var speaker: Speaker

    private var pickList: PickList? = null
    private var index = 0                 // 0..lines.size — lines.size 이면 목적지/출하 화면
    private var orderNo: String = ""
    private var shipped = false

    private val scanLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val code = if (result.resultCode == Activity.RESULT_OK)
            BarcodeScanner.extractResult(result.data) else null

        when {
            code.isNullOrBlank() ->
                showStatus("바코드를 읽지 못했습니다 · 다시 시도", Status.ERROR)
            // 주문이 아직 안 실렸으면 = 주문 스캔, 실렸으면 = 제품 스캔
            pickList == null -> loadOrder(code)
            else -> onProductScanned(code)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPickingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        speaker = Speaker(this)   // 아래 loadOrder/startOrderScan 의 showStatus 보다 먼저 준비

        binding.cancelButton.setOnClickListener { finish() }   // 음성 "취소" → 앱 홈
        binding.prevButton.setOnClickListener { step(-1) }

        // 재생성 복원: extra > 저장된 주문번호 > 신규 주문 스캔
        val preset = intent.getStringExtra(EXTRA_ORDER_NO)
        val restored = savedInstanceState?.getString(STATE_ORDER_NO)
        when {
            !preset.isNullOrBlank() -> loadOrder(preset)
            !restored.isNullOrBlank() -> loadOrder(restored)
            else -> startOrderScan()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (orderNo.isNotBlank()) outState.putString(STATE_ORDER_NO, orderNo)
    }

    override fun onDestroy() {
        speaker.shutdown()
        super.onDestroy()
    }

    private fun startOrderScan() {
        showStatus("출고 주문 바코드를 스캔하세요", Status.NEUTRAL)
        launchScanner()
    }

    private fun scanProduct() = launchScanner()

    private fun launchScanner() {
        try {
            scanLauncher.launch(BarcodeScanner.scanIntent(this))
        } catch (e: Exception) {
            showStatus("내장 스캐너를 호출할 수 없습니다 · 기기 Intent 키 확인", Status.ERROR)
        }
    }

    private fun loadOrder(scanned: String) {
        setLoading(true)
        binding.orderText.text = scanned
        binding.locationText.text = ""
        showStatus("피킹 목록 조회 중…", Status.NEUTRAL, speak = false)
        lifecycleScope.launch {
            val result = api.queryPickList(scanned)
            setLoading(false)
            when (result) {
                is PickResult.Success -> {
                    orderNo = scanned
                    pickList = result.pickList
                    // 재개: 첫 미완료 라인으로 이동(전부 완료면 목적지)
                    val firstIncomplete = result.pickList.lines.indexOfFirst { it.pickedQty < it.qty }
                    index = if (firstIncomplete < 0) result.pickList.lines.size else firstIncomplete
                    shipped = result.pickList.status == "Done"
                    render()
                }
                is PickResult.NotFound -> showStatus(result.message + " · ‘취소’로 종료", Status.ERROR)
                is PickResult.Error -> showStatus(result.message + " · ‘취소’로 종료", Status.ERROR)
            }
        }
    }

    private fun onProductScanned(barcode: String) {
        val order = orderNo
        if (order.isBlank() || pickList == null) return
        setLoading(true)
        showStatus("확정 중…", Status.NEUTRAL, speak = false)
        lifecycleScope.launch {
            val res = api.confirmPick(order, barcode)
            setLoading(false)
            if (res == null) {
                showStatus("확정 실패(네트워크/서버) · ‘스캔’ 다시", Status.ERROR)
                return@launch
            }
            pickList = res.pickList
            val total = res.pickList.lines.size
            when {
                res.allPicked -> {
                    index = total
                    render()
                    showStatus("전 라인 피킹 완료 · ‘출하 확정’", Status.SUCCESS)
                }
                res.matched -> {
                    val line = res.pickList.lines.getOrNull(index)
                    if (line != null && line.pickedQty >= line.qty) {
                        index = (index + 1).coerceAtMost(total)
                    }
                    render()
                    showStatus("✓ " + res.message, Status.SUCCESS)
                }
                else -> {
                    render()
                    showStatus("❌ " + res.message, Status.ERROR)
                }
            }
        }
    }

    private fun confirmShip() {
        val order = orderNo
        if (order.isBlank()) return
        setLoading(true)
        showStatus("출하 확정 중…", Status.NEUTRAL, speak = false)
        lifecycleScope.launch {
            val updated = api.confirmShip(order)
            setLoading(false)
            if (updated == null) {
                showStatus("출하 확정 실패(네트워크/서버)", Status.ERROR)
                return@launch
            }
            pickList = updated
            shipped = true
            render()
            showStatus("✓ 출하 완료! · ‘취소’로 종료", Status.SUCCESS)
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
            val line = list.lines[index]
            binding.progressText.text = "${index + 1} / $total"
            val p = LocationFormat.parse(line.locationText)
            binding.warehouseMap.setLocation(p)
            binding.locationText.text = line.locationText.ifBlank { "위치 미등록" }
            binding.itemText.text = listOf(line.itemCode, line.itemName)
                .filter { it.isNotBlank() }.joinToString("  ")
            binding.qtyText.text = "수량 ${line.pickedQty} / ${line.qty}"
            binding.hintText.text = if (p.ok) LocationFormat.hint(p) else ""

            setAction(getString(R.string.btn_pick_scan), Status.NEUTRAL, enabled = true) { scanProduct() }
            binding.nextButton.text = getString(R.string.btn_next)
            binding.nextButton.setOnClickListener { step(+1) }
            showStatus("이 위치에서 제품을 ‘스캔’ 하세요", Status.NEUTRAL)
        } else {
            binding.progressText.text = if (shipped) "출하완료" else "완료"
            binding.warehouseMap.setLocation(null)   // 목적지(도크)는 랙 위치가 아님
            binding.hintText.text = "출고 목적지"
            binding.locationText.text = list.destination?.ifBlank { "목적지 미지정" } ?: "목적지 미지정"
            binding.itemText.text = "출고 목적지"
            binding.qtyText.text = when {
                shipped -> "출하 확정됨"
                allPicked -> "전 라인 피킹 완료 ($total)"
                else -> "⚠ 미완료 라인 있음"
            }

            setAction(getString(R.string.btn_ship), Status.SUCCESS, enabled = allPicked && !shipped) { confirmShip() }
            binding.nextButton.text = getString(R.string.btn_close)
            binding.nextButton.setOnClickListener { finish() }
            showStatus(
                when {
                    shipped -> "✓ 출하 완료! · ‘취소’로 종료"
                    allPicked -> "‘출하 확정’ 하세요"
                    else -> "미완료 라인이 있습니다 · ‘이전’으로 확인"
                },
                if (shipped) Status.SUCCESS else Status.NEUTRAL
            )
        }

        binding.prevButton.isEnabled = index > 0
    }

    private fun setAction(text: String, kind: Status, enabled: Boolean, onClick: () -> Unit) {
        binding.actionButton.text = text
        binding.actionButton.isEnabled = enabled
        binding.actionButton.alpha = if (enabled) 1f else 0.45f
        val (bg, fg) = if (kind == Status.SUCCESS)
            R.color.success to R.color.on_success
        else
            R.color.primary to R.color.on_primary
        binding.actionButton.backgroundTintList =
            ColorStateList.valueOf(ContextCompat.getColor(this, bg))
        binding.actionButton.setTextColor(ContextCompat.getColor(this, fg))
        binding.actionButton.setOnClickListener { onClick() }
    }

    private fun setLoading(loading: Boolean) {
        binding.progress.isVisible = loading
        binding.prevButton.isEnabled = !loading
        binding.actionButton.isEnabled = !loading
        binding.nextButton.isEnabled = !loading
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
