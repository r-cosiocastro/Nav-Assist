package com.rafaelcosio.navassist.ui.viewmodel

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rafaelcosio.navassist.bluetooth.BluetoothService
import com.rafaelcosio.navassist.bluetooth.BluetoothStateManager
import com.rafaelcosio.navassist.ui.adapter.BleDevice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class BluetoothViewModel @Inject constructor(
    application: Application,
    private val bluetoothStateManager: BluetoothStateManager,
) : AndroidViewModel(application) {

    val scannedBleDeviceItems: StateFlow<List<BleDevice>> = bluetoothStateManager.scanResults
    val isScanning: StateFlow<Boolean> = bluetoothStateManager.scanStarted
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val scanError: StateFlow<String?> = bluetoothStateManager.scanFailed
        .map { errorCode -> errorCode?.let { "Error de escaneo: $it" } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val connectionStatusText: StateFlow<String> = bluetoothStateManager.connectionSuccessful
        .map { it?.deviceDisplayName?.let { "Conectado a $it" } ?: "Desconectado" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "Desconectado")

    val connectedDeviceName: StateFlow<String?> = bluetoothStateManager.connectionSuccessful
        .map { it?.deviceDisplayName }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val isConnected: StateFlow<Boolean> = bluetoothStateManager.connectionSuccessful
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun startScan() {
        Log.d("BluetoothViewModel_ACTION", "Solicitando inicio de escaneo al servicio.")
        val intent = Intent(getApplication(), BluetoothService::class.java).apply {
            action = BluetoothService.ACTION_START_SCAN
        }
        getApplication<Application>().startService(intent)
    }
    fun stopScan() {
        Log.d("BluetoothViewModel_ACTION", "Solicitando detención de escaneo al servicio.")
        val intent = Intent(getApplication(), BluetoothService::class.java).apply {
            action = BluetoothService.ACTION_STOP_SCAN
        }
        getApplication<Application>().startService(intent)
    }
    fun connectToDevice(device: BluetoothDevice) {
        val intent = Intent(getApplication(), BluetoothService::class.java).apply {
            action = BluetoothService.ACTION_CONNECT_BLE
            putExtra(BluetoothService.EXTRA_DEVICE_ADDRESS, device.address)
        }
        getApplication<Application>().startService(intent)
    }
    fun disconnectDevice() {
        val intent = Intent(getApplication(), BluetoothService::class.java).apply {
            action = BluetoothService.ACTION_DISCONNECT_BLE
        }
        getApplication<Application>().startService(intent)
    }
}