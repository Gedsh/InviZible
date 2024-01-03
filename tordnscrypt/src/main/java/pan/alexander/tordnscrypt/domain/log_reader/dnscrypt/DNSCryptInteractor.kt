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

package pan.alexander.tordnscrypt.domain.log_reader.dnscrypt

import android.util.Log
import pan.alexander.tordnscrypt.domain.log_reader.ModulesLogRepository
import pan.alexander.tordnscrypt.modules.ModulesStatus
import pan.alexander.tordnscrypt.utils.root.RootExecService.LOG_TAG
import pan.alexander.tordnscrypt.utils.enums.ModuleState
import java.lang.Exception
import java.lang.ref.WeakReference

class DNSCryptInteractor(private val modulesLogRepository: ModulesLogRepository) {
    private val listeners: HashMap<Class<*>, WeakReference<OnDNSCryptLogUpdatedListener>> = hashMapOf()
    private var parser: DNSCryptLogParser? = null
    private val modulesStatus = ModulesStatus.getInstance()

    fun <T: OnDNSCryptLogUpdatedListener> addListener(listener: T?) {
        listener?.let { listeners[it.javaClass] = WeakReference(it) }
    }

    fun <T: OnDNSCryptLogUpdatedListener> removeListener(listener: T?) {
        listener?.let { listeners.remove(it.javaClass) }

        if (listeners.isEmpty()) {
            resetParserState()
        }
    }

    fun hasAnyListener(): Boolean {
        return listeners.isNotEmpty()
    }

    fun parseDNSCryptLog() {
        try {
            parseLog()
        } catch (e: Exception) {
            Log.e(
                LOG_TAG, "DNSCryptInteractor parseDNSCryptLog exception " +
                        "${e.message} ${e.cause} ${e.stackTrace.joinToString { "," }}"
            )
        }
    }

    fun resetParserState() {
        if (modulesStatus.dnsCryptState != ModuleState.RUNNING) {
            parser = null
        }
    }

    private fun parseLog() {
        if (listeners.isEmpty()) {
            return
        }

        resetParserState()

        parser = parser ?: DNSCryptLogParser(modulesLogRepository)

        val dnsCryptLogData = parser?.parseLog()

        listeners.forEach { listener ->
            if (listener.value.get()?.isActive() == true) {
                dnsCryptLogData?.let { listener.value.get()?.onDNSCryptLogUpdated(it) }
            } else {
                removeListener(listener.value.get())
            }
        }
    }
}
