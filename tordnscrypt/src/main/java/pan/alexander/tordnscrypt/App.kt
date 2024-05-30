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

    Copyright 2019-2024 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt

import android.app.*
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat.getSystemService
import androidx.lifecycle.ProcessLifecycleOwner
import pan.alexander.tordnscrypt.crash_handling.TopExceptionHandler
import pan.alexander.tordnscrypt.di.*
import pan.alexander.tordnscrypt.language.Language
import pan.alexander.tordnscrypt.utils.multidex.MultidexActivator

const val AUX_CHANNEL_ID = "Auxiliary"

class App : Application() {

    val daggerComponent: AppComponent by lazy {
        DaggerAppComponent
            .builder()
            .appContext(applicationContext)
            .build()
    }

    val subcomponentsManager by lazy {
        SubcomponentsManager(this, daggerComponent)
    }

    @Volatile
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

        Language.setFromPreference(this, "pref_fast_language")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createAuxChannel()
        }

        setExceptionHandler()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
        }

        initAppLifecycleListener()
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
        val exceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        if (exceptionHandler is TopExceptionHandler) {
            return
        }
        Thread.setDefaultUncaughtExceptionHandler(
            TopExceptionHandler(
                getSharedPreferences(
                    SharedPreferencesModule.APP_PREFERENCES_NAME,
                    Context.MODE_PRIVATE
                ),
                exceptionHandler
            )
        )
    }

    private fun initAppLifecycleListener() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(AppLifecycleListener(this))
    }

}

