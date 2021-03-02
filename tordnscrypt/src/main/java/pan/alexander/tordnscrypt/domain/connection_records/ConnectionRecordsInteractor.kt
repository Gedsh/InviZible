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

package pan.alexander.tordnscrypt.domain.connection_records

import android.util.Log
import pan.alexander.tordnscrypt.ApplicationBase
import pan.alexander.tordnscrypt.domain.ConnectionRecordsRepository
import pan.alexander.tordnscrypt.domain.entities.ConnectionRecord
import pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG
import java.lang.Exception
import kotlin.collections.HashSet

class ConnectionRecordsInteractor(private val connectionRecordsRepository: ConnectionRecordsRepository) {
    private val applicationContext = ApplicationBase.instance?.applicationContext
    private val listeners: HashSet<OnConnectionRecordsUpdatedListener?> = HashSet()
    private var converter: ConnectionRecordsConverter? = null
    private var parser: ConnectionRecordsParser? = null

    fun addListener(listener: OnConnectionRecordsUpdatedListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: OnConnectionRecordsUpdatedListener) {
        listeners.remove(listener)
        stopConverter()
    }

    fun hasAnyListener(): Boolean {
        return listeners.isNotEmpty()
    }

    fun convertRecords() {
        val context = applicationContext

        if (context == null || listeners.isEmpty()) {
            return
        }

        converter = converter ?: ConnectionRecordsConverter(context)
        parser = parser ?: ConnectionRecordsParser(context)

        var rawConnections: List<ConnectionRecord?> = emptyList()

        try {
            rawConnections = connectionRecordsRepository.getRawConnectionRecords()
        } catch (e: Exception) {
            Log.e(LOG_TAG, "ConnectionRecordsInteractor getRawConnectionRecords exception ${e.message} ${e.cause}")
        }

        if (rawConnections.isEmpty()) {
            return
        }

        var connectionRecords: List<ConnectionRecord>? = emptyList()

        try {
            connectionRecords = converter?.convertRecords(rawConnections)
        } catch (e: Exception) {
            Log.e(LOG_TAG, "ConnectionRecordsInteractor convertRecords exception ${e.message} ${e.cause}")
        }

        if (connectionRecords?.isEmpty() == true) {
            return
        }

        var records: String? = ""
        try {
            records = parser?.formatLines(connectionRecords ?: emptyList())
        } catch (e: Exception) {
            Log.e(LOG_TAG, "ConnectionRecordsInteractor formatLines exception ${e.message} ${e.cause}")
        }

        if (records.isNullOrBlank()) {
            return
        }

        val listeners = listeners.toHashSet()

        for (listener in listeners) {
            if (listener?.isActive() == true) {
                listener.onConnectionRecordsUpdated(records)
            } else {
                listener?.let { removeListener(it) }

            }
        }

    }

    fun clearConnectionRecords() {
        connectionRecordsRepository.clearConnectionRawRecords()
    }

    fun stopConverter(forceStop: Boolean = false) {
        if (listeners.isEmpty() || forceStop) {
            connectionRecordsRepository.connectionRawRecordsNoMoreRequired()
            converter?.onStop()
            converter = null
            parser = null
        }
    }
}