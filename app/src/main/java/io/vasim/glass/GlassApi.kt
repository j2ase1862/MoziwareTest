package io.vasim.glass

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** `/api/glass` 호출 결과를 화면 상태로 환원한 sealed 타입. */
sealed interface GlassResult {
    data class Success(val location: InboundLocation) : GlassResult
    data class NotFound(val message: String) : GlassResult
    data class Error(val message: String) : GlassResult
}

/** 출고 피킹 목록 조회 결과. */
sealed interface PickResult {
    data class Success(val pickList: PickList) : PickResult
    data class NotFound(val message: String) : PickResult
    data class Error(val message: String) : PickResult
}

/**
 * BODA.VMS.Web 의 글라스 전용 엔드포인트를 호출하는 얇은 HTTP 클라이언트.
 * 네트워크/파싱은 모두 [Dispatchers.IO] 에서 수행하고, 호출부는 코루틴으로 await 한다.
 */
class GlassApi(
    private val baseUrl: String = BuildConfig.BASE_URL,
    private val apiKey: String = BuildConfig.API_KEY,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * GET /api/glass/inbound-location?barcode={바코드}&mode={입고|입고제품}
     *
     * @param barcode 스캔한 바코드 원문
     * @param mode    음성 명령("입고" / "입고제품")
     */
    suspend fun queryInboundLocation(barcode: String, mode: String): GlassResult =
        withContext(Dispatchers.IO) {
            try {
                val url = "$baseUrl/api/glass/inbound-location".toHttpUrl().newBuilder()
                    .addQueryParameter("barcode", barcode)
                    .addQueryParameter("mode", mode)
                    .build()

                val requestBuilder = Request.Builder().url(url).get()
                if (apiKey.isNotBlank()) {
                    requestBuilder.header("X-API-Key", apiKey)
                }

                client.newCall(requestBuilder.build()).execute().use { response ->
                    when {
                        response.isSuccessful -> {
                            val body = response.body?.string().orEmpty()
                            GlassResult.Success(json.decodeFromString<InboundLocation>(body))
                        }
                        response.code == 404 -> GlassResult.NotFound("등록되지 않은 바코드")
                        else -> GlassResult.Error("서버 오류 (HTTP ${response.code})")
                    }
                }
            } catch (e: IOException) {
                GlassResult.Error("네트워크 오류: ${e.message ?: "연결 실패"}")
            } catch (e: Exception) {
                GlassResult.Error("응답 처리 오류: ${e.message ?: e.javaClass.simpleName}")
            }
        }

    /**
     * GET /api/glass/pick-list?orderNo={주문번호}
     * 출고 주문 기반 피킹 목록(읽기 가이드).
     */
    suspend fun queryPickList(orderNo: String): PickResult =
        withContext(Dispatchers.IO) {
            try {
                val url = "$baseUrl/api/glass/pick-list".toHttpUrl().newBuilder()
                    .addQueryParameter("orderNo", orderNo)
                    .build()

                val requestBuilder = Request.Builder().url(url).get()
                if (apiKey.isNotBlank()) {
                    requestBuilder.header("X-API-Key", apiKey)
                }

                client.newCall(requestBuilder.build()).execute().use { response ->
                    when {
                        response.isSuccessful -> {
                            val body = response.body?.string().orEmpty()
                            PickResult.Success(json.decodeFromString<PickList>(body))
                        }
                        response.code == 404 -> PickResult.NotFound("등록되지 않은 주문번호")
                        else -> PickResult.Error("서버 오류 (HTTP ${response.code})")
                    }
                }
            } catch (e: IOException) {
                PickResult.Error("네트워크 오류: ${e.message ?: "연결 실패"}")
            } catch (e: Exception) {
                PickResult.Error("응답 처리 오류: ${e.message ?: e.javaClass.simpleName}")
            }
        }
}
