package io.vasim.glass

import kotlinx.serialization.Serializable

/**
 * 재고 항목 — 서버 `/api/glass/inventory` 응답 DTO(StockItemDto)와 1:1.
 * 식품 콜드체인 운용 반영: 온도대(coldChain) · 로트/유통기한(FEFO).
 * daysToExpiry/fefoFlag 는 서버가 계산해 내려준다(임박 기준은 서버 정책).
 */
@Serializable
data class StockItem(
    val itemCode: String = "",
    val itemName: String = "",
    val qty: Int = 0,
    val unit: String = "EA",
    val locationText: String = "",
    val coldChain: String = "AMBIENT", // FROZEN | CHILLED | AMBIENT
    val lotNo: String = "",
    val mfgDate: String = "",
    val expiryDate: String = "",
    val daysToExpiry: Int = 0,
    val fefoFlag: Boolean = false,
)

/** 홈 대시보드 집계 — 서버 `/api/glass/summary` 응답(SummaryDto)과 1:1. */
@Serializable
data class GlassSummary(
    val inboundWaiting: Int = 0,
    val outboundWaiting: Int = 0,
    val stockSku: Int = 0,
    val expiringCount: Int = 0,
)
