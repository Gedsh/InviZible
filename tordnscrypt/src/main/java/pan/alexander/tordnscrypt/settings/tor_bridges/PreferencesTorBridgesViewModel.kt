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

package pan.alexander.tordnscrypt.settings.tor_bridges

import android.content.SharedPreferences
import android.graphics.Bitmap
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import pan.alexander.tordnscrypt.di.SharedPreferencesModule.Companion.DEFAULT_PREFERENCES_NAME
import pan.alexander.tordnscrypt.domain.bridges.BridgeCountryData
import pan.alexander.tordnscrypt.domain.bridges.BridgePingData
import pan.alexander.tordnscrypt.domain.bridges.BridgePingResult
import pan.alexander.tordnscrypt.domain.bridges.BridgesCountriesInteractor
import pan.alexander.tordnscrypt.domain.bridges.DefaultVanillaBridgeInteractor
import pan.alexander.tordnscrypt.domain.bridges.ParseBridgesResult
import pan.alexander.tordnscrypt.domain.bridges.PingCheckComplete
import pan.alexander.tordnscrypt.domain.bridges.RequestBridgesInteractor
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository
import pan.alexander.tordnscrypt.utils.Utils.prepareFakeSniHosts
import pan.alexander.tordnscrypt.utils.Utils.verifyHostsSet
import pan.alexander.tordnscrypt.utils.enums.BridgeType
import pan.alexander.tordnscrypt.utils.logger.Logger.loge
import pan.alexander.tordnscrypt.utils.logger.Logger.logw
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys
import pan.alexander.tordnscrypt.utils.session.AppSessionStore
import pan.alexander.tordnscrypt.utils.session.SessionKeys.TOR_BRIDGES_IP_WITH_WARNING
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Named

const val TIMEOUT_REPORTED_BY_TOR = -2

@ExperimentalCoroutinesApi
class PreferencesTorBridgesViewModel @Inject constructor(
    @Named(DEFAULT_PREFERENCES_NAME)
    private val defaultPreferences: SharedPreferences,
    private val preferenceRepository: PreferenceRepository,
    private val defaultVanillaBridgeInteractor: DefaultVanillaBridgeInteractor,
    private val requestBridgesInteractor: RequestBridgesInteractor,
    private val bridgesCountriesInteractor: BridgesCountriesInteractor,
    private val bridgePingHelper: BridgePingHelper,
    private val sessionStore: AppSessionStore
) : ViewModel() {

    private val timeouts = mutableListOf<BridgePingResult>()
    private val timeoutMutableLiveData = MutableLiveData<List<BridgePingResult>>()
    private var timeoutsMeasurementJob: Job? = null
    private var timeoutsObserveJob: Job? = null
    val timeoutLiveData: LiveData<List<BridgePingResult>> get() = timeoutMutableLiveData


    private val defaultVanillaBridgesMutableLiveData = MutableLiveData<List<String>>()
    val defaultVanillaBridgesLiveData: LiveData<List<String>> get() = defaultVanillaBridgesMutableLiveData
    private var relayBridgesRequestJob: Job? = null

    private var torBridgesRequestJob: Job? = null

    private val bridgeCountries = mutableListOf<BridgeCountryData>()
    private val bridgeCountriesMutableLiveData = MutableLiveData<List<BridgeCountryData>>()
    val bridgeCountriesLiveData: LiveData<List<BridgeCountryData>> get() = bridgeCountriesMutableLiveData
    private var bridgeCountriesSearchingJob: Job? = null
    private var bridgeCountriesObserveJob: Job? = null

    private val dialogsFlowMutableLiveData = MutableLiveData<DialogsFlowState>()
    val dialogsFlowLiveData: LiveData<DialogsFlowState> get() = dialogsFlowMutableLiveData

    private val errorsMutableLiveData = MutableLiveData<String>()
    val errorsLiveData: LiveData<String> get() = errorsMutableLiveData

    private val bridgesMatcherMap by lazy { ConcurrentHashMap<Int, Int>() }

    fun measureTimeouts(bridges: List<ObfsBridge>) {

        if (bridges.isEmpty()) {
            return
        }

        cancelMeasuringTimeouts()

        if (timeoutsObserveJob?.isCancelled != false) {
            initBridgeCheckerObserver()
        }

        bridgesMatcherMap.clear()

        timeoutsMeasurementJob = viewModelScope.launch {

            val torBridgesToCheckPing = filterAndReportBridgesWithWarning(bridges)
            if (torBridgesToCheckPing.isEmpty()) {
                timeouts.add(PingCheckComplete)
                timeoutMutableLiveData.value = timeouts
                return@launch
            }

            when (torBridgesToCheckPing.firstOrNull()?.obfsType) {
                BridgeType.webtunnel -> handleWebTunnelBridgesTimeout(torBridgesToCheckPing)
                BridgeType.meek_lite -> handleMeekLiteBridgesTimeout(torBridgesToCheckPing)
                BridgeType.snowflake -> handleSnowFlakeBridgesTimeout(torBridgesToCheckPing)
                BridgeType.conjure -> handleConjureBridgesTimeout(torBridgesToCheckPing)
                else -> handleOtherBridgesTimeout(torBridgesToCheckPing)
            }
        }
    }

    private fun filterAndReportBridgesWithWarning(bridges: List<ObfsBridge>): List<ObfsBridge> {

        if (!bridgePingHelper.isConnected()) {
            return bridges
        }

        val torBridgesWithWarning = sessionStore.restoreSet<String>(TOR_BRIDGES_IP_WITH_WARNING)
        val torBridgesToCheckPing = arrayListOf<ObfsBridge>()
        for (bridge in bridges) {
            for (bridgeWithWarn in torBridgesWithWarning) {
                if (bridge.bridge.contains(bridgeWithWarn)) {
                    timeouts.add(BridgePingData(bridge.bridge.hashCode(), TIMEOUT_REPORTED_BY_TOR, true))
                    timeoutMutableLiveData.value = timeouts
                    break
                }
            }
            torBridgesToCheckPing.add(bridge)
        }
        return torBridgesToCheckPing
    }

    private suspend fun handleWebTunnelBridgesTimeout(bridges: List<ObfsBridge>) = coroutineScope {
        val bridgesToMeasure = bridgePingHelper.getRealIPFromWebTunnelBridges(
            ArrayList(bridges),
            bridgesMatcherMap
        ).takeIf { it.isNotEmpty() } ?: bridges.map { it.bridge }
        launch {
            defaultVanillaBridgeInteractor.measureTimeouts(ArrayList(bridgesToMeasure))
        }
        searchBridgeCountries(ArrayList(bridgesToMeasure).map {
            ObfsBridge(
                it,
                BridgeType.vanilla,
                false
            )
        })
    }

    private suspend fun handleMeekLiteBridgesTimeout(bridges: List<ObfsBridge>) = coroutineScope {
        val bridgesToMeasure = bridgePingHelper.getRealIPFromMeekLiteBridges(
            ArrayList(bridges),
            bridgesMatcherMap
        ).takeIf { it.isNotEmpty() } ?: bridges.map { it.bridge }
        launch {
            defaultVanillaBridgeInteractor.measureTimeouts(ArrayList(bridgesToMeasure))
        }
        searchBridgeCountries(ArrayList(bridgesToMeasure).map {
            ObfsBridge(
                it,
                BridgeType.vanilla,
                false
            )
        })
    }

    private suspend fun handleSnowFlakeBridgesTimeout(bridges: List<ObfsBridge>) = coroutineScope {
        val bridgesToMeasure = bridgePingHelper.getRealIPFromSnowFlakeBridges(
            ArrayList(bridges),
            bridgesMatcherMap
        ).takeIf { it.isNotEmpty() } ?: bridges.map { it.bridge }
        launch {
            defaultVanillaBridgeInteractor.measureTimeouts(ArrayList(bridgesToMeasure))
        }
        searchBridgeCountries(ArrayList(bridgesToMeasure).map {
            ObfsBridge(
                it,
                BridgeType.vanilla,
                false
            )
        })
    }

    private suspend fun handleConjureBridgesTimeout(bridges: List<ObfsBridge>) = coroutineScope {
        val bridgesToMeasure = bridgePingHelper.getRealIPFromConjureBridges(
            ArrayList(bridges),
            bridgesMatcherMap
        ).takeIf { it.isNotEmpty() } ?: bridges.map { it.bridge }
        launch {
            defaultVanillaBridgeInteractor.measureTimeouts(ArrayList(bridgesToMeasure))
        }
        searchBridgeCountries(ArrayList(bridgesToMeasure).map {
            ObfsBridge(
                it,
                BridgeType.vanilla,
                false
            )
        })
    }

    private suspend fun handleOtherBridgesTimeout(bridges: List<ObfsBridge>) {
        defaultVanillaBridgeInteractor.measureTimeouts(bridges.map { it.bridge })
    }

    fun cancelMeasuringTimeouts() {
        timeoutsMeasurementJob?.cancelChildren()
        timeouts.clear()
    }

    private fun initBridgeCheckerObserver() {
        timeoutsObserveJob = viewModelScope.launch {
            defaultVanillaBridgeInteractor.observeTimeouts()
                .filter {
                    when (it) {
                        is BridgePingData -> it.ping != 0
                        is PingCheckComplete -> true
                    }
                }.map {
                    if (it is BridgePingData && bridgesMatcherMap.containsKey(it.bridgeHash)) {
                        BridgePingData(bridgesMatcherMap[it.bridgeHash] ?: 0, it.ping)
                    } else {
                        it
                    }
                }.onEach {
                    when (it) {
                        is BridgePingData -> if (!updateBridgesPingWithWarning(it)) timeouts.add(it)
                        else -> timeouts.add(it)
                    }
                    timeoutMutableLiveData.value = timeouts
                }.collect()
        }
    }

    private fun updateBridgesPingWithWarning(currentBridge: BridgePingData): Boolean {
        var found = false
        for (bridge in timeouts) {
            if (bridge is BridgePingData && currentBridge.bridgeHash == bridge.bridgeHash) {
                if (currentBridge.ping > 0) {
                    bridge.ping = currentBridge.ping
                } else {
                    return true
                }
                found = true
            }
        }
        return found
    }

    fun requestRelayBridges(allowIPv6Relays: Boolean, fascistFirewall: Boolean) {
        relayBridgesRequestJob?.cancel()
        relayBridgesRequestJob = viewModelScope.launch {
            try {
                defaultVanillaBridgesMutableLiveData.value =
                    defaultVanillaBridgeInteractor.requestRelays(allowIPv6Relays, fascistFirewall)
                        .map {
                            if (it.address.isIPv6Address()) {
                                "[${it.address}]:${it.port} ${it.fingerprint}"
                            } else {
                                "${it.address}:${it.port} ${it.fingerprint}"
                            }
                        }
            } catch (_: CancellationException) {
            } catch (e: Exception) {
                e.message?.let {
                    errorsMutableLiveData.value = it
                }
                loge("PreferencesTorBridgesViewModel requestRelayBridges", e)
            }
        }
    }

    private fun String.isIPv6Address() = contains(":")

    fun cancelRequestingRelayBridges() {
        relayBridgesRequestJob?.cancel()
    }

    fun dismissRequestBridgesDialogs() {
        dialogsFlowMutableLiveData.value = DialogsFlowState.NoDialogs
    }

    fun cancelTorBridgesRequestJob() {
        torBridgesRequestJob?.cancel()
        dialogsFlowMutableLiveData.value = DialogsFlowState.NoDialogs
    }

    fun showSelectRequestBridgesTypeDialog() {
        dialogsFlowMutableLiveData.value = DialogsFlowState.SelectBridgesTransportDialog
    }

    fun requestTorBridgesCaptchaChallenge(transport: String, ipv6Bridges: Boolean) {

        showPleaseWaitDialog()

        torBridgesRequestJob?.cancel()
        torBridgesRequestJob = viewModelScope.launch {
            try {
                val result =
                    requestBridgesInteractor.requestCaptchaChallenge(transport, ipv6Bridges)
                dismissRequestBridgesDialogs()
                showCaptchaDialog(transport, ipv6Bridges, result.first, result.second)
            } catch (e: CancellationException) {
                logw("PreferencesTorBridgesViewModel requestTorBridgesCaptchaChallenge", e)
            } catch (e: java.util.concurrent.CancellationException) {
                logw("PreferencesTorBridgesViewModel requestTorBridgesCaptchaChallenge", e)
            } catch (_: IllegalStateException) {
                requestTorBridges(transport, ipv6Bridges, "", "")
            } catch (e: Exception) {
                e.message?.let { showErrorMessage(it) }
                loge("PreferencesTorBridgesViewModel requestTorBridgesCaptchaChallenge", e)
            }
        }
    }

    private fun showCaptchaDialog(
        transport: String,
        ipv6Bridges: Boolean,
        captcha: Bitmap,
        secretCode: String
    ) {
        dialogsFlowMutableLiveData.value =
            DialogsFlowState.CaptchaDialog(transport, ipv6Bridges, captcha, secretCode)
    }

    fun requestTorBridges(
        transport: String,
        ipv6Bridges: Boolean,
        captchaText: String,
        secretCode: String
    ) {

        showPleaseWaitDialog()

        torBridgesRequestJob?.cancel()
        torBridgesRequestJob = viewModelScope.launch {
            try {
                val result = requestBridgesInteractor.requestBridges(
                    transport,
                    ipv6Bridges,
                    captchaText,
                    secretCode
                )

                dismissRequestBridgesDialogs()

                when (result) {
                    is ParseBridgesResult.BridgesReady ->
                        showBridgesReadyDialog(result.bridges)

                    is ParseBridgesResult.RecaptchaChallenge ->
                        showCaptchaDialog(transport, ipv6Bridges, result.captcha, result.secretCode)
                }
            } catch (e: CancellationException) {
                logw("PreferencesTorBridgesViewModel requestTorBridges", e)
            } catch (e: java.util.concurrent.CancellationException) {
                logw("PreferencesTorBridgesViewModel requestTorBridges", e)
            } catch (e: Exception) {
                e.message?.let { showErrorMessage(it) }
                loge("PreferencesTorBridgesViewModel requestTorBridges", e)
            }
        }
    }

    fun searchBridgeCountries(bridges: List<ObfsBridge>) {

        if (bridges.isEmpty()) {
            return
        }

        if (bridges.first().obfsType == BridgeType.webtunnel
            || bridges.first().obfsType == BridgeType.meek_lite
            || bridges.first().obfsType == BridgeType.snowflake
        ) {
            return
        }

        cancelSearchingBridgeCountries()

        if (bridgeCountriesObserveJob?.isCancelled != false) {
            initBridgeCountriesObserver()
        }

        bridgeCountriesSearchingJob = viewModelScope.launch {
            bridgesCountriesInteractor.searchBridgeCountries(bridges.map { it.bridge })
        }
    }

    private fun initBridgeCountriesObserver() {
        bridgeCountriesObserveJob = viewModelScope.launch {
            bridgesCountriesInteractor.observeBridgeCountries()
                .map {
                    if (bridgesMatcherMap.containsKey(it.bridgeHash)) {
                        BridgeCountryData(
                            bridgesMatcherMap[it.bridgeHash] ?: 0,
                            it.country
                        )
                    } else {
                        it
                    }
                }.onEach {
                    bridgeCountries.add(it)
                    bridgeCountriesMutableLiveData.value = bridgeCountries
                }.collect()
        }
    }

    fun cancelSearchingBridgeCountries() {
        bridgeCountriesSearchingJob?.cancelChildren()
        bridgeCountries.clear()
    }

    private fun showBridgesReadyDialog(bridges: String) {
        dialogsFlowMutableLiveData.value = DialogsFlowState.BridgesReadyDialog(bridges)
    }

    private fun showPleaseWaitDialog() {
        dialogsFlowMutableLiveData.value = DialogsFlowState.PleaseWaitDialog
    }

    private fun showErrorMessage(message: String) {
        dialogsFlowMutableLiveData.value = DialogsFlowState.ErrorMessage(message)
    }

    fun isFakeSniEnabled() =
        defaultPreferences.getBoolean(PreferenceKeys.FAKE_SNI, false)

    fun getFakeSniHosts(defaultHosts: List<String>, bridgeLength: Int): String {
        val hosts: Set<String> = verifyHostsSet(
            preferenceRepository.getStringSetPreference(PreferenceKeys.FAKE_SNI_HOSTS)
        )
        return prepareFakeSniHosts(hosts, defaultHosts, bridgeLength)
    }
}
