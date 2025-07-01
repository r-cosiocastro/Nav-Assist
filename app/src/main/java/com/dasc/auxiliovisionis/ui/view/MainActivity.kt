package com.dasc.auxiliovisionis.ui.view

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dasc.auxiliovisionis.bluetooth.BluetoothService
import com.dasc.auxiliovisionis.ui.view.components.BluetoothDevicesScreen
import com.dasc.auxiliovisionis.ui.view.components.MainScreen
import com.dasc.auxiliovisionis.ui.view.components.PermissionSetupScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Intent(this, BluetoothService::class.java).also { serviceIntent ->
            startService(serviceIntent)
        }

        setContent {
            MaterialTheme {
                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = "permissions"
                ) {
                    composable("main_screen") {
                        MainScreen(navController = navController)
                    }

                    composable("bluetooth_devices") {
                        BluetoothDevicesScreen(navController = navController)
                    }

                    composable("permissions") {
                        PermissionSetupScreen(navController = navController)
                    }
                }
            }
        }
    }
}