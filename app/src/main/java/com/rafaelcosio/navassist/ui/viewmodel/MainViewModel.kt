package com.rafaelcosio.navassist.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rafaelcosio.navassist.bluetooth.ActionType
import com.rafaelcosio.navassist.bluetooth.BleAction
import com.rafaelcosio.navassist.bluetooth.BluetoothStateManager
import com.rafaelcosio.navassist.data.remote.model.Address
import com.rafaelcosio.navassist.domain.usecase.GetStreetFromCoordinatesUseCase
import com.rafaelcosio.navassist.domain.usecase.TranslateTextUseCase
import com.rafaelcosio.navassist.services.TextToSpeechService
import com.rafaelcosio.navassist.utils.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val application: Application,
    private val bluetoothStateManager: BluetoothStateManager,
    private val translateTextUseCase: TranslateTextUseCase,
    private val getStreetFromCoordinatesUseCase: GetStreetFromCoordinatesUseCase,
    private val sharedPreferences: SharedPreferences
) : ViewModel() {

    private val _savedPhoneNumber = MutableStateFlow<String>(getSavedPhoneNumber())
    val savedPhoneNumber: StateFlow<String> = _savedPhoneNumber.asStateFlow()

    val dataReceived: StateFlow<BleAction> = bluetoothStateManager.actionReceived
        .map { it?: BleAction() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, BleAction())

    private val _address = MutableStateFlow<Address?>(null)
    val address: StateFlow<Address?> = _address.asStateFlow()

    private val _translation = MutableStateFlow<String>("")
    val translation: StateFlow<String> = _translation.asStateFlow()

    private val _speakRequest = kotlinx.coroutines.flow.MutableSharedFlow<String>(replay = 0)
    val speakRequest: kotlinx.coroutines.flow.SharedFlow<String> = _speakRequest

    val connectionStatusText: StateFlow<String> = bluetoothStateManager.connectionSuccessful
        .map { it?.deviceDisplayName?.let { "Conectado a $it" } ?: "Desconectado" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "Desconectado")

    // Acciones

    init {
        observeBleActions()
    }

    fun stopBackgroundSpeaking(context: Context) {
        val serviceIntent = Intent(context, TextToSpeechService::class.java)
        context.stopService(serviceIntent)
    }

    fun triggerSpeakInstruction(text: String) {
        if (text.isBlank()) return
        Log.d("MainViewModel_TTS", "Solicitando al servicio que hable: $text")
        requestSpeakInBackground(text)
    }

    private fun requestSpeakInBackground(text: String) {
        val context = application.applicationContext
        val serviceIntent = Intent(context, TextToSpeechService::class.java).apply {
            action = TextToSpeechService.ACTION_SPEAK
            putExtra(TextToSpeechService.EXTRA_TEXT_TO_SPEAK, text)
        }
        context.startService(serviceIntent)
    }

    private fun observeBleActions() {
        dataReceived
            .onEach { bleAction ->
                Log.d("BluetoothViewModel_ACTION", "Acción recibida: ${bleAction.action}")
                handleBleAction(bleAction)
            }
            .launchIn(viewModelScope)
    }

    private fun handleBleAction(bleAction: BleAction) {
        when (bleAction.action) {
            ActionType.NO_ACTION -> {
                Log.i("BluetoothViewModel_Handler", "No action received or handled.")
                // No hacer nada o mostrar un mensaje de "inactivo" en la UI
            }
            ActionType.TALK_LOCATION -> {
                Log.i("BluetoothViewModel_Handler", "Handling TALK_LOCATION: Lat=${bleAction.lat}, Lon=${bleAction.lon}")
                viewModelScope.launch {

                    val fetchedAddressDeferred = async { fetchAddressAndGet(bleAction.lat, bleAction.lon) }
                    val fetchedAddress = fetchedAddressDeferred.await()
                    val roadName = fetchedAddress?.road ?: "Calle desconocida"
                    val neighborhood = fetchedAddress?.neighbourhood ?: "Colonia desconocida"

                    Log.d("BluetoothViewModel_Handler", "Dirección: $roadName, $neighborhood")
                    triggerSpeak("Estás en $roadName, colonia $neighborhood")
                }

            }
            ActionType.TALK_OBJECT -> {
                Log.i("BluetoothViewModel_Handler", "Handling TALK_OBJECT: ${bleAction.objectDescription}")
                viewModelScope.launch {
                    val translatedDescriptionDeferred = async { translateTextAndGet(bleAction.objectDescription) }

                    val translatedDescription = translatedDescriptionDeferred.await()

                    Log.d("BluetoothViewModel_Handler", "Objeto original: ${bleAction.objectDescription}, Objeto traducido: $translatedDescription")

                    val messageToSpeak = "Se ha detectado: $translatedDescription."
                    triggerSpeak(messageToSpeak)
                }
            }
            ActionType.SEND_LOCATION_SMS -> {
                Log.i("BluetoothViewModel_Handler", "Handling SEND_LOCATION_SMS: Lat=${bleAction.lat}, Lon=${bleAction.lon}")

                viewModelScope.launch {

                    val fetchedAddressDeferred = async { fetchAddressAndGet(bleAction.lat, bleAction.lon) }
                    val fetchedAddress = fetchedAddressDeferred.await()
                    val roadName = fetchedAddress?.road ?: "Calle desconocida"
                    val neighborhood = fetchedAddress?.neighbourhood ?: "Colonia desconocida"

                    Log.d("BluetoothViewModel_Handler", "Dirección: $roadName, $neighborhood")
                    sendLocationSms(fetchedAddress, bleAction.lat, bleAction.lon)
                    triggerSpeak("Se ha enviado un mensaje con tu ubicación actual.")
                }
            }
        }
    }

    private suspend fun translateTextAndGet(text: String): String {
        return try {
            val translated = translateTextUseCase(text)
            _translation.value = translated
            Log.d("BluetoothViewModel_Translate", "Texto traducido: $translated (original: $text)")
            translated
        } catch (e: Exception) {
            Log.e("BluetoothViewModel_Translate", "Error traduciendo texto: $text", e)
            text // Devuelve el original en caso de error o "" o maneja de otra forma
        }
    }

    private suspend fun fetchAddressAndGet(lat: Double, lon: Double): Address? {
        return try {
            val result = getStreetFromCoordinatesUseCase(lat, lon)
            _address.value = result
            Log.d("BluetoothViewModel_Location", "Dirección obtenida: $result")
            result
        } catch (e: Exception) {
            Log.e("BluetoothViewModel_Location", "Error obteniendo dirección", e)
            null
        }
    }

    private fun triggerSpeak(text: String) {
        viewModelScope.launch {
            triggerSpeakInstruction(text)
        }
    }

    private fun getSavedPhoneNumber(): String {
        return sharedPreferences.getString(AppPreferences.KEY_PHONE_NUMBER, "6121692423") ?: "6121692423"
    }

    fun savePhoneNumber(phoneNumber: String) {
        sharedPreferences.edit {
            putString(AppPreferences.KEY_PHONE_NUMBER, phoneNumber)
        }
        _savedPhoneNumber.value = phoneNumber
        Log.d("MainViewModel", "Número de teléfono guardado: $phoneNumber")
    }

    fun fetchExampleCoordinates() {
        val lat = 24.1497301
        val lon = -110.2775868
        // fetchAddress(lat, lon)
    }

    fun sendLocationSms(result: Address?, lat: Double, lon: Double) {
        var mensaje : String
        if(result != null) {
            val calle = result.road ?: "una calle desconocida"
            val ciudad = result.city
            val estado = result.state
            val pais = result.country


            mensaje = buildString {
                append("Hola, necesito ayuda. Estoy en $calle")
                if (!ciudad.isNullOrEmpty()) append(", $ciudad")
                if (!estado.isNullOrEmpty()) append(", $estado")
                if (!pais.isNullOrEmpty()) append(", $pais")
            }

            mensaje = "Hola, necesito ayuda. Estoy en https://maps.google.com/?q=$lat,$lon"
        }else{
            mensaje = "Hola, necesito ayuda. Estoy en https://maps.google.com/?q=$lat,$lon"
        }

        SmsManager.getDefault().sendTextMessage(savedPhoneNumber.value, null, mensaje, null, null)
        Log.d("LocationViewModel", "Mensaje enviado automáticamente: $mensaje")
    }
}