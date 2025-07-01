package com.dasc.auxiliovisionis.ui.view.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dasc.auxiliovisionis.ui.viewmodel.LocationViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun MainScreen(
    viewModel: LocationViewModel = hiltViewModel()
) {
    val smsPermissionState = rememberPermissionState(android.Manifest.permission.SEND_SMS)
    val address by viewModel.address.collectAsState()

    // Lanzar petición de coordenadas solo si permiso está concedido
    LaunchedEffect(smsPermissionState.status) {
        if (smsPermissionState.status.isGranted) {
            viewModel.fetchExampleCoordinates()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Ubicación desde Coordenadas") })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            when {
                !smsPermissionState.status.isGranted -> {
                    Text("Permiso para enviar SMS requerido.")
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { smsPermissionState.launchPermissionRequest() }) {
                        Text("Solicitar permiso SMS")
                    }
                }
                address != null -> {
                    Text("📍 Calle: ${address?.road ?: "N/D"}")
                    Text("🏠 Número: ${address?.house_number ?: "N/D"}")
                    Text("🧭 Colonia: ${address?.suburb ?: "N/D"}")
                    Text("🏙️ Ciudad: ${address?.city ?: "N/D"}")
                    Text("🗺️ Estado: ${address?.state ?: "N/D"}")
                    Text("🌎 País: ${address?.country ?: "N/D"}")
                    Text("📮 CP: ${address?.postcode ?: "N/D"}")
                }
                else -> {
                    CircularProgressIndicator()
                }
            }
        }
    }
}
