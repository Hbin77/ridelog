package com.ridelog.app.voice

import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale
import ai.pleos.playground.stt.SpeechToText as PleosStt
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.tts.TextToSpeech as PleosTts
import ai.pleos.playground.tts.constant.Mode as TtsMode
import android.speech.tts.TextToSpeech as AndroidTts

private const val TAG = "RideLog.Voice"

interface SpeechOut {
    val engineName: String
    fun speak(text: String)
    fun release()
}

interface SpeechIn {
    val engineName: String
    /** 한 번 듣고 최종 문장을 돌려준다. 실패 시 null. */
    fun listenOnce(onResult: (String?) -> Unit)
    fun release()
}

/** Pleos TTS(ON_DEVICE, 앱 등록 불필요) 우선, 실패 시 Android 표준 TTS. */
class VehicleSpeechOut(private val context: Context) : SpeechOut {
    private var pleos: PleosTts? = null
    private var pleosReady = false
    private var android: AndroidTts? = null
    override var engineName = "초기화 중"; private set

    init {
        runCatching {
            val tts = PleosTts(context, TtsMode.ON_DEVICE)
            val events = object : EventListener {
                override fun onStart() = Unit
                override fun onReady() = Unit
                override fun onDone() = Unit
                override fun onStop() = Unit
                override fun onUpdatedRms(p0: Double) = Unit
                override fun onError(p0: ErrorType) { Log.w(TAG, "Pleos TTS 오류 $p0") }
            }
            tts.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    runCatching { tts.addEventListener(events) }.onFailure { Log.w(TAG, "TTS 리스너 등록 실패", it) }
                    runCatching { tts.setLocale(Locale.KOREA) }
                    pleosReady = true
                    engineName = "Pleos TTS (온디바이스)"; Log.i(TAG, "Pleos TTS 준비 완료")
                }
                override fun onFailure(p0: ErrorType) {
                    Log.w(TAG, "Pleos TTS 초기화 실패 $p0 → Android TTS")
                    useAndroid()
                }
            })
            pleos = tts
        }.onFailure { Log.w(TAG, "Pleos TTS 생성 실패", it); useAndroid() }
    }

    private fun useAndroid() {
        if (android != null) return
        android = AndroidTts(context) { status ->
            if (status == AndroidTts.SUCCESS) {
                android?.language = Locale.KOREA
                engineName = "Android TTS"
            }
        }
    }

    override fun speak(text: String) {
        val p = pleos
        if (p != null && pleosReady) {
            runCatching { p.speak(text, true) }.onFailure { useAndroid(); android?.speak(text, AndroidTts.QUEUE_ADD, null, text) }
        } else {
            useAndroid()
            android?.speak(text, AndroidTts.QUEUE_ADD, null, text.hashCode().toString())
        }
    }

    override fun release() {
        runCatching { pleos?.release() }
        android?.shutdown()
    }
}

/** Pleos STT(ON_DEVICE) 우선, 실패 시 Android SpeechRecognizer. */
class VehicleSpeechIn(private val context: Context) : SpeechIn {
    private var pleos: PleosStt? = null
    private var pleosReady = false
    private var pending: ((String?) -> Unit)? = null
    override var engineName = "초기화 중"; private set

    init {
        runCatching {
            val stt = PleosStt(context, SttMode.ON_DEVICE)
            val results = object : ResultListener {
                override fun onUpdated(p0: String, p1: Boolean) {
                    if (p1) deliver(p0)
                }
                override fun onUpdatedEpdData(p0: Long, p1: Long) = Unit
                override fun onStartedRecognition() = Unit
                override fun onEndedRecognition() = deliver(null) // 최종 결과 없이 끝나면 대기를 푼다
                override fun onError(p0: ai.pleos.playground.stt.constant.ErrorType) {
                    Log.w(TAG, "Pleos STT 오류 $p0"); deliver(null)
                }
                override fun onReady() = Unit
                override fun onUpdatedRms(p0: Double) = Unit
            }
            stt.initialize(object : ai.pleos.playground.stt.listener.OnInitializeListener {
                override fun onSuccess() {
                    runCatching { stt.addListener(results) }.onFailure { Log.w(TAG, "STT 리스너 등록 실패", it) }
                    runCatching { stt.setLocale(Locale.KOREA) }
                    pleosReady = true
                    engineName = "Pleos STT (온디바이스)"; Log.i(TAG, "Pleos STT 준비 완료")
                }
                override fun onFailure(p0: ai.pleos.playground.stt.constant.ErrorType) {
                    Log.w(TAG, "Pleos STT 초기화 실패 $p0"); engineName = "Android 음성 인식"
                }
            })
            pleos = stt
        }.onFailure { Log.w(TAG, "Pleos STT 생성 실패", it); engineName = "Android 음성 인식" }
    }

    private fun deliver(text: String?) {
        val cb = pending ?: return
        pending = null
        cb(text?.takeIf { it.isNotBlank() })
    }

    override fun listenOnce(onResult: (String?) -> Unit) {
        pending = onResult
        val p = pleos
        if (p != null && pleosReady) {
            runCatching { p.request() }.onFailure { listenWithAndroid() }
        } else listenWithAndroid()
    }

    /** 대체 인식기도 기기 내 인식만 쓴다 (음성이 기기 밖으로 나가지 않게). */
    private fun listenWithAndroid() {
        if (android.os.Build.VERSION.SDK_INT < 31 || !SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) { deliver(null); return }
        val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                deliver(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull())
                recognizer.destroy()
            }
            override fun onError(error: Int) { deliver(null); recognizer.destroy() }
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        recognizer.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        })
    }

    override fun release() { runCatching { pleos?.release() } }
}
