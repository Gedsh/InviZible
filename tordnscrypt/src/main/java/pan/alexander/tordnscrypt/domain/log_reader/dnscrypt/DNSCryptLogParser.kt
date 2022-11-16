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

    Copyright 2019-2022 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.domain.log_reader.dnscrypt

import pan.alexander.tordnscrypt.domain.log_reader.LogDataModel
import pan.alexander.tordnscrypt.domain.log_reader.AbstractLogParser
import pan.alexander.tordnscrypt.domain.log_reader.ModulesLogRepository

private const val COUNT_DOWN_TIMER = 5

class DNSCryptLogParser(private val modulesLogRepository: ModulesLogRepository) : AbstractLogParser() {

    private var startedSuccessfully = false
    private var startedWithError = false
    private var linesSaved = listOf<String>()
    private var errorCountDownCounter = COUNT_DOWN_TIMER

    override fun parseLog(): LogDataModel {
        val lines = modulesLogRepository.getDNSCryptLog()

        if (lines.size != linesSaved.size) {
            linesSaved = lines
        }

        if (!startedSuccessfully) {
            for (i in lines.size - 1 downTo 0) {
                val line = lines[i]
                if (line.contains("lowest initial latency")) {
                    startedSuccessfully = true
                    startedWithError = false
                    errorCountDownCounter = COUNT_DOWN_TIMER
                    break
                } else if (line.contains(" OK ")
                    || line.contains("Stopped.")) {
                    startedSuccessfully = false
                    startedWithError = false
                    break
                } else if (line.contains("connect: connection refused")
                    || (line.contains("ERROR") && !line.contains("Unable to resolve"))
                    || line.contains("[CRITICAL]")
                    || line.contains("[FATAL]")
                ) {
                    if (errorCountDownCounter <= 0) {
                        startedSuccessfully = false
                        startedWithError = true
                        errorCountDownCounter = COUNT_DOWN_TIMER
                    } else {
                        errorCountDownCounter--
                    }

                    break
                }
            }
        }

        return LogDataModel(
            startedSuccessfully,
            startedWithError,
            -1,
            formatLines(linesSaved),
            linesSaved.size
        )
    }
}
