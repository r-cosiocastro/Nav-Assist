package com.dasc.auxiliovisionis.ui.view

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import com.dasc.auxiliovisionis.bluetooth.BluetoothService
import com.dasc.auxiliovisionis.ui.view.components.BluetoothDevicesScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class BluetoothDevicesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Intent(this, BluetoothService::class.java).also { serviceIntent ->
            startService(serviceIntent)
        }

        setContent {
            MaterialTheme {
                BluetoothDevicesScreen()
            }
        }
    }
}