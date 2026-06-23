package io.vasim.glass

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.vasim.glass.databinding.ActivityDashboardBinding
import kotlinx.coroutines.launch

/**
 * 홈 대시보드 — 앱의 진입점(런처).
 *
 * 입고/출고/재고 3섹션 + 유통기한 임박(FEFO) 경고를 한눈에 보여주고,
 * 음성 명령(버튼 text)으로 각 작업 화면으로 진입한다.
 *   "입고" → 입고 작업(MainActivity)
 *   "출고" → 출고 목록(OrderListActivity)
 *   "재고" → 재고 목록(StockListActivity)
 *   "임박" → 재고 목록(임박 필터)
 */
class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private val api = GlassApi()
    private lateinit var speaker: Speaker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)
        speaker = Speaker(this)

        binding.inboundButton.setOnClickListener {                       // "입고"
            startActivity(Intent(this, MainActivity::class.java))
        }
        binding.outboundButton.setOnClickListener {                      // "출고"
            startActivity(Intent(this, OrderListActivity::class.java))
        }
        binding.inventoryButton.setOnClickListener {                     // "재고"
            startActivity(Intent(this, StockListActivity::class.java))
        }
        binding.expiringButton.setOnClickListener {                      // "임박"
            startActivity(
                Intent(this, StockListActivity::class.java)
                    .putExtra(StockListActivity.EXTRA_EXPIRING_ONLY, true)
            )
        }
        binding.exitButton.setOnClickListener { finishAffinity() }       // "종료"
        binding.voiceButton.bindVoiceToggle(speaker)                     // "음성 끄기"/"음성 켜기"
    }

    override fun onResume() {
        super.onResume()
        binding.voiceButton.refreshVoiceLabel()
        load()
    }

    override fun onDestroy() {
        speaker.shutdown()
        super.onDestroy()
    }

    /** 집계 로드 — 실패해도 화면은 유지(카운트만 '—'). */
    private fun load() {
        lifecycleScope.launch {
            val s = api.querySummary() ?: return@launch
            binding.inboundCount.text = s.inboundWaiting.toString()
            binding.outboundCount.text = s.outboundWaiting.toString()
            binding.inventoryCount.text = formatCount(s.stockSku)
            binding.expiringText.text =
                if (s.expiringCount > 0) "${getString(R.string.expiring_title)} ${s.expiringCount}건"
                else getString(R.string.expiring_title)
        }
    }

    /** 1,240 처럼 천단위 구분. */
    private fun formatCount(n: Int): String = "%,d".format(n)
}
