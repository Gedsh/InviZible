package pan.alexander.tordnscrypt.dialogs.progressDialogs

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

    Copyright 2019-2022 by Garmatin Oleksandr invizible.soft@gmail.com
*/

import android.app.Dialog
import android.os.Bundle
import android.widget.ProgressBar
import androidx.appcompat.app.AlertDialog
import pan.alexander.tordnscrypt.R
import pan.alexander.tordnscrypt.dialogs.ExtendedDialogFragment
import java.util.concurrent.Future

class CheckUpdatesDialog: ExtendedDialogFragment() {
    var checkUpdatesTask: Future<*>? = null

    override fun assignBuilder(): AlertDialog.Builder? {

        val activity = activity
        if (activity == null || activity.isFinishing) {
            return null
        }

        val builder = AlertDialog.Builder(activity, R.style.CustomAlertDialogTheme)
        builder.setTitle(R.string.update_checking_title)
        builder.setMessage(R.string.update_checking_message)
        builder.setIcon(R.drawable.ic_visibility_off_black_24dp)
        builder.setPositiveButton(R.string.cancel) { dialogInterface, _ ->
            checkUpdatesTask?.cancel(true)
            dialogInterface.dismiss()
            checkUpdatesTask = null
        }
        val progressBar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal)
        progressBar.setBackgroundResource(R.drawable.background_10dp_padding)
        progressBar.isIndeterminate = true
        builder.setView(progressBar)
        builder.setCancelable(false)
        return builder
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)

        if (checkUpdatesTask?.isDone == true) {
            dialog.dismiss()
        } else {
            dialog.setCanceledOnTouchOutside(false)
        }

        return dialog
    }

    override fun onDestroy() {
        super.onDestroy()
        checkUpdatesTask?.cancel(true)
        checkUpdatesTask = null
    }
}