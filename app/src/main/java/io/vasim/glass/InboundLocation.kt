package io.vasim.glass

import kotlinx.serialization.Serializable

/**
 * 서버 응답 DTO. BODA.VMS.Web `/api/glass/inbound-location` 응답과 1:1 매핑.
 * ASP.NET 기본 직렬화(camelCase) ↔ Kotlin 필드명 자동 매칭.
 *
 *   200 InboundLocationDto { itemCode, itemName, locationText, coord{ x, y, z } }
 */
@Serializable
data class InboundLocation(
    val itemCode: String? = null,
    val itemName: String? = null,
    val locationText: String = "",
    val coord: Coord? = null,
)

/** 2차 목표(3D 도면 하이라이트)에서 사용할 좌표. 1차(텍스트)에서는 미사용. */
@Serializable
data class Coord(
    val x: Double = 0.0,
    val y: Double = 0.0,
    val z: Double = 0.0,
)
