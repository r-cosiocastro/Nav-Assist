package com.dasc.auxiliovisionis.ui.adapter

import android.bluetooth.BluetoothDevice

data class BleDevice(
    val device: BluetoothDevice,
    val resolvedName: String?,
    val address: String
)