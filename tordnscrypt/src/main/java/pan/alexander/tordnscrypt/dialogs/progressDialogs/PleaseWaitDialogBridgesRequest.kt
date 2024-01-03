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

package pan.alexander.tordnscrypt.dialogs.progressDialogs

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.widget.ProgressBar
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import pan.alexander.tordnscrypt.R
import pan.alexander.tordnscrypt.dialogs.ExtendedDialogFragment
import pan.alexander.tordnscrypt.settings.tor_bridges.PreferencesTorBridgesViewModel
import javax.inject.Inject

@ExperimentalCoroutinesApi
class PleaseWaitDialogBridgesRequest @Inject constructor(
    private val viewModelFactory: ViewModelProvider.Factory
) : ExtendedDialogFragment() {

    private val preferencesTorBridgesViewModel: PreferencesTorBridgesViewModel by viewModels(
        { requireParentFragment() },
        { viewModelFactory }
    )

    override fun assignBuilder(): AlertDialog.Builder =
        AlertDialog.Builder(requireActivity(), R.style.CustomAlertDialogTheme).apply {
            setTitle(R.string.pref_fast_use_tor_bridges_request_dialog)
            setMessage(R.string.please_wait)
            setIcon(R.drawable.ic_visibility_off_black_24dp)
            setPositiveButton(R.string.cancel) { dialogInterface, _ ->
                dialogInterface.cancel()
            }
            val progressBar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal)
            progressBar.setBackgroundResource(R.drawable.background_10dp_padding)
            progressBar.isIndeterminate = true
            setView(progressBar)
            setCancelable(false)
        }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)

        dialog.setCanceledOnTouchOutside(false)

        return dialog
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)

        if (activity?.isChangingConfigurations == false) {
            preferencesTorBridgesViewModel.cancelTorBridgesRequestJob()
        }

    }
}
