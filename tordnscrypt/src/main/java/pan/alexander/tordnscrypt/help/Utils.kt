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

package pan.alexander.tordnscrypt.help

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import pan.alexander.tordnscrypt.BuildConfig
import pan.alexander.tordnscrypt.R
import pan.alexander.tordnscrypt.TopFragment
import pan.alexander.tordnscrypt.assistance.AccelerateDevelop
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository
import pan.alexander.tordnscrypt.settings.PathVars
import pan.alexander.tordnscrypt.utils.logger.Logger.loge
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys
import pan.alexander.tordnscrypt.vpn.VpnUtils
import java.util.*

object Utils {

    fun sendMail(context: Context, text: String, attachmentUri: Uri) {

        val sendEmailIntent = Intent(Intent.ACTION_SEND).apply {
            // The intent does not have a URI, so declare the "text/plain" MIME type
            type = "message/rfc822"
            putExtra(Intent.EXTRA_EMAIL, arrayOf("invizible.soft@gmail.com")) // recipients
            putExtra(Intent.EXTRA_SUBJECT, "InviZible Pro ${BuildConfig.VERSION_NAME} logcat")
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_STREAM, attachmentUri)
        }

        // Verify it resolves
        val activities: List<ResolveInfo> = context.packageManager.queryIntentActivities(sendEmailIntent, 0)
        val isIntentSafe: Boolean = activities.isNotEmpty()

        if (isIntentSafe) {
            try {
                context.startActivity(sendEmailIntent)
            } catch (e: java.lang.Exception) {
                loge("sendMail", e)
            }

        }
    }

    fun ownFault(context: Context, exp: Throwable): Boolean {

        var ex = exp

        if (ex is OutOfMemoryError) {
            return false
        }

        if (ex.cause != null) {
            ex = ex.cause!!
        }

        for (ste in ex.stackTrace) {
            if (ste.className.startsWith(context.packageName)) {
                return true
            }
        }

        return false
    }

    fun collectInfo(appSign: String, appVersion: String, appProcVersion: String, version: String): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return "BRAND " + Build.BRAND + 10.toChar() +
                    "MODEL " + Build.MODEL + 10.toChar() +
                    "MANUFACTURER " + Build.MANUFACTURER + 10.toChar() +
                    "PRODUCT " + Build.PRODUCT + 10.toChar() +
                    "DEVICE " + Build.DEVICE + 10.toChar() +
                    "BOARD " + Build.BOARD + 10.toChar() +
                    "HARDWARE " + Build.HARDWARE + 10.toChar() +
                    "SUPPORTED_ABIS " + Arrays.toString(Build.SUPPORTED_ABIS) + 10.toChar() +
                    "SUPPORTED_32_BIT_ABIS " + Arrays.toString(Build.SUPPORTED_32_BIT_ABIS) + 10.toChar() +
                    "SUPPORTED_64_BIT_ABIS " + Arrays.toString(Build.SUPPORTED_64_BIT_ABIS) + 10.toChar() +
                    "SDK_INT " + Build.VERSION.SDK_INT + 10.toChar() +
                    "THREADS " + Thread.getAllStackTraces().size + 10.toChar() +
                    "VERSION " + version + 10.toChar() +
                    "APP_VERSION_CODE " + BuildConfig.VERSION_CODE + 10.toChar() +
                    "APP_VERSION_NAME " + BuildConfig.VERSION_NAME + 10.toChar() +
                    "APP_PROC_VERSION " + appProcVersion + 10.toChar() +
                    "CAN_FILTER " + VpnUtils.canFilter() + 10.toChar() +
                    "APP_VERSION " + appVersion + 10.toChar() +
                    "DNSCRYPT_INTERNAL_VERSION " + TopFragment.DNSCryptVersion + 10.toChar() +
                    "TOR_INTERNAL_VERSION " + TopFragment.TorVersion + 10.toChar() +
                    "I2PD_INTERNAL_VERSION " + TopFragment.ITPDVersion + 10.toChar() +
                    "SIGN_VERSION " + appSign
        } else {
            return "BRAND " + Build.BRAND + 10.toChar() +
                    "MODEL " + Build.MODEL + 10.toChar() +
                    "MANUFACTURER " + Build.MANUFACTURER + 10.toChar() +
                    "PRODUCT " + Build.PRODUCT + 10.toChar() +
                    "DEVICE " + Build.DEVICE + 10.toChar() +
                    "BOARD " + Build.BOARD + 10.toChar() +
                    "HARDWARE " + Build.HARDWARE + 10.toChar() +
                    "SDK_INT " + Build.VERSION.SDK_INT + 10.toChar() +
                    "THREADS " + Thread.getAllStackTraces().size + 10.toChar() +
                    "VERSION " + version + 10.toChar() +
                    "APP_VERSION_CODE " + BuildConfig.VERSION_CODE + 10.toChar() +
                    "APP_VERSION_NAME " + BuildConfig.VERSION_NAME + 10.toChar() +
                    "APP_PROC_VERSION " + appProcVersion + 10.toChar() +
                    "CAN_FILTER " + VpnUtils.canFilter() + 10.toChar() +
                    "APP_VERSION " + appVersion + 10.toChar() +
                    "DNSCRYPT_INTERNAL_VERSION " + TopFragment.DNSCryptVersion + 10.toChar() +
                    "TOR_INTERNAL_VERSION " + TopFragment.TorVersion + 10.toChar() +
                    "I2PD_INTERNAL_VERSION " + TopFragment.ITPDVersion + 10.toChar() +
                    "SIGN_VERSION " + appSign
        }
    }

    @JvmStatic
    fun getAppVersion(context: Context, pathVars: PathVars, preferences: PreferenceRepository) =
        if (pathVars.appVersion.endsWith("p")) {
            if (AccelerateDevelop.accelerated) {
                context.getString(R.string.premium_version)
            } else if (preferences.getStringPreference(PreferenceKeys.GP_DATA).isNotEmpty()) {
                context.getString(R.string.refunded_version)
            } else {
                context.getString(R.string.free_version)
            }
        } else if (pathVars.appVersion.startsWith("p")) {
            context.getString(R.string.premium_version)
        } else {
            context.getString(R.string.free_version)
        }
}
