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

package pan.alexander.tordnscrypt.utils.notification

import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentManager
import pan.alexander.tordnscrypt.R
import pan.alexander.tordnscrypt.dialogs.ExtendedDialogFragment

class NotificationPermissionDialog: ExtendedDialogFragment() {

    override fun assignBuilder(): AlertDialog.Builder? {
        if (activity?.isFinishing != false) {
            return null
        }

        val builder = AlertDialog.Builder(requireActivity())
        builder.setMessage(getString(R.string.notifications_permission_rationale_message))
            .setTitle(R.string.reset_settings_title)
            .setPositiveButton(R.string.ok) { _, _ ->
                activity?.supportFragmentManager?.let {
                    getListener(it)?.notificationPermissionDialogOkPressed()
                }
            }
            .setNegativeButton(R.string.ask_later) { _, _ ->
                dismiss()
            }
            .setNeutralButton(R.string.dont_show) { _, _ ->
                activity?.supportFragmentManager?.let {
                    getListener(it)?.notificationPermissionDialogDoNotShowPressed()
                }
            }
        return builder
    }

    private fun getListener(manager: FragmentManager): NotificationPermissionDialogListener ? {
        for (fragment in manager.fragments) {
            if (fragment is NotificationPermissionDialogListener) {
                return fragment
            }
            getListener(fragment.childFragmentManager)
        }
        return null
    }

    interface NotificationPermissionDialogListener {
        fun notificationPermissionDialogOkPressed()
        fun notificationPermissionDialogDoNotShowPressed()
    }
}
