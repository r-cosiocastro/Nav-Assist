package com.dasc.auxiliovisionis.bluetooth

data class BleAction(
    val action: ActionType = ActionType.NO_ACTION,
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val objectDescription: String = "",
)

enum class ActionType {
    NO_ACTION,
    TALK_LOCATION,
    TALK_OBJECT,
    SEND_LOCATION_SMS
}