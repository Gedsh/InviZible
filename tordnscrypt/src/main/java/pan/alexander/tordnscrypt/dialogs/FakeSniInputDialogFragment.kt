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

import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import pan.alexander.tordnscrypt.R
import pan.alexander.tordnscrypt.utils.Utils.dp2pixels
import javax.inject.Inject

const val FAKE_SNI_ARG = "pan.alexander.tordnscrypt.dialogs.FAKE_SNI_ARG"

class FakeSniInputDialogFragment @Inject constructor() : ExtendedDialogFragment() {

    override fun assignBuilder(): AlertDialog.Builder =
        AlertDialog.Builder(requireActivity(), R.style.CustomAlertDialogTheme).apply {

            setTitle(R.string.pref_fast_fake_sni)

            val etSniInput = EditText(context).apply {
                setPadding(
                    dp2pixels(8).toInt(),
                    paddingTop,
                    dp2pixels(8).toInt(),
                    paddingBottom
                )
            }

            setView(etSniInput)

            arguments?.getStringArrayList(FAKE_SNI_ARG)?.let {
                etSniInput.setText(it.joinToString(", "))
            }

            setPositiveButton(R.string.ok) { _, _ ->

                val sniText = etSniInput.text.toString()

                val parent = parentFragment
                if (parent is SniInputListener) {
                    parent.setSni(sniText)
                }
            }

            setNegativeButton(R.string.cancel) { dialog, _ -> dialog.cancel() }
        }
}

interface SniInputListener {
    fun setSni(text: String?)
}
