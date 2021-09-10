package pan.alexander.tordnscrypt.dialogs

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

import android.util.Log
import androidx.appcompat.app.AlertDialog
import pan.alexander.tordnscrypt.R
import pan.alexander.tordnscrypt.modules.ModulesStatus
import pan.alexander.tordnscrypt.settings.firewall.*
import pan.alexander.tordnscrypt.utils.root.RootExecService.LOG_TAG
import pan.alexander.tordnscrypt.utils.enums.ModuleState
import java.lang.Exception
import java.lang.ref.WeakReference

private var instance: SaveFirewallChanges? = null
private var firewallFragmentWeakReference: WeakReference<FirewallFragment>? = null

class SaveFirewallChanges private constructor() : ExtendedDialogFragment() {

    val modulesStatus = ModulesStatus.getInstance()

    companion object INSTANCE {
        fun getInstance(_firewallFragment: FirewallFragment): SaveFirewallChanges? {

            firewallFragmentWeakReference = WeakReference<FirewallFragment>(_firewallFragment)

            if (instance == null) {
                instance = SaveFirewallChanges()
            }

            return instance
        }
    }

    override fun assignBuilder(): AlertDialog.Builder? {

        val activity = activity
        if (activity == null || activity.isFinishing) {
            return null
        }

        val modulesRunning = modulesStatus.dnsCryptState == ModuleState.RUNNING
                || modulesStatus.torState == ModuleState.RUNNING
        val firewallEnabled = firewallFragmentWeakReference?.get()?.firewallEnabled ?: false

        val message = if (!firewallEnabled || firewallEnabled && modulesRunning) {
            activity.getString(R.string.ask_save_changes)
        } else {
            activity.getString(R.string.ask_save_changes) + "\n\t\n" +
                    activity.getString(R.string.firewall_warning_enable_module)
        }

        val builder = AlertDialog.Builder(activity, R.style.CustomAlertDialogTheme)

        builder.setTitle(R.string.menu_firewall)
        builder.setMessage(message)

        builder.setPositiveButton(R.string.ok) { _, _ ->
            try {
                firewallFragmentWeakReference?.get()?.saveFirewallChanges()
            } catch (e: Exception) {
                Log.e(LOG_TAG, "SaveFirewallChanges exception ${e.message} ${e.cause}")
            } finally {
                activity.finish()
            }

        }

        builder.setNegativeButton(R.string.cancel) { dialog, _ ->
            try {
                dialog.cancel()
            } catch (e: Exception) {
                Log.e(LOG_TAG, "SaveFirewallChanges exception ${e.message} ${e.cause}")
            } finally {
                activity.finish()
            }
        }

        return builder
    }

    override fun onDestroy() {
        super.onDestroy()

        instance = null
        firewallFragmentWeakReference = null
    }
}
