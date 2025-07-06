package com.rafaelcosio.navassist.services

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.rafaelcosio.navassist.utils.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.LinkedList
import java.util.Locale
import java.util.Queue

class TextToSpeechService : Service(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private var isTtsInitialized = false
    private val ttsQueue: Queue<String> = LinkedList()
    private var isSpeaking = false

    private val _ttsState = MutableSharedFlow<TtsState>()
    val ttsState: SharedFlow<TtsState> = _ttsState.asSharedFlow()

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    companion object {
        const val ACTION_SPEAK = "com.dasc.auxiliovisionis.action.SPEAK"
        const val EXTRA_TEXT_TO_SPEAK = "com.dasc.auxiliovisionis.extra.TEXT_TO_SPEAK"
        const val ACTION_STOP_SPEAKING = "com.dasc.auxiliovisionis.action.STOP_SPEAKING"
    }

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): TextToSpeechService = this@TextToSpeechService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("TextToSpeechService", "Servicio onCreate")
        tts = TextToSpeech(this, this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("TextToSpeechService", "Servicio onStartCommand: ${intent?.action}")
        when (intent?.action) {
            ACTION_SPEAK -> {
                val text = intent.getStringExtra(EXTRA_TEXT_TO_SPEAK)
                if (!text.isNullOrBlank()) {
                    speak(text)
                }
                if (!isForegroundServiceRunning()) {
                    startForegroundWithNotification("Servicio TTS activo")
                }
            }
            ACTION_STOP_SPEAKING -> {
                stopSpeaking()
                stopSelf()
            }
            else -> {
                startForegroundWithNotification("Servicio TTS inicializado")
            }
        }
        return START_STICKY
    }

    private fun startForegroundWithNotification(contentText: String) {
        val notification = NotificationHelper.createTTSServiceNotification(
            this,
            contentText
        )
        startForeground(NotificationHelper.TTS_SERVICE_NOTIFICATION_ID, notification)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale("es", "MX"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TextToSpeechService", "Idioma no soportado.")
                serviceScope.launch { _ttsState.emit(TtsState.Error("Idioma no soportado")) }
                val fallbackResult = tts.setLanguage(Locale.getDefault())
                if (fallbackResult == TextToSpeech.LANG_MISSING_DATA || fallbackResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("TextToSpeechService", "Idioma de fallback no soportado.")
                } else {
                    isTtsInitialized = true
                }
            } else {
                Log.i("TextToSpeechService", "TTS inicializado.")
                isTtsInitialized = true
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        isSpeaking = true
                        serviceScope.launch { _ttsState.emit(TtsState.Speaking) }
                    }

                    override fun onDone(utteranceId: String?) {
                        isSpeaking = false
                        serviceScope.launch { _ttsState.emit(TtsState.Idle) }
                        processQueue()
                        if (ttsQueue.isEmpty()) {

                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        isSpeaking = false
                        serviceScope.launch { _ttsState.emit(TtsState.Error("Error en TTS")) }
                        Log.e("TextToSpeechService", "Error en TTS para utteranceId: $utteranceId")
                        processQueue()
                    }
                })
                processQueue()
            }
        } else {
            Log.e("TextToSpeechService", "Falló la inicialización de TTS.")
            serviceScope.launch { _ttsState.emit(TtsState.Error("Falló la inicialización de TTS")) }
        }
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        Log.d("TextToSpeechService", "Solicitud para hablar: $text, TTS Init: $isTtsInitialized")
        if (isTtsInitialized) {
            ttsQueue.add(text)
            if (!isSpeaking) {
                processQueue()
            }
        } else {
            ttsQueue.add(text)
            Log.w("TextToSpeechService", "TTS no listo, mensaje encolado: $text")
        }
    }

    private fun processQueue() {
        if (isSpeaking || ttsQueue.isEmpty() || !isTtsInitialized) {
            return
        }
        val textToSpeak = ttsQueue.poll()
        if (textToSpeak != null) {
            Log.d("TextToSpeechService", "Procesando de cola: $textToSpeak")
            val utteranceId = textToSpeak.hashCode().toString() + System.currentTimeMillis()
            tts.speak(textToSpeak, TextToSpeech.QUEUE_ADD, null, utteranceId) // QUEUE_ADD para manejar la cola interna de TTS también
        }
    }

    fun stopSpeaking() {
        if (::tts.isInitialized && isTtsInitialized) {
            tts.stop()
        }
        ttsQueue.clear()
        isSpeaking = false
        serviceScope.launch { _ttsState.emit(TtsState.Idle) }
    }

    private fun isForegroundServiceRunning(): Boolean {
        val notificationManager = getSystemService(NotificationManager::class.java)
        return notificationManager.activeNotifications.any { it.id == NotificationHelper.TTS_SERVICE_NOTIFICATION_ID }
    }

    override fun onBind(intent: Intent): IBinder {
        Log.d("TextToSpeechService", "Servicio onBind")
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d("TextToSpeechService", "Servicio onUnbind")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        Log.d("TextToSpeechService", "Servicio onDestroy")
        serviceJob.cancel()
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        if (isForegroundServiceRunning()) {
            stopForeground(true)
        }
        super.onDestroy()
    }
}

// Para comunicar estados a la UI
sealed class TtsState {
    object Idle : TtsState()
    object Speaking : TtsState()
    data class Error(val message: String) : TtsState()
}