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

import android.content.Context
import android.util.Log
import android.view.MenuItem
import android.widget.Toast
import androidx.preference.PreferenceManager
import pan.alexander.tordnscrypt.R
import pan.alexander.tordnscrypt.iptables.IptablesRules
import pan.alexander.tordnscrypt.iptables.ModulesIptablesRules
import pan.alexander.tordnscrypt.modules.ModulesAux
import pan.alexander.tordnscrypt.modules.ModulesStatus
import pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG
import pan.alexander.tordnscrypt.utils.enums.ModuleState
import pan.alexander.tordnscrypt.utils.enums.OperationMode
import pan.alexander.tordnscrypt.vpn.service.ServiceVPNHelper

object ChangeMode {
    fun switchToRootMode(context: Context, item: MenuItem?, changeModeInterface: ChangeModeInterface?) {

        item?.isChecked = true

        val modulesStatus = ModulesStatus.getInstance()

        PrefManager(context).setStrPref("OPERATION_MODE", OperationMode.ROOT_MODE.toString())
        Log.i(LOG_TAG, "Root mode enabled")

        val fixTTL = modulesStatus.isFixTTL && !modulesStatus.isUseModulesWithRoot
        val operationMode: OperationMode = modulesStatus.mode
        if (operationMode == OperationMode.VPN_MODE && !fixTTL) {
            ServiceVPNHelper.stop("Switch to root mode", context)
            Toast.makeText(context, context.getText(R.string.vpn_mode_off), Toast.LENGTH_LONG).show()
        } else if (operationMode == OperationMode.PROXY_MODE && fixTTL) {
            changeModeInterface?.prepareVPNService()
        }

        //This start iptables adaptation
        modulesStatus.mode = OperationMode.ROOT_MODE
        modulesStatus.setIptablesRulesUpdateRequested(true)
        ModulesAux.requestModulesStatusUpdate(context)

        changeModeInterface?.setFirewallNavigationItemVisible(false)
        changeModeInterface?.invalidateOptionsMenu()
    }

    fun switchToProxyMode(context: Context, item: MenuItem?, changeModeInterface: ChangeModeInterface?) {

        item?.isChecked = true

        val modulesStatus = ModulesStatus.getInstance()

        PrefManager(context).setStrPref("OPERATION_MODE", OperationMode.PROXY_MODE.toString())
        Log.i(LOG_TAG, "Proxy mode enabled")
        val operationMode: OperationMode = modulesStatus.mode

        //This stop iptables adaptation
        modulesStatus.mode = OperationMode.PROXY_MODE
        if (modulesStatus.isRootAvailable && operationMode == OperationMode.ROOT_MODE) {
            val iptablesRules: IptablesRules = ModulesIptablesRules(context)
            val commands = iptablesRules.clearAll()
            iptablesRules.sendToRootExecService(commands)
            Log.i(LOG_TAG, "Iptables rules removed")
        } else if (operationMode == OperationMode.VPN_MODE) {
            ServiceVPNHelper.stop("Switch to proxy mode", context)
            Toast.makeText(context, context.getText(R.string.vpn_mode_off), Toast.LENGTH_LONG).show()
        }

        changeModeInterface?.setFirewallNavigationItemVisible(false)
        changeModeInterface?.invalidateOptionsMenu()
    }

    fun switchToVPNMode(context: Context, item: MenuItem?, changeModeInterface: ChangeModeInterface?) {

        item?.isChecked = true

        val modulesStatus = ModulesStatus.getInstance()

        PrefManager(context).setStrPref("OPERATION_MODE", OperationMode.VPN_MODE.toString())
        Log.i(LOG_TAG, "VPN mode enabled")
        val operationMode: OperationMode = modulesStatus.mode

        //This stop iptables adaptation
        modulesStatus.mode = OperationMode.VPN_MODE
        if (modulesStatus.isRootAvailable && operationMode == OperationMode.ROOT_MODE) {
            val iptablesRules: IptablesRules = ModulesIptablesRules(context)
            val commands = iptablesRules.clearAll()
            iptablesRules.sendToRootExecService(commands)
            Log.i(LOG_TAG, "Iptables rules removed")
        }
        val dnsCryptState: ModuleState = modulesStatus.dnsCryptState
        val torState: ModuleState = modulesStatus.torState
        val itpdState: ModuleState = modulesStatus.itpdState
        if (dnsCryptState != ModuleState.STOPPED
                || torState != ModuleState.STOPPED
                || itpdState != ModuleState.STOPPED) {
            if (modulesStatus.isUseModulesWithRoot) {
                Toast.makeText(context, "Stop modules...", Toast.LENGTH_LONG).show()
                disableUseModulesWithRoot(context, modulesStatus)
            } else {
                changeModeInterface?.prepareVPNService()
            }
        }
        if (dnsCryptState == ModuleState.STOPPED
                && torState == ModuleState.STOPPED
                && itpdState == ModuleState.STOPPED
                && modulesStatus.isUseModulesWithRoot) {
            disableUseModulesWithRoot(context, modulesStatus)
        }

        changeModeInterface?.setFirewallNavigationItemVisible(true)
        changeModeInterface?.invalidateOptionsMenu()
    }

    private fun disableUseModulesWithRoot(context: Context, modulesStatus: ModulesStatus) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        sharedPreferences.edit().putBoolean("swUseModulesRoot", false).apply()
        ModulesAux.stopModulesIfRunning(context)
        modulesStatus.isUseModulesWithRoot = false
        modulesStatus.isContextUIDUpdateRequested = true
        ModulesAux.requestModulesStatusUpdate(context)
        Log.i(LOG_TAG, "Switch to VPN mode, disable use modules with root option")
    }
}