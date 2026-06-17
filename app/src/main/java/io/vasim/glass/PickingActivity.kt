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
 * 출고 피킹(읽기 가이드) 화면.
 *
 * 흐름: 주문/송장 바코드 스캔 → 서버에서 피킹 목록 조회 → 라인별로
 * "어디서(위치) 무엇을 몇 개" 안내 → 마지막에 출고 목적지(도크/레인) 표시.
 *
 * 음성: 버튼 텍스트(이전/다음/닫기)가 WearHF 명령으로 등록된다.
 */
class PickingActivity : AppCompatActivity() {

    companion object {
        /** (선택) 호출부에서 주문번호를 미리 넘기면 스캔을 건너뛴다. */
        const val EXTRA_ORDER_NO = "io.vasim.glass.ORDER_NO"
    }

    private lateinit var binding: ActivityPickingBinding
    private val api = GlassApi()

    private var pickList: PickList? = null
    /** 0..lines.size — lines.size 이면 "출고 목적지" 화면. */
    private var index = 0

    private val scanLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val code = result.data?.getStringExtra(BarcodeScanner.EXTRA_RESULT)
            if (!code.isNullOrBlank()) loadOrder(code)
            else showStatus("주문 바코드를 읽지 못했습니다. ‘닫기’ 후 다시 시도하세요")
        } else {
            showStatus("스캔이 취소되었습니다. ‘닫기’로 종료")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPickingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.prevButton.setOnClickListener { step(-1) }   // 음성 "이전"
        binding.nextButton.setOnClickListener { step(+1) }   // 음성 "다음"
        binding.closeButton.setOnClickListener { finish() }  // 음성 "닫기"

        val preset = intent.getStringExtra(EXTRA_ORDER_NO)
        if (!preset.isNullOrBlank()) loadOrder(preset) else startScan()
    }

    /** 주문/송장 바코드 스캔. */
    private fun startScan() {
        showStatus("출고 주문 바코드를 스캔하세요")
        try {
            scanLauncher.launch(Intent(BarcodeScanner.ACTION_SCAN))
        } catch (e: Exception) {
            showStatus("내장 스캐너를 호출할 수 없습니다.\n기기 문서의 스캐너 Intent 키를 확인하세요")
        }
    }

    private fun loadOrder(orderNo: String) {
        setLoading(true)
        binding.orderText.text = orderNo
        binding.locationText.text = ""
        showStatus("피킹 목록 조회 중…")
        lifecycleScope.launch {
            val result = api.queryPickList(orderNo)
            setLoading(false)
            when (result) {
                is PickResult.Success -> {
                    pickList = result.pickList
                    index = 0
                    render()
                }
                is PickResult.NotFound -> showStatus(result.message + " · ‘닫기’로 종료")
                is PickResult.Error -> showStatus(result.message + " · ‘닫기’로 종료")
            }
        }
    }

    private fun step(delta: Int) {
        val list = pickList ?: return
        val max = list.lines.size            // 마지막 인덱스 = 목적지 화면
        index = (index + delta).coerceIn(0, max)
        render()
    }

    private fun render() {
        val list = pickList ?: return
        val total = list.lines.size
        val header = buildString {
            append(list.orderNo)
            list.customerName?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
        }
        binding.orderText.text = header

        if (index < total) {
            // 피킹 라인
            val line = list.lines[index]
            binding.progressText.text = "${index + 1} / $total"
            binding.locationText.text = line.locationText.ifBlank { "위치 미등록" }
            binding.itemText.text = listOf(line.itemCode, line.itemName)
                .filter { it.isNotBlank() }.joinToString("  ")
            binding.qtyText.text = "× ${line.qty}"
            showStatus("집은 뒤 ‘다음’")
        } else {
            // 출고 목적지 (완료)
            binding.progressText.text = "완료"
            binding.locationText.text = list.destination?.ifBlank { "목적지 미지정" } ?: "목적지 미지정"
            binding.itemText.text = "출고 목적지"
            binding.qtyText.text = if (total > 0) "$total 개 라인 피킹 완료" else "피킹 라인 없음"
            showStatus("‘닫기’로 종료 · ‘이전’으로 재확인")
        }

        binding.prevButton.isEnabled = index > 0
        binding.nextButton.isEnabled = index < total
    }

    private fun setLoading(loading: Boolean) {
        binding.progress.isVisible = loading
        binding.prevButton.isEnabled = !loading
        binding.nextButton.isEnabled = !loading
    }

    private fun showStatus(message: String) {
        binding.statusText.text = message
    }
}
