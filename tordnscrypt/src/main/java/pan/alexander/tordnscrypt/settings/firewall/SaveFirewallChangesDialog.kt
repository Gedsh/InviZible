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

package pan.alexander.tordnscrypt.settings.firewall

import androidx.appcompat.app.AlertDialog
import pan.alexander.tordnscrypt.R
import pan.alexander.tordnscrypt.dialogs.ExtendedDialogFragment
import pan.alexander.tordnscrypt.modules.ModulesStatus
import pan.alexander.tordnscrypt.utils.logger.Logger.loge
import java.lang.Exception

class SaveFirewallChangesDialog : ExtendedDialogFragment() {

    val modulesStatus = ModulesStatus.getInstance()

    override fun assignBuilder(): AlertDialog.Builder? {

        val activity = activity
        if (activity == null || activity.isFinishing) {
            return null
        }

        val firewallFragment = parentFragmentManager.findFragmentByTag(FirewallFragment.TAG)
                as? FirewallFragment ?: return null

        val message = activity.getString(R.string.ask_save_changes)

        val builder = AlertDialog.Builder(activity)

        builder.setTitle(R.string.menu_firewall)
        builder.setMessage(message)

        builder.setPositiveButton(R.string.ok) { _, _ ->
            try {
                firewallFragment.viewModel.saveFirewallChanges()
            } catch (e: Exception) {
                loge("SaveFirewallChanges", e)
            } finally {
                activity.finish()
            }

        }

        builder.setNegativeButton(R.string.cancel) { dialog, _ ->
            try {
                dialog.cancel()
            } catch (e: Exception) {
                loge("SaveFirewallChanges", e)
            } finally {
                activity.finish()
            }
        }

        return builder
    }

    companion object {
        const val TAG = "pan.alexander.tordnscrypt.settings.firewall.SaveFirewallChangesDialog"
    }
}
