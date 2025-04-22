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

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import pan.alexander.tordnscrypt.assistance.AccelerateDevelop.accelerated
import pan.alexander.tordnscrypt.settings.PathVars
import pan.alexander.tordnscrypt.utils.logger.Logger.loge
import java.lang.Exception

object ThemeUtils {

    @JvmStatic
    @Suppress("deprecation")
    fun setDayNightTheme(context: Context, pathVars: PathVars) {
        try {
            val theme = if (pathVars.appVersion.startsWith("g") && !accelerated) {
                "1"
            } else {
                val defaultSharedPreferences =
                    PreferenceManager.getDefaultSharedPreferences(context)
                defaultSharedPreferences.getString("pref_fast_theme", "4") ?: "4"
            }
            when (theme) {
                "1" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                "2" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                "3" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_AUTO_TIME)
                "4" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
        } catch (e: Exception) {
            loge("ThemeUtils setDayNightTheme", e)
        }
    }

    fun isNightMode(context: Context) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.resources.configuration.isNightModeActive
        } else {
            context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        }
}
