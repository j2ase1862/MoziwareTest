package io.vasim.glass

/**
 * 내장 바코드 스캐너 Intent 상수.
 *
 * 아래 값은 **RealWear 계열 관례값**이며, Moziware Cimo 실제 개발 문서로
 * 검증·교체해야 한다. (아키텍처 문서 §8 / TODO #2)
 *
 * 내장 스캐너 Intent 가 없는 기기라면 `zxing-android-embedded` 같은
 * 포터블 라이브러리로 대체할 수 있다.
 */
object  BarcodeScanner {
    /** 스캐너를 띄우는 Intent action. */
    const val ACTION_SCAN = "com.realwear.barcodereader.intent.action.SCAN_BARCODE"

    /** 결과 Intent 에 담겨 오는 바코드 문자열 extra 키. */
    const val EXTRA_RESULT = "com.realwear.barcodereader.intent.extra.RESULT"
}
