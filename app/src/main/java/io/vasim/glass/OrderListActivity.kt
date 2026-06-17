package io.vasim.glass

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import io.vasim.glass.databinding.ActivityOrderListBinding
import kotlinx.coroutines.launch

/**
 * 출고 목록 화면. 활성(대기/피킹중) 출고 주문을 글라스에서 보고 선택해 피킹 시작.
 * 종이/스캔 없이도 진행 가능하며, "주문 스캔"으로 스캔 진입도 병행한다.
 *
 * 주문 선택(탭/음성 번호) → PickingActivity(해당 주문 로드).
 */
class OrderListActivity : AppCompatActivity() {

    private enum class Status { NEUTRAL, ERROR }

    private lateinit var binding: ActivityOrderListBinding
    private val api = GlassApi()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOrderListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.cancelButton.setOnClickListener { finish() }            // 음성 "취소" → 메인
        binding.refreshButton.setOnClickListener { load() }             // 음성 "새로고침"
        binding.scanOrderButton.setOnClickListener {                    // 음성 "주문 스캔"
            startActivity(Intent(this, PickingActivity::class.java))
        }
    }

    // 피킹 후 돌아오면 상태가 바뀌었을 수 있으니 매번 갱신
    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        showStatus("불러오는 중…", Status.NEUTRAL)
        lifecycleScope.launch {
            val orders = api.queryOrders()
            if (orders == null) {
                showStatus("목록을 불러오지 못했습니다 · ‘새로고침’", Status.ERROR)
                return@launch
            }
            populate(orders)
        }
    }

    private fun populate(orders: List<GlassOrder>) {
        binding.listContainer.removeAllViews()
        if (orders.isEmpty()) {
            showStatus("대기 중인 출고 주문이 없습니다", Status.NEUTRAL)
            return
        }
        showStatus("주문을 선택하거나 ‘주문 스캔’ 하세요", Status.NEUTRAL)

        for (o in orders) {
            val btn = layoutInflater.inflate(R.layout.item_order, binding.listContainer, false) as MaterialButton
            val customer = o.customerName?.takeIf { it.isNotBlank() }?.let { "  $it" } ?: ""
            val suffix = if (o.status == "Picking") "  [피킹중]" else ""
            btn.text = "${o.orderNo}$customer  ·  ${o.lineCount}건$suffix"
            btn.setOnClickListener {
                startActivity(
                    Intent(this, PickingActivity::class.java)
                        .putExtra(PickingActivity.EXTRA_ORDER_NO, o.orderNo)
                )
            }
            binding.listContainer.addView(btn)
        }
    }

    private fun showStatus(message: String, kind: Status) {
        binding.statusText.text = message
        val (bg, fg) = when (kind) {
            Status.ERROR -> R.color.danger to R.color.bg
            Status.NEUTRAL -> R.color.surface_variant to R.color.fg
        }
        binding.statusText.backgroundTintList =
            ColorStateList.valueOf(ContextCompat.getColor(this, bg))
        binding.statusText.setTextColor(ContextCompat.getColor(this, fg))
    }
}
