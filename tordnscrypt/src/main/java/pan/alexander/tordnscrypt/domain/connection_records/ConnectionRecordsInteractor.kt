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

    Copyright 2019-2023 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.domain.connection_records

import android.content.SharedPreferences
import android.util.Log
import pan.alexander.tordnscrypt.App
import pan.alexander.tordnscrypt.di.SharedPreferencesModule
import pan.alexander.tordnscrypt.di.logreader.LogReaderScope
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.CONNECTION_LOGS
import pan.alexander.tordnscrypt.utils.root.RootExecService.LOG_TAG
import java.lang.Exception
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Named

@LogReaderScope
class ConnectionRecordsInteractor @Inject constructor(
    private val connectionRecordsRepository: ConnectionRecordsRepository,
    private val converter: dagger.Lazy<ConnectionRecordsConverter>,
    private var parser: ConnectionRecordsParser,
    @Named(SharedPreferencesModule.DEFAULT_PREFERENCES_NAME)
    private val defaultPreferences: dagger.Lazy<SharedPreferences>
) {
    private val applicationContext = App.instance.applicationContext
    private val listeners: HashMap<Class<*>, WeakReference<OnConnectionRecordsUpdatedListener>> =
        hashMapOf()

    fun <T : OnConnectionRecordsUpdatedListener> addListener(listener: T?) {
        listener?.let { listeners[it.javaClass] = WeakReference(it) }
    }

    fun <T : OnConnectionRecordsUpdatedListener> removeListener(listener: T?) {
        listener?.let { listeners.remove(it.javaClass) }
        //stopConverter()
    }

    fun hasAnyListener(): Boolean {
        return listeners.isNotEmpty()
    }

    fun convertRecords() {
        try {
            convert()
        } catch (e: Exception) {
            Log.e(
                LOG_TAG, "ConnectionRecordsInteractor convertRecords exception " +
                        "${e.message} ${e.cause} ${e.stackTrace.joinToString { "," }}"
            )
        }
    }

    fun clearConnectionRecords() {
        connectionRecordsRepository.clearConnectionRawRecords()
    }

    fun stopConverter(forceStop: Boolean = false) {
        if (listeners.isEmpty() || forceStop) {
            connectionRecordsRepository.connectionRawRecordsNoMoreRequired()
            converter.get().onStop()
        }
    }

    private fun convert() {
        val context = applicationContext

        if (context == null || listeners.isEmpty() || isRealTimeLogsDisabled()) {
            return
        }

        var rawConnections: List<ConnectionRecord?> = emptyList()

        try {
            rawConnections = connectionRecordsRepository.getRawConnectionRecords()
        } catch (e: Exception) {
            Log.e(
                LOG_TAG,
                "ConnectionRecordsInteractor getRawConnectionRecords exception ${e.message} ${e.cause}"
            )
        }

        if (rawConnections.isEmpty()) {
            return
        }

        var connectionRecords: List<ConnectionRecord>? = emptyList()

        try {
            connectionRecords = converter.get().convertRecords(rawConnections)
        } catch (e: Exception) {
            Log.e(
                LOG_TAG,
                "ConnectionRecordsInteractor convertRecords exception ${e.message} ${e.cause}"
            )
        }

        if (connectionRecords?.isEmpty() == true) {
            return
        }

        var records: String? = ""
        try {
            records = parser.formatLines(connectionRecords ?: emptyList())
        } catch (e: Exception) {
            Log.e(
                LOG_TAG,
                "ConnectionRecordsInteractor formatLines exception ${e.message} ${e.cause}"
            )
        }

        if (records.isNullOrBlank()) {
            return
        }

        listeners.forEach { listener ->
            if (listener.value.get()?.isActive() == true) {
                listener.value.get()?.onConnectionRecordsUpdated(records)
            } else {
                removeListener(listener.value.get())

            }
        }
    }

    private fun isRealTimeLogsDisabled() =
        !defaultPreferences.get().getBoolean(CONNECTION_LOGS, true)
}
