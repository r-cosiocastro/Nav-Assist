package com.dasc.auxiliovisionis.ui.view.components

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.shouldShowRationale

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionSetupScreen(navController: NavController) {
    val context = LocalContext.current

    val permissionsToRequest = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.SEND_SMS,
                Manifest.permission.READ_CONTACTS
            )
        } else {
            listOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.SEND_SMS,
                Manifest.permission.READ_CONTACTS
            )
        }.toMutableList().apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toList()
    }

    val multiplePermissionsState =
        rememberMultiplePermissionsState(permissions = permissionsToRequest)
    var allPermissionsGrantedHandled by remember { mutableStateOf(false) }
    var initialRequestMade by remember { mutableStateOf(false) }

    LaunchedEffect(
        key1 = multiplePermissionsState.allPermissionsGranted,
        key2 = allPermissionsGrantedHandled,
        key3 = initialRequestMade
    ) {
        if (!multiplePermissionsState.allPermissionsGranted && !allPermissionsGrantedHandled) {
            if (!initialRequestMade) {
                multiplePermissionsState.launchMultiplePermissionRequest()
                initialRequestMade = true
            }
        } else if (multiplePermissionsState.allPermissionsGranted && !allPermissionsGrantedHandled) {
            allPermissionsGrantedHandled = true
            navController.navigate("main_screen") {
                popUpTo(navController.graph.startDestinationId) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Card(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val revokedPermissions = multiplePermissionsState.revokedPermissions
                    val permanentlyDenied =
                        revokedPermissions.any { it.status.isPermanentlyDenied() }

                    Icon(
                        imageVector = if (permanentlyDenied) Icons.Default.Warning else Icons.Default.Lock,
                        contentDescription = null,
                        tint = if (permanentlyDenied) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = when {
                            permanentlyDenied -> "Permisos denegados permanentemente"
                            multiplePermissionsState.shouldShowRationale -> "Se requieren permisos para continuar"
                            else -> "Solicitando permisos..."
                        },
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = when {
                            permanentlyDenied -> "Algunos permisos han sido denegados de forma permanente. Ve a la configuración para habilitarlos."
                            multiplePermissionsState.shouldShowRationale -> "Otorga los permisos para que la aplicación funcione correctamente."
                            else -> "Esperando autorización de permisos..."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    revokedPermissions.forEach { perm ->
                        val permissionText = when (perm.permission) {
                            Manifest.permission.ACCESS_FINE_LOCATION -> "Ubicación precisa"
                            Manifest.permission.BLUETOOTH_SCAN -> "Escaneo Bluetooth"
                            Manifest.permission.BLUETOOTH_CONNECT -> "Conexión Bluetooth"
                            Manifest.permission.POST_NOTIFICATIONS -> "Notificaciones"
                            Manifest.permission.SEND_SMS -> "Enviar SMS"
                            Manifest.permission.READ_CONTACTS -> "Leer Contactos"
                            else -> perm.permission.substringAfterLast('.')
                        }

                        val statusColor = when (perm.status) {
                            is PermissionStatus.Granted -> Color.Green
                            is PermissionStatus.Denied -> MaterialTheme.colorScheme.error
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = statusColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "$permissionText: ${perm.status::class.simpleName}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    ElevatedButton(
                        onClick = {
                            if (permanentlyDenied) {
                                val intent =
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                    }
                                context.startActivity(intent)
                            } else {
                                multiplePermissionsState.launchMultiplePermissionRequest()
                            }
                        }
                    ) {
                        Text(if (permanentlyDenied) "Abrir configuración" else "Otorgar permisos")
                    }
                }
            }
        }
    }
}

@ExperimentalPermissionsApi
fun PermissionStatus.isPermanentlyDenied(): Boolean {
    return !shouldShowRationale && this is PermissionStatus.Denied
}