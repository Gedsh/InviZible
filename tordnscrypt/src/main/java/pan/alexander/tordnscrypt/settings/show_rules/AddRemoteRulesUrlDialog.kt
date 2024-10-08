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

package pan.alexander.tordnscrypt.settings.show_rules

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.setPadding
import pan.alexander.tordnscrypt.R
import pan.alexander.tordnscrypt.utils.Constants.URL_REGEX
import pan.alexander.tordnscrypt.utils.Utils.dp2pixels
import pan.alexander.tordnscrypt.utils.Utils.getDomainNameFromUrl
import java.util.regex.Pattern

class AddRemoteRulesUrlDialog {

    var callback: OnAddRemoteRulesUrl? = null

    fun createDialog(context: Context) =
        AlertDialog.Builder(context)
            .apply {
                val urlPattern = Pattern.compile(URL_REGEX)
                val editText = EditText(context).apply {
                    setPadding(dp2pixels(8).toInt())
                    addTextChangedListener(object : TextWatcher {
                        override fun beforeTextChanged(
                            s: CharSequence?,
                            start: Int,
                            count: Int,
                            after: Int
                        ) {
                        }

                        override fun onTextChanged(
                            s: CharSequence?,
                            start: Int,
                            before: Int,
                            count: Int
                        ) {
                        }

                        override fun afterTextChanged(s: Editable?) {
                            if (urlPattern.matcher(s?.toString() ?: "").matches()) {
                                setTextColor(ContextCompat.getColor(context, R.color.colorText))
                            } else {
                                setTextColor(ContextCompat.getColor(context, R.color.colorAlert))
                            }
                        }

                    })
                }
                setView(editText)
                setTitle(R.string.dns_rule_add_url)
                setPositiveButton(R.string.ok) { _, _ ->
                    val url = editText.text?.toString() ?: ""
                    if (urlPattern.matcher(url).matches()) {
                        val name = getDomainNameFromUrl(url)
                        callback?.onRemoteRulesUrlAdded(url, name)
                    }
                }
                setNegativeButton(R.string.cancel) { _, _ -> }
            }.create()

    interface OnAddRemoteRulesUrl {
        fun onRemoteRulesUrlAdded(url: String, name: String)
    }
}
