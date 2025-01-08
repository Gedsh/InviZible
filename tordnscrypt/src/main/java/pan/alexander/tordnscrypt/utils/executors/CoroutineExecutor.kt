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

package pan.alexander.tordnscrypt.utils.executors

import kotlinx.coroutines.*
import pan.alexander.tordnscrypt.di.CoroutinesModule
import javax.inject.Inject
import javax.inject.Named

class CoroutineExecutor @Inject constructor(
    @Named(CoroutinesModule.SUPERVISOR_JOB_IO_DISPATCHER_SCOPE_SINGLETON)
    val baseCoroutineScope: CoroutineScope,
    val coroutineExceptionHandler: CoroutineExceptionHandler
) {
    inline fun submit(
        name: String,
        crossinline block: () -> Unit
    ): Job {
        val scope = baseCoroutineScope + CoroutineName(name) + coroutineExceptionHandler
        return scope.launch {
            runInterruptible(coroutineContext) {
                block()
            }
        }
    }

    @JvmOverloads
    inline fun <T> execute(
        maxExecutingTimeMinutes: Int = EXECUTE_TIMEOUT_MINUTES,
        name: String,
        crossinline block: () -> T
    ): Job {
        val scope = baseCoroutineScope + CoroutineName(name) + coroutineExceptionHandler
        return scope.launch {
            if (maxExecutingTimeMinutes == 0) {
                runInterruptible(coroutineContext) {
                    block()
                }
            } else {
                withTimeoutOrNull(maxExecutingTimeMinutes * 60 * 1000L) {
                    runInterruptible(coroutineContext) {
                        block()
                    }
                }
            }
        }
    }

    @JvmOverloads
    inline fun <T> repeat(
        times: Int,
        delaySec: Int,
        maxExecutingTimeMinutes: Int = EXECUTE_TIMEOUT_MINUTES,
        name: String,
        crossinline block: () -> T
    ): Job {
        val scope = baseCoroutineScope + CoroutineName(name) + coroutineExceptionHandler

        return scope.launch {
            var timesCount = 0
            while ((times == 0 || timesCount < times) && isActive) {
                delay(delaySec * 1000L)
                if (maxExecutingTimeMinutes == 0) {
                    runInterruptible(coroutineContext) {
                        block()
                    }
                } else {
                    withTimeoutOrNull(maxExecutingTimeMinutes * 60 * 1000L) {
                        runInterruptible(coroutineContext) {
                            block()
                        }
                    }
                }
                timesCount++
            }
        }
    }

    companion object {
        const val EXECUTE_TIMEOUT_MINUTES = 10
    }

}
