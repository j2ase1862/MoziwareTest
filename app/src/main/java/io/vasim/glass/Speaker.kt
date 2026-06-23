package io.vasim.glass

import android.content.Context
import android.speech.tts.TextToSpeech
import com.google.android.material.button.MaterialButton
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

    companion object {
        /** 발화 속도(1.0=기본). 느리게 해 또렷하게. */
        private const val SPEECH_RATE = 0.8f
        /** 발화 높낮이(1.0=기본). */
        private const val SPEECH_PITCH = 1.0f
        /** 절(. , ! ?) 사이에 끼우는 무음 길이(ms) — 끊어 읽기. */
        private const val PAUSE_MS = 350L
    }

    private val appContext = context.applicationContext
    private var ready = false
    private var pending: String? = null

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.KOREA   // ko-KR 전체 로케일
            tts.setSpeechRate(SPEECH_RATE)   // 느리게 → 또렷하게
            tts.setPitch(SPEECH_PITCH)
            selectBestKoreanVoice()
            ready = true
            pending?.let { say(it) }
            pending = null
        }
    }

    /** 엔진이 한국어 보이스를 여러 개 제공하면 최고 품질·오프라인 것을 고른다. */
    private fun selectBestKoreanVoice() {
        runCatching {
            tts.voices
                ?.filter { it.locale.language == "ko" && !it.isNetworkConnectionRequired }
                ?.maxByOrNull { it.quality }
                ?.let { tts.voice = it }
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

    /**
     * 절(. , ! ?) 단위로 끊어, 각 절 사이에 [PAUSE_MS] 무음을 끼워 또박또박 읽는다.
     * 첫 절은 QUEUE_FLUSH 로 이전 발화를 끊고, 이후는 QUEUE_ADD 로 이어 붙인다.
     */
    private fun say(text: String) {
        val parts = text.split(Regex("(?<=[.,!?])\\s*")).filter { it.isNotBlank() }
        if (parts.isEmpty()) return
        var mode = TextToSpeech.QUEUE_FLUSH
        parts.forEachIndexed { i, part ->
            tts.speak(part, mode, null, "u$i")
            mode = TextToSpeech.QUEUE_ADD
            if (i < parts.lastIndex) tts.playSilentUtterance(PAUSE_MS, TextToSpeech.QUEUE_ADD, "s$i")
        }
    }

    /**
     * 화면용 약식 문구를 소리내어 읽기 자연스럽게 정리한다.
     *  - 장식 기호(✓ ❌ … ‘ ’ “ ”) 제거
     *  - 중점 `·` → 문장 끊기(자연스러운 쉼)
     *  - 슬래시 `/` → '또는', 괄호 보조설명 → 쉼표
     *  - 공백/중복 종결부호 정리 후 문장 끝맺음(하강 억양)
     */
    private fun clean(s: String): String {
        var t = s
            .replace("✓", "")
            .replace("❌", "")
            .replace("…", " ")
            .replace("‘", "").replace("’", "")     // 음성 명령 인용부호
            .replace("“", "").replace("”", "")
            .replace("·", ". ")                     // 중점 → 문장 끊기
            .replace("/", " 또는 ")                 // 슬래시 → '또는'
            .replace("(", ", ").replace(")", "")    // 괄호 보조설명 → 쉼표
        t = t.replace(Regex("\\s+"), " ").trim()                 // 공백 정리
        t = t.replace(Regex("([.!?])\\s*\\."), "$1")             // '! .' 같은 중복 종결 정리
        t = t.replace(Regex("[\\s.,]+$"), "")                    // 끝 공백/구두점 제거
        return if (t.isBlank()) t else "$t."                     // 문장 끝맺음
    }

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

/** 토글 버튼 라벨을 현재 상태의 반대 동작('음성 끄기'⇄'음성 켜기')으로 맞춘다. */
fun MaterialButton.refreshVoiceLabel() = setText(
    if (VoiceSetting.enabled(context)) R.string.btn_voice_off else R.string.btn_voice_on
)

/**
 * 음성 토글 버튼 배선(홈·피킹·출고목록 공용). 누르면 전역 [VoiceSetting] 을 뒤집고
 * 라벨을 갱신한다. 켤 때 확인 발화, 끌 때 진행 중 발화 중단.
 * 다른 화면에서 바뀐 상태 반영을 위해 onResume 에서 [refreshVoiceLabel] 도 호출할 것.
 */
fun MaterialButton.bindVoiceToggle(speaker: Speaker) {
    refreshVoiceLabel()
    setOnClickListener {
        val enabled = !VoiceSetting.enabled(context)
        VoiceSetting.setEnabled(context, enabled)
        refreshVoiceLabel()
        if (enabled) speaker.speak("음성 안내를 켰습니다") else speaker.stop()
    }
}
