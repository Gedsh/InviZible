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

    Copyright 2019-2025 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.domain.log_reader.itpd

import pan.alexander.tordnscrypt.domain.log_reader.LogDataModel
import pan.alexander.tordnscrypt.domain.log_reader.AbstractLogParser
import pan.alexander.tordnscrypt.domain.log_reader.ModulesLogRepository

class ITPDLogParser(private val modulesLogRepository: ModulesLogRepository) : AbstractLogParser() {
    private var startedSuccessfully = false
    private var startedWithError = false
    private var linesSaved = listOf<String>()

    override fun parseLog(): LogDataModel {
        val lines = modulesLogRepository.getITPDLog()

        if (lines.size != linesSaved.size) {
            linesSaved = ArrayList(lines)
        }

        if (!startedSuccessfully && linesSaved.isNotEmpty()) {
            startedSuccessfully = true
            startedWithError = false
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