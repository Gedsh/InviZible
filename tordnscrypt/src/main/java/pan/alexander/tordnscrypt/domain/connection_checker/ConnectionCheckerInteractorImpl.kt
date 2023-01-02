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

package pan.alexander.tordnscrypt.domain.connection_checker

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import kotlinx.coroutines.*
import pan.alexander.tordnscrypt.di.CoroutinesModule.Companion.SUPERVISOR_JOB_IO_DISPATCHER_SCOPE
import pan.alexander.tordnscrypt.di.SharedPreferencesModule
import pan.alexander.tordnscrypt.domain.dns_resolver.DnsRepository
import pan.alexander.tordnscrypt.modules.ModulesStatus
import pan.alexander.tordnscrypt.settings.PathVars
import pan.alexander.tordnscrypt.utils.Constants.*
import pan.alexander.tordnscrypt.utils.enums.ModuleState
import pan.alexander.tordnscrypt.utils.enums.OperationMode
import pan.alexander.tordnscrypt.utils.logger.Logger.loge
import pan.alexander.tordnscrypt.utils.logger.Logger.logi
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.*
import pan.alexander.tordnscrypt.vpn.VpnUtils
import java.io.IOException
import java.lang.ref.WeakReference
import java.net.Inet4Address
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

private const val CHECK_INTERVAL_SEC = 10
private const val ADDITIONAL_DELAY_SEC = 30
private const val CHECK_SOCKET_TIMEOUT_SEC = 20
private const val CHECKING_LOOP_TIMEOUT_MINT = 20
private const val CHECKING_TIMEOUT_SEC = 120

@Singleton
class ConnectionCheckerInteractorImpl @Inject constructor(
    private val checkerRepository: ConnectionCheckerRepository,
    private val pathVars: PathVars,
    @Named(SUPERVISOR_JOB_IO_DISPATCHER_SCOPE)
    private val baseCoroutineScope: CoroutineScope,
    private val dnsRepository: DnsRepository,
    @Named(SharedPreferencesModule.DEFAULT_PREFERENCES_NAME)
    private val defaultPreferences: SharedPreferences,
    private val context: Context
) : ConnectionCheckerInteractor {

    private val coroutineScope = baseCoroutineScope + CoroutineName("ConnectionCheckerInteractor")

    private val listenersMap =
        ConcurrentHashMap<String, WeakReference<OnInternetConnectionCheckedListener>>()
    private val modulesStatus = ModulesStatus.getInstance()

    private val checking by lazy { AtomicBoolean(false) }

    @Volatile
    private var internetAvailable = false

    @Volatile
    private var networkAvailable = false

    @Volatile
    private var task: Job? = null

    @Synchronized
    override fun <T : OnInternetConnectionCheckedListener> addListener(listener: T) {
        listenersMap[listener.javaClass.name] = WeakReference(listener)
    }

    @Synchronized
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

    @Synchronized
    private fun checkConnection(via: Via) {

        if (task?.isCompleted == false) {
            task?.cancel()
        }

        task = coroutineScope.launch {
            tryCheckConnection(via)
        }
    }

    private suspend fun tryCheckConnection(via: Via) {
        try {
            withTimeout(CHECKING_LOOP_TIMEOUT_MINT * 60_000L) {
                while (isActive && !internetAvailable) {
                    val available = try {
                        withTimeout(CHECKING_TIMEOUT_SEC * 1000L) {
                            check(via)
                        }
                    } catch (e: SocketTimeoutException) {
                        logException(via, e)
                        false
                    } catch (e: IOException) {
                        logException(via, e)
                        checking.getAndSet(false)
                        makeDelay(ADDITIONAL_DELAY_SEC)
                        false
                    } catch (e: Exception) {
                        logException(via, e)
                        false
                    } finally {
                        checking.compareAndSet(true, false)
                    }

                    ensureActive()

                    logi("Internet is ${if (available) "available" else "not available"}")

                    internetAvailable = available

                    informListeners(available)

                    if (!available) {
                        makeDelay(CHECK_INTERVAL_SEC)
                    }
                }
            }
        } catch (e: Exception) {
            if (e !is CancellationException) {
                loge("ConnectionCheckerInteractor tryCheckConnection", e)
            }
        } finally {
            checking.compareAndSet(true, false)
        }
    }

    private fun informListeners(available: Boolean) {
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

    private suspend fun makeDelay(delaySec: Int) {
        try {
            delay(delaySec * 1000L)
        } catch (ignored: Exception) {
        }
    }

    private fun logException(via: Via, e: Exception) {
        loge("CheckConnectionInteractor checkConnection via $via", e)
    }

    private suspend fun check(via: Via): Boolean = coroutineScope {
        when (via) {
            Via.TOR -> {
                logi("Checking connection via Tor")
                dnsRepository.resolveDomainUDP(
                    TOR_SITE_ADDRESS,
                    pathVars.torDNSPort.toInt(),
                    CHECK_SOCKET_TIMEOUT_SEC
                ).isNotEmpty()
            }
            Via.DIRECT -> {
                val proxyAddress =
                    defaultPreferences.getString(PROXY_ADDRESS, "") ?: ""
                val proxyPort = defaultPreferences.getString(PROXY_PORT, "").let {
                    if (it?.matches(Regex(NUMBER_REGEX)) == true) {
                        it.toInt()
                    } else {
                        1080
                    }
                }
                val useProxy = defaultPreferences.getBoolean(USE_PROXY, false)
                        && proxyAddress.isNotBlank()
                        && proxyPort != 0

                if (useProxy && modulesStatus.mode == OperationMode.VPN_MODE) {
                    val site = sequenceOf(DNS_GOOGLE, DNS_QUAD9, DNS_MOZILLA).shuffled().first()

                    logi("Checking connection via Socks Proxy $proxyAddress:$proxyPort $site")

                    checkerRepository.checkInternetAvailableOverHttp(
                        site,
                        proxyAddress,
                        proxyPort
                    )
                } else {
                    val dnsForConnectivityCheck = getNetworkDns()
                        .plus(pathVars.dnsCryptFallbackRes)
                        .shuffled()
                        .first()

                    logi("Checking connection directly using $dnsForConnectivityCheck")

                    checkerRepository.checkInternetAvailableOverSocks(
                        dnsForConnectivityCheck,
                        PLAINTEXT_DNS_PORT,
                        "",
                        0
                    )
                }

            }
        }

    }

    private fun getNetworkDns(): List<String> = try {
        //Don't get network DNS if SDK_INT < O because jni_getprop("net.dns1") doesn't work well on all phones
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            VpnUtils.getDefaultDNS(context)
                .filter {
                    val dns = InetAddress.getByName(it)
                    !dns.isLoopbackAddress && !dns.isAnyLocalAddress && dns is Inet4Address
                }
        } else {
            emptyList()
        }
    } catch (e: Exception) {
        listOf(pathVars.dnsCryptFallbackRes)
    }

    private enum class Via {
        TOR,
        DIRECT
    }
}
