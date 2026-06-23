package io.vasim.glass

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import io.vasim.glass.databinding.ActivityOrderListBinding
import kotlinx.coroutines.launch

/**
 * 출고 목록 화면. 활성(대기/피킹중) 주문을 글라스에서 보고 선택해 피킹 시작.
 *
 * 선택 방법 3가지(병행):
 *  1) 커서: "다음"/"이전"으로 강조 이동 → "열기"        (피킹과 동일, 가장 안정적)
 *  2) WearHF "항목 N 열기": 행 배지 번호로 직접 선택      (행은 클릭 가능 + 텍스트 a11y 숨김)
 *  3) 탭: 행을 직접 터치
 * "주문 스캔"으로 스캔 진입도 병행.
 */
class OrderListActivity : AppCompatActivity() {

    private enum class Status { NEUTRAL, ERROR }

    private lateinit var binding: ActivityOrderListBinding
    private val api = GlassApi()
    private lateinit var speaker: Speaker

    private var orders: List<GlassOrder> = emptyList()
    private val rows = mutableListOf<View>()
    private var cursor = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOrderListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        speaker = Speaker(this)   // onResume → load() 의 showStatus 보다 먼저 준비

        binding.cancelButton.setOnClickListener { finish() }                 // "취소"
        binding.refreshButton.setOnClickListener { load() }                  // "새로고침"
        binding.prevButton.setOnClickListener { moveCursor(-1) }             // "이전"
        binding.nextButton.setOnClickListener { moveCursor(+1) }             // "다음"
        binding.openButton.setOnClickListener { openCursor() }               // "열기"
        binding.scanOrderButton.setOnClickListener {                         // "주문 스캔"
            startActivity(Intent(this, PickingActivity::class.java))
        }
        binding.voiceButton.bindVoiceToggle(speaker)                         // "음성 끄기"/"음성 켜기"
    }

    override fun onResume() {
        super.onResume()
        binding.voiceButton.refreshVoiceLabel()   // 다른 화면에서 바뀐 상태 반영
        load()
    }

    override fun onDestroy() {
        speaker.shutdown()
        super.onDestroy()
    }

    private fun load() {
        showStatus("불러오는 중…", Status.NEUTRAL, speak = false)
        lifecycleScope.launch {
            val result = api.queryOrders()
            if (result == null) {
                showStatus("목록을 불러오지 못했습니다 · ‘새로고침’", Status.ERROR)
                return@launch
            }
            populate(result)
        }
    }

    private fun populate(list: List<GlassOrder>) {
        orders = list
        binding.listContainer.removeAllViews()
        rows.clear()

        val hasItems = list.isNotEmpty()
        binding.prevButton.isEnabled = hasItems
        binding.nextButton.isEnabled = hasItems
        binding.openButton.isEnabled = hasItems

        if (!hasItems) {
            showStatus("대기 중인 출고 주문이 없습니다 · ‘주문 스캔’", Status.NEUTRAL)
            return
        }

        list.forEachIndexed { i, o ->
            val row = layoutInflater.inflate(R.layout.item_order, binding.listContainer, false)
            row.findViewById<TextView>(R.id.badgeText).text = (i + 1).toString()
            row.findViewById<TextView>(R.id.orderText).text = o.orderNo

            // 서브라인: 거래처 · N건 · 목적지 (값 있는 것만)
            val sub = buildList {
                o.customerName?.takeIf { it.isNotBlank() }?.let { add(it) }
                add("${o.lineCount}건")
                o.destination?.takeIf { it.isNotBlank() }?.let { add(it) }
            }.joinToString("  ·  ")
            row.findViewById<TextView>(R.id.subText).text = sub

            // 상태 태그(피킹중)만 표시. 콜드체인/FEFO 는 서버 데이터 추가 시 coldTag 로 노출.
            row.findViewById<TextView>(R.id.statusTag).apply {
                if (o.status == "Picking") {
                    text = "피킹중"
                    visibility = View.VISIBLE
                } else {
                    visibility = View.GONE
                }
            }

            row.setOnClickListener { open(o) }   // 탭 + WearHF "항목 N 열기"
            binding.listContainer.addView(row)
            rows.add(row)
        }

        cursor = cursor.coerceIn(0, list.size - 1)
        highlight()
        showStatus("‘다음/이전’ 후 ‘열기’, 또는 ‘항목 N 열기’·탭", Status.NEUTRAL)
    }

    private fun moveCursor(delta: Int) {
        if (orders.isEmpty()) return
        cursor = (cursor + delta).coerceIn(0, orders.size - 1)
        highlight()
    }

    private fun openCursor() {
        orders.getOrNull(cursor)?.let { open(it) }
    }

    private fun open(o: GlassOrder) {
        startActivity(
            Intent(this, PickingActivity::class.java)
                .putExtra(PickingActivity.EXTRA_ORDER_NO, o.orderNo)
        )
    }

    private fun highlight() {
        rows.forEachIndexed { i, row ->
            val sel = i == cursor
            row.setBackgroundResource(if (sel) R.drawable.bg_row_selected else R.drawable.bg_row)
            row.findViewById<TextView>(R.id.badgeText).apply {
                setBackgroundResource(if (sel) R.drawable.bg_badge_selected else R.drawable.bg_badge)
                setTextColor(
                    ContextCompat.getColor(
                        this@OrderListActivity,
                        if (sel) R.color.primary else R.color.badge_fg
                    )
                )
            }
        }
        // 커서 행을 화면에 보이게 스크롤
        rows.getOrNull(cursor)?.let { row ->
            binding.scrollView.post {
                binding.scrollView.smoothScrollTo(0, (row.top - 12).coerceAtLeast(0))
            }
        }
    }

    private fun showStatus(message: String, kind: Status, speak: Boolean = true) {
        binding.statusText.text = message
        if (speak) speaker.speak(message)
        val (bg, fg) = when (kind) {
            Status.ERROR -> R.color.danger to R.color.bg
            Status.NEUTRAL -> R.color.surface_variant to R.color.fg
        }
        binding.statusText.backgroundTintList =
            ColorStateList.valueOf(ContextCompat.getColor(this, bg))
        binding.statusText.setTextColor(ContextCompat.getColor(this, fg))
    }
}
