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

package pan.alexander.tordnscrypt.domain.log_reader

import android.text.TextUtils
import pan.alexander.tordnscrypt.utils.logger.Logger.loge
import java.lang.StringBuilder
import java.util.*

abstract class AbstractLogParser {

    abstract fun parseLog(): LogDataModel

    fun formatLines(lines: List<String>): String {
        val stringBuilder = StringBuilder()

        try {
            for (line in lines) {

                if (line.isBlank()) {
                    continue
                }

                //s = Html.escapeHtml(s);
                var encodedLine = TextUtils.htmlEncode(line)
                val encodedLineLowerCase = encodedLine.lowercase(Locale.ROOT)

                if (encodedLineLowerCase.contains("[notice]") || encodedLineLowerCase.contains("/info")) {
                    encodedLine = "<font color=#808080>" + encodedLine.replace("[notice]", "")
                        .replace("[NOTICE]", "") + "</font>"
                } else if (encodedLineLowerCase.contains("[warn]") || encodedLineLowerCase.contains("/warn")) {
                    encodedLine = "<font color=#ffa500>$encodedLine</font>"
                } else if (encodedLineLowerCase.contains("[warning]")) {
                    encodedLine = "<font color=#ffa500>$encodedLine</font>"
                } else if (encodedLineLowerCase.contains("[error]") || encodedLineLowerCase.contains("/error")) {
                    encodedLine = "<font color=#f08080>$encodedLine</font>"
                } else if (encodedLineLowerCase.contains("[critical]")) {
                    encodedLine = "<font color=#990000>$encodedLine</font>"
                } else if (encodedLineLowerCase.contains("[fatal]")) {
                    encodedLine = "<font color=#990000>$encodedLine</font>"
                } else if (encodedLineLowerCase.isNotEmpty()) {
                    encodedLine = "<font color=#6897bb>$encodedLine</font>"
                }
                if (encodedLine.isNotBlank()) {
                    stringBuilder.append(encodedLine)
                    stringBuilder.append("<br />")
                }
            }
        } catch (e: Exception) {
            loge("LogParser formatLines", e)
        }

        val lastBrIndex: Int = stringBuilder.lastIndexOf("<br />")

        return if (lastBrIndex > 0) {
            stringBuilder.substring(0, lastBrIndex)
        } else {
            stringBuilder.toString()
        }
    }
}
