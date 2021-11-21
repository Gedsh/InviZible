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

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import pan.alexander.tordnscrypt.R
import pan.alexander.tordnscrypt.settings.SettingsActivity
import pan.alexander.tordnscrypt.settings.tor_bridges.GetNewBridgesCallbacks
import pan.alexander.tordnscrypt.settings.tor_bridges.GetNewBridges
import java.lang.ref.WeakReference

private var instance: ShowBridgesCodeImage? = null
private var newBridgesCallbacks: GetNewBridgesCallbacks? = null
private var codeImage: Bitmap? = null
private var secretCode: String = ""

class ShowBridgesCodeImage private constructor(): ExtendedDialogFragment() {

    companion object INSTANCE {
        fun getInstance(_codeImage: Bitmap, _secretCode:String, _newBridgesCallbacks: WeakReference<GetNewBridgesCallbacks>): ShowBridgesCodeImage? {
            newBridgesCallbacks = _newBridgesCallbacks.get()
            codeImage = _codeImage
            secretCode = _secretCode

            if (instance == null) {
                instance = ShowBridgesCodeImage()
            }

            return instance
        }
    }

    @SuppressLint("InflateParams")
    override fun assignBuilder(): AlertDialog.Builder? {
        if (activity == null || requireActivity().isFinishing) {
            return null
        }

        val builder = AlertDialog.Builder(requireActivity(), R.style.CustomAlertDialogTheme)
        val layoutInflater = requireActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

        val view: View = layoutInflater.inflate(R.layout.tor_transport_code_image, null)

        val imgCode = view.findViewById<ImageView>(R.id.imgCode)
        val etCode = view.findViewById<EditText>(R.id.etCode)

        imgCode.setImageBitmap(codeImage)

        builder.setView(view)

        builder.setPositiveButton(R.string.ok) { _, _ ->
            if (activity != null && !requireActivity().isFinishing) {

                if (newBridgesCallbacks == null) {
                    newBridgesCallbacks = GetNewBridges(WeakReference(activity as SettingsActivity?))
                }

                newBridgesCallbacks?.requestNewBridges(etCode.text.toString(), secretCode)
                newBridgesCallbacks?.showProgressDialog()
            }

        }

        builder.setNegativeButton(R.string.cancel) { dialog, _ -> dialog.cancel() }

        return builder
    }

    override fun onPause() {
        super.onPause()

        newBridgesCallbacks = null
    }

    override fun onDestroy() {
        super.onDestroy()

        instance = null
        newBridgesCallbacks = null
        codeImage = null
    }
}