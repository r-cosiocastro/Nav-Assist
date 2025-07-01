package com.dasc.auxiliovisionis.ui.view.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.dasc.auxiliovisionis.ui.viewmodel.BluetoothViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothDevicesScreen(
    navController: NavController,
    bluetoothViewModel: BluetoothViewModel = hiltViewModel()
) {
    val devices by bluetoothViewModel.scannedBleDeviceItems.collectAsState()
    val isScanning by bluetoothViewModel.isScanning.collectAsState()
    val scanError by bluetoothViewModel.scanError.collectAsState()
    val connectionStatus by bluetoothViewModel.connectionStatusText.collectAsState()
    val isConnected by bluetoothViewModel.isConnected.collectAsState()


    Column(modifier = Modifier.fillMaxWidth()) {
        TopAppBar(title = { Text("Dispositivos Bluetooth") })

        Text(text = connectionStatus, modifier = Modifier.padding(8.dp))

        Button(
            onClick = {
                if (isScanning) bluetoothViewModel.stopScan()
                else bluetoothViewModel.startScan()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Text(if (isScanning) "Detener Escaneo" else "Escanear Dispositivos")
        }

        if (scanError != null) {
            Text(text = scanError ?: "", color = Color.Red, modifier = Modifier.padding(8.dp))
        }

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(devices) { device ->
                ListItem(
                    headlineContent = { Text(device.resolvedName!!) },
                    supportingContent = { Text(device.address) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            bluetoothViewModel.stopScan()
                            bluetoothViewModel.connectToDevice(device.device)
                        }
                )
                HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
            }
        }

    }
}
