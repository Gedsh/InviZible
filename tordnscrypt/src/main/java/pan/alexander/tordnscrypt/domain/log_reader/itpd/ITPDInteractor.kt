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

package pan.alexander.tordnscrypt.domain.log_reader.itpd

import pan.alexander.tordnscrypt.domain.log_reader.ModulesLogRepository
import pan.alexander.tordnscrypt.modules.ModulesStatus
import pan.alexander.tordnscrypt.utils.enums.ModuleState
import pan.alexander.tordnscrypt.utils.logger.Logger.loge
import java.lang.Exception
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

class ITPDInteractor(private val modulesLogRepository: ModulesLogRepository) {
    private val listeners = ConcurrentHashMap<Class<*>, WeakReference<OnITPDLogUpdatedListener>>()
    private var parser: ITPDLogParser? = null
    private val modulesStatus = ModulesStatus.getInstance()

    fun <T : OnITPDLogUpdatedListener> addListener(listener: T?) {
        listener?.let { listeners[listener.javaClass] = WeakReference(listener) }
    }

    fun <T : OnITPDLogUpdatedListener> removeListener(listener: T?) {
        listener?.let { listeners.remove(listener.javaClass) }

        if (listeners.isEmpty()) {
            resetParserState()
        }
    }

    fun hasAnyListener(): Boolean {
        return listeners.isNotEmpty()
    }

    fun parseITPDLog() {
        try {
            parseLog()
        } catch (e: Exception) {
            loge("ITPDInteractor parseITPDLog", e, true)
        }
    }

    fun resetParserState() {
        if (modulesStatus.itpdState != ModuleState.RUNNING) {
            parser = null
        }
    }

    private fun parseLog() {
        if (listeners.isEmpty()) {
            return
        }

        resetParserState()

        parser = parser ?: ITPDLogParser(modulesLogRepository)

        val itpdLogData = parser?.parseLog()

        listeners.forEach { listener ->
            if (listener.value.get()?.isActive() == true) {
                itpdLogData?.let { listener.value.get()?.onITPDLogUpdated(it) }
            } else {
                removeListener(listener.value.get())
            }
        }
    }
}
