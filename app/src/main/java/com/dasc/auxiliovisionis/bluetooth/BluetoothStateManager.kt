package com.dasc.auxiliovisionis.bluetooth

import android.bluetooth.BluetoothDevice
import com.dasc.auxiliovisionis.ui.adapter.BleDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BluetoothStateManager @Inject constructor() {
    // --- Escaneo ---
    private val _scanStarted = MutableStateFlow<Unit?>(null)
    val scanStarted: StateFlow<Unit?> = _scanStarted

    private val _scanResults = MutableStateFlow<List<BleDevice>>(emptyList())
    val scanResults: StateFlow<List<BleDevice>> = _scanResults

    private val _scanFailed = MutableStateFlow<Int?>(null)
    val scanFailed: StateFlow<Int?> = _scanFailed

    private val _scanStopped = MutableStateFlow<Unit?>(null)
    val scanStopped: StateFlow<Unit?> = _scanStopped

    // --- Conexión ---
    private val _attemptingConnection = MutableStateFlow<String?>(null)
    val attemptingConnection: StateFlow<String?> = _attemptingConnection

    private val _connectionSuccessful = MutableStateFlow<ConnectionStatusInfo?>(null)
    val connectionSuccessful: StateFlow<ConnectionStatusInfo?> = _connectionSuccessful

    private val _connectionFailed = MutableStateFlow<ConnectionStatusInfo?>(null)
    val connectionFailed: StateFlow<ConnectionStatusInfo?> = _connectionFailed

    private val _deviceDisconnected = MutableStateFlow<ConnectionStatusInfo?>(null)
    val deviceDisconnected: StateFlow<ConnectionStatusInfo?> = _deviceDisconnected

    // --- Datos ---
    private val _actionReceived = MutableStateFlow<BleAction?>(null)
    val actionReceived: StateFlow<BleAction?> = _actionReceived

    // Funciones para que BluetoothService actualice (sin lógica de permisos aquí)
    fun postScanStarted() { _scanStarted.value = Unit }
    fun postScanResults(devicesInfo: List<BleDevice>) { _scanResults.value = devicesInfo }
    fun postScanFailed(errorCode: Int) { _scanFailed.value = errorCode }
    fun postScanStopped() { _scanStopped.value = Unit }

    fun postAttemptingConnection(deviceName: String) { _attemptingConnection.value = deviceName }
    fun postConnectionSuccessful(displayName: String, device: BluetoothDevice) {
        _connectionSuccessful.value = ConnectionStatusInfo(displayName, device = device)
    }
    fun postConnectionFailed(displayName: String?, errorMessage: String?) {
        _connectionFailed.value = ConnectionStatusInfo(displayName, errorMessage)
    }
    fun postDeviceDisconnected(displayName: String?) {
        _deviceDisconnected.value = ConnectionStatusInfo(displayName)
    }

    fun postDataReceived(bleAction: BleAction) {
        _actionReceived.value = bleAction
    }

}


data class ConnectionStatusInfo(
    val deviceDisplayName: String?, // Nombre ya resuelto o dirección como fallback
    val errorMessage: String? = null,
    val device: BluetoothDevice? = null // Opcional, si la UI necesita el objeto completo
)