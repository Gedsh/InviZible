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

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.os.Bundle
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
import pan.alexander.tordnscrypt.App
import pan.alexander.tordnscrypt.R
import pan.alexander.tordnscrypt.databinding.SelectTorTransportBinding
import pan.alexander.tordnscrypt.di.SharedPreferencesModule.Companion.DEFAULT_PREFERENCES_NAME
import pan.alexander.tordnscrypt.settings.tor_bridges.PreferencesTorBridgesViewModel
import pan.alexander.tordnscrypt.utils.logger.Logger.loge
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.TOR_USE_IPV6
import javax.inject.Inject
import javax.inject.Named

@ExperimentalCoroutinesApi
class SelectBridgesTransportDialogFragment : ExtendedDialogFragment() {

    @Inject
    @Named(DEFAULT_PREFERENCES_NAME)
    lateinit var defaultPreferences: SharedPreferences
    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    private val preferencesTorBridgesViewModel: PreferencesTorBridgesViewModel by viewModels(
        { requireParentFragment() },
        { viewModelFactory }
    )

    private var okButtonPressed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        App.instance.daggerComponent.inject(this)
        super.onCreate(savedInstanceState)
    }

    @SuppressLint("InflateParams")
    override fun assignBuilder(): AlertDialog.Builder =
        AlertDialog.Builder(requireActivity()).apply {

            val binding = try {
                SelectTorTransportBinding.inflate(LayoutInflater.from(requireContext()))
            } catch (e: Exception) {
                loge("SelectBridgesTransportDialogFragment assignBuilder", e)
                throw e
            }

            if (defaultPreferences.getBoolean(TOR_USE_IPV6, true)) {
                binding.chbRequestIPv6Bridges.visibility = VISIBLE
            } else {
                binding.chbRequestIPv6Bridges.visibility = GONE
            }

            setView(binding.root)

            setTitle(R.string.pref_fast_use_tor_bridges_transport_select)

            setPositiveButton(R.string.ok) { _, _ ->

                okButtonPressed = true

                when (binding.rbgTorTransport.checkedRadioButtonId) {
                    R.id.rbObfsNone ->
                        preferencesTorBridgesViewModel.requestTorBridgesCaptchaChallenge(
                            "vanilla",
                            binding.chbRequestIPv6Bridges.isChecked
                        )
                    R.id.rbObfs4 ->
                        preferencesTorBridgesViewModel.requestTorBridgesCaptchaChallenge(
                            "obfs4",
                            binding.chbRequestIPv6Bridges.isChecked
                        )
                    R.id.rbWebTunnel ->
                        preferencesTorBridgesViewModel.requestTorBridgesCaptchaChallenge(
                            "webtunnel",
                            binding.chbRequestIPv6Bridges.isChecked
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
