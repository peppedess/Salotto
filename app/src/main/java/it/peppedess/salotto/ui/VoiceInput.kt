package it.peppedess.salotto.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/** Riconoscimento vocale in-app, con risultati parziali e livello del microfono. */
class VoiceInput(
    private val context: Context,
    private val onListening: (Boolean) -> Unit,
    private val onLevel: (Float) -> Unit,
    private val onPartial: (String) -> Unit,
    private val onResult: (String) -> Unit,
    private val onFailure: (String) -> Unit,
) {
    private var recognizer: SpeechRecognizer? = null

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun start() {
        stop()
        val r = runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull()
        if (r == null) {
            onFailure("Riconoscimento vocale non disponibile")
            return
        }
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = onListening(true)
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) = onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() = onLevel(0f)
            override fun onError(error: Int) {
                onListening(false)
                onLevel(0f)
                onFailure(describe(error))
            }

            override fun onResults(results: Bundle?) {
                onListening(false)
                onLevel(0f)
                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.let(onResult)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.let(onPartial)
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        recognizer = r
        onListening(true)
        r.startListening(recognizerIntent())
    }

    fun stop() {
        recognizer?.let {
            runCatching { it.cancel() }
            runCatching { it.destroy() }
        }
        recognizer = null
        onLevel(0f)
    }

    private fun describe(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Non ho capito, riprova"
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Problema di rete"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Serve il permesso del microfono"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Il riconoscimento è occupato, riprova"
        else -> "Errore del riconoscimento vocale ($error)"
    }

    companion object {
        fun recognizerIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "it-IT")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Cosa vuoi guardare?")
        }
    }
}
