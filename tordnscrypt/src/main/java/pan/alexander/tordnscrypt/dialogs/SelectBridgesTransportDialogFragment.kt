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

package pan.alexander.tordnscrypt.dialogs

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.CheckBox
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import pan.alexander.tordnscrypt.R
import pan.alexander.tordnscrypt.di.SharedPreferencesModule.Companion.DEFAULT_PREFERENCES_NAME
import pan.alexander.tordnscrypt.settings.tor_bridges.PreferencesTorBridgesViewModel
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.TOR_USE_IPV6
import javax.inject.Inject
import javax.inject.Named

@ExperimentalCoroutinesApi
class SelectBridgesTransportDialogFragment @Inject constructor(
    @Named(DEFAULT_PREFERENCES_NAME)
    private val defaultPreferences: SharedPreferences,
    private val viewModelFactory: ViewModelProvider.Factory
) : ExtendedDialogFragment() {

    private val preferencesTorBridgesViewModel: PreferencesTorBridgesViewModel by viewModels(
        { requireParentFragment() },
        { viewModelFactory }
    )

    private var okButtonPressed = false

    @SuppressLint("InflateParams")
    override fun assignBuilder(): AlertDialog.Builder =
        AlertDialog.Builder(requireActivity(), R.style.CustomAlertDialogTheme).apply {
            val layoutInflater =
                requireActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

            val view: View = layoutInflater.inflate(R.layout.select_tor_transport, null)

            val rbgTorTransport = view.findViewById<RadioGroup>(R.id.rbgTorTransport)
            val chbRequestIPv6Bridges = view.findViewById<CheckBox>(R.id.chbRequestIPv6Bridges)

            if (defaultPreferences.getBoolean(TOR_USE_IPV6, false)) {
                chbRequestIPv6Bridges.visibility = VISIBLE
            } else {
                chbRequestIPv6Bridges.visibility = GONE
            }

            setView(view)

            setTitle(R.string.pref_fast_use_tor_bridges_transport_select)

            setPositiveButton(R.string.ok) { _, _ ->

                okButtonPressed = true

                val ipv6Bridges = chbRequestIPv6Bridges.isChecked

                when (rbgTorTransport.checkedRadioButtonId) {
                    R.id.rbObfsNone ->
                        preferencesTorBridgesViewModel.requestTorBridgesCaptchaChallenge(
                            "0",
                            ipv6Bridges
                        )
                    R.id.rbObfs4 ->
                        preferencesTorBridgesViewModel.requestTorBridgesCaptchaChallenge(
                            "obfs4",
                            ipv6Bridges
                        )
                }
            }

            setNegativeButton(R.string.cancel) { dialog, _ ->
                dialog.cancel()
            }
        }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)

        if (activity?.isChangingConfigurations == false && !okButtonPressed) {
            preferencesTorBridgesViewModel.dismissRequestBridgesDialogs()
        }

    }

}
