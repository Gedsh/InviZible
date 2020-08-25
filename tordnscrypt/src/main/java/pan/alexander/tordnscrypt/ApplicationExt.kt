package pan.alexander.tordnscrypt

/*
    This file is part of InviZible Pro.

    InviZible Pro is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    InviZible Pro is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with InviZible Pro.  If not, see <http://www.gnu.org/licenses/>.

    Copyright 2019-2020 by Garmatin Oleksandr invizible.soft@gmail.com
*/

import android.annotation.TargetApi
import android.app.*
import android.content.res.Configuration
import android.os.Build
import androidx.core.content.ContextCompat.getSystemService
import pan.alexander.tordnscrypt.crash_handling.TopExceptionHandler
import pan.alexander.tordnscrypt.language.Language
import java.lang.ref.WeakReference

const val ANDROID_CHANNEL_ID = "InviZible"

class ApplicationExt : Application() {

    //Required for an app update on AndroidQ
    var langAppCompatActivityActive: Boolean = false

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        Language.setFromPreference(this, "pref_fast_language")
    }

    override fun onCreate() {
        super.onCreate()

        Language.setFromPreference(this, "pref_fast_language")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }

        setExceptionHandler()
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        val notificationManager = getSystemService(this, NotificationManager::class.java)
        val channel = NotificationChannel(ANDROID_CHANNEL_ID, getString(R.string.notification_channel_services), NotificationManager.IMPORTANCE_MIN)
        channel.setSound(null, Notification.AUDIO_ATTRIBUTES_DEFAULT)
        channel.description = ""
        channel.enableLights(false)
        channel.enableVibration(false)
        channel.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        channel.setShowBadge(false)
        notificationManager?.createNotificationChannel(channel)
    }

    private fun setExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler(TopExceptionHandler(this))
    }

}

