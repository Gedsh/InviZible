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

import android.util.Log
import pan.alexander.tordnscrypt.domain.ModulesLogRepository
import pan.alexander.tordnscrypt.modules.ModulesStatus
import pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG
import pan.alexander.tordnscrypt.utils.enums.ModuleState
import java.lang.Exception

class TorInteractor(private val modulesLogRepository: ModulesLogRepository) {
    private val listeners: HashSet<OnTorLogUpdatedListener?> = HashSet()
    private var parser: TorLogParser? = null
    private val modulesStatus = ModulesStatus.getInstance()

    fun addListener (listener: OnTorLogUpdatedListener?) {
        listeners.add(listener)
    }

    fun removeListener (listener: OnTorLogUpdatedListener?) {
        listeners.remove(listener)

        if (listeners.isEmpty()) {
            resetParserState()
        }
    }

    fun hasAnyListener(): Boolean {
        return listeners.isNotEmpty()
    }

    fun parseTorLog() {
        try {
            parseLog()
        } catch (e: Exception) {
            Log.e(
                LOG_TAG, "TorInteractor parseTorLog exception " +
                        "${e.message} ${e.cause} ${e.stackTrace.joinToString { "," }}"
            )
        }
    }

    fun resetParserState() {
        if (modulesStatus.torState != ModuleState.RUNNING) {
            parser = null
        }
    }

    private fun parseLog() {
        if (listeners.isEmpty()) {
            return
        }

        resetParserState()

        parser = parser ?: TorLogParser(modulesLogRepository)

        val torLogData = parser?.parseLog()

        val listeners = listeners.toHashSet()

        listeners.forEach { listener ->
            if (listener?.isActive() == true) {
                torLogData?.let { listener.onTorLogUpdated(it) }
            } else {
                listener?.let { removeListener(it) }
            }
        }
    }
}
