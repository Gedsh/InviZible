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

package pan.alexander.tordnscrypt.utils.mode

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.PreferenceManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import pan.alexander.tordnscrypt.R
import pan.alexander.tordnscrypt.di.SharedPreferencesModule
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository
import pan.alexander.tordnscrypt.iptables.IptablesRules
import pan.alexander.tordnscrypt.iptables.ModulesIptablesRules
import pan.alexander.tordnscrypt.modules.ModulesAux
import pan.alexander.tordnscrypt.modules.ModulesStatus
import pan.alexander.tordnscrypt.nflog.NflogManager
import pan.alexander.tordnscrypt.utils.enums.ModuleState
import pan.alexander.tordnscrypt.utils.enums.ModuleState.RESTARTING
import pan.alexander.tordnscrypt.utils.enums.ModuleState.RUNNING
import pan.alexander.tordnscrypt.utils.enums.ModuleState.STARTING
import pan.alexander.tordnscrypt.utils.enums.OperationMode
import pan.alexander.tordnscrypt.utils.logger.Logger.logi
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.*
import pan.alexander.tordnscrypt.vpn.service.ServiceVPNHelper
import javax.inject.Inject
import javax.inject.Named

@ExperimentalCoroutinesApi
class AppModeManager @Inject constructor(
    private val context: Context,
    private val preferenceRepository: dagger.Lazy<PreferenceRepository>,
    private val nflogManager: dagger.Lazy<NflogManager>,
    @Named(SharedPreferencesModule.DEFAULT_PREFERENCES_NAME)
    private val defaultPreferences: dagger.Lazy<SharedPreferences>
) {

    private val modulesStatus = ModulesStatus.getInstance()

    fun switchToRootMode(appModeManagerCallback: AppModeManagerCallback?) {

        preferenceRepository.get()
            .setStringPreference(OPERATION_MODE, OperationMode.ROOT_MODE.toString())
        logi("Root mode enabled")

        val fixTTL = modulesStatus.isFixTTL && !modulesStatus.isUseModulesWithRoot
        val operationMode: OperationMode = modulesStatus.mode
        if (operationMode == OperationMode.VPN_MODE && !fixTTL) {
            ServiceVPNHelper.stop("Switch to root mode", context)
            Toast.makeText(context, context.getText(R.string.vpn_mode_off), Toast.LENGTH_LONG)
                .show()
        } else if (operationMode == OperationMode.PROXY_MODE && fixTTL) {
            appModeManagerCallback?.prepareVPNService()
        }

        if (defaultPreferences.get().getBoolean(CONNECTION_LOGS, true)) {
            val dnsCryptState = modulesStatus.dnsCryptState
            val torState = modulesStatus.dnsCryptState
            var firewallState = modulesStatus.firewallState
            if (dnsCryptState == RUNNING || dnsCryptState == STARTING || dnsCryptState == RESTARTING
                || torState == RUNNING || torState == STARTING || torState == RESTARTING
                || firewallState == RUNNING || firewallState == STARTING) {
                nflogManager.get().startNflog()
            }
        }

        //This start iptables adaptation
        modulesStatus.mode = OperationMode.ROOT_MODE
        ModulesAux.clearIptablesCommandsSavedHash(context)
        modulesStatus.setIptablesRulesUpdateRequested(true)

        appModeManagerCallback?.setFirewallNavigationItemVisible(true)
        appModeManagerCallback?.invalidateMenu()
    }

    fun switchToProxyMode(appModeManagerCallback: AppModeManagerCallback?) {

        preferenceRepository.get()
            .setStringPreference(OPERATION_MODE, OperationMode.PROXY_MODE.toString())
        logi("Proxy mode enabled")
        val operationMode: OperationMode = modulesStatus.mode

        if (operationMode == OperationMode.ROOT_MODE) {
            nflogManager.get().stopNflog()
        }

        //This stop iptables adaptation
        modulesStatus.mode = OperationMode.PROXY_MODE
        modulesStatus.setFirewallState(ModuleState.STOPPED, preferenceRepository.get())
        if (modulesStatus.isRootAvailable && operationMode == OperationMode.ROOT_MODE) {
            val iptablesRules: IptablesRules = ModulesIptablesRules(context)
            val commands = iptablesRules.clearAll()
            iptablesRules.sendToRootExecService(commands)
            logi("Iptables rules removed")
        } else if (operationMode == OperationMode.VPN_MODE) {
            ServiceVPNHelper.stop("Switch to proxy mode", context)
            Toast.makeText(context, context.getText(R.string.vpn_mode_off), Toast.LENGTH_LONG)
                .show()
        }

        appModeManagerCallback?.setFirewallNavigationItemVisible(false)
        appModeManagerCallback?.invalidateMenu()
    }

    fun switchToVPNMode(appModeManagerCallback: AppModeManagerCallback?) {

        preferenceRepository.get()
            .setStringPreference(OPERATION_MODE, OperationMode.VPN_MODE.toString())
        logi("VPN mode enabled")
        val operationMode: OperationMode = modulesStatus.mode

        if (operationMode == OperationMode.ROOT_MODE) {
            nflogManager.get().stopNflog()
        }

        //This stop iptables adaptation
        modulesStatus.mode = OperationMode.VPN_MODE
        if (modulesStatus.isRootAvailable && operationMode == OperationMode.ROOT_MODE) {
            val iptablesRules: IptablesRules = ModulesIptablesRules(context)
            val commands = iptablesRules.clearAll()
            iptablesRules.sendToRootExecService(commands)
            logi("Iptables rules removed")
        }
        val dnsCryptState: ModuleState = modulesStatus.dnsCryptState
        val torState: ModuleState = modulesStatus.torState
        val itpdState: ModuleState = modulesStatus.itpdState
        val firewallState: ModuleState = modulesStatus.firewallState
        if (dnsCryptState != ModuleState.STOPPED
            || torState != ModuleState.STOPPED
            || itpdState != ModuleState.STOPPED
            || firewallState != ModuleState.STOPPED
        ) {
            if (modulesStatus.isUseModulesWithRoot) {
                Toast.makeText(context, "Stop modules...", Toast.LENGTH_LONG).show()
                disableUseModulesWithRoot(context, modulesStatus)
            } else {
                appModeManagerCallback?.prepareVPNService()
            }
        }
        if (dnsCryptState == ModuleState.STOPPED
            && torState == ModuleState.STOPPED
            && itpdState == ModuleState.STOPPED
            && modulesStatus.isUseModulesWithRoot
        ) {
            disableUseModulesWithRoot(context, modulesStatus)
        }

        appModeManagerCallback?.setFirewallNavigationItemVisible(true)
        appModeManagerCallback?.invalidateMenu()
    }

    private fun disableUseModulesWithRoot(context: Context, modulesStatus: ModulesStatus) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        sharedPreferences.edit().putBoolean(RUN_MODULES_WITH_ROOT, false).apply()
        ModulesAux.stopModulesIfRunning(context)
        modulesStatus.isUseModulesWithRoot = false
        modulesStatus.isContextUIDUpdateRequested = true
        ModulesAux.requestModulesStatusUpdate(context)
        logi("Switch to VPN mode, disable use modules with root option")
    }
}
