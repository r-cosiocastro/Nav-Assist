package com.dasc.auxiliovisionis.ui.viewmodel

import android.telephony.SmsManager
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dasc.auxiliovisionis.data.remote.model.Address
import com.dasc.auxiliovisionis.domain.usecase.GetStreetFromCoordinatesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LocationViewModel @Inject constructor(
    private val getStreetFromCoordinatesUseCase: GetStreetFromCoordinatesUseCase
) : ViewModel() {

    private val _address = MutableStateFlow<Address?>(null)
    val address: StateFlow<Address?> = _address.asStateFlow()

    fun fetchAddress(lat: Double, lon: Double) {
        viewModelScope.launch {
            try {
                val result = getStreetFromCoordinatesUseCase(lat, lon)
                _address.value = result

                sendLocationSms(result)
            } catch (e: Exception) {
                Log.e("LocationViewModel", "Error fetching address", e)
            }
        }
    }

    fun fetchExampleCoordinates() {
        val lat = 24.1497301
        val lon = -110.2775868
        fetchAddress(lat, lon)
    }

    fun sendLocationSms(result: Address) {
        val calle = result.road ?: "una calle desconocida"
        val ciudad = result.city
        val estado = result.state
        val pais = result.country

        val mensaje = buildString {
            append("Hola, estoy en $calle")
            if (!ciudad.isNullOrEmpty()) append(", $ciudad")
            if (!estado.isNullOrEmpty()) append(", $estado")
            if (!pais.isNullOrEmpty()) append(", $pais")
        }

        SmsManager.getDefault().sendTextMessage("6121692423", null, mensaje, null, null)
        Log.d("LocationViewModel", "Mensaje enviado automáticamente: $mensaje")
    }
}