package io.vasim.glass

import kotlinx.serialization.Serializable

/** 입고(적치) 확정 요청 — 서버 InboundConfirmRequest 와 1:1. */
@Serializable
data class InboundConfirmRequest(
    val barcode: String,
    val qty: Int = 1,
)

/** 입고 확정 결과 — 서버 InboundConfirmResult 와 1:1 (출고 PickConfirmResult 와 대칭). */
@Serializable
data class InboundConfirmResult(
    val confirmed: Boolean = false,
    val message: String = "",
    val itemCode: String = "",
    val itemName: String = "",
    val locationText: String = "",
    val qty: Int = 0,
    val stockAfter: Int = 0,
)
