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

    Copyright 2019-2023 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.utils.notification

import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AlertDialog
import pan.alexander.tordnscrypt.App
import pan.alexander.tordnscrypt.R
import pan.alexander.tordnscrypt.dialogs.ExtendedDialogFragment
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository
import javax.inject.Inject

class NotificationPermissionDialog: ExtendedDialogFragment() {

    @Inject
    lateinit var preferenceRepository: dagger.Lazy<PreferenceRepository>

    var manager: NotificationPermissionManager? = null
    var launcher: ActivityResultLauncher<String>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        App.instance.daggerComponent.inject(this)
        super.onCreate(savedInstanceState)
    }

    override fun assignBuilder(): AlertDialog.Builder? {
        if (activity?.isFinishing != false) {
            return null
        }

        val builder = AlertDialog.Builder(requireActivity(), R.style.CustomAlertDialogTheme)
        builder.setMessage(getString(R.string.notifications_permission_rationale_message))
            .setTitle(R.string.ask_force_close_title)
            .setPositiveButton(R.string.ok) { _, _ ->
                if (activity?.isFinishing == false) {
                    launcher?.let {
                        manager?.launchNotificationPermissionSystemDialog(it)
                    }
                }
            }
            .setNegativeButton(R.string.ask_later) { _, _ ->
                dismiss()
            }
            .setNeutralButton(R.string.dont_show) { _, _ ->
                manager?.onPermissionResultListener?.onDenied()
            }
        return builder
    }
}
