package com.rafaelcosio.navassist.bluetooth

import android.Manifest
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import com.rafaelcosio.navassist.ui.adapter.BleDevice
import com.rafaelcosio.navassist.utils.AppPreferences
import com.rafaelcosio.navassist.utils.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class BluetoothService : Service() {

    @Inject
    lateinit var bluetoothStateManager: BluetoothStateManager

    companion object {
        const val ACTION_CONNECT_BLE = "com.dasc.auxiliovisionis.ACTION_CONNECT_BLE"
        const val ACTION_DISCONNECT_BLE = "com.dasc.auxiliovisionis.ACTION_DISCONNECT_BLE"
        const val ACTION_REQUEST_CURRENT_STATUS =
            "com.dasc.auxiliovisionis.ACTION_REQUEST_CURRENT_STATUS"
        const val ACTION_START_SCAN = "com.dasc.auxiliovisionis.ACTION_START_SCAN"
        const val ACTION_STOP_SCAN = "com.dasc.auxiliovisionis.ACTION_STOP_SCAN"
        const val EXTRA_DEVICE_ADDRESS = "com.dasc.auxiliovisionis.EXTRA_DEVICE_ADDRESS"

        private const val NOTIFICATION_CHANNEL_ID = "BluetoothServiceChannel"
        private const val NOTIFICATION_ID = 1

        private val SERVICE_UUID_STRING = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
        private val CHARACTERISTIC_UUID_STRING =
            UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")
        private val CCCD_UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    }

    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter

    // Componentes para BLE
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private val connectedBleDevices =
        mutableMapOf<String, BluetoothGatt>() // Para manejar múltiples conexiones BLE si es necesario

    private val binder = LocalBinder()
    private var connectedGatt: BluetoothGatt? = null
    private var currentDeviceAddress: String? = null
    private var currentDeviceName: String? = null


    private var isAttemptingAutoReconnect: Boolean = false
    private var lastAttemptedDeviceNameForToast: String? = null
    private var lastAttemptedDeviceAddressForAutoReconnect: String? =
        null

    private val discoveredDevicesList = mutableListOf<BleDevice>()
    private val scanHandler = Handler(Looper.getMainLooper())
    private var isScanning = false
    private val SCAN_PERIOD: Long = 15000

    @Inject
    lateinit var notificationHelper: NotificationHelper

    private val serviceJob = SupervisorJob()
    private val serviceScope =
        CoroutineScope(Dispatchers.IO + serviceJob)

    private fun startBleScanWithPermissionCheck() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.e(
                    "BluetoothService_SCAN",
                    "Permiso BLUETOOTH_SCAN no concedido (API 31+). No se puede escanear."
                )
                bluetoothStateManager.postScanFailed(ScanCallback.SCAN_FAILED_INTERNAL_ERROR)
                return
            }
        } else {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_ADMIN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.e(
                    "BluetoothService_SCAN",
                    "Permisos de ubicación o Bluetooth Admin no concedidos (API < 31). No se puede escanear."
                )
                bluetoothStateManager.postScanFailed(ScanCallback.SCAN_FAILED_INTERNAL_ERROR)
                return
            }
        }
        actuallyStartBleScan()
    }

    private fun actuallyStartBleScan() {
        if (!bluetoothAdapter.isEnabled || bluetoothLeScanner == null) {
            Log.w(
                "BluetoothService_SCAN",
                "Bluetooth no habilitado o scanner nulo. No se puede escanear."
            )
            bluetoothStateManager.postScanFailed(ScanCallback.SCAN_FAILED_INTERNAL_ERROR)
            return
        }
        if (isScanning) {
            Log.d("BluetoothService_SCAN", "El escaneo ya está en progreso.")
            return
        }
        discoveredDevicesList.clear()
        bluetoothStateManager.postScanResults(emptyList())
        bluetoothStateManager.postScanStarted()

        val scanFilters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid.fromString(SERVICE_UUID_STRING.toString())).build()
        )
        val scanSettings =
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()

        Log.i("BluetoothService_SCAN", "Iniciando escaneo BLE...")
        try {
            bluetoothLeScanner?.startScan(scanFilters, scanSettings, bleScanCallback)
            isScanning = true
            scanHandler.postDelayed({
                if (isScanning) {
                    stopBleScanWithPermissionCheck()
                }
            }, SCAN_PERIOD)
        } catch (e: SecurityException) {
            Log.e("BluetoothService_SCAN", "SecurityException al iniciar escaneo: ${e.message}", e)
            bluetoothStateManager.postScanFailed(ScanCallback.SCAN_FAILED_INTERNAL_ERROR)
            isScanning = false
        } catch (e: IllegalStateException) {
            Log.e(
                "BluetoothService_SCAN",
                "IllegalStateException al iniciar escaneo (Bluetooth podría estar apagado): ${e.message}",
                e
            )
            bluetoothStateManager.postScanFailed(ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED)
            isScanning = false
        }
    }

    private fun stopBleScanWithPermissionCheck() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.e(
                    "BluetoothService_SCAN",
                    "Permiso BLUETOOTH_SCAN no concedido (API 31+). No se puede detener explícitamente el escaneo, pero se detendrá por timeout o al cerrar."
                )
                if (isScanning) {
                    isScanning = false
                    bluetoothStateManager.postScanStopped()
                }
                return
            }
        }
        actuallyStopBleScan()
    }

    private fun actuallyStopBleScan() {
        if (isScanning && bluetoothLeScanner != null) {
            Log.i("BluetoothService_SCAN", "Deteniendo escaneo BLE.")
            try {
                bluetoothLeScanner?.stopScan(bleScanCallback)
            } catch (e: SecurityException) {
                Log.e(
                    "BluetoothService_SCAN",
                    "SecurityException al detener escaneo: ${e.message}",
                    e
                )
            } catch (e: IllegalStateException) {
                Log.e(
                    "BluetoothService_SCAN",
                    "IllegalStateException al detener escaneo (Bluetooth podría estar apagado): ${e.message}",
                    e
                )
            }
            isScanning = false
            bluetoothStateManager.postScanStopped()
        }
        scanHandler.removeCallbacksAndMessages(null)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private val bleScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            val device = result.device
            var deviceName: String? = null

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(
                        this@BluetoothService,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    try {
                        deviceName = device.name
                    } catch (e: SecurityException) {
                        Log.w(
                            "BluetoothService_SCAN",
                            "onScanResult: SecEx para device.name API31+",
                            e
                        )
                    }
                } else {
                    Log.w(
                        "BluetoothService_SCAN",
                        "onScanResult: Sin permiso BLUETOOTH_CONNECT para device.name API31+"
                    )
                }
            } else {
                if (ActivityCompat.checkSelfPermission(
                        this@BluetoothService,
                        Manifest.permission.BLUETOOTH
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    try {
                        deviceName = device.name
                    } catch (e: SecurityException) {
                        Log.w(
                            "BluetoothService_SCAN",
                            "onScanResult: SecEx para device.name API<31",
                            e
                        )
                    }
                } else {
                    Log.w(
                        "BluetoothService_SCAN",
                        "onScanResult: Sin permiso BLUETOOTH para device.name API<31"
                    )
                }
            }

            val existingDeviceIndex =
                discoveredDevicesList.indexOfFirst { it.address == device.address }
            if (existingDeviceIndex == -1) {
                if (deviceName != null || result.scanRecord?.deviceName != null) {
                    Log.d(
                        "BluetoothService_SCAN",
                        "Nuevo dispositivo: ${deviceName ?: result.scanRecord?.deviceName} (${device.address}) RSSI: ${result.rssi}"
                    )
                    discoveredDevicesList.add(
                        BleDevice(
                            device,
                            deviceName ?: result.scanRecord?.deviceName,
                            device.address
                        )
                    )
                }
            } else {
                val existing = discoveredDevicesList[existingDeviceIndex]
                if (existing.resolvedName == null && (deviceName != null || result.scanRecord?.deviceName != null)) {
                    discoveredDevicesList[existingDeviceIndex] = BleDevice(
                        device,
                        deviceName ?: result.scanRecord?.deviceName,
                        device.address /*, result.rssi*/
                    )
                } else {
                }
            }
            bluetoothStateManager.postScanResults(ArrayList(discoveredDevicesList))
        }

        override fun onBatchScanResults(results: List<ScanResult>) {
            super.onBatchScanResults(results)
            var changed = false
            results.forEach { result ->
                val device = result.device
                var deviceName: String? = null
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ActivityCompat.checkSelfPermission(
                            this@BluetoothService,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        try {
                            deviceName = device.name
                        } catch (e: SecurityException) { /* log */
                        }
                    }
                } else {
                    if (ActivityCompat.checkSelfPermission(
                            this@BluetoothService,
                            Manifest.permission.BLUETOOTH
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        try {
                            deviceName = device.name
                        } catch (e: SecurityException) { /* log */
                        }
                    }
                }
                if (!discoveredDevicesList.any { it.address == device.address }) {
                    if (deviceName != null || result.scanRecord?.deviceName != null) {
                        discoveredDevicesList.add(
                            BleDevice(
                                device,
                                deviceName ?: result.scanRecord?.deviceName,
                                device.address
                            )
                        )
                        changed = true
                    }
                }
            }
            if (changed) bluetoothStateManager.postScanResults(ArrayList(discoveredDevicesList))
        }
    }

    private fun isServiceConnectedToDevice(): Boolean {
        return bluetoothGatt != null && connectedBleDevices.isNotEmpty()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    override fun onCreate() {
        super.onCreate()
        bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        if (bluetoothAdapter.isEnabled) {
            bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
        }
        Log.d("BluetoothService_LIFECYCLE", "Servicio Creado")
    }

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT])
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d("BluetoothService_LIFECYCLE", "onStartCommand, Action: $action")

        if (intent?.action == null && !isServiceConnectedToDevice()) {
            val sharedPrefs = getSharedPreferences(AppPreferences.PREFS_NAME, Context.MODE_PRIVATE)
            val lastDeviceAddress =
                sharedPrefs.getString(AppPreferences.KEY_LAST_CONNECTED_BLE_DEVICE_ADDRESS, null)


            if (lastDeviceAddress != null) {
                var nameForInitialToast =
                    sharedPrefs.getString(AppPreferences.KEY_LAST_CONNECTED_BLE_DEVICE_NAME, null)
                if (nameForInitialToast == null) {
                    if (ActivityCompat.checkSelfPermission(
                            this,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        try {
                            val device = bluetoothAdapter.getRemoteDevice(lastDeviceAddress)
                            nameForInitialToast =
                                device.name ?: lastDeviceAddress
                        } catch (e: IllegalArgumentException) {
                            nameForInitialToast = lastDeviceAddress
                        }
                    } else {
                        nameForInitialToast =
                            lastDeviceAddress
                    }
                }

                isAttemptingAutoReconnect = true
                lastAttemptedDeviceAddressForAutoReconnect = lastDeviceAddress
                sendReconnectAttemptingBroadcast(nameForInitialToast)
                attemptAutoReconnectToDevice(lastDeviceAddress)
            }
        }

        if (!bluetoothAdapter.isEnabled) {
            Log.w(
                "BluetoothService_CMD",
                "Bluetooth no está habilitado. Ignorando comando $action."
            )
            stopSelf()
            return START_NOT_STICKY
        }
        if (bluetoothLeScanner == null) {
            bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
            if (bluetoothLeScanner == null) {
                Log.e(
                    "BluetoothService_CMD",
                    "Falló al obtener BluetoothLeScanner incluso después de que BT está ON."
                )
                stopSelf()
                return START_NOT_STICKY
            }
        }


        when (action) {
            ACTION_START_SCAN -> {
                startBleScanWithPermissionCheck()
            }

            ACTION_STOP_SCAN -> {
                stopBleScanWithPermissionCheck()
            }

            ACTION_CONNECT_BLE -> {
                val address = intent.getStringExtra(EXTRA_DEVICE_ADDRESS)
                if (address != null) {
                    val device = bluetoothAdapter.getRemoteDevice(address)
                    connectToDeviceWithPermissionCheck(device)
                } else {
                    Log.w(
                        "BluetoothService_CMD",
                        "Dirección del dispositivo no proporcionada para conectar."
                    )
                }
            }

            ACTION_DISCONNECT_BLE -> {
                disconnectDeviceWithPermissionCheck()
            }
        }
        return START_REDELIVER_INTENT
    }

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT])
    private fun attemptAutoReconnectToDevice(deviceAddress: String) {
        if (!bluetoothAdapter.isEnabled) {
            sendConnectionFailedBroadcast(
                lastAttemptedDeviceNameForToast,
                "Bluetooth no habilitado."
            )
            isAttemptingAutoReconnect = false
            return
        }
        try {
            val device = bluetoothAdapter.getRemoteDevice(deviceAddress)
            Log.i(
                "BluetoothService_BLE",
                "Intentando reconexión automática a: ${device.name ?: device.address}"
            )
            bluetoothGatt = device.connectGatt(this, true, gattCallback)
        } catch (e: IllegalArgumentException) {
            Log.e(
                "BluetoothService_BLE",
                "Dirección MAC guardada inválida para reconexión: $deviceAddress",
                e
            )
            sendConnectionFailedBroadcast(
                lastAttemptedDeviceNameForToast,
                "Dirección Bluetooth inválida."
            )
            clearLastConnectedDeviceAddress()
            isAttemptingAutoReconnect = false
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun determineDisplayName(device: BluetoothDevice): String {
        val deviceAddress = device.address
        var actualDeviceNameFromHardware: String? = null
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            actualDeviceNameFromHardware = device.name
        } else {
            Log.w(
                "BluetoothService_NAME",
                "determineDisplayName: Permiso BLUETOOTH_CONNECT denegado. No se puede acceder a device.name."
            )
        }

        val savedName = getSavedDeviceName(deviceAddress)
        var displayName: String

        Log.d(
            "BluetoothService_NAME",
            "determineDisplayName: HardwareName='${actualDeviceNameFromHardware}', SavedName='${savedName}' for $deviceAddress"
        )

        if (savedName != null) {
            displayName = savedName
            if (actualDeviceNameFromHardware != null && actualDeviceNameFromHardware != savedName) {
                displayName =
                    actualDeviceNameFromHardware
            }
        } else {
            displayName = actualDeviceNameFromHardware ?: deviceAddress
        }
        return displayName
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun handleDeviceNameLogicAndBroadcasts(gatt: BluetoothGatt, connectionContext: String) {
        val device = gatt.device
        val deviceAddress = device.address

        val displayNameForUI = determineDisplayName(device)

        var actualDeviceNameFromHardwareForSave: String? = null
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            actualDeviceNameFromHardwareForSave = device.name
        }

        val currentSavedName = getSavedDeviceName(deviceAddress)

        if (actualDeviceNameFromHardwareForSave != null && actualDeviceNameFromHardwareForSave != currentSavedName) {
            saveDeviceName(deviceAddress, actualDeviceNameFromHardwareForSave)
            Log.d(
                "BluetoothService_NAME",
                "$connectionContext: HardwareName ('$actualDeviceNameFromHardwareForSave') es nuevo/diferente. Guardado."
            )
        } else if (currentSavedName == null && actualDeviceNameFromHardwareForSave != null) {
            saveDeviceName(deviceAddress, actualDeviceNameFromHardwareForSave)
            Log.d(
                "BluetoothService_NAME",
                "$connectionContext: No había SavedName, HardwareName ('$actualDeviceNameFromHardwareForSave') guardado."
            )
        }

        Log.d(
            "BluetoothService_DEBUG",
            "$connectionContext: Usando displayNameForUI = '$displayNameForUI'"
        )

        sendConnectionSuccessfulBroadcast(gatt, displayNameForUI)
        sendDeviceConnectedInfoBroadcast(device, displayNameForUI)

        if (isAttemptingAutoReconnect && deviceAddress == lastAttemptedDeviceAddressForAutoReconnect) {
            if (connectionContext == "onDescriptorWrite" || (connectionContext == "onServicesDiscovered" /* && no usas onDescriptorWrite como punto final */)) {
                isAttemptingAutoReconnect = false
                lastAttemptedDeviceAddressForAutoReconnect = null
            }
        }
    }

    private fun getSavedDeviceName(deviceAddress: String): String? {
        val sharedPrefs = getSharedPreferences(AppPreferences.PREFS_NAME, MODE_PRIVATE)
        return sharedPrefs.getString(AppPreferences.KEY_LAST_CONNECTED_BLE_DEVICE_NAME, null)
    }

    private fun saveDeviceName(deviceAddress: String, name: String?) {
        val sharedPrefs = getSharedPreferences(AppPreferences.PREFS_NAME, MODE_PRIVATE)
        sharedPrefs.edit {
            putString(
                AppPreferences.KEY_LAST_CONNECTED_BLE_DEVICE_ADDRESS,
                deviceAddress
            )
            putString(AppPreferences.KEY_LAST_CONNECTED_BLE_DEVICE_NAME, name)
        }
        Log.i("BluetoothService_NAME", "Nombre guardado para $deviceAddress: '$name'")
    }

    private fun clearLastConnectedDeviceAddress() {
        val sharedPrefs = getSharedPreferences(AppPreferences.PREFS_NAME, MODE_PRIVATE)
        sharedPrefs.edit {
            remove(AppPreferences.KEY_LAST_CONNECTED_BLE_DEVICE_ADDRESS)
            remove(AppPreferences.KEY_LAST_CONNECTED_BLE_DEVICE_NAME)
        }
        Log.i("BluetoothService_BLE", "Dirección del último dispositivo conectado eliminada.")
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun sendConnectionSuccessfulBroadcast(
        gatt: BluetoothGatt,
        displayName: String
    ) {
        Log.d(
            "BluetoothService_DEBUG",
            "sendConnectionSuccessfulBroadcast: Usando displayName = '$displayName'"
        )
        bluetoothStateManager.postConnectionSuccessful(displayName, gatt.device)
    }

    private fun sendReconnectAttemptingBroadcast(deviceName: String?) {
        bluetoothStateManager.postAttemptingConnection(deviceName.toString())
    }

    private fun sendConnectionFailedBroadcast(deviceName: String?, errorMessage: String?) {
        val nameForMsg = deviceName ?: "Dispositivo"
        val errorMsg = errorMessage ?: "Error desconocido"
        bluetoothStateManager.postConnectionFailed(nameForMsg, errorMsg)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun sendDeviceConnectedInfoBroadcast(device: BluetoothDevice, displayName: String) {
        Log.d(
            "BluetoothService_DEBUG",
            "sendDeviceConnectedInfoBroadcast: Usando displayName = '$displayName', device.address = '${device.address}'"
        )
        bluetoothStateManager.postConnectionSuccessful(displayName, device)
    }

    private fun connectToDeviceWithPermissionCheck(device: BluetoothDevice) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.e(
                    "BluetoothService_CONN",
                    "Permiso BLUETOOTH_CONNECT no concedido (API 31+). No se puede conectar."
                )
                bluetoothStateManager.postConnectionFailed(
                    currentDeviceName ?: device.address,
                    "Permiso requerido"
                )
                return
            }
        } else {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_ADMIN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.e(
                    "BluetoothService_CONN",
                    "Permiso BLUETOOTH_ADMIN no concedido (API < 31). No se puede conectar."
                )
                bluetoothStateManager.postConnectionFailed(
                    currentDeviceName ?: device.address,
                    "Permiso requerido (admin)"
                )
                return
            }
        }
        actuallyConnectToDevice(device)
    }

    private fun actuallyConnectToDevice(device: BluetoothDevice) {
        if (!bluetoothAdapter.isEnabled) {
            Log.w(
                "BluetoothService_CONN",
                "BT no habilitado, no se puede conectar a ${device.address}"
            )
            bluetoothStateManager.postConnectionFailed(
                currentDeviceName ?: device.address,
                "Bluetooth apagado"
            )
            return
        }
        Log.d("BluetoothService_CONN", "Intentando conectar a: ${device.address}")
        stopBleScanWithPermissionCheck()

        currentDeviceAddress = device.address
        if (currentDeviceName == null) {
            currentDeviceName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    try {
                        device.name ?: device.address
                    } catch (e: SecurityException) {
                        device.address
                    }
                } else {
                    device.address
                }
            } else {
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    try {
                        device.name ?: device.address
                    } catch (e: SecurityException) {
                        device.address
                    }
                } else {
                    device.address
                }
            }
        }

        bluetoothStateManager.postAttemptingConnection(
            currentDeviceName ?: device.address
        )

        try {
            connectedGatt =
                device.connectGatt(this, true, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } catch (e: SecurityException) {
            Log.e("BluetoothService_CONN", "SecurityException al conectar gatt: ${e.message}", e)
            bluetoothStateManager.postConnectionFailed(
                currentDeviceName ?: device.address,
                e.message
            )
        }
    }

    private fun disconnectDeviceWithPermissionCheck() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.e(
                    "BluetoothService_CONN",
                    "Permiso BLUETOOTH_CONNECT no concedido (API 31+). No se puede desconectar explícitamente."
                )
                if (connectedGatt != null) {
                    try {
                        connectedGatt?.close()
                    } catch (e: SecurityException) { /* log */
                    }
                    connectedGatt = null
                    bluetoothStateManager.postDeviceDisconnected(
                        currentDeviceName ?: currentDeviceAddress
                    )
                    currentDeviceName = null
                    currentDeviceAddress = null
                }
                return
            }
        }
        actuallyDisconnectDevice()
    }

    private fun actuallyDisconnectDevice() {
        if (connectedGatt != null) {
            Log.d("BluetoothService_CONN", "Desconectando de ${currentDeviceAddress}")
            try {
                connectedGatt?.disconnect()
            } catch (e: SecurityException) {
                Log.e(
                    "BluetoothService_CONN",
                    "SecurityException al desconectar gatt: ${e.message}",
                    e
                )
                try {
                    connectedGatt?.close()
                } catch (se: SecurityException) { /* log */
                }
                bluetoothStateManager.postDeviceDisconnected(
                    currentDeviceName ?: currentDeviceAddress
                )
                connectedGatt = null
                currentDeviceName = null
                currentDeviceAddress = null
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val deviceAddress = gatt.device.address
            val deviceNameForNotification =
                currentDeviceName ?: deviceAddress

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.i(
                        "BluetoothService_GATT",
                        "Conectado a GATT server $deviceNameForNotification ($deviceAddress)."
                    )
                    connectedGatt = gatt
                    currentDeviceAddress = deviceAddress
                    gatt.requestMtu(185)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (ActivityCompat.checkSelfPermission(
                                this@BluetoothService,
                                Manifest.permission.BLUETOOTH_CONNECT
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            try {
                                currentDeviceName = gatt.device.name ?: deviceNameForNotification
                            } catch (e: SecurityException) {/*log*/
                            }
                        }
                    } else {
                        if (ActivityCompat.checkSelfPermission(
                                this@BluetoothService,
                                Manifest.permission.BLUETOOTH
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            try {
                                currentDeviceName = gatt.device.name ?: deviceNameForNotification
                            } catch (e: SecurityException) {/*log*/
                            }
                        }
                    }
                    bluetoothStateManager.postConnectionSuccessful(
                        currentDeviceName ?: deviceAddress, gatt.device
                    )

                    gatt.discoverServices()
                    startForegroundWithNotification("Conectado a ${currentDeviceName ?: deviceAddress}")
                    handleDeviceNameLogicAndBroadcasts(gatt, "onConnectionStateChange")

                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.i(
                        "BluetoothService_GATT",
                        "Desconectado de GATT server $deviceNameForNotification ($deviceAddress)."
                    )
                    try {
                        gatt.close()
                    } catch (e: SecurityException) {
                        Log.e("BluetoothService_GATT", "SecEx al cerrar GATT en desconexión", e)
                    }
                    connectedGatt = null
                    bluetoothStateManager.postDeviceDisconnected(deviceNameForNotification)
                    currentDeviceName = null
                    currentDeviceAddress = null
                    stopForeground(STOP_FOREGROUND_REMOVE)
                }
            } else {
                Log.w(
                    "BluetoothService_GATT",
                    "Error GATT: $status al cambiar estado para $deviceNameForNotification ($deviceAddress)"
                )
                try {
                    gatt.close()
                } catch (e: SecurityException) {
                    Log.e("BluetoothService_GATT", "SecEx al cerrar GATT en error", e)
                }
                connectedGatt = null
                bluetoothStateManager.postConnectionFailed(
                    deviceNameForNotification,
                    "Error GATT: $status"
                )
                currentDeviceName = null
                currentDeviceAddress = null
                stopForeground(STOP_FOREGROUND_REMOVE)
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        private fun closeGattConnection(string: String) {
            val gatt = connectedBleDevices.remove(string)
            if (gatt != null) {
                Log.d("BluetoothService_BLE", "Cerrando conexión GATT para $string")
                gatt.close()
                if (bluetoothGatt == gatt) {
                    bluetoothGatt = null
                }
            } else {
                Log.w("BluetoothService_BLE", "No se encontró conexión GATT para cerrar: $string")
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val device = gatt.device
            val currentDeviceName = device.name
            Log.e("BluetoothService_BLE", "onServicesDiscovered llamado para: $currentDeviceName")
            val displayName = currentDeviceName ?: device.address
            val deviceAddress = device.address
            val tempInitialName = getSavedDeviceName(deviceAddress) ?: device.name ?: deviceAddress
            val displayNameForLog = currentDeviceName ?: deviceAddress
            Log.d(
                "BluetoothService_DEBUG",
                "onServicesDiscovered: device.name = '$currentDeviceName', device.address = '$deviceAddress'"
            )
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i("BluetoothService_BLE", "Servicios descubiertos para $displayNameForLog")

                val service = gatt.getService(SERVICE_UUID_STRING)
                if (service == null) {
                    Log.e(
                        "BluetoothService_BLE",
                        "Servicio $SERVICE_UUID_STRING no encontrado en $displayName"
                    )
                    return
                }

                val characteristic = service.getCharacteristic(CHARACTERISTIC_UUID_STRING)
                if (characteristic == null) {
                    Log.e(
                        "BluetoothService_BLE",
                        "Característica $CHARACTERISTIC_UUID_STRING no encontrada en $displayName"
                    )
                    return
                }

                if (gatt.setCharacteristicNotification(characteristic, true)) {
                    Log.i(
                        "BluetoothService_BLE",
                        "Notificaciones habilitadas localmente para ${characteristic.uuid}"
                    )

                    val descriptor = characteristic.getDescriptor(CCCD_UUID)
                    if (descriptor != null) {
                        val writeSuccess: Boolean
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val result = gatt.writeDescriptor(
                                descriptor,
                                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            )
                            Log.d(
                                "BluetoothService_BLE",
                                "Escribiendo en descriptor CCCD (API 33+), resultado: $result"
                            )
                        } else {
                            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            writeSuccess = gatt.writeDescriptor(descriptor)
                            if (writeSuccess) {
                                Log.d(
                                    "BluetoothService_BLE",
                                    "Escritura en descriptor CCCD (API <33) iniciada..."
                                )
                            } else {
                                Log.e(
                                    "BluetoothService_BLE",
                                    "Fallo al iniciar escritura en descriptor CCCD (API <33)"
                                )
                                val bestName = getSavedDeviceName(deviceAddress) ?: device.name
                                ?: deviceAddress
                                sendConnectionFailedBroadcast(
                                    bestName,
                                    "Fallo al habilitar notificaciones (escritura desc)."
                                )
                                closeGattConnection(deviceAddress)
                                if (isAttemptingAutoReconnect && deviceAddress == lastAttemptedDeviceAddressForAutoReconnect) {
                                    isAttemptingAutoReconnect = false
                                    lastAttemptedDeviceAddressForAutoReconnect = null
                                }
                                return
                            }
                        }
                    } else {
                        Log.e(
                            "BluetoothService_BLE",
                            "Descriptor CCCD no encontrado para ${characteristic.uuid}"
                        )
                    }
                } else {
                    Log.e(
                        "BluetoothService_BLE",
                        "Fallo al habilitar notificaciones localmente para ${characteristic.uuid}"
                    )
                }
            } else {
                Log.w(
                    "BluetoothService_BLE",
                    "onServicesDiscovered recibió: $status para $tempInitialName"
                )
                sendConnectionFailedBroadcast(
                    tempInitialName,
                    "Fallo al descubrir servicios ($status)."
                )
                closeGattConnection(deviceAddress)
                if (isAttemptingAutoReconnect && deviceAddress == lastAttemptedDeviceAddressForAutoReconnect) {
                    isAttemptingAutoReconnect = false
                    lastAttemptedDeviceAddressForAutoReconnect = null
                }
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onDescriptorWrite(
            gatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor?,
            status: Int
        ) {
            super.onDescriptorWrite(gatt, descriptor, status)
            if (gatt != null && status == BluetoothGatt.GATT_SUCCESS) {
                if (CCCD_UUID == descriptor?.uuid) {
                    Log.i(
                        "BluetoothService_BLE",
                        "Notificaciones habilitadas. Conexión completamente establecida."
                    )
                    handleDeviceNameLogicAndBroadcasts(gatt, "onDescriptorWrite")
                } else {
                    Log.i(
                        "BluetoothService_BLE",
                        "Descriptor ${descriptor?.uuid} escrito exitosamente (no CCCD)."
                    )
                }
            } else if (gatt != null) {
                val device = gatt.device
                val bestName = getSavedDeviceName(device.address) ?: device.name ?: device.address
                Log.w(
                    "BluetoothService_BLE",
                    "Error al escribir descriptor para $bestName: $status"
                )
                sendConnectionFailedBroadcast(
                    bestName,
                    "Fallo al configurar notificaciones (desc write $status)."
                )
                if (isAttemptingAutoReconnect && device.address == lastAttemptedDeviceAddressForAutoReconnect) {
                    isAttemptingAutoReconnect = false
                    lastAttemptedDeviceAddressForAutoReconnect = null
                }
                closeGattConnection(device.address)
            } else {
                Log.w(
                    "BluetoothService_BLE",
                    "Error al escribir descriptor: GATT es null, status $status"
                )
                val deviceAddressForError =
                    lastAttemptedDeviceAddressForAutoReconnect ?: "Dispositivo desconocido"
                val nameForError = if (lastAttemptedDeviceAddressForAutoReconnect != null) {
                    getSavedDeviceName(lastAttemptedDeviceAddressForAutoReconnect!!)
                } else {
                    null
                } ?: deviceAddressForError
                sendConnectionFailedBroadcast(
                    nameForError,
                    "Error crítico al configurar notificaciones (gatt null)."
                )
                if (isAttemptingAutoReconnect) {
                    isAttemptingAutoReconnect = false
                    lastAttemptedDeviceAddressForAutoReconnect = null
                }
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            super.onCharacteristicChanged(gatt, characteristic, value)
            Log.d(
                "BluetoothService_DATA",
                "Datos recibidos de ${gatt.device.address} en ${characteristic.uuid}: ${value.toHexString()}"
            )
            handleCharacteristicChanged(gatt.device.address, characteristic.uuid.toString(), value)
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            super.onCharacteristicChanged(gatt, characteristic)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                Log.d(
                    "BluetoothService_BLE",
                    "onCharacteristicChanged (API <33) from ${characteristic.uuid}"
                )
                handleCharacteristicChanged(
                    gatt.device.address,
                    characteristic.uuid.toString(),
                    characteristic.value
                )
            }
        }

        private fun handleCharacteristicChanged(
            deviceAddress: String,
            charUuid: String,
            value: ByteArray
        ) {
            val dataString = String(value, Charsets.UTF_8).trim()
            Log.d("BluetoothService_BLE", "Datos recibidos de $deviceAddress: $dataString")
            val parts = dataString.split(',').map { it.trim() }
            if (parts.size == 4) {
                try {
                    val idAction = parts[0].toInt()
                    val latitud = parts[1].toDouble()
                    val longitud = parts[2].toDouble()
                    val objectDescription = parts[3].toString()

                    Log.d(
                        "BluetoothService_BLE",
                        "Parseado: ID_Action=$idAction, Lat=$latitud, Lon=$longitud" +
                                ", Desc='$objectDescription' from $deviceAddress"
                    )

                    serviceScope.launch {
                        bluetoothStateManager.postDataReceived(
                            createBleAction(
                                idAction, latitud, longitud, objectDescription
                            ))
                    }

                } catch (e: NumberFormatException) {
                    Log.e(
                        "BluetoothService_BLE",
                        "Error al parsear datos de ubicación: '$dataString'",
                        e
                    )
                }
            } else {
                Log.w(
                    "BluetoothService_BLE",
                    "Formato de datos de ubicación inesperado: '$dataString'"
                )
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            val deviceName = gatt.device.name ?: gatt.device.address
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(
                    "BluetoothService_BLE",
                    "Característica leída de $deviceName ${characteristic.uuid}: ${value.toHexString()}"
                )
            } else {
                Log.w(
                    "BluetoothService_BLE",
                    "Fallo al leer característica de $deviceName ${characteristic.uuid}, status: $status"
                )
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            super.onMtuChanged(gatt, mtu, status)
            val deviceName = try {
                gatt.device.name ?: gatt.device.address
            } catch (e: SecurityException) {
                gatt.device.address
            }

            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i("BluetoothService_GATT", "MTU cambiada a $mtu para $deviceName")
                Log.d(
                    "BluetoothService_GATT",
                    "MTU confirmada, iniciando descubrimiento de servicios para $deviceName"
                )
                if (!gatt.discoverServices()) {
                    Log.e(
                        "BluetoothService_GATT",
                        "No se pudo iniciar el descubrimiento de servicios después del cambio de MTU para $deviceName."
                    )
                } else {
                    bluetoothStateManager.postConnectionSuccessful(
                        currentDeviceName ?: gatt.device.address, gatt.device
                    )
                    startForegroundWithNotification("Conectado a ${currentDeviceName ?: gatt.device.address}")
                    handleDeviceNameLogicAndBroadcasts(
                        gatt,
                        "onMtuChanged_after_discoverServices_initiated"
                    )
                }
            } else {
                Log.w(
                    "BluetoothService_GATT",
                    "Error al cambiar MTU a $mtu para $deviceName, estado: $status. Usando MTU predeterminada."
                )
                Log.d(
                    "BluetoothService_GATT",
                    "Cambio de MTU fallido, iniciando descubrimiento de servicios con MTU predeterminada para $deviceName"
                )
                if (!gatt.discoverServices()) {
                    Log.e(
                        "BluetoothService_GATT",
                        "No se pudo iniciar el descubrimiento de servicios después del fallo de cambio de MTU para $deviceName."
                    )
                } else {
                    bluetoothStateManager.postConnectionSuccessful(
                        currentDeviceName ?: gatt.device.address, gatt.device
                    )
                    startForegroundWithNotification("Conectado a ${currentDeviceName ?: gatt.device.address}")
                    handleDeviceNameLogicAndBroadcasts(
                        gatt,
                        "onMtuChanged_failed_after_discoverServices_initiated"
                    )
                }
            }
        }
    }

    fun createBleAction(actionInt: Int, lat: Double, lon: Double, description: String): BleAction {
        val actionType: ActionType = when (actionInt) {
            0 -> ActionType.NO_ACTION
            1 -> ActionType.TALK_LOCATION
            2 -> ActionType.TALK_OBJECT
            3 -> ActionType.SEND_LOCATION_SMS
            else -> {
                println("Error: Int $actionInt no corresponde a un ActionType válido.")
                ActionType.NO_ACTION
            }
        }

        return BleAction(actionType, lat, lon, description)

    }

    fun ByteArray.toHexString(): String =
        joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }


    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private fun startForegroundWithNotification(contentText: String) {
        val notification = NotificationHelper.createBluetoothServiceNotification(
            this,
            contentText
        )
        startForeground(NotificationHelper.BLUETOOTH_SERVICE_NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        Log.d("BluetoothService_LIFECYCLE", "Servicio Destruido")
        stopBleScanWithPermissionCheck()
        if (connectedGatt != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                } else {
                    connectedGatt?.disconnect()
                }
                connectedGatt?.close()
            } catch (e: SecurityException) {
                Log.e(
                    "BluetoothService_LIFECYCLE",
                    "SecurityException al limpiar GATT en onDestroy",
                    e
                )
            }
            connectedGatt = null
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder = binder
    inner class LocalBinder : Binder() {
        fun getService(): BluetoothService = this@BluetoothService
    }
}