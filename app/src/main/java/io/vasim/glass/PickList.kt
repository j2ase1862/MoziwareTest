package io.vasim.glass

import kotlinx.serialization.Serializable

/**
 * 출고 피킹 목록 — 서버 `/api/glass/pick-list` 응답 DTO (PickListDto와 1:1).
 * 주문 1건 + 집어야 할 라인들 + 완료 후 출고 목적지.
 */
@Serializable
data class PickList(
    val orderNo: String = "",
    val customerName: String? = null,
    val shipTo: String? = null,
    val destination: String? = null,
    val status: String = "",
    val lines: List<PickLine> = emptyList(),
)

/** 피킹 한 줄 — "어디서 무엇을 몇 개". */
@Serializable
data class PickLine(
    val seq: Int = 0,
    val barcode: String = "",
    val itemCode: String = "",
    val itemName: String = "",
    val qty: Int = 0,
    val pickedQty: Int = 0,
    val locationText: String = "",
)
