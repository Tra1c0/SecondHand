// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * The system's on-device speech recognizer (SpeechRecognizer.createOnDeviceSpeechRecognizer,
 * Android 12+). There is deliberately no other implementation: where on-device recognition is
 * missing, dictation is unavailable. The general SpeechRecognizer, which the platform documents
 * as likely to stream audio to remote servers, is never created. The recognition itself is done
 * by the system's recognition service, not by this app.
 */
class OnDeviceSpeechEngine(context: Context) : SpeechEngine {
    private val context = context.applicationContext
    private var recognizer: SpeechRecognizer? = null

    override val available: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(context) }.getOrDefault(false)

    override fun start(languageTag: String, listener: SpeechEngine.Listener) {
        release()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !available) {
            listener.onError(DictationError.UNAVAILABLE)
            return
        }
        val created = try { SpeechRecognizer.createOnDeviceSpeechRecognizer(context) }
            catch (_: Exception) { listener.onError(DictationError.UNAVAILABLE); return }
        recognizer = created
        created.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                if (recognizer !== created) return
                release()
                listener.onFinal(best(results))
            }
            override fun onPartialResults(partialResults: Bundle?) {
                if (recognizer === created) listener.onPartial(best(partialResults))
            }
            override fun onError(error: Int) {
                if (recognizer !== created) return
                release()
                listener.onError(map(error))
            }
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        try {
            created.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true))
        } catch (_: Exception) {
            release()
            listener.onError(DictationError.FAILED)
        }
    }

    override fun stop() { runCatching { recognizer?.stopListening() } }

    override fun cancel() {
        val old = recognizer
        recognizer = null // first: its late callbacks are ignored by the identity checks
        runCatching { old?.cancel() }
        runCatching { old?.destroy() }
    }

    private fun release() {
        val old = recognizer
        recognizer = null
        runCatching { old?.destroy() }
    }

    private fun best(results: Bundle?): String =
        results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()

    companion object {
        internal fun map(error: Int): DictationError = when (error) {
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> DictationError.PERMISSION
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> DictationError.NO_SPEECH
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY, 10 /* ERROR_TOO_MANY_REQUESTS */ -> DictationError.BUSY
            12 /* ERROR_LANGUAGE_NOT_SUPPORTED */, 13 /* ERROR_LANGUAGE_UNAVAILABLE */ -> DictationError.LANGUAGE
            else -> DictationError.FAILED
        }
    }
}
