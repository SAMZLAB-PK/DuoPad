package com.unipoint

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class UniPointApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val connectionChannel = NotificationChannel(
                CHANNEL_CONNECTION,
                "Active Connection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps DOUPAD connection alive in background"
                setShowBadge(false)
            }

            val statusChannel = NotificationChannel(
                CHANNEL_STATUS,
                "Status Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            )

            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(connectionChannel)
            nm.createNotificationChannel(statusChannel)
        }
    }

    companion object {
        const val CHANNEL_CONNECTION = "unipoint_connection"
        const val CHANNEL_STATUS = "unipoint_status"
    }
}
