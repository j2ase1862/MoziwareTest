package io.vasim.glass

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import io.vasim.glass.databinding.ActivityStockListBinding
import kotlinx.coroutines.launch

/**
 * 재고 목록 화면. 콜드체인(냉동/냉장/상온)·유통기한(FEFO) 운용 반영.
 *
 *  · 서버가 임박(daysToExpiry/fefoFlag)을 계산해 내려주고, 화면은 표시만 한다.
 *  · '임박' 필터로 FEFO 우선 처리 대상만 추린다.
 *  · 커서('이전'/'다음')로 행을 강조, 행을 탭/선택하면 상세(현재고·위치·유통기한)를 음성 안내.
 */
class StockListActivity : AppCompatActivity() {

    companion object {
        /** true 로 시작하면 임박 필터가 켜진 상태로 진입(홈 '임박'). */
        const val EXTRA_EXPIRING_ONLY = "expiring_only"
    }

    private enum class Status { NEUTRAL, ERROR }

    private lateinit var binding: ActivityStockListBinding
    private val api = GlassApi()
    private lateinit var speaker: Speaker

    private var items: List<StockItem> = emptyList()
    private val rows = mutableListOf<View>()
    private var cursor = 0
    private var expiringOnly = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStockListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        speaker = Speaker(this)

        expiringOnly = intent.getBooleanExtra(EXTRA_EXPIRING_ONLY, false)

        binding.cancelButton.setOnClickListener { finish() }           // "취소"
        binding.refreshButton.setOnClickListener { load() }            // "새로고침"
        binding.prevButton.setOnClickListener { moveCursor(-1) }       // "이전"
        binding.nextButton.setOnClickListener { moveCursor(+1) }       // "다음"
        binding.filterButton.setOnClickListener { toggleFilter() }     // "임박"/"전체"
        binding.voiceButton.bindVoiceToggle(speaker)                   // "음성 끄기"/"음성 켜기"
    }

    override fun onResume() {
        super.onResume()
        binding.voiceButton.refreshVoiceLabel()
        updateFilterLabel()
        load()
    }

    override fun onDestroy() {
        speaker.shutdown()
        super.onDestroy()
    }

    private fun load() {
        showStatus("불러오는 중…", Status.NEUTRAL, speak = false)
        lifecycleScope.launch {
            val result = api.queryInventory(expiringOnly = expiringOnly)
            if (result == null) {
                showStatus("재고를 불러오지 못했습니다 · ‘새로고침’", Status.ERROR)
                return@launch
            }
            populate(result)
        }
    }

    private fun populate(list: List<StockItem>) {
        items = list
        binding.listContainer.removeAllViews()
        rows.clear()

        val hasItems = list.isNotEmpty()
        binding.prevButton.isEnabled = hasItems
        binding.nextButton.isEnabled = hasItems

        if (!hasItems) {
            val msg = if (expiringOnly) "임박 재고가 없습니다" else "재고가 없습니다"
            showStatus("$msg · ‘전체’ 또는 ‘새로고침’", Status.NEUTRAL)
            return
        }

        list.forEachIndexed { i, s ->
            val row = layoutInflater.inflate(R.layout.item_stock, binding.listContainer, false)
            row.findViewById<TextView>(R.id.badgeText).text = (i + 1).toString()
            row.findViewById<TextView>(R.id.nameText).text = s.itemName

            bindColdTag(row.findViewById(R.id.coldTag), s.coldChain)
            bindFefoTag(row.findViewById(R.id.fefoTag), s)

            row.findViewById<TextView>(R.id.subText).text =
                "${s.itemCode}  ·  ${s.qty}${s.unit}  ·  ${s.locationText}"

            row.setOnClickListener { speakDetail(s) }   // 탭 + WearHF "항목 N 열기"
            binding.listContainer.addView(row)
            rows.add(row)
        }

        cursor = cursor.coerceIn(0, list.size - 1)
        highlight()
        val head = if (expiringOnly) "임박 ${list.size}건 · FEFO 우선" else "재고 ${list.size}건"
        showStatus("$head · ‘다음/이전’ 후 탭", Status.NEUTRAL)
    }

    /** 콜드체인 태그 — 온도대별 색. */
    private fun bindColdTag(tag: TextView, coldChain: String) {
        val (label, fg, bg) = when (coldChain.uppercase()) {
            "FROZEN" -> Triple("냉동", R.color.tag_frozen_fg, R.color.tag_frozen_bg)
            "CHILLED" -> Triple("냉장", R.color.tag_chilled_fg, R.color.tag_chilled_bg)
            else -> Triple("상온", R.color.tag_ambient_fg, R.color.tag_ambient_bg)
        }
        tag.text = label
        tag.setTextColor(ContextCompat.getColor(this, fg))
        tag.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, bg))
    }

    /** FEFO(임박) 태그 — 임박분만 표시. D-n / 당일 / 만료. */
    private fun bindFefoTag(tag: TextView, s: StockItem) {
        if (!s.fefoFlag) {
            tag.visibility = View.GONE
            return
        }
        tag.text = when {
            s.daysToExpiry < 0 -> "만료"
            s.daysToExpiry == 0 -> "오늘"
            else -> "D-${s.daysToExpiry}"
        }
        tag.setTextColor(ContextCompat.getColor(this, R.color.tag_fefo_fg))
        tag.backgroundTintList =
            ColorStateList.valueOf(ContextCompat.getColor(this, R.color.tag_fefo_bg))
        tag.visibility = View.VISIBLE
    }

    private fun speakDetail(s: StockItem) {
        val expiry = if (s.fefoFlag) " · 유통기한 ${s.daysToExpiry}일 남음" else ""
        showStatus("${s.itemName} · 현재고 ${s.qty}${s.unit} · ${s.locationText}$expiry", Status.NEUTRAL)
    }

    private fun moveCursor(delta: Int) {
        if (items.isEmpty()) return
        cursor = (cursor + delta).coerceIn(0, items.size - 1)
        highlight()
    }

    private fun toggleFilter() {
        expiringOnly = !expiringOnly
        cursor = 0
        updateFilterLabel()
        load()
    }

    private fun updateFilterLabel() {
        binding.filterButton.setText(
            if (expiringOnly) R.string.btn_filter_all else R.string.btn_filter_expiring
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
                        this@StockListActivity,
                        if (sel) R.color.primary else R.color.badge_fg
                    )
                )
            }
        }
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
