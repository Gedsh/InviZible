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
import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
import pan.alexander.tordnscrypt.R
import pan.alexander.tordnscrypt.utils.ChangeMode
import pan.alexander.tordnscrypt.utils.ChangeModeInterface
import pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG
import pan.alexander.tordnscrypt.utils.enums.OperationMode
import java.lang.ref.WeakReference

private var instance: WeakReference<ChangeModeDialog>? = null
private var changeModeInterface: WeakReference<ChangeModeInterface>? = null
private var menuItem: WeakReference<MenuItem>? = null
private var mode: OperationMode? = OperationMode.UNDEFINED

class ChangeModeDialog: ExtendedDialogFragment() {

    companion object INSTANCE {
        fun getInstance(_changeModeInterface: ChangeModeInterface, _item: MenuItem, _mode: OperationMode): ChangeModeDialog? {

            changeModeInterface = WeakReference(_changeModeInterface)
            menuItem = WeakReference(_item)
            mode = _mode

            if (instance == null) {
                instance = WeakReference(ChangeModeDialog())
            }

            return instance?.get()
        }
    }

    override fun assignBuilder(): AlertDialog.Builder? {

        val activity = activity
        if (activity == null || activity.isFinishing) {
            return null
        }

        val builder = AlertDialog.Builder(activity, R.style.CustomAlertDialogTheme)

        builder.setTitle(mode?.name ?: "")
        builder.setMessage(R.string.ask_save_changes)

        builder.setPositiveButton(R.string.ok) { _, _ ->
            when (mode) {
                OperationMode.ROOT_MODE -> ChangeMode.switchToRootMode(activity.applicationContext, menuItem?.get(), changeModeInterface?.get())
                OperationMode.PROXY_MODE -> ChangeMode.switchToProxyMode(activity.applicationContext, menuItem?.get(), changeModeInterface?.get())
                OperationMode.VPN_MODE -> ChangeMode.switchToVPNMode(activity.applicationContext, menuItem?.get(), changeModeInterface?.get())
                else -> Log.e(LOG_TAG, "ChangeModeDialog unknown mode!")
            }

        }

        builder.setNegativeButton(R.string.cancel) { dialog, _ ->
            dialog.cancel()
        }

        return builder
    }

    override fun onDestroy() {
        super.onDestroy()

        instance = null
        changeModeInterface = null
        menuItem = null
        mode = null
    }
}