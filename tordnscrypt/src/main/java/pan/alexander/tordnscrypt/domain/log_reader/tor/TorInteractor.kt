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

package pan.alexander.tordnscrypt.domain.log_reader.tor

import pan.alexander.tordnscrypt.domain.log_reader.ModulesLogRepository
import pan.alexander.tordnscrypt.modules.ModulesStatus
import pan.alexander.tordnscrypt.utils.enums.ModuleState
import pan.alexander.tordnscrypt.utils.logger.Logger.loge
import java.lang.Exception
import java.lang.ref.WeakReference

class TorInteractor(private val modulesLogRepository: ModulesLogRepository) {
    private val listeners: HashMap<Class<*>, WeakReference<OnTorLogUpdatedListener>> = hashMapOf()
    private var parser: TorLogParser? = null
    private val modulesStatus = ModulesStatus.getInstance()

    fun <T: OnTorLogUpdatedListener> addListener (listener: T?) {
        listener?.let { listeners[listener.javaClass] = WeakReference(it) }
    }

    fun <T: OnTorLogUpdatedListener> removeListener (listener: T?) {

        listener?.let { listeners.remove(it.javaClass) }

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
            loge("TorInteractor parseTorLog", e, true)
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

        listeners.forEach { listener ->
            if (listener.value.get()?.isActive() == true) {
                torLogData?.let { listener.value.get()?.onTorLogUpdated(it) }
            } else {
                removeListener(listener.value.get())
            }
        }
    }
}
