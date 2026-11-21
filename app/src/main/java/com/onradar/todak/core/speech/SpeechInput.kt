package com.onradar.todak.core.speech

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionService
import android.util.Log
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * 누르고 말하기 (STT). 메인 스레드에서 만들고 불러야 합니다.
 * 결과는 서버로 글로만 보냅니다 (음성은 서버로 가지 않음).
 */
class SpeechInput(
    context: Context,
    private val onListening: () -> Unit,
    private val onResult: (String) -> Unit,
    private val onError: (SpeechError) -> Unit,
) {
    enum class SpeechError { NOT_AVAILABLE, NEED_GOOGLE_APP, NO_MATCH, NETWORK, OTHER }

    private val appContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null

    /**
     * 어떤 인식기를 쓸지 고릅니다.
     * - Google 음성 인식 서비스가 있으면 그것을 직접 씁니다. 삼성 폰 기본값인 빅스비는 결국 Google로 넘기기만 해서,
     *   중간 단계에서 실패하면 앱에 아무 응답도 오지 않기 때문입니다.
     * - Google 앱이 설치돼 있는데 꺼져 있고 남은 인식기가 빅스비뿐이면, 기다리지 않고 바로 NEED_GOOGLE_APP.
     */
    private sealed interface Choice {
        data class Use(val component: ComponentName?) : Choice
        data class Unavailable(val error: SpeechError) : Choice
    }

    private fun choose(): Choice {
        val pm = appContext.packageManager
        val services = pm.queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), 0)
            .map { ComponentName(it.serviceInfo.packageName, it.serviceInfo.name) }
        Log.d(TAG, "인식 서비스: $services")
        services.firstOrNull { it.packageName == GOOGLE_APP }?.let { return Choice.Use(it) }
        if (services.isEmpty() || !SpeechRecognizer.isRecognitionAvailable(appContext)) {
            return Choice.Unavailable(if (isGoogleAppDisabled(pm)) SpeechError.NEED_GOOGLE_APP else SpeechError.NOT_AVAILABLE)
        }
        // 빅스비만 남았는데 빅스비가 넘길 Google 앱이 꺼져 있으면 동작하지 않습니다.
        if (services.all { it.packageName == BIXBY } && isGoogleAppDisabled(pm)) {
            return Choice.Unavailable(SpeechError.NEED_GOOGLE_APP)
        }
        return Choice.Use(null) // 폰 기본 인식기
    }

    private fun isGoogleAppDisabled(pm: PackageManager): Boolean = try {
        !pm.getApplicationInfo(GOOGLE_APP, 0).enabled
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    fun start() {
        val component = when (val choice = choose()) {
            is Choice.Unavailable -> {
                Log.w(TAG, "음성 인식을 쓸 수 없음: ${choice.error}")
                onError(choice.error)
                return
            }
            is Choice.Use -> choice.component
        }
        recognizer?.destroy()
        recognizer = (
            if (component != null) SpeechRecognizer.createSpeechRecognizer(appContext, component)
            else SpeechRecognizer.createSpeechRecognizer(appContext)
            ).apply {
            setRecognitionListener(listener)
            startListening(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                },
            )
        }
    }

    /** 말을 다 했다고 알림 → 인식 결과가 곧 옵니다. */
    fun stop() {
        recognizer?.stopListening()
    }

    fun release() {
        recognizer?.destroy()
        recognizer = null
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = onListening()
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onPartialResults(partialResults: Bundle?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
            if (text.isNullOrEmpty()) onError(SpeechError.NO_MATCH) else onResult(text)
        }

        override fun onError(error: Int) {
            Log.w(TAG, "인식 오류 코드: $error")
            onError(
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SpeechError.NO_MATCH
                    // Google 음성 인식은 목소리를 서버로 보내 글로 바꾸므로 인터넷이 필요합니다.
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER -> SpeechError.NETWORK
                    else -> SpeechError.OTHER
                },
            )
        }
    }

    private companion object {
        const val TAG = "SpeechInput"
        const val GOOGLE_APP = "com.google.android.googlequicksearchbox"
        const val BIXBY = "com.samsung.android.bixby.agent"
    }
}
