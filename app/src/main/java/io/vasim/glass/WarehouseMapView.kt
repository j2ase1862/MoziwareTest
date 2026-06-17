package io.vasim.glass

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

/** locationText("A-01-2-07")를 파싱한 위치 정보. */
data class ParsedLoc(
    val zone: String,
    val rackText: String,
    val rack: Int,
    val level: String,
    val bin: String,
    val ok: Boolean,
)

object LocationFormat {
    /** "A-01-2-07" → Zone/Rack/Level/Bin. 형식이 아니어도 안전하게 파싱. */
    fun parse(text: String?): ParsedLoc {
        val parts = (text ?: "").split("-").map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return ParsedLoc("", "", 0, "", "", false)
        val zone = parts.getOrElse(0) { "" }
        val rackText = parts.getOrElse(1) { "" }
        val level = parts.getOrElse(2) { "" }
        val bin = parts.getOrElse(3) { "" }
        val rack = rackText.toIntOrNull() ?: rackText.filter { it.isDigit() }.toIntOrNull() ?: 0
        return ParsedLoc(zone, rackText, rack, level, bin, true)
    }

    /** 방향 힌트: "A구역 · 01랙 · 2단 · 07칸". */
    fun hint(p: ParsedLoc): String {
        val s = mutableListOf<String>()
        if (p.zone.isNotEmpty()) s.add("${p.zone}구역")
        if (p.rackText.isNotEmpty()) s.add("${p.rackText}랙")
        if (p.level.isNotEmpty()) s.add("${p.level}단")
        if (p.bin.isNotEmpty()) s.add("${p.bin}칸")
        return s.joinToString("  ·  ")
    }
}

/**
 * 창고 2D 미니맵(탑다운). 구역(A/B/C…)을 세로 통로로 그리고, 대상 위치를 핀으로 표시한다.
 * 좌표가 없어도 Zone(열)+Rack(행)으로 위치를 근사 표시. (단안 글라스용 경량 HUD)
 */
class WarehouseMapView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val zoneOrder = listOf("A", "B", "C", "D", "E")
    private val maxRack = 8
    private var loc: ParsedLoc? = null

    private fun c(res: Int) = ContextCompat.getColor(context, res)
    private val dm = resources.displayMetrics
    private fun dp(v: Float) = v * dm.density
    private fun sp(v: Float) = v * dm.scaledDensity

    private val colStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = dp(1.5f); color = c(R.color.divider)
    }
    private val activeFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; color = c(R.color.primary); alpha = 55
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = dp(1f); color = c(R.color.divider); alpha = 120
    }
    private val pinFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c(R.color.accent) }
    private val pinRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = dp(3f); color = c(R.color.accent); alpha = 110
    }
    private val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; isFakeBoldText = true; textSize = sp(15f); color = c(R.color.muted)
    }
    private val pinTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; isFakeBoldText = true; textSize = sp(13f); color = c(R.color.bg)
    }
    private val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; textSize = sp(14f); color = c(R.color.muted)
    }

    fun setLocation(p: ParsedLoc?) {
        loc = if (p?.ok == true) p else null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pad = dp(6f)
        val left = pad; val top = pad
        val w = width - 2 * pad; val h = height - 2 * pad
        if (w <= 0 || h <= 0) return

        val l = loc
        if (l == null) {
            canvas.drawText("위치", left + w / 2, top + h / 2, placeholderPaint)
            return
        }

        val zIdx = zoneOrder.indexOf(l.zone)            // -1 = 미지정 구역
        val cols = maxOf(3, zIdx + 1)
        val colW = w / cols
        val headerH = dp(22f)
        val gridTop = top + headerH
        val gridH = h - headerH

        for (i in 0 until cols) {
            val cx = left + i * colW
            val r = RectF(cx + dp(3f), gridTop, cx + colW - dp(3f), top + h)
            if (i == zIdx) canvas.drawRoundRect(r, dp(8f), dp(8f), activeFill)
            canvas.drawRoundRect(r, dp(8f), dp(8f), colStroke)
            // 구역 머리글
            headerPaint.color = if (i == zIdx) c(R.color.accent) else c(R.color.muted)
            canvas.drawText(zoneOrder.getOrElse(i) { "?" }, cx + colW / 2, top + headerH - dp(7f), headerPaint)
            // 랙 눈금
            for (rk in 1 until maxRack) {
                val y = gridTop + gridH * rk / maxRack
                canvas.drawLine(cx + dp(8f), y, cx + colW - dp(8f), y, tickPaint)
            }
        }

        // 핀
        val pinR = dp(13f)
        if (zIdx >= 0) {
            val cx = left + zIdx * colW + colW / 2
            val rk = (if (l.rack in 1..maxRack) l.rack else 1).toFloat()
            val py = gridTop + gridH * (rk - 0.5f) / maxRack
            canvas.drawCircle(cx, py, pinR + dp(5f), pinRing)
            canvas.drawCircle(cx, py, pinR, pinFill)
            val label = l.rackText.ifEmpty { "?" }
            canvas.drawText(label, cx, py + sp(4.5f), pinTextPaint)
        } else {
            // 구역 미지정 → 가운데 마커 + 라벨
            val cx = left + w / 2; val cy = gridTop + gridH / 2
            canvas.drawCircle(cx, cy, pinR + dp(5f), pinRing)
            canvas.drawCircle(cx, cy, pinR, pinFill)
            canvas.drawText("●", cx, cy + sp(4.5f), pinTextPaint)
        }
    }
}
