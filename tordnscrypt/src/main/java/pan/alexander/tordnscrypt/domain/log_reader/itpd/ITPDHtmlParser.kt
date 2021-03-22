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

package pan.alexander.tordnscrypt.domain.log_reader.itpd

import pan.alexander.tordnscrypt.domain.entities.LogDataModel
import pan.alexander.tordnscrypt.domain.ModulesLogRepository
import java.util.*
import java.util.regex.Pattern

private val patternBootstrappedPercents = Pattern.compile("Tunnel creation success rate:.+(\\d+)%")

class ITPDHtmlParser(val modulesLogRepository: ModulesLogRepository) {
    private var startedSuccessfully = false
    private var startedWithError = false
    private var percentsSaved = 0
    private var linesSaved = listOf<String>()
    private var linesSavedHash = 0

    fun parseHtmlLines(): LogDataModel {
        val lines = modulesLogRepository.getITPDHtmlData()

        val linesHash = lines.hashCode()

        if (linesHash != linesSavedHash) {
            linesSaved = lines
            linesSavedHash = linesHash
        }

        if (!startedSuccessfully) {

            var errorFound = false

            for (line in lines) {
                val matcher = patternBootstrappedPercents.matcher(line)

                if (matcher.find()) {
                    percentsSaved = matcher.group(1)?.toInt() ?: percentsSaved

                    when (percentsSaved) {
                        0 -> {
                            startedSuccessfully = false
                            startedWithError = errorFound
                        }
                        else -> {
                            startedSuccessfully = true
                            startedWithError = false
                        }
                    }

                    break
                } else if (line.contains("Network status")) {
                    if (line.toLowerCase(Locale.ROOT).contains("error")) {
                        startedSuccessfully = false
                        startedWithError = true
                        errorFound = true
                    }
                }
            }
        }

        return LogDataModel(
            startedSuccessfully,
            startedWithError,
            percentsSaved,
            formatLines(linesSaved),
            linesSaved.hashCode()
        )
    }

    private fun formatLines(lines: List<String>): String {
        val output = StringBuilder()

        lines.forEach { line ->
            var formattedLine = line
            if (line.contains("<b>Network status:</b>")
                || line.contains("<b>Tunnel creation success rate:</b>")
                || line.contains("<b>Received:</b> ")
                || line.contains("<b>Sent:</b>")
                || line.contains("<b>Transit:</b>")
                || line.contains("<b>Routers:</b>")
                || line.contains("<b>Client Tunnels:</b>")
                || line.contains("<b>Uptime:</b>")
            ) {
                formattedLine = formattedLine
                    .replace("<div class=\"content\">", "")
                    .replace("<br>", "<br />")
                output.append(formattedLine)
            }
        }

        if (output.contains("<br />")) {
            return output.substring(0, output.lastIndexOf("<br />"))
        }

        return output.toString()
    }
}