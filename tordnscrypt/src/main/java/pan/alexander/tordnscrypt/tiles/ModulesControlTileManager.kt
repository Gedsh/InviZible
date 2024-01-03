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

package pan.alexander.tordnscrypt.tiles

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.service.quicksettings.Tile
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.core.os.postDelayed
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.*
import pan.alexander.tordnscrypt.R
import pan.alexander.tordnscrypt.di.CoroutinesModule
import pan.alexander.tordnscrypt.di.SharedPreferencesModule
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository
import pan.alexander.tordnscrypt.modules.*
import pan.alexander.tordnscrypt.modules.ModulesService.*
import pan.alexander.tordnscrypt.settings.PathVars
import pan.alexander.tordnscrypt.utils.Constants.DEFAULT_SITES_IPS_REFRESH_INTERVAL
import pan.alexander.tordnscrypt.utils.Utils
import pan.alexander.tordnscrypt.utils.Utils.isInterfaceLocked
import pan.alexander.tordnscrypt.utils.enums.ModuleState
import pan.alexander.tordnscrypt.utils.enums.OperationMode
import pan.alexander.tordnscrypt.utils.filemanager.FileShortener
import pan.alexander.tordnscrypt.utils.jobscheduler.JobSchedulerManager.stopRefreshTorUnlockIPs
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.*
import pan.alexander.tordnscrypt.utils.root.RootCommands
import pan.alexander.tordnscrypt.utils.root.RootCommandsMark.*
import pan.alexander.tordnscrypt.utils.root.RootExecService.*
import pan.alexander.tordnscrypt.vpn.service.ServiceVPNHelper
import javax.inject.Inject
import javax.inject.Named

@RequiresApi(Build.VERSION_CODES.N)
class ModulesControlTileManager @Inject constructor(
    private val dispatcherMain: MainCoroutineDispatcher,
    @Named(CoroutinesModule.SUPERVISOR_JOB_IO_DISPATCHER_SCOPE)
    private val baseCoroutineScope: CoroutineScope,
    private val coroutineExceptionHandler: CoroutineExceptionHandler,
    private val context: Context,
    private val preferenceRepository: PreferenceRepository,
    @Named(SharedPreferencesModule.DEFAULT_PREFERENCES_NAME)
    private val defaultPreferences: SharedPreferences,
    private val handler: Handler,
    private val pathVars: PathVars
) {
    private val modulesStatus = ModulesStatus.getInstance()

    @Volatile
    private var task: Job? = null
    private var tile: Tile? = null

    private val coroutineScope by lazy { baseCoroutineScope + coroutineExceptionHandler }

    fun startUpdatingState(tile: Tile, manageTask: ManageTask) {

        this.tile = tile

        task?.cancel()

        task = (coroutineScope + CoroutineName("Update tile $manageTask")).launch {
            while (isActive) {
                updateTile(manageTask)
                delay(UPDATE_INTERVAL_SEC * 1000L)
            }
        }
    }

    fun stopUpdatingState() {
        task?.cancel()
        tile = null
    }

    private suspend fun updateTile(manageTask: ManageTask) {
        val tile = tile ?: return

        var moduleState = ModuleState.UNDEFINED

        val labelUpdated = when (manageTask) {
            ManageTask.MANAGE_TOR -> {
                moduleState = modulesStatus.torState
                updateTorTileLabel(moduleState)
            }
            ManageTask.MANAGE_DNSCRYPT -> {
                moduleState = modulesStatus.dnsCryptState
                updateDnsCryptTileLabel(moduleState)
            }
            ManageTask.MANAGE_ITPD -> {
                moduleState = modulesStatus.itpdState
                updateITPDTileLabel(moduleState)
            }
        }

        val iconUpdated = updateTileIconState(moduleState)

        if (labelUpdated || iconUpdated) {
            withContext(dispatcherMain) {
                tile.updateTile()
            }
        }
    }

    private fun updateTorTileLabel(moduleState: ModuleState): Boolean {
        val tile = tile ?: return false

        val savedTileLabel = tile.label

        val newTileLabel = when (moduleState) {
            ModuleState.STARTING, ModuleState.RESTARTING -> {
                context.getString(R.string.tvTorStarting)
            }
            ModuleState.RUNNING -> {
                refreshModuleInterfaceIfAppLaunched(
                    TOR_RUN_FRAGMENT_MARK,
                    TOR_KEYWORD,
                    pathVars.torPath
                )
                context.getString(R.string.tvTorRunning)
            }
            ModuleState.STOPPING -> {
                context.getString(R.string.tvTorStopping)
            }
            else -> {
                context.getString(R.string.tvTorStop)
            }
        }

        if (newTileLabel != savedTileLabel) {
            tile.label = newTileLabel
            return true
        }
        return false
    }

    private fun updateDnsCryptTileLabel(moduleState: ModuleState): Boolean {
        val tile = tile ?: return false

        val savedTileLabel = tile.label

        val newTileLabel = when (moduleState) {
            ModuleState.STARTING, ModuleState.RESTARTING -> {
                context.getString(R.string.tvDNSStarting)
            }
            ModuleState.RUNNING -> {
                refreshModuleInterfaceIfAppLaunched(
                    DNSCRYPT_RUN_FRAGMENT_MARK,
                    DNSCRYPT_KEYWORD,
                    pathVars.dnsCryptPath
                )
                context.getString(R.string.tvDNSRunning)
            }
            ModuleState.STOPPING -> {
                context.getString(R.string.tvDNSStopping)
            }
            else -> {
                context.getString(R.string.tvDNSStop)
            }
        }

        if (savedTileLabel != newTileLabel) {
            tile.label = newTileLabel
            return true
        }
        return false
    }

    private fun updateITPDTileLabel(moduleState: ModuleState): Boolean {
        val tile = tile ?: return false

        val savedTileLabel = tile.label

        val newTileLabel = when (moduleState) {
            ModuleState.STARTING, ModuleState.RESTARTING -> {
                context.getString(R.string.tvITPDStarting)
            }
            ModuleState.RUNNING -> {
                refreshModuleInterfaceIfAppLaunched(
                    I2PD_RUN_FRAGMENT_MARK,
                    ITPD_KEYWORD,
                    pathVars.itpdPath
                )
                context.getString(R.string.tvITPDRunning)
            }
            ModuleState.STOPPING -> {
                context.getString(R.string.tvITPDStopping)
            }
            else -> {
                context.getString(R.string.tvITPDStop)
            }
        }

        if (savedTileLabel != newTileLabel) {
            tile.label = newTileLabel
            return true
        }
        return false
    }

    private fun updateTileIconState(moduleState: ModuleState): Boolean {
        val tile = tile ?: return false

        val savedTileState = tile.state

        val newTileState = when (moduleState) {
            ModuleState.RUNNING, ModuleState.STARTING, ModuleState.RESTARTING -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }

        if (savedTileState != newTileState) {
            tile.state = newTileState
            return true
        }
        return false
    }

    fun manageModule(tile: Tile, manageTask: ManageTask) {

        (coroutineScope + CoroutineName("Manage tile $manageTask")).launch {
            withTimeout(MANAGE_MODULE_TIMEOUT_SEC * 1000L) {
                initActionsInCaseOfFirstStart()

                when (manageTask) {
                    ManageTask.MANAGE_TOR -> manageTor()
                    ManageTask.MANAGE_DNSCRYPT -> manageDnsCrypt()
                    ManageTask.MANAGE_ITPD -> manageITPD()
                }

                ModulesAux.speedupModulesStateLoopTimer(context)

                startVpnServiceIfRequired()
            }
        }

        if (tile.state == Tile.STATE_INACTIVE) {
            tile.state = Tile.STATE_ACTIVE
            tile.updateTile()
        } else if (tile.state == Tile.STATE_ACTIVE) {
            tile.state = Tile.STATE_INACTIVE
            tile.updateTile()
        }

        if (task?.isCompleted != false) {
            startUpdatingState(tile, manageTask)
        }
    }

    private fun initActionsInCaseOfFirstStart() {
        var mode = modulesStatus.mode ?: OperationMode.UNDEFINED

        if (mode != OperationMode.UNDEFINED) {
            return
        }

        resetModulesSavedState(preferenceRepository)

        val rootIsAvailable: Boolean = preferenceRepository.getBoolPreference(ROOT_IS_AVAILABLE)
        val runModulesWithRoot: Boolean =
            defaultPreferences.getBoolean(RUN_MODULES_WITH_ROOT, false)
        val operationMode: String = preferenceRepository.getStringPreference(OPERATION_MODE)

        if (operationMode.isNotEmpty()) {
            mode = OperationMode.valueOf(operationMode)
        }

        ModulesAux.switchModes(rootIsAvailable, runModulesWithRoot, mode)

        val fixTTL = defaultPreferences.getBoolean(FIX_TTL, false)
        modulesStatus.isFixTTL = fixTTL

        Utils.startAppExitDetectService(context)

        //shortenTooLongSnowflakeLog(context, preferenceRepository, pathVars)
    }

    private fun resetModulesSavedState(preferences: PreferenceRepository) {
        preferences.setStringPreference(SAVED_DNSCRYPT_STATE_PREF, ModuleState.UNDEFINED.toString())
        preferences.setStringPreference(SAVED_TOR_STATE_PREF, ModuleState.UNDEFINED.toString())
        preferences.setStringPreference(SAVED_ITPD_STATE_PREF, ModuleState.UNDEFINED.toString())
    }

    private suspend fun manageTor() {

        if (isInterfaceLocked(preferenceRepository)) {
            showInterfaceLockedToast()
            return
        }

        if (modulesStatus.torState != ModuleState.RUNNING) {

            if (isStartingNotAllowed(modulesStatus.torState)) {
                showPleaseWaitToast()
                return
            }

            runTor()
        } else if (modulesStatus.torState == ModuleState.RUNNING) {
            stopRefreshTorUnlockIPsIfRequired()
            stopTor()
        }
    }

    private fun runTor() {
        if (!modulesStatus.isDnsCryptReady) {
            allowSystemDNS()
        }
        ModulesRunner.runTor(context)
        ModulesAux.saveTorStateRunning(true)
    }

    private fun stopRefreshTorUnlockIPsIfRequired() {
        val refreshPeriod = defaultPreferences.getString(
            SITES_IPS_REFRESH_INTERVAL, DEFAULT_SITES_IPS_REFRESH_INTERVAL.toString()
        )?.toInt() ?: DEFAULT_SITES_IPS_REFRESH_INTERVAL

        if (refreshPeriod == 0) {
            return
        }

        stopRefreshTorUnlockIPs(context)
    }

    private fun stopTor() {
        ModulesKiller.stopTor(context)
        ModulesAux.saveTorStateRunning(false)
    }

    private suspend fun manageDnsCrypt() {

        if (isInterfaceLocked(preferenceRepository)) {
            showInterfaceLockedToast()
            return
        }

        if (modulesStatus.dnsCryptState != ModuleState.RUNNING) {

            if (isStartingNotAllowed(modulesStatus.dnsCryptState)) {
                showPleaseWaitToast()
                return
            }

            runDNSCrypt()
        } else if (modulesStatus.dnsCryptState == ModuleState.RUNNING) {
            stopDNSCrypt()
        }
    }

    private fun runDNSCrypt() {
        if (!modulesStatus.isTorReady) {
            allowSystemDNS()
        }
        ModulesRunner.runDNSCrypt(context)
        ModulesAux.saveDNSCryptStateRunning(true)
    }

    private fun stopDNSCrypt() {
        ModulesKiller.stopDNSCrypt(context)
        ModulesAux.saveDNSCryptStateRunning(false)
    }

    private suspend fun manageITPD() {

        if (isInterfaceLocked(preferenceRepository)) {
            showInterfaceLockedToast()
            return
        }

        if (modulesStatus.itpdState != ModuleState.RUNNING) {

            if (isStartingNotAllowed(modulesStatus.itpdState)) {
                showPleaseWaitToast()
                return
            }
            FileShortener.shortenTooTooLongFile(pathVars.appDataDir + "/logs/i2pd.log")
            runITPD()
        } else if (modulesStatus.itpdState == ModuleState.RUNNING) {
            stopITPD()
            FileShortener.shortenTooTooLongFile(pathVars.appDataDir + "/logs/i2pd.log")
        }
    }

    private fun runITPD() {
        ModulesRunner.runITPD(context)
        ModulesAux.saveITPDStateRunning(true)
    }

    private fun stopITPD() {
        ModulesKiller.stopITPD(context)
        ModulesAux.saveITPDStateRunning(false)
    }

    private fun startVpnServiceIfRequired() {
        if (modulesStatus.mode != OperationMode.VPN_MODE && !modulesStatus.isFixTTL
            || defaultPreferences.getBoolean(VPN_SERVICE_ENABLED, false)
        ) {
            return
        }

        if (VpnService.prepare(context) == null) {
            handler.postDelayed(VPN_SERVICE_START_DELAY_SEC * 1000L) {
                defaultPreferences.edit().let {
                    it.putBoolean(VPN_SERVICE_ENABLED, true)
                    it.apply()
                }
                ServiceVPNHelper.start("Tile start", context)
            }
        }

    }

    private fun isStartingNotAllowed(moduleState: ModuleState): Boolean {
        return modulesStatus.isContextUIDUpdateRequested
                || !(moduleState == ModuleState.STOPPED
                || moduleState == ModuleState.FAULT
                || moduleState == ModuleState.UNDEFINED)
    }

    private fun allowSystemDNS() {
        if ((!modulesStatus.isRootAvailable || !modulesStatus.isUseModulesWithRoot)
            && !defaultPreferences.getBoolean(IGNORE_SYSTEM_DNS, false)
        ) {
            modulesStatus.isSystemDNSAllowed = true
        }
    }

    private suspend fun showInterfaceLockedToast() {
        showToast(R.string.action_mode_dialog_locked)
    }

    private suspend fun showPleaseWaitToast() {
        showToast(R.string.please_wait)
    }

    private suspend fun showToast(@StringRes message: Int) = withContext(dispatcherMain) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    private fun refreshModuleInterfaceIfAppLaunched(
        moduleMark: Int,
        moduleKeyWord: String,
        binaryPath: String
    ) {
        val comResult = RootCommands(arrayListOf(moduleKeyWord, binaryPath))
        val intent = Intent(COMMAND_RESULT)
        intent.putExtra("CommandsResult", comResult)
        intent.putExtra("Mark", moduleMark)
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
    }

    enum class ManageTask {
        MANAGE_TOR,
        MANAGE_DNSCRYPT,
        MANAGE_ITPD
    }

    companion object {
        const val UPDATE_INTERVAL_SEC = 1
        private const val VPN_SERVICE_START_DELAY_SEC = 2
        private const val MANAGE_MODULE_TIMEOUT_SEC = 3
    }
}
