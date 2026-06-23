package io.vasim.glass

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * 상태 메시지를 음성으로 읽어주는 얇은 [TextToSpeech] 래퍼.
 *
 * 시모 내장 엔진 `io.moziware.ttsservice`(MoziVoice)가 표준 `TTS_SERVICE` 를
 * 구현하므로, 별도 의존성 없이 안드로이드 표준 API 만으로 한국어가 합성된다.
 *
 * 동작 규칙:
 *  - 초기화는 **비동기**. 준비 전 [speak] 호출은 마지막 1건만 보관했다가 준비되면 재생.
 *  - 새 메시지는 `QUEUE_FLUSH` 로 이전 발화를 끊는다(상태가 바뀌면 옛 안내는 무의미).
 *  - 화면 장식 기호(✓ ❌ ‘ ’ “ ” … ·)는 읽지 않도록 [clean] 으로 정리.
 *
 * 결과/완료 메시지만 발화한다('…중' 같은 로딩 안내는 호출부에서 speak=false 로 제외).
 */
class Speaker(context: Context) {

    private val appContext = context.applicationContext
    private var ready = false
    private var pending: String? = null

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.KOREAN
            ready = true
            pending?.let { say(it) }
            pending = null
        }
    }

    /**
     * [message] 를 한국어로 발화. 준비 전이면 보관 후 준비되면 마지막 1건 재생.
     * 음성 안내가 꺼져 있으면([VoiceSetting]) 아무것도 하지 않는다.
     */
    fun speak(message: String) {
        if (!VoiceSetting.enabled(appContext)) return
        val text = clean(message)
        if (text.isBlank()) return
        if (ready) say(text) else pending = text
    }

    /** 진행 중인 발화를 즉시 중단(음성 끄기 시). */
    fun stop() {
        pending = null
        tts.stop()
    }

    private fun say(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, text.hashCode().toString())
    }

    /** 발화 전 화면 장식 기호 제거(읽으면 어색한 문자). */
    private fun clean(s: String): String =
        s.replace("✓", "")
            .replace("❌", "")
            .replace("‘", "").replace("’", "")
            .replace("“", "").replace("”", "")
            .replace("·", " ")
            .replace("…", " ")
            .trim()

    /** 액티비티 onDestroy 에서 호출해 엔진 자원을 해제한다. */
    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}

/**
 * 음성 안내 on/off 전역 설정. SharedPreferences 로 앱 전체·재시작에 걸쳐 영속.
 * 모든 화면의 [Speaker] 가 이 값을 따른다(기본 켜짐).
 */
object VoiceSetting {
    private const val PREFS = "glass_prefs"
    private const val KEY_VOICE = "voice_enabled"

    fun enabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_VOICE, true)

    fun setEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_VOICE, value).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
