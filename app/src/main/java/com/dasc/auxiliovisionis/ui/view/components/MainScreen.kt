package com.dasc.auxiliovisionis.ui.view.components

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.dasc.auxiliovisionis.ui.viewmodel.MainViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun MainScreen(
    navController: NavController,
    mainViewModel: MainViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val smsPermissionState = rememberPermissionState(android.Manifest.permission.SEND_SMS)
    val address by mainViewModel.address.collectAsState()
    val lastObjectSeen by mainViewModel.translation.collectAsState()
    val phoneNumberToShow by mainViewModel.savedPhoneNumber.collectAsState()

    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val contactUri: Uri? = result.data?.data
            if (contactUri != null) {
                val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
                context.contentResolver.query(contactUri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        if (numberIndex >= 0) {
                            val number = cursor.getString(numberIndex).trim().replace(Regex("[^0-9]"), "")
                            mainViewModel.savePhoneNumber(number)
                            Log.d("ContactPicker", "Número seleccionado: $number")
                        } else {
                            Log.e("ContactPicker", "Columna NUMBER no encontrada.")
                        }
                    }
                }
            }
        } else {
            Log.d("ContactPicker", "Selección de contacto cancelada.")
        }
    }

    // Lanzar petición de coordenadas solo si permiso está concedido
    LaunchedEffect(smsPermissionState.status) {
        if (smsPermissionState.status.isGranted) {
            mainViewModel.fetchExampleCoordinates()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Auxilio Visionis") })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
        ) {


            Button(onClick = {
                val intent = Intent(Intent.ACTION_PICK).apply {
                    type = ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE // Solo contactos con números de teléfono
                }
                if (intent.resolveActivity(context.packageManager) != null) {
                    contactPickerLauncher.launch(intent)
                } else {
                    Log.e("ContactPicker", "No se encontró ninguna aplicación para seleccionar contactos.")
                    Toast.makeText(context, "No se encontró ninguna aplicación para seleccionar contactos.", Toast.LENGTH_LONG).show()
                }
            }) {
                Text("Seleccionar Teléfono de Contacto")
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Filled.Contacts,
                    contentDescription = "Icono de Contactos"
                )
            }

            Text(text = "Número de emergencia guardado: $phoneNumberToShow")

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = { navController.navigate("bluetooth_devices") }) {
                Text("Ir a Dispositivos Bluetooth")
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Filled.Bluetooth,
                    contentDescription = "Icono de Dispositivos Bluetooth"
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
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
                    Text("🧭 Colonia: ${address?.neighbourhood ?: "N/D"}")
                    Text("🏙️ Ciudad: ${address?.city ?: "N/D"}")
                    Text("🗺️ Estado: ${address?.state ?: "N/D"}")
                    Text("🌎 País: ${address?.country ?: "N/D"}")
                    Text("📮 CP: ${address?.postcode ?: "N/D"}")
                }
                else -> {
                    CircularProgressIndicator()
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            TopAppBar(title = { Text("Información Recibida") })

            Column(modifier = Modifier.fillMaxWidth()) {
                if (address != null) {
                    Text(text = "Dirección: ${address?.road ?: "N/D"}", modifier = Modifier.padding(8.dp))
                    Text(text = "Colonia: ${address?.neighbourhood ?: "N/D"}", modifier = Modifier.padding(8.dp))
                }

                Text(
                    text = "Último objeto detectado: $lastObjectSeen",
                    modifier = Modifier.padding(8.dp)
                )
            }
        }


    }
}
