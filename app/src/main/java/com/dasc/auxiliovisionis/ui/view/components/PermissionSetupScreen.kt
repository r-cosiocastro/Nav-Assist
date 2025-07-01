package com.dasc.auxiliovisionis.ui.view.components

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
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

    // Lista de todos los permisos que tu aplicación necesita
    val permissionsToRequest = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 12+
            listOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                // Manifest.permission.CAMERA, // Descomenta si lo necesitas
                // Manifest.permission.RECORD_AUDIO, // Descomenta si lo necesitas
                // Si tu app usa FOREGROUND_SERVICE_MEDIA_PLAYBACK y targetea Android 14+ (API 34+)
                // el permiso FOREGROUND_SERVICE se otorga automáticamente si el tipo está bien declarado
                // y el servicio se inicia correctamente.
                // Sin embargo, para otros tipos de servicios en primer plano,
                // o si quieres ser explícito, puedes incluirlo.
                // No se puede solicitar directamente POST_NOTIFICATIONS aquí de la misma manera
                // para servicios en primer plano, se maneja de forma diferente.
            )
        } else { // Versiones anteriores a Android 12
            listOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION, // Bluetooth lo necesita en < API 31
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                // Manifest.permission.CAMERA,
                // Manifest.permission.RECORD_AUDIO,
            )
        }.toMutableList().apply {
            // Permiso de notificaciones para Android 13+ (API 33+)
            // Se debe solicitar por separado o junto con otros,
            // pero el usuario puede negarlo y la app debe funcionar.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13+
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toList()
    }

    val multiplePermissionsState = rememberMultiplePermissionsState(
        permissions = permissionsToRequest
    )

    var allPermissionsGrantedHandled by remember { mutableStateOf(false) }

    // Variable para controlar si la solicitud inicial ya se hizo en esta "sesión" del LaunchedEffect
    // Esto ayuda a evitar solicitudes repetidas si el usuario navega fuera y vuelve
    // mientras los permisos aún no están todos concedidos.
    var initialRequestMade by remember { mutableStateOf(false) }

    // Lanzar la solicitud de permisos cuando el Composable entra en la composición
    // si aún no se han otorgado todos o si no se ha manejado el estado de todos otorgados.
    LaunchedEffect(key1 = multiplePermissionsState.allPermissionsGranted, key2 = allPermissionsGrantedHandled, key3 = initialRequestMade) {
        if (!multiplePermissionsState.allPermissionsGranted && !allPermissionsGrantedHandled) {
            // Si no todos los permisos están concedidos y aún no se ha manejado el estado de "todos concedidos"

            if (!initialRequestMade) {
                // Si la solicitud inicial aún no se ha hecho en esta instancia del LaunchedEffect,
                // la realizamos. Esto cubre la primera vez que se muestra la pantalla
                // o si el LaunchedEffect se reinicia.
                Log.d("PermissionSetup", "Solicitud inicial de permisos.")
                multiplePermissionsState.launchMultiplePermissionRequest()
                initialRequestMade = true // Marcar que la solicitud inicial se ha hecho
            } else {
                // Si la solicitud inicial ya se hizo y aún no están todos los permisos,
                // significa que el usuario interactuó con el diálogo de permisos.
                // Aquí podrías basar la lógica en multiplePermissionsState.shouldShowRationale
                // para decidir si vuelves a lanzar la solicitud (lo cual el botón ya hace)
                // o simplemente esperas la interacción del usuario con la UI que has montado.
                // Por ahora, dejaremos que la UI maneje las siguientes solicitudes a través del botón.
                Log.d("PermissionSetup", "Solicitud inicial ya hecha. Estado de permisos: ${multiplePermissionsState.permissions.map { it.status }}")
            }

        } else if (multiplePermissionsState.allPermissionsGranted && !allPermissionsGrantedHandled) {
            // Todos los permisos otorgados, navegar a la pantalla principal
            Log.d("PermissionSetup", "Todos los permisos concedidos. Navegando...")
            allPermissionsGrantedHandled = true // Marcar como manejado
            navController.navigate("main_screen") { // Reemplaza "main_screen" con tu ruta real
                popUpTo(navController.graph.startDestinationId) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (!multiplePermissionsState.allPermissionsGranted) {
                val revokedPermissions = multiplePermissionsState.revokedPermissions
                val permanentlyDenied = revokedPermissions.any { it.status.isPermanentlyDenied() }

                Text(
                    if (permanentlyDenied) {
                        "Algunos permisos han sido denegados permanentemente. Por favor, habilítalos desde la configuración de la aplicación para continuar."
                    } else if (multiplePermissionsState.shouldShowRationale) {
                        "Esta aplicación necesita varios permisos para funcionar correctamente. Por favor, otórguelos para la mejor experiencia."
                    } else {
                        "Solicitando permisos necesarios..."
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(onClick = {
                    if (permanentlyDenied) {
                        // Abrir la configuración de la aplicación
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        val uri = Uri.fromParts("package", context.packageName, null)
                        intent.data = uri
                        context.startActivity(intent)
                    } else {
                        multiplePermissionsState.launchMultiplePermissionRequest()
                    }
                }) {
                    Text(if (permanentlyDenied) "Abrir Configuración" else "Otorgar Permisos")
                }

                revokedPermissions.forEach { perm ->
                    val permissionText = when (perm.permission) {
                        Manifest.permission.ACCESS_FINE_LOCATION -> "Ubicación precisa"
                        Manifest.permission.BLUETOOTH_SCAN -> "Escaneo Bluetooth"
                        Manifest.permission.BLUETOOTH_CONNECT -> "Conexión Bluetooth"
                        Manifest.permission.POST_NOTIFICATIONS -> "Notificaciones"
                        // Añade más casos según los permisos que solicites
                        else -> perm.permission.substringAfterLast('.') // Nombre corto del permiso
                    }
                    Text(
                        text = "Permiso requerido: $permissionText - Estado: ${perm.status}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

            } else {
                // Este bloque se alcanza si todos los permisos ya están concedidos al entrar
                // o después de que el LaunchedEffect los maneje.
                // El LaunchedEffect ya debería haber navegado si todos fueron concedidos.
                // Puedes mostrar un mensaje de carga o simplemente dejar que el LaunchedEffect haga la navegación.
                Text("Todos los permisos concedidos. Redirigiendo...")
                // Si la navegación en LaunchedEffect no ocurre inmediatamente,
                // podrías añadir un pequeño retraso aquí antes de forzar la navegación
                // o simplemente confiar en que el LaunchedEffect se ejecute.
                // Si allPermissionsGrantedHandled no se usara, podría haber una doble navegación.
            }
        }
    }
}


// Extensión para simplificar la comprobación de denegación permanente
@ExperimentalPermissionsApi
fun PermissionStatus.isPermanentlyDenied(): Boolean {
    return !shouldShowRationale && this is PermissionStatus.Denied
}