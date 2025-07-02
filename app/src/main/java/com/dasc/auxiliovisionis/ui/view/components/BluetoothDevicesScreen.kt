package com.dasc.auxiliovisionis.ui.view.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Dispositivos Bluetooth") })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            Text(
                text = connectionStatus,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            ElevatedButton(
                onClick = {
                    if (isScanning) bluetoothViewModel.stopScan()
                    else bluetoothViewModel.startScan()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = if (isScanning) Icons.Default.Stop else Icons.AutoMirrored.Filled.BluetoothSearching,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isScanning) "Detener Escaneo" else "Escanear Dispositivos")
            }

            if (scanError != null) {
                Text(
                    text = scanError ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            if (isScanning) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Buscando dispositivos...", style = MaterialTheme.typography.bodyMedium)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(devices) { device ->
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable {
                                bluetoothViewModel.stopScan()
                                bluetoothViewModel.connectToDevice(device.device)
                            }
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = device.resolvedName ?: "Sin nombre",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = device.address,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

