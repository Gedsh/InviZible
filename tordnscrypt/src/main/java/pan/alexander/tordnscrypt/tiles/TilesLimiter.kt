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

package pan.alexander.tordnscrypt.tiles

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.service.quicksettings.TileService
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.ContextThemeWrapper
import pan.alexander.tordnscrypt.MainActivity
import pan.alexander.tordnscrypt.R
import pan.alexander.tordnscrypt.TopFragment.appVersion
import pan.alexander.tordnscrypt.assistance.AccelerateDevelop.accelerated
import pan.alexander.tordnscrypt.di.SharedPreferencesModule.Companion.DEFAULT_PREFERENCES_NAME
import pan.alexander.tordnscrypt.di.tiles.TilesScope
import pan.alexander.tordnscrypt.dialogs.Registration.wrongRegistrationCode
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository
import pan.alexander.tordnscrypt.settings.PathVars
import pan.alexander.tordnscrypt.utils.ThemeUtils
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.ALWAYS_SHOW_HELP_MESSAGES
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.TILES_LIMIT_DIALOG_NOT_SHOW
import pan.alexander.tordnscrypt.utils.root.RootExecService.LOG_TAG
import java.lang.Exception
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Named

private const val TILES_SAFE_COUNT = 3

@RequiresApi(Build.VERSION_CODES.N)
@TilesScope
class TilesLimiter @Inject constructor(
    private val appPreferences: dagger.Lazy<PreferenceRepository>,
    @Named(DEFAULT_PREFERENCES_NAME)
    private val defaultPreferences: dagger.Lazy<SharedPreferences>
) {

    private val currentTilesSet by lazy {
        Collections.newSetFromMap(ConcurrentHashMap<Class<TileService>, Boolean>())
    }

    private val isModulesNotInstalled by lazy {
        !PathVars.isModulesInstalled(appPreferences.get())
    }

    fun <T : TileService> listenTile(service: T) {
        currentTilesSet.add(service.javaClass)
        activeTilesSet.add(service.javaClass)
    }

    fun <T : TileService> unlistenTile(service: T) {
        currentTilesSet.remove(service.javaClass)

        if (currentTilesSet.isEmpty()) {
            BaseTileService.releaseTilesSubcomponent()
        }
    }

    fun checkActiveTilesCount(service: TileService) {
        appVersion = service.getString(R.string.appVersion)

        applyAppTheme(service)

        if (checkModulesNotInstalled(service)) {
            return
        }

        if (activeTilesSet.size > TILES_SAFE_COUNT) {
            val doNotShow = appPreferences.get()
                .getBoolPreference(TILES_LIMIT_DIALOG_NOT_SHOW)

            val showHelperMessages = defaultPreferences.get()
                .getBoolean(ALWAYS_SHOW_HELP_MESSAGES, false)

            if (!service.isSecure && (!doNotShow || showHelperMessages)) {
                showDialog(service, getWarningDialog(service))
            }
        } else {
            if (appVersion.endsWith("e")
                && wrongRegistrationCode
                && service is ChangeTorIpTileService) {
                showDialog(service, getDonateDialogForLite(service))
            } else if (appVersion.endsWith("p") && !accelerated) {
                showDialog(service, getDonateDialogForGp(service))
            }
        }
    }

    private fun applyAppTheme(service: TileService) {
        if (!themeApplied) {
            ThemeUtils.setDayNightTheme(service)
            themeApplied = true
        }
    }

    private fun checkModulesNotInstalled(service: TileService): Boolean {
        if (isModulesNotInstalled) {
            tryStartMainActivity(service)
        }
        return isModulesNotInstalled
    }

    fun reset() {
        activeTilesSet.clear()
    }

    private fun getWarningDialog(context: Context): Dialog =
        AlertDialog.Builder(ContextThemeWrapper(context, R.style.CustomTileDialogTheme))
            .apply {
                setTitle(R.string.main_activity_label)
                setMessage(R.string.tile_dialog_over_three_tiles_message)
                setPositiveButton(R.string.ok) { _, _ -> }
                setNegativeButton(R.string.dont_show) { _, _ ->
                    appPreferences.get().setBoolPreference(TILES_LIMIT_DIALOG_NOT_SHOW, true)
                }
            }.create()

    private fun showDialog(service: TileService, dialog: Dialog) {
        try {
            service.showDialog(dialog)
        } catch (e: Exception) {
            Log.e(LOG_TAG, "TilesLimiter show dialog ${e.javaClass} ${e.message}\n${e.cause}")
        }
    }

    private fun getDonateDialogForLite(context: Context): Dialog =
        AlertDialog.Builder(ContextThemeWrapper(context, R.style.CustomTileDialogTheme))
            .apply {
                setTitle(R.string.donate)
                setMessage(R.string.donate_project)
                setPositiveButton(R.string.ok) { _, _ ->
                    tryStartMainActivity(context)
                }
                setNegativeButton(R.string.cancel) { _, _ -> }
            }.create()

    private fun getDonateDialogForGp(context: Context): Dialog =
        AlertDialog.Builder(ContextThemeWrapper(context, R.style.CustomTileDialogTheme))
            .apply {
                setTitle(R.string.premium)
                setMessage(R.string.buy_premium_gp)
                setPositiveButton(R.string.ok) { _, _ ->
                    tryStartMainActivity(context)
                }
                setNegativeButton(R.string.cancel) { _, _ -> }
            }.create()

    private fun tryStartMainActivity(context: Context) {
        try {
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(this)
            }
        } catch (e: Exception) {
            Log.e(LOG_TAG, "TilesLimiter show activity ${e.javaClass} ${e.message}\n${e.cause}")
        }
    }

    companion object {

        private var themeApplied = false

        private val activeTilesSet by lazy {
            Collections.newSetFromMap(ConcurrentHashMap<Class<TileService>, Boolean>())
        }

        fun resetActiveTiles() {
            activeTilesSet.clear()
        }
    }

}
