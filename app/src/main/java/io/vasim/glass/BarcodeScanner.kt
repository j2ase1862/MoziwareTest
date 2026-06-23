package io.vasim.glass

import android.content.Context
import android.content.Intent

/**
 * 내장 바코드 스캐너 Intent 헬퍼.
 *
 * **모지웨어 시모(MZ1000) 실기기 검증 완료 (2026-06-23).**
 * 내장 스캐너 앱 `cn.moziware.barcodescanner` (= ZXing 기반 CodeScanner)는
 * 네이티브 키와 RealWear 호환 키를 **둘 다** 같은 액티비티에 매핑한다.
 *  - 네이티브 : `cn.moziware.barcodescanner.action.SCAN_BARCODE` / `.extra.RESULT`
 *  - 호환     : `com.realwear.barcodereader.intent.action.SCAN_BARCODE` / `.extra.RESULT`
 *
 * 네이티브 키를 우선 사용하고, 기기에서 해석되지 않으면 RealWear 키로 폴백한다.
 * (RealWear 호환 alias 가 향후 FOTA 에서 제거될 가능성에 대비)
 *
 * 한 프레임당 단일 바코드만 디코딩하므로 FOV 멀티 리딩은 지원하지 않는다.
 *
 * 참고: targetSdk 30+ 패키지 가시성 때문에 [scanIntent] 의 `resolveActivity`
 * 프로빙이 동작하려면 AndroidManifest 의 `<queries>` 에 두 action 이 선언돼야 한다.
 */
object BarcodeScanner {
    /** 모지웨어 네이티브 스캔 action. */
    const val ACTION_SCAN = "cn.moziware.barcodescanner.action.SCAN_BARCODE"

    /** RealWear 호환 스캔 action (폴백). */
    const val ACTION_SCAN_REALWEAR = "com.realwear.barcodereader.intent.action.SCAN_BARCODE"

    /** 모지웨어 네이티브 결과 extra 키. */
    const val EXTRA_RESULT = "cn.moziware.barcodescanner.extra.RESULT"

    /** RealWear 호환 결과 extra 키 (폴백). */
    const val EXTRA_RESULT_REALWEAR = "com.realwear.barcodereader.intent.extra.RESULT"

    /**
     * 내장 스캐너 호출 Intent. 네이티브 action 이 기기에서 해석되면 그것을,
     * 아니면 RealWear 호환 action 을 사용한다.
     */
    fun scanIntent(context: Context): Intent {
        val native = Intent(ACTION_SCAN)
        val resolvable = context.packageManager.resolveActivity(native, 0) != null
        return if (resolvable) native else Intent(ACTION_SCAN_REALWEAR)
    }

    /** 결과 Intent 에서 바코드 문자열을 추출한다. 네이티브 키 우선, RealWear 키 폴백. */
    fun extractResult(data: Intent?): String? =
        data?.getStringExtra(EXTRA_RESULT) ?: data?.getStringExtra(EXTRA_RESULT_REALWEAR)
}
