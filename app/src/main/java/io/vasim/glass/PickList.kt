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

/** 출고 목록 항목 — 서버 GlassOrderDto와 1:1 (경량 요약). */
@Serializable
data class GlassOrder(
    val orderNo: String = "",
    val customerName: String? = null,
    val destination: String? = null,
    val lineCount: Int = 0,
    val status: String = "",
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

/** 스캔 검증 피킹 확정 요청 (2차). */
@Serializable
data class PickConfirmRequest(
    val orderNo: String,
    val barcode: String,
    val qty: Int = 1,
)

/** 피킹 확정 결과 (2차) — PickConfirmResult DTO와 1:1. */
@Serializable
data class PickConfirmResult(
    val matched: Boolean = false,
    val alreadyComplete: Boolean = false,
    val allPicked: Boolean = false,
    val message: String = "",
    val pickList: PickList = PickList(),
)
