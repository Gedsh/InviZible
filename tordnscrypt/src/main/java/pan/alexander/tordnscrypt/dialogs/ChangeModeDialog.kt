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

package pan.alexander.tordnscrypt.dialogs

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import pan.alexander.tordnscrypt.App
import pan.alexander.tordnscrypt.R
import pan.alexander.tordnscrypt.utils.mode.AppModeManager
import pan.alexander.tordnscrypt.utils.mode.AppModeManagerCallback
import pan.alexander.tordnscrypt.utils.enums.OperationMode
import pan.alexander.tordnscrypt.utils.logger.Logger.loge
import javax.inject.Inject

private const val OPERATION_MODE_ARG = "pan.alexander.tordnscrypt.dialogs.ChangeModeDialog"

@ExperimentalCoroutinesApi
class ChangeModeDialog: ExtendedDialogFragment() {

    @Inject
    lateinit var appModeManager: AppModeManager

    override fun onCreate(savedInstanceState: Bundle?) {
        App.instance.subcomponentsManager.modulesServiceSubcomponent().inject(this)
        super.onCreate(savedInstanceState)
    }

    override fun assignBuilder(): AlertDialog.Builder? {

        val activity = activity
        if (activity == null || activity.isFinishing) {
            return null
        }

        val builder = AlertDialog.Builder(activity)

        val mode = arguments?.get(OPERATION_MODE_ARG) as OperationMode

        builder.setTitle(mode.name)
        builder.setMessage(R.string.ask_save_changes)

        builder.setPositiveButton(R.string.ok) { _, _ ->

            val appModeManagerCallback = activity as? AppModeManagerCallback
            appModeManagerCallback ?: return@setPositiveButton

            when (mode) {
                OperationMode.ROOT_MODE -> appModeManager.switchToRootMode(appModeManagerCallback)
                OperationMode.PROXY_MODE -> appModeManager.switchToProxyMode(appModeManagerCallback)
                OperationMode.VPN_MODE -> appModeManager.switchToVPNMode(appModeManagerCallback)
                else -> loge("ChangeModeDialog unknown mode!")
            }

        }

        builder.setNegativeButton(R.string.cancel) { dialog, _ ->
            dialog.cancel()
        }

        return builder
    }

    companion object INSTANCE {
        @JvmStatic
        fun getInstance(mode: OperationMode) = ChangeModeDialog().apply {
            arguments = bundleOf(OPERATION_MODE_ARG to mode)
        }
    }
}
