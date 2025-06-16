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

package pan.alexander.tordnscrypt.modules

import android.content.Context
import kotlinx.coroutines.*
import pan.alexander.tordnscrypt.di.CoroutinesModule
import pan.alexander.tordnscrypt.domain.connection_checker.ConnectionCheckerInteractor
import pan.alexander.tordnscrypt.settings.PathVars
import pan.alexander.tordnscrypt.utils.enums.ModuleState
import pan.alexander.tordnscrypt.utils.filemanager.FileManager
import pan.alexander.tordnscrypt.utils.logger.Logger.loge
import pan.alexander.tordnscrypt.utils.logger.Logger.logi
import javax.inject.Inject
import javax.inject.Named
import kotlin.math.pow

private const val DELAY_BEFORE_RESTART_TOR_SEC = 10
private const val DELAY_BEFORE_FULL_RESTART_TOR_SEC = 60

@ExperimentalCoroutinesApi
class TorRestarterReconnector @Inject constructor(
    private val context: Context,
    @Named(CoroutinesModule.DISPATCHER_IO)
    dispatcherIo: CoroutineDispatcher,
    private val pathVars: PathVars,
    private val connectionCheckerInteractor: dagger.Lazy<ConnectionCheckerInteractor>
) {

    private val scope by lazy {
        CoroutineScope(
            SupervisorJob() +
                    dispatcherIo.limitedParallelism(1) +
                    CoroutineName("TorRestarterReconnector")
        )
    }

    private val modulesStatus = ModulesStatus.getInstance()

    @Volatile
    private var fullRestartCounter = 0
    @Volatile
    private var partialRestartCounter = 0

    fun startRestarterCounter() {
        try {
            if (modulesStatus.isTorReady && !isFullRestartCounterRunning() && !isFullRestartCounterLocked()) {
                stopRestarterCounters()
                makeTorDelayedFullRestart()
            } else if (!modulesStatus.isTorReady && !isPartialRestartCounterRunning() && !isFullRestartCounterLocked()) {
                stopRestarterCounters()
                makeTorProgressivePartialRestart()
            } else if (!modulesStatus.isTorReady && !isPartialRestartCounterRunning()) {
                cancelPreviousTasks()
                makeTorProgressivePartialRestart()
            }
        } catch (_: CancellationException) {
            resetCounters()
        } catch (e: Exception) {
            loge("TorRestarterReconnector startRestarterCounter", e)
        }
    }

    private fun makeTorProgressivePartialRestart() = scope.launch {
        logi("Start Tor partial restarter counter")
        while (coroutineContext.isActive) {
            if (isNetworkAvailable()) {
                partialRestartCounter++
            } else {
                stopRestarterCounters()
                break
            }
            delay(1000L * 60 * partialRestartCounter.toDouble().pow(2).toLong())// 1, 4, 9, 16, 25, 36 ... minutes
            if (modulesStatus.isTorReady && !isFullRestartCounterLocked()) {
                resetCounters()
                makeTorDelayedFullRestart()
                break
            } else if (isNetworkAvailable()) {
                logi("Reload Tor configuration to re-establish a connection")
                ModulesRestarter.rebootTor(context)
            }
        }
    }

    private fun makeTorDelayedFullRestart() = scope.launch {
        logi("Start Tor full restarter counter")
        while (coroutineContext.isActive && fullRestartCounter < DELAY_BEFORE_FULL_RESTART_TOR_SEC) {
            if (fullRestartCounter == DELAY_BEFORE_RESTART_TOR_SEC
                && modulesStatus.isTorReady
                && isNetworkAvailable()) {
                logi("Reload Tor configuration to re-establish a connection")
                ModulesRestarter.rebootTor(context)
            }
            fullRestartCounter++
            delay(1000L)
        }

        if (modulesStatus.torState == ModuleState.RUNNING
            && modulesStatus.isTorReady
            && isNetworkAvailable()
            && coroutineContext.isActive
        ) {
            deleteTorCachedFiles()
            ModulesRestarter.restartTor(context)
            lockFullRestarterCounter()
            logi("Restart Tor to re-establish a connection")
        } else {
            resetCounters()
            logi("Reset Tor restarter counter")
        }
    }

    private fun deleteTorCachedFiles() {
        //FileManager.deleteFileSynchronous(context, pathVars.appDataDir + "/tor_data", "cached-descriptors")
        //FileManager.deleteFileSynchronous(context, pathVars.appDataDir + "/tor_data", "cached-descriptors.new")
        FileManager.deleteFileSynchronous(context, pathVars.appDataDir + "/tor_data", "cached-microdesc-consensus")
        //FileManager.deleteFileSynchronous(context, pathVars.appDataDir + "/tor_data", "cached-microdescs")
        //FileManager.deleteFileSynchronous(context, pathVars.appDataDir + "/tor_data", "cached-microdescs.new")
    }

    fun stopRestarterCounters() {
        try {
            when {
                partialRestartCounter > 0 -> logi("Stop Tor partial restarter counter")
                partialRestartCounter < 0 -> logi("Reset Tor partial restarter counter")
                fullRestartCounter > 0 -> logi("Stop Tor full restarter counter")
                fullRestartCounter < 0 -> logi("Reset Tor full restarter counter")
                else -> return
            }

            cancelPreviousTasks()
            resetCounters()
        } catch (e: Exception) {
            loge("TorRestarterReconnector stopRestarterCounters", e)
        }
    }

    private fun cancelPreviousTasks() {
        scope.coroutineContext.cancelChildren()
    }

    private fun isPartialRestartCounterRunning() = partialRestartCounter > 0

    private fun isFullRestartCounterRunning() = fullRestartCounter > 0

    private fun isFullRestartCounterLocked() = fullRestartCounter < 0

    private fun lockFullRestarterCounter() {
        fullRestartCounter = -1
    }

    private fun resetCounters() {
        partialRestartCounter = 0
        fullRestartCounter = 0
    }

    private fun isNetworkAvailable() = with(connectionCheckerInteractor.get()) {
        checkNetworkConnection()
        getNetworkConnectionResult()
    }
}
