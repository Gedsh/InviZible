package pan.alexander.tordnscrypt

import android.annotation.TargetApi
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.res.Configuration
import android.os.Build
import androidx.core.content.ContextCompat.getSystemService
import pan.alexander.tordnscrypt.language.Language

const val ANDROID_CHANNEL_ID = "InviZible"
const val ANDROID_CHANNEL_NAME = "NOTIFICATION_CHANNEL_INVIZIBLE"

class ApplicationExt: Application() {

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        Language.setFromPreference(this, "pref_fast_language")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }
    }

    override fun onCreate() {
        super.onCreate()
        Language.setFromPreference(this, "pref_fast_language")
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        val notificationManager = getSystemService<NotificationManager>(this, NotificationManager::class.java)
        val channel = NotificationChannel(ANDROID_CHANNEL_ID, ANDROID_CHANNEL_NAME, NotificationManager.IMPORTANCE_MIN)
        channel.setSound(null, Notification.AUDIO_ATTRIBUTES_DEFAULT)
        channel.description = ""
        channel.enableLights(false)
        channel.enableVibration(false)
        channel.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        channel.setShowBadge(false)
        notificationManager?.createNotificationChannel(channel)
    }

}

