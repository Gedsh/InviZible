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

    Copyright 2019-2022 by Garmatin Oleksandr invizible.soft@gmail.com
*/

import android.annotation.TargetApi
import android.app.*
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import androidx.annotation.MainThread
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat.getSystemService
import androidx.lifecycle.ProcessLifecycleOwner
import pan.alexander.tordnscrypt.crash_handling.TopExceptionHandler
import pan.alexander.tordnscrypt.di.*
import pan.alexander.tordnscrypt.di.logreader.LogReaderSubcomponent
import pan.alexander.tordnscrypt.language.Language
import pan.alexander.tordnscrypt.utils.multidex.MultidexActivator

const val ANDROID_CHANNEL_ID = "InviZible"
const val FIREWALL_CHANNEL_ID = "Firewall"
const val AUX_CHANNEL_ID = "Auxiliary"

class App : Application() {

    //var currentActivity: WeakReference<Activity>? = null

    lateinit var daggerComponent: AppComponent
    private set

    private var logReaderDaggerSubcomponent: LogReaderSubcomponent? = null

    var isAppForeground: Boolean = false

    companion object {
        @JvmStatic
        lateinit var instance: App
        private set
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        if (BuildConfig.DEBUG) {
            MultidexActivator.activate(this)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        Language.setFromPreference(this, "pref_fast_language")
    }

    override fun onCreate() {
        super.onCreate()

        instance = this

        initDaggerComponent()

        Language.setFromPreference(this, "pref_fast_language")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
            createFirewallChannel()
            createAuxChannel()
        }

        setExceptionHandler()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
        }

        initAppLifecycleListener()
    }

    private fun initDaggerComponent() {
        daggerComponent = DaggerAppComponent
            .builder()
            .appContext(applicationContext)
            .build()
    }

    @MainThread
    fun initLogReaderDaggerSubcomponent(context: Context) = logReaderDaggerSubcomponent ?:
        daggerComponent.logReaderSubcomponent().create(context).also {
            logReaderDaggerSubcomponent = it
        }

    fun releaseLogReaderScope() {
        logReaderDaggerSubcomponent = null
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        val notificationManager = getSystemService(this, NotificationManager::class.java)
        val channel = NotificationChannel(
            ANDROID_CHANNEL_ID,
            getString(R.string.notification_channel_services),
            NotificationManager.IMPORTANCE_MIN
        )
        channel.setSound(null, Notification.AUDIO_ATTRIBUTES_DEFAULT)
        channel.description = ""
        channel.enableLights(false)
        channel.enableVibration(false)
        channel.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        channel.setShowBadge(false)
        notificationManager?.createNotificationChannel(channel)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createFirewallChannel() {
        val notificationManager = getSystemService(this, NotificationManager::class.java)
        val channel = NotificationChannel(
            FIREWALL_CHANNEL_ID,
            getString(R.string.notification_channel_firewall),
            NotificationManager.IMPORTANCE_HIGH
        )
        channel.setSound(null, Notification.AUDIO_ATTRIBUTES_DEFAULT)
        channel.description = ""
        channel.enableLights(true)
        channel.enableVibration(true)
        channel.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        channel.setShowBadge(true)
        notificationManager?.createNotificationChannel(channel)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createAuxChannel() {
        val notificationManager = getSystemService(this, NotificationManager::class.java)
        val channel = NotificationChannel(
            AUX_CHANNEL_ID,
            getString(R.string.notification_channel_auxiliary),
            NotificationManager.IMPORTANCE_HIGH
        )
        channel.setSound(null, Notification.AUDIO_ATTRIBUTES_DEFAULT)
        channel.description = ""
        channel.enableLights(true)
        channel.lightColor = Color.YELLOW
        channel.enableVibration(true)
        channel.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        channel.setShowBadge(true)
        notificationManager?.createNotificationChannel(channel)
    }

    private fun setExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler(TopExceptionHandler())
    }

    private fun initAppLifecycleListener() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(AppLifecycleListener(this))
    }

}

