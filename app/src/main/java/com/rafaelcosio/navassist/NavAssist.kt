package com.rafaelcosio.navassist

import android.app.Application
import com.rafaelcosio.navassist.utils.NotificationHelper
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class NavAssist : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannel(
            applicationContext,
            NotificationHelper.BLUETOOTH_SERVICE_CHANNEL_ID,
            getString(R.string.bluetooth_service_channel_name),
            getString(R.string.bluetooth_service_channel_description),
        )

        NotificationHelper.createNotificationChannel(
            applicationContext,
            NotificationHelper.TTS_SERVICE_CHANNEL_ID,
            getString(R.string.tts_service_channel_name),
            getString(R.string.bluetooth_service_channel_description),
        )
    }
}