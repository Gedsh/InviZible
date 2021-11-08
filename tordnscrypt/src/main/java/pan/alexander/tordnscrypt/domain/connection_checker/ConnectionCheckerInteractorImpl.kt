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

package pan.alexander.tordnscrypt.domain.connection_checker

import android.util.Log
import kotlinx.coroutines.*
import pan.alexander.tordnscrypt.di.CoroutinesModule.Companion.SUPERVISOR_JOB_IO_DISPATCHER_SCOPE
import pan.alexander.tordnscrypt.modules.ModulesStatus
import pan.alexander.tordnscrypt.settings.PathVars
import pan.alexander.tordnscrypt.utils.Constants.*
import pan.alexander.tordnscrypt.utils.enums.ModuleState
import pan.alexander.tordnscrypt.utils.root.RootExecService.LOG_TAG
import java.lang.Exception
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

const val INTERNET_CONNECTION_CHECK_INTERVAL_SEC = 10

@Singleton
class ConnectionCheckerInteractorImpl @Inject constructor(
    private val checkerRepository: ConnectionCheckerRepository,
    private val pathVars: PathVars,
    @Named(SUPERVISOR_JOB_IO_DISPATCHER_SCOPE)
    private val baseCoroutineScope: CoroutineScope
) : ConnectionCheckerInteractor {

    private val coroutineScope = baseCoroutineScope + CoroutineName("ConnectionCheckerInteractor")

    private val listenersMap =
        ConcurrentHashMap<String, WeakReference<OnInternetConnectionCheckedListener>>()
    private val modulesStatus = ModulesStatus.getInstance()

    private val checking by lazy { AtomicBoolean(false) }

    @Volatile
    private var internetAvailable = false

    private var networkAvailable = false

    private var task: Job? = null
        @Synchronized get
        @Synchronized set

    override fun <T : OnInternetConnectionCheckedListener> addListener(listener: T) {
        listenersMap[listener.javaClass.name] = WeakReference(listener)
    }

    override fun <T : OnInternetConnectionCheckedListener> removeListener(listener: T) {

        listenersMap.remove(listener.javaClass.name)
        if (listenersMap.isEmpty()) {
            task?.let {
                if (!it.isCompleted) {
                    it.cancel()
                }
            }
            task = null
        }
    }

    override fun getInternetConnectionResult(): Boolean = internetAvailable

    override fun setInternetConnectionResult(internetIsAvailable: Boolean) {
        this.internetAvailable = internetIsAvailable
    }

    override fun checkNetworkConnection() {
        networkAvailable = checkerRepository.checkNetworkAvailable()
    }

    override fun getNetworkConnectionResult(): Boolean = networkAvailable

    override fun checkInternetConnection() {
        if (checking.compareAndSet(false, true)) {
            if (modulesStatus.torState == ModuleState.RUNNING
                || modulesStatus.torState == ModuleState.STARTING
                || modulesStatus.torState == ModuleState.RESTARTING
            ) {
                checkConnection(Via.TOR)
            } else {
                checkConnection(Via.DIRECT)
            }
        }
    }

    private fun checkConnection(via: Via) {

        if (task?.isCompleted == false) {
            task?.cancel()
        }

        task = coroutineScope.launch {
            while (isActive && !internetAvailable) {
                try {
                    check(via)
                } catch (e: Exception) {
                    Log.e(
                        LOG_TAG, "CheckConnectionInteractor checkConnection($via)" +
                                " exception ${e.message} ${e.cause} ${e.stackTrace.joinToString { "," }}"
                    )
                } finally {
                    checking.getAndSet(false)
                    try {
                        delay(INTERNET_CONNECTION_CHECK_INTERVAL_SEC * 1000L)
                    } catch (ignored: Exception){
                    }
                }
            }
        }
    }

    private suspend fun check(via: Via) = coroutineScope {
        val available = when(via) {
            Via.TOR -> {
                checkerRepository.checkInternetAvailableOverHttp(
                    TOR_SITE_ADDRESS,
                    true
                )
            }
            Via.DIRECT -> {
                checkerRepository.checkInternetAvailableOverSocks(
                    pathVars.dnsCryptFallbackRes,
                    PLAINTEXT_DNS_PORT,
                    false
                )
            }
        }

        ensureActive()

        internetAvailable = available

        val iterator = listenersMap.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value.get()?.isActive() == true) {
                entry.value.get()?.onConnectionChecked(available)
            } else {
                iterator.remove()
            }
        }

    }

    private enum class Via {
        TOR,
        DIRECT
    }
}
