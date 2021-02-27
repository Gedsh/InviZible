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

package pan.alexander.tordnscrypt.domain.log_reader.tor

import pan.alexander.tordnscrypt.domain.entities.LogDataModel
import pan.alexander.tordnscrypt.domain.log_reader.LogParser
import pan.alexander.tordnscrypt.domain.ModulesLogRepository
import java.util.regex.Pattern

private val patternBootstrappedPercents = Pattern.compile("Bootstrapped +(\\d+)%")

class TorLogParser(private val modulesLogRepository: ModulesLogRepository) : LogParser() {
    private var startedSuccessfully = false
    private var startedWithError = false
    private var percentsSaved = -1
    private var linesSaved = listOf<String>()

    override fun parseLog(): LogDataModel {

        val lines = modulesLogRepository.getTorLog()

        if (lines.size != linesSaved.size) {
            linesSaved = lines
        }

        if (!startedSuccessfully) {

            var errorFound = false

            for (i in lines.size - 1 downTo 0) {
                val line = lines[i]

                val matcher = patternBootstrappedPercents.matcher(line)

                if (matcher.find()) {
                    percentsSaved = matcher.group(1)?.toInt() ?: percentsSaved

                    if (percentsSaved == 100) {
                        startedSuccessfully = true
                        startedWithError = false
                    } else if (!errorFound) {
                        startedWithError = false
                    }

                    break
                } else if (
                    line.contains("No running bridges")
                    || line.contains("Network unreachable")
                    || line.contains("Problem bootstrapping")
                    || line.contains("Stuck at")
                ) {
                    startedSuccessfully = false
                    startedWithError = true
                    errorFound = true
                }

            }
        }

        return LogDataModel(
            startedSuccessfully,
            startedWithError,
            percentsSaved,
            formatLines(linesSaved),
            linesSaved.size
        )
    }
}