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

    Copyright 2019-2022 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.modules

import android.content.Context
import kotlinx.coroutines.*
import pan.alexander.tordnscrypt.di.CoroutinesModule
import pan.alexander.tordnscrypt.domain.connection_checker.ConnectionCheckerInteractor
import pan.alexander.tordnscrypt.settings.PathVars
import pan.alexander.tordnscrypt.utils.enums.ModuleState
import pan.alexander.tordnscrypt.utils.filemanager.FileManager
import pan.alexander.tordnscrypt.utils.logger.Logger.logi
import javax.inject.Inject
import javax.inject.Named

private const val DELAY_BEFORE_RESTART_TOR_SEC = 30

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
    private var counter = 0

    fun startRestarterCounter() {

        if (isCounterRunning() || isCounterLocked()) {
            return
        }

        scope.launch {
            try {

                if (isCounterRunning() || isCounterLocked()) {
                    return@launch
                }

                logi("Start Tor restarter counter")

                while (counter < DELAY_BEFORE_RESTART_TOR_SEC) {
                    counter++
                    delay(1000L)
                }

                if (modulesStatus.torState == ModuleState.RUNNING && modulesStatus.isTorReady
                    && isNetworkAvailable()
                ) {
                    FileManager.deleteDirSynchronous(context, pathVars.appDataDir + "/tor_data")
                    ModulesRestarter.restartTor(context)
                    lockCounter()
                    logi("Restart Tor to re-establish a connection")
                } else {
                    resetCounter()
                    logi("Reset Tor restarter counter")
                }

            } catch (e: CancellationException) {
                resetCounter()
            }
        }
    }

    fun stopRestarterCounter() {
        when {
            counter > 0 -> logi("Stop Tor restarter counter")
            counter < 0 -> logi("Reset Tor restarter counter")
            else -> return
        }

        scope.coroutineContext.cancelChildren()
        resetCounter()
    }

    private fun isCounterRunning() = counter > 0

    private fun isCounterLocked() = counter < 0

    private fun lockCounter() {
        counter = -1
    }

    private fun resetCounter() {
        counter = 0
    }

    private fun isNetworkAvailable() = with(connectionCheckerInteractor.get()) {
        checkNetworkConnection()
        getNetworkConnectionResult()
    }
}
