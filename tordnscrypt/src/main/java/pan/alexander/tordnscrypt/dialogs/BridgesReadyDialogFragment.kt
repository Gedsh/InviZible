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

package pan.alexander.tordnscrypt.dialogs

import android.content.DialogInterface
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import pan.alexander.tordnscrypt.App
import pan.alexander.tordnscrypt.R
import pan.alexander.tordnscrypt.settings.tor_bridges.PreferencesTorBridges
import pan.alexander.tordnscrypt.settings.tor_bridges.PreferencesTorBridgesViewModel
import javax.inject.Inject

@ExperimentalCoroutinesApi
class BridgesReadyDialogFragment : ExtendedDialogFragment() {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    private val preferencesTorBridgesViewModel: PreferencesTorBridgesViewModel by viewModels(
        { requireParentFragment() },
        { viewModelFactory }
    )

    var bridges = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        App.instance.daggerComponent.inject(this)
        super.onCreate(savedInstanceState)
    }

    override fun assignBuilder(): AlertDialog.Builder =
        AlertDialog.Builder(requireActivity()).apply {
            val tvBridges = TextView(requireActivity()).apply {
                setBackgroundResource(R.drawable.background_10dp_padding)
                setTextIsSelectable(true)
                isSingleLine = false
                isVerticalScrollBarEnabled = true
                text = bridges
            }

            setTitle(R.string.pref_fast_use_tor_bridges_show_dialog)
            setView(tvBridges)

            setPositiveButton(R.string.pref_fast_use_tor_bridges_add_dialog) { _, _ ->
                val preferencesTorBridges = parentFragment as? PreferencesTorBridges
                if (preferencesTorBridges != null && bridges.isNotEmpty()) {
                    preferencesTorBridges.readSavedCustomBridges(bridges)
                } else {
                    Toast.makeText(requireActivity(), "Unable to save bridges!", Toast.LENGTH_LONG)
                        .show()
                }
            }

            setNegativeButton(R.string.pref_fast_use_tor_bridges_close_dialog) { dialogInterface, _ ->
                dialogInterface.cancel()
            }
        }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)

        if (activity?.isChangingConfigurations == false) {
            preferencesTorBridgesViewModel.dismissRequestBridgesDialogs()
        }

    }

}
