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
import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import pan.alexander.tordnscrypt.R
import pan.alexander.tordnscrypt.settings.tor_bridges.PreferencesTorBridgesViewModel
import javax.inject.Inject

@ExperimentalCoroutinesApi
class BridgesCaptchaDialogFragment @Inject constructor(
    private val viewModelFactory: ViewModelProvider.Factory
) : ExtendedDialogFragment() {

    var transport = ""
    var ipv6 = false
    var captcha: Bitmap? = null
    var secretCode: String = ""

    private val preferencesTorBridgesViewModel: PreferencesTorBridgesViewModel by viewModels(
        { requireParentFragment() },
        { viewModelFactory }
    )

    private var okButtonPressed = false

    @SuppressLint("InflateParams")
    override fun assignBuilder(): AlertDialog.Builder =
        AlertDialog.Builder(requireActivity(), R.style.CustomAlertDialogTheme).apply {
            val layoutInflater = requireActivity().getSystemService(
                Context.LAYOUT_INFLATER_SERVICE
            ) as LayoutInflater

            val view: View = layoutInflater.inflate(R.layout.tor_transport_code_image, null)

            val imgCode = view.findViewById<ImageView>(R.id.imgCode)
            val etCode = view.findViewById<EditText>(R.id.etCode)

            imgCode.setImageBitmap(captcha)

            setView(view)

            setPositiveButton(R.string.ok) { _, _ ->
                okButtonPressed = true

                val captchaText = etCode.text.toString()

                if (transport.isNotEmpty() && secretCode.isNotEmpty()) {
                    preferencesTorBridgesViewModel.requestTorBridges(
                        transport,
                        ipv6,
                        captchaText,
                        secretCode
                    )
                }
            }

            setNegativeButton(R.string.cancel) { dialog, _ -> dialog.cancel() }
        }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)

        if (activity?.isChangingConfigurations == false && !okButtonPressed) {
            preferencesTorBridgesViewModel.dismissRequestBridgesDialogs()
        }

    }
}
