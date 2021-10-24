package pan.alexander.tordnscrypt.utils

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

    Copyright 2019-2021 by Garmatin Oleksandr invizible.soft@gmail.com
*/

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Point
import android.os.Environment
import android.util.Base64
import android.util.Log
import android.view.Display
import androidx.preference.PreferenceManager
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository
import pan.alexander.tordnscrypt.modules.ModulesService
import pan.alexander.tordnscrypt.settings.PathVars
import pan.alexander.tordnscrypt.settings.tor_bridges.PreferencesTorBridges
import pan.alexander.tordnscrypt.utils.appexit.AppExitDetectService
import pan.alexander.tordnscrypt.utils.filemanager.FileShortener
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.*
import pan.alexander.tordnscrypt.utils.root.RootExecService.LOG_TAG
import java.io.File
import java.io.PrintWriter
import java.lang.IllegalArgumentException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException
import kotlin.math.roundToInt

object Utils {
    fun getScreenOrientationOld(activity: Activity): Int {
        val getOrient: Display = activity.windowManager.defaultDisplay
        val point = Point()
        getOrient.getSize(point)
        return if (point.x == point.y) {
            Configuration.ORIENTATION_UNDEFINED
        } else {
            if (point.x < point.y) {
                Configuration.ORIENTATION_PORTRAIT
            } else {
                Configuration.ORIENTATION_LANDSCAPE
            }
        }
    }

    fun getScreenOrientation(activity: Activity): Int {
        val displayMetrics = activity.resources.displayMetrics
        return when {
            displayMetrics.widthPixels < displayMetrics.heightPixels -> Configuration.ORIENTATION_PORTRAIT
            displayMetrics.widthPixels > displayMetrics.heightPixels -> Configuration.ORIENTATION_LANDSCAPE
            else -> Configuration.ORIENTATION_UNDEFINED
        }
    }


    fun dips2pixels(dips: Int, context: Context): Int {
        return (dips * context.resources.displayMetrics.density + 0.5f).roundToInt()
    }

    fun getDeviceIP(): String {
        try {
            val en = NetworkInterface.getNetworkInterfaces()
            while (en.hasMoreElements()) {
                val intf = en.nextElement()
                val enumIpAddr = intf.inetAddresses
                while (enumIpAddr.hasMoreElements()) {
                    val inetAddress = enumIpAddr.nextElement()
                    if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                        return inetAddress.getHostAddress() ?: ""
                    }
                }
            }
        } catch (e: SocketException) {
            Log.e(LOG_TAG, "Utils SocketException " + e.message + " " + e.cause)
        }

        return ""
    }

    fun isLANInterfaceExist(): Boolean {

        var result = false

        try {
            val en = NetworkInterface.getNetworkInterfaces()
            while (en.hasMoreElements()) {
                val intf = en.nextElement()

                if (intf.isLoopback || intf.isVirtual || !intf.isUp || intf.isPointToPoint || intf.hardwareAddress == null) {
                    continue
                }

                if (intf.name.replace("\\d+".toRegex(), "").equals("eth", ignoreCase = true)) {
                    result = true
                    break
                }

            }
        } catch (e: SocketException) {
            Log.e(LOG_TAG, "Util SocketException " + e.message + " " + e.cause)
        }

        return result
    }

    fun getHostByIP(IP: String): String {
        val addr = InetAddress.getByName(IP)
        return addr.hostName
    }

    //For backwards compatibility, it will still return the caller's own services.
    @Suppress("deprecation")
    fun isServiceRunning(context: Context, serviceClass: Class<ModulesService>): Boolean {
        var result = false

        try {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            for (serviceFounded in manager.getRunningServices(Int.MAX_VALUE)) {
                if (serviceClass.name == serviceFounded.service.className) {
                    result = true
                }
            }
        } catch (exception: Exception) {
            Log.e(
                LOG_TAG,
                "Utils isServiceRunning exception " + exception.message + " " + exception.cause
            )
        }

        return result
    }

    fun isShowNotification(context: Context): Boolean {
        val shPref = PreferenceManager.getDefaultSharedPreferences(context)
        return shPref.getBoolean("swShowNotification", true)
    }

    fun isLogsDirAccessible(): Boolean {
        var result = false
        try {
            val dir = Environment.getExternalStorageDirectory()
            if (dir != null && dir.isDirectory) {
                result = dir.list()?.isNotEmpty() ?: false
            } else {
                Log.w(LOG_TAG, "Root Dir is not read accessible!")
            }

            var rootDirPath = "/storage/emulated/0"
            if (dir != null && result) {
                rootDirPath = dir.canonicalPath
            }
            val saveDirPath = "$rootDirPath/TorDNSCrypt"
            val saveDir = File(saveDirPath)
            if (result && !saveDir.isDirectory && !saveDir.mkdir()) {
                result = false
                Log.w(LOG_TAG, "Root Dir is not write accessible!")
            }

            if (result) {
                val testFilePath = "$saveDirPath/testFile"
                val testFile = File(testFilePath)
                PrintWriter(testFile).print("")
                if (!testFile.isFile || !testFile.delete()) {
                    result = false
                    Log.w(LOG_TAG, "Root Dir is not write accessible!")
                }
            }

        } catch (e: Exception) {
            result = false
            Log.w(LOG_TAG, "Download Dir is not accessible " + e.message + e.cause)
        }
        return result
    }

    @JvmStatic
    fun isInterfaceLocked(preferenceRepository: PreferenceRepository): Boolean {
        var locked = false
        try {
            locked = String(
                Base64.decode(preferenceRepository.getStringPreference(CHILD_LOCK_PASSWORD), 16)
            ).contains("-l-o-c-k-e-d")
        } catch (e: IllegalArgumentException) {
            Log.e(LOG_TAG, "Decode child password exception ${e.message}")
        }
        return locked
    }

    @JvmStatic
    fun startAppExitDetectService(context: Context) {
        try {
            Intent(context, AppExitDetectService::class.java).apply {
                context.startService(this)
                Log.i(LOG_TAG, "Start app exit detect service")
            }
        } catch (e: java.lang.Exception) {
            Log.e(LOG_TAG, "Start app exit detect service exception + ${e.message} ${e.cause}")
        }
    }

    @JvmStatic
    fun shortenTooLongSnowflakeLog(context: Context, preferences: PreferenceRepository) {
        try {
            val bridgesSnowflakeDefault =
                preferences.getStringPreference(DEFAULT_BRIDGES_OBFS) == PreferencesTorBridges.snowFlakeBridgesDefault
            val bridgesSnowflakeOwn =
                preferences.getStringPreference(OWN_BRIDGES_OBFS) == PreferencesTorBridges.snowFlakeBridgesOwn
            val shPref = PreferenceManager.getDefaultSharedPreferences(context)
            val showHelperMessages =
                shPref.getBoolean(ALWAYS_SHOW_HELP_MESSAGES, false)
            if (showHelperMessages && (bridgesSnowflakeDefault || bridgesSnowflakeOwn)) {
                val pathVars = PathVars.getInstance(context)
                FileShortener.shortenTooTooLongFile(pathVars.appDataDir + "/logs/Snowflake.log")
            }
        } catch (e: Exception) {
            Log.e(LOG_TAG, "ShortenTooLongSnowflakeLog exception ${e.message} ${e.cause}")
        }
    }

}
