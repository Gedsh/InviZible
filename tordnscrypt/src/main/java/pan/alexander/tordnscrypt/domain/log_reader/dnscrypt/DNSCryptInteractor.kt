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

package pan.alexander.tordnscrypt.domain.log_reader.dnscrypt

import pan.alexander.tordnscrypt.domain.ModulesLogRepository
import pan.alexander.tordnscrypt.modules.ModulesStatus
import pan.alexander.tordnscrypt.utils.enums.ModuleState

class DNSCryptInteractor(private val modulesLogRepository: ModulesLogRepository) {
    private val listeners: HashSet<OnDNSCryptLogUpdatedListener?> = HashSet()
    private var parser: DNSCryptLogParser? = null
    private val modulesStatus = ModulesStatus.getInstance()

    fun addListener(listener: OnDNSCryptLogUpdatedListener?) {
        listeners.add(listener)
    }

    fun removeListener(listener: OnDNSCryptLogUpdatedListener?) {
        listeners.remove(listener)
        resetParserState()
    }

    fun hasAnyListener(): Boolean {
        return listeners.isNotEmpty()
    }

    fun parseDNSCryptLog() {

        if (listeners.isEmpty()) {
            return
        }

        parser = parser ?: DNSCryptLogParser(modulesLogRepository)

        val dnsCryptLogData = parser?.parseLog()

        val listeners = listeners.toHashSet()

        listeners.forEach { listener ->
            if (listener?.isActive() == true) {
                dnsCryptLogData?.let { listener.onDNSCryptLogUpdated(it) }
            } else {
                listener?.let { removeListener(it) }
            }
        }
    }

    private fun resetParserState() {
        if (listeners.isEmpty() && modulesStatus.dnsCryptState != ModuleState.RUNNING) {
            parser = null
        }
    }
}