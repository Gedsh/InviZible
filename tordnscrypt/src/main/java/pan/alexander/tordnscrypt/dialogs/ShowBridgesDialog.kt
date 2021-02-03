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

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import pan.alexander.tordnscrypt.R
import pan.alexander.tordnscrypt.settings.tor_bridges.PreferencesTorBridges

class ShowBridgesDialog private constructor(): ExtendedDialogFragment() {

    private var bridges = ""

    companion object INSTANCE {
        fun getInstance(bridges: String): ShowBridgesDialog {
            val bundle = Bundle()
            bundle.putString("bridges", bridges)
            val dialog = ShowBridgesDialog()
            dialog.arguments = bundle
            return dialog
        }
    }

    override fun assignBuilder(): AlertDialog.Builder? {
        if (activity == null || requireActivity().isFinishing) {
            return null
        }

        arguments?.getString("bridges")?.let { bridges = it }

        val builder = AlertDialog.Builder(requireActivity(), R.style.CustomAlertDialogTheme)

        val tvBridges = TextView(requireActivity())
        tvBridges.setBackgroundResource(R.drawable.background_10dp_padding)
        tvBridges.setTextIsSelectable(true)
        tvBridges.isSingleLine = false
        tvBridges.isVerticalScrollBarEnabled = true
        tvBridges.text = bridges

        builder.setTitle(R.string.pref_fast_use_tor_bridges_show_dialog)
        builder.setView(tvBridges)

        builder.setPositiveButton(R.string.pref_fast_use_tor_bridges_add_dialog) { _, _ ->
            if (activity != null && !requireActivity().isFinishing) {
                val fm = requireActivity().supportFragmentManager
                val frgPreferencesTorBridges = fm.findFragmentByTag("PreferencesTorBridges") as PreferencesTorBridges?
                if (frgPreferencesTorBridges != null) {
                    frgPreferencesTorBridges.readSavedCustomBridges(bridges)
                } else {
                    Toast.makeText(requireActivity(), "Unable to save bridges!", Toast.LENGTH_LONG).show()
                }
            }
        }

        builder.setNegativeButton(R.string.pref_fast_use_tor_bridges_close_dialog) { dialogInterface, _ -> dialogInterface.cancel() }
        return builder
    }

}