package com.dasc.auxiliovisionis.ui.view.components

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.dasc.auxiliovisionis.data.remote.model.Address
import com.dasc.auxiliovisionis.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    navController: NavController,
    mainViewModel: MainViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val address by mainViewModel.address.collectAsState()
    val lastObjectSeen by mainViewModel.translation.collectAsState()
    val phoneNumberToShow by mainViewModel.savedPhoneNumber.collectAsState()
    val connectionStatus by mainViewModel.connectionStatusText.collectAsState()

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
                        }
                    }
                }
            }
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
                .verticalScroll(rememberScrollState())
        ) {
            EmergencyContactCard(phoneNumberToShow, contactPickerLauncher)
            Spacer(Modifier.height(20.dp))
            BluetoothConnectionCard(connectionStatus) { navController.navigate("bluetooth_devices") }
            Spacer(Modifier.height(20.dp))
            LocationCard(address)
            Spacer(Modifier.height(20.dp))
            DetectedObjectCard(lastObjectSeen)
        }
    }
}

@Composable
fun EmergencyContactCard(
    phoneNumber: String,
    contactPickerLauncher: ManagedActivityResultLauncher<Intent, ActivityResult>
) {
    val context = LocalContext.current

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(6.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("📱 Contacto de Emergencia", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            ElevatedButton(onClick = {
                val intent = Intent(Intent.ACTION_PICK).apply {
                    type = ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE
                }
                if (intent.resolveActivity(context.packageManager) != null) {
                    contactPickerLauncher.launch(intent)
                } else {
                    Toast.makeText(
                        context,
                        "No se encontró app para seleccionar contactos.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }) {
                Icon(Icons.Default.Contacts, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Seleccionar contacto")
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "📞 $phoneNumber",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun BluetoothConnectionCard(
    connectionStatus: String,
    onNavigate: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(6.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("🔗 Conexión Bluetooth", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            ElevatedButton(onClick = onNavigate) {
                Icon(Icons.Default.Bluetooth, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Ir a dispositivos Bluetooth")
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "📶 Estado de conexión:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = connectionStatus,
                style = MaterialTheme.typography.bodyLarge,
                color = if (connectionStatus.contains("conectado", true))
                    MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun LocationCard(address: Address?) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(6.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("📍 Datos de Ubicación", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))
            if (address != null) {
                Column {
                    LocationRow("Calle", address.road)
                    LocationRow("Colonia", address.neighbourhood)
                    LocationRow("Ciudad", address.city)
                    LocationRow("Estado", address.state)
                    LocationRow("País", address.country)
                    LocationRow("Código Postal", address.postcode)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Obteniendo dirección...")
                }
            }
        }
    }
}

@Composable
fun DetectedObjectCard(lastObjectSeen: String) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(6.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("🧠 Información Recibida", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text("🔎 Último objeto detectado:", style = MaterialTheme.typography.labelMedium)
            Text(
                text = lastObjectSeen,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun LocationRow(label: String, value: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = "$label:", style = MaterialTheme.typography.bodyMedium)
        Text(text = value ?: "N/D", style = MaterialTheme.typography.bodyMedium)
    }
}


