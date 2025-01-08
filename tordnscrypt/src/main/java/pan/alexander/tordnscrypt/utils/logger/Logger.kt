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

package pan.alexander.tordnscrypt.utils.logger

import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

private const val LOG_TAG = "pan.alexander.TPDCLogs"

object Logger {

    private val coroutineScope by lazy {
        CoroutineScope(
            SupervisorJob() +
                    Dispatchers.IO +
                    CoroutineName("Logger") +
                    CoroutineExceptionHandler { _, throwable ->
                        Log.e(LOG_TAG, "Logger uncaught exception", throwable)
                    }
        )
    }

    private val logFlow by lazy {
        MutableSharedFlow<LogEntry>(
            replay = 0,
            extraBufferCapacity = 100,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        ).also { flow ->
            flow.distinctUntilChanged()
                .onEach {
                    when (it.level) {
                        LogLevel.INFO -> Log.i(LOG_TAG, it.message)
                        LogLevel.WARN -> Log.w(LOG_TAG, it.message)
                        LogLevel.ERROR -> Log.e(LOG_TAG, it.message)
                    }
                }.launchIn(coroutineScope)
        }
    }

    @JvmStatic
    fun logi(message: String) {
        logFlow.tryEmit(LogEntry(LogLevel.INFO, message))
    }

    @JvmStatic
    fun logw(message: String) {
        logFlow.tryEmit(LogEntry(LogLevel.WARN, message))
    }

    @JvmStatic
    fun logw(message: String, e: Throwable) {
        logFlow.tryEmit(
            LogEntry(
                LogLevel.WARN,
                "$message ${e.javaClass.canonicalName} ${e.message} ${e.cause ?: ""}"
            )
        )
    }

    @JvmStatic
    fun loge(message: String, e: Throwable) {
        logFlow.tryEmit(
            LogEntry(
                LogLevel.ERROR,
                "$message ${e.javaClass.canonicalName} ${e.message} ${e.cause ?: ""}"
            )
        )
    }

    @JvmStatic
    fun loge(message: String, e: Throwable, printStackTrace: Boolean) {
        logFlow.tryEmit(
            LogEntry(
                LogLevel.ERROR,
                "$message ${e.javaClass.canonicalName} ${e.message} ${e.cause ?: ""}" +
                        if (printStackTrace) "\n" + Log.getStackTraceString(e) else ""
            )
        )
    }

    @JvmStatic
    fun loge(message: String) {
        logFlow.tryEmit(LogEntry(LogLevel.ERROR, message))
    }

    private data class LogEntry(
        val level: LogLevel,
        val message: String
    )

    private enum class LogLevel {
        INFO,
        WARN,
        ERROR,
    }
}
