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

    Copyright 2019-2025 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.utils

import android.app.Activity
import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Point
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Process
import android.text.Html
import android.util.Base64
import android.util.TypedValue
import android.view.Display
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository
import pan.alexander.tordnscrypt.modules.ModulesService
import pan.alexander.tordnscrypt.modules.ModulesStatus
import pan.alexander.tordnscrypt.settings.PathVars
import pan.alexander.tordnscrypt.settings.tor_apps.ApplicationData.Companion.SPECIAL_UID_CONNECTIVITY_CHECK
import pan.alexander.tordnscrypt.settings.tor_apps.ApplicationData.Companion.SPECIAL_UID_NTP
import pan.alexander.tordnscrypt.settings.tor_bridges.PreferencesTorBridges
import pan.alexander.tordnscrypt.utils.Constants.DNS_DEFAULT_UID
import pan.alexander.tordnscrypt.utils.Constants.HOST_NAME_REGEX
import pan.alexander.tordnscrypt.utils.Constants.IPv4_REGEX
import pan.alexander.tordnscrypt.utils.Constants.NETWORK_STACK_DEFAULT_UID
import pan.alexander.tordnscrypt.utils.appexit.AppExitDetectService
import pan.alexander.tordnscrypt.utils.filemanager.FileShortener
import pan.alexander.tordnscrypt.utils.logger.Logger.loge
import pan.alexander.tordnscrypt.utils.logger.Logger.logi
import pan.alexander.tordnscrypt.utils.logger.Logger.logw
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.ALWAYS_SHOW_HELP_MESSAGES
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.CHILD_LOCK_PASSWORD
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.DEFAULT_BRIDGES_OBFS
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.OWN_BRIDGES_OBFS
import pan.alexander.tordnscrypt.utils.root.RootCommands
import pan.alexander.tordnscrypt.utils.root.RootCommandsMark.NULL_MARK
import java.io.File
import java.io.PrintWriter
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.SocketException
import java.util.Locale
import java.util.regex.Pattern
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

    @JvmStatic
    fun dp2pixels(dp: Int) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        dp.toFloat(),
        Resources.getSystem().displayMetrics
    )


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
            loge("Utils SocketException", e)
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
            loge("Util SocketException", e)
        }

        return result
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
            loge("Utils isServiceRunning exception", exception)
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
                logw("Root Dir is not read accessible!")
            }

            var rootDirPath = "/storage/emulated/0"
            if (dir != null && result) {
                rootDirPath = dir.canonicalPath
            }
            val saveDirPath = "$rootDirPath/TorDNSCrypt"
            val saveDir = File(saveDirPath)
            if (result && !saveDir.isDirectory && !saveDir.mkdir()) {
                result = false
                logw("Root Dir is not write accessible!")
            }

            if (result) {
                val testFilePath = "$saveDirPath/testFile"
                val testFile = File(testFilePath)
                PrintWriter(testFile).print("")
                if (!testFile.isFile || !testFile.delete()) {
                    result = false
                    logw("Root Dir is not write accessible!")
                }
            }

        } catch (e: Exception) {
            result = false
            logw("Download Dir is not accessible", e)
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
            loge("Decode child password exception ${e.message}")
        }
        return locked
    }

    @JvmStatic
    fun startAppExitDetectService(context: Context) {
        try {
            Intent(context, AppExitDetectService::class.java).apply {
                context.startService(this)
                logi("Start app exit detect service")
            }
        } catch (e: java.lang.Exception) {
            loge("Start app exit detect service exception", e)
        }
    }

    @JvmStatic
    fun shortenTooLongSnowflakeLog(
        context: Context,
        preferences: PreferenceRepository,
        pathVars: PathVars
    ) {
        try {
            val bridgesSnowflakeDefault =
                preferences.getStringPreference(DEFAULT_BRIDGES_OBFS) == PreferencesTorBridges.SNOWFLAKE_BRIDGES_DEFAULT
            val bridgesSnowflakeOwn =
                preferences.getStringPreference(OWN_BRIDGES_OBFS) == PreferencesTorBridges.SNOWFLAKE_BRIDGES_OWN
            val shPref = PreferenceManager.getDefaultSharedPreferences(context)
            val showHelperMessages =
                shPref.getBoolean(ALWAYS_SHOW_HELP_MESSAGES, false)
            val betaVersion = pathVars.appVersion.equals("beta")
            if ((showHelperMessages || betaVersion) && (bridgesSnowflakeDefault || bridgesSnowflakeOwn)) {
                FileShortener.shortenTooTooLongFile(pathVars.appDataDir + "/logs/Snowflake.log")
            }
        } catch (e: Exception) {
            loge("ShortenTooLongSnowflakeLog exception", e)
        }
    }

    @JvmStatic
    fun shortenTooLongConjureLog(
        context: Context,
        preferences: PreferenceRepository,
        pathVars: PathVars
    ) {
        try {
            val bridgesConjureDefault =
                preferences.getStringPreference(DEFAULT_BRIDGES_OBFS) == PreferencesTorBridges.CONJURE_BRIDGES_DEFAULT
            val bridgesConjureOwn =
                preferences.getStringPreference(OWN_BRIDGES_OBFS) == PreferencesTorBridges.CONJURE_BRIDGES_OWN
            val shPref = PreferenceManager.getDefaultSharedPreferences(context)
            val showHelperMessages =
                shPref.getBoolean(ALWAYS_SHOW_HELP_MESSAGES, false)
            val betaVersion = pathVars.appVersion.equals("beta")
            if ((showHelperMessages || betaVersion) && (bridgesConjureDefault || bridgesConjureOwn)) {
                FileShortener.shortenTooTooLongFile(pathVars.appDataDir + "/logs/Conjure.log")
            }
        } catch (e: Exception) {
            loge("ShortenTooLongConjureLog exception", e)
        }
    }

    @JvmStatic
    fun shortenTooLongWebTunnelLog(
        context: Context,
        preferences: PreferenceRepository,
        pathVars: PathVars
    ) {
        try {
            val bridgesWebTunnelDefault =
                preferences.getStringPreference(DEFAULT_BRIDGES_OBFS) == PreferencesTorBridges.WEB_TUNNEL_BRIDGES_DEFAULT
            val bridgesWebTunnelOwn =
                preferences.getStringPreference(OWN_BRIDGES_OBFS) == PreferencesTorBridges.WEB_TUNNEL_BRIDGES_OWN
            val shPref = PreferenceManager.getDefaultSharedPreferences(context)
            val showHelperMessages =
                shPref.getBoolean(ALWAYS_SHOW_HELP_MESSAGES, false)
            val betaVersion = pathVars.appVersion.equals("beta")
            if ((showHelperMessages || betaVersion) && (bridgesWebTunnelDefault || bridgesWebTunnelOwn)) {
                FileShortener.shortenTooTooLongFile(pathVars.appDataDir + "/logs/WebTunnel.log")
            }
        } catch (e: Exception) {
            loge("ShortenTooLongWebTunnelLog exception", e)
        }
    }

    fun getUidForName(name: String, defaultValue: Int): Int {
        var uid = defaultValue
        try {
            val result = Process.getUidForName(name)
            if (result > 0) {
                uid = result
            } else {
                logw("No uid for $name, using default value $defaultValue")
            }
        } catch (e: Exception) {
            logw("No uid for $name, using default value $defaultValue")
        }
        return uid
    }

    fun getCriticalSystemUids(ownUid: Int): List<Int> =
        arrayListOf(
            getUidForName("system", Process.SYSTEM_UID + ownUid / 100_000 * 100_000),
            getUidForName("dns", DNS_DEFAULT_UID + ownUid / 100_000 * 100_000),
            getUidForName("network_stack", NETWORK_STACK_DEFAULT_UID + ownUid / 100_000 * 100_000),
            getUidForName("mdnsr", 1020 + ownUid / 100_000 * 100_000),
            getUidForName("clat", 1029 + ownUid / 100_000 * 100_000),
            getUidForName("dns_tether", 1052 + ownUid / 100_000 * 100_000),
            SPECIAL_UID_CONNECTIVITY_CHECK,
            SPECIAL_UID_NTP
        )

    fun getDnsTetherUid(ownUid: Int) =
        getUidForName("dns_tether", 1052 + ownUid / 100_000 * 100_000)

    @JvmStatic
    fun allowInteractAcrossUsersPermissionIfRequired(
        context: Context,
        pathVars: PathVars
    ) {
        if (!pathVars.appVersion.endsWith("p")
            && ModulesStatus.getInstance().isRootAvailable
            && !isInteractAcrossUsersPermissionGranted(context)
        ) {
            val allowAccessToWorkProfileApps = listOf(
                "pm grant ${context.packageName} android.permission.INTERACT_ACROSS_USERS"
            )
            RootCommands.execute(context, allowAccessToWorkProfileApps, NULL_MARK)
            logi("Grant INTERACT_ACROSS_USERS permission to access applications in work profile")
        }
    }

    private fun isInteractAcrossUsersPermissionGranted(context: Context) =
        ContextCompat.checkSelfPermission(
            context,
            "android.permission.INTERACT_ACROSS_USERS"
        ) == PackageManager.PERMISSION_GRANTED

    @JvmStatic
    fun areNotificationsAllowed(notificationManager: NotificationManager) =
        if (Build.VERSION.SDK_INT >= 24) {
            notificationManager.areNotificationsEnabled()
        } else {
            true
        }

    @JvmStatic
    fun areNotificationsNotAllowed(notificationManager: NotificationManager) =
        !areNotificationsAllowed(notificationManager)

    @JvmStatic
    fun verifyHostsSet(hosts: Set<String>) =
        hosts.filter {
            it.length < 255
                    && (it.matches(HOST_NAME_REGEX.toRegex()) || it.matches(IPv4_REGEX.toRegex()))
        }.toSet()

    @JvmStatic
    fun hideKeyboard(activity: Activity) =
        Handler(activity.mainLooper).post {
            val imm = activity.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
            var view = activity.currentFocus
            if (view == null) {
                view = View(activity)
            }
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }

    @JvmStatic
    fun unescapeHTML(line: String): String {
        var result = line
        val pattern = Pattern.compile("&#\\d+;")
        val matcher = pattern.matcher(line)
        if (matcher.find()) {
            result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                matcher.replaceAll(
                    Html.fromHtml(matcher.group(), Html.FROM_HTML_MODE_LEGACY).toString()
                )
            } else {
                matcher.replaceAll(Html.fromHtml(matcher.group()).toString())
            }
        }
        return result
    }

    fun formatFileSizeToReadableUnits(length: Long): String {
        var bytes = length
        if (bytes <= 1024) return String.format(Locale.ROOT, "%d B", bytes)
        var u = 0
        while (bytes > 1024 * 1024) {
            u++
            bytes = bytes shr 10
        }
        return String.format(Locale.ROOT, "%.1f %cB", bytes / 1024f, "kMGTPE"[u])
    }

    fun getDomainNameFromUrl(url: String): String =
        url.removePrefix("http://")
            .removePrefix("https://")
            .substringBefore("/")

}
