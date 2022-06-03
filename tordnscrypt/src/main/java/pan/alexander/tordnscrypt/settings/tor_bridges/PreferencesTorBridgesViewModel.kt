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

package pan.alexander.tordnscrypt.settings.tor_bridges

import android.graphics.Bitmap
import androidx.lifecycle.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onEach
import pan.alexander.tordnscrypt.domain.bridges.DefaultVanillaBridgeInteractor
import pan.alexander.tordnscrypt.domain.bridges.BridgePingData
import pan.alexander.tordnscrypt.domain.bridges.ParseBridgesResult
import pan.alexander.tordnscrypt.domain.bridges.RequestBridgesInteractor
import pan.alexander.tordnscrypt.utils.logger.Logger.loge
import pan.alexander.tordnscrypt.utils.logger.Logger.logw
import javax.inject.Inject
import kotlin.Exception

@ExperimentalCoroutinesApi
class PreferencesTorBridgesViewModel @Inject constructor(
    private val defaultVanillaBridgeInteractor: DefaultVanillaBridgeInteractor,
    private val requestBridgesInteractor: RequestBridgesInteractor
) : ViewModel() {

    private val timeouts = mutableListOf<BridgePingData>()
    private val timeoutMutableLiveData = MutableLiveData<List<BridgePingData>>()
    private var timeoutsMeasurementJob: Job? = null
    private var timeoutsObserveJob: Job? = null
    val timeoutLiveData: LiveData<List<BridgePingData>> get() = timeoutMutableLiveData


    private val defaultVanillaBridgesMutableLiveData = MutableLiveData<List<String>>()
    val defaultVanillaBridgesLiveData: LiveData<List<String>> get() = defaultVanillaBridgesMutableLiveData
    private var relayBridgesRequestJob: Job? = null

    private var torBridgesRequestJob: Job? = null

    private val dialogsFlowMutableLiveData = MutableLiveData<DialogsFlowState>()
    val dialogsFlowLiveData: LiveData<DialogsFlowState> get() = dialogsFlowMutableLiveData.distinctUntilChanged()

    private val errorsMutableLiveData = MutableLiveData<String>()
    val errorsLiveData: LiveData<String> get() = errorsMutableLiveData

    fun measureTimeouts(bridges: List<ObfsBridge>) {

        timeoutsMeasurementJob?.cancelChildren()
        timeouts.clear()

        if (timeoutsObserveJob?.isCancelled != false) {
            initBridgeCheckerObserver()
        }

        timeoutsMeasurementJob = viewModelScope.launch {
            defaultVanillaBridgeInteractor.measureTimeouts(bridges.map { it.bridge })
        }
    }

    private fun initBridgeCheckerObserver() {
        timeoutsObserveJob = viewModelScope.launch {
            defaultVanillaBridgeInteractor.observeTimeouts()
                .filter { it.ping != 0 }
                .onEach {
                    timeouts.add(it)
                    timeoutMutableLiveData.value = timeouts
                }
                .collect()
        }
    }

    fun requestRelayBridges() {
        relayBridgesRequestJob?.cancel()
        relayBridgesRequestJob = viewModelScope.launch {
            try {
                defaultVanillaBridgesMutableLiveData.value =
                    defaultVanillaBridgeInteractor.requestRelays()
                        .map { "${it.address}:${it.port} ${it.fingerprint}" }
            } catch (ignored: CancellationException) {
            } catch (e: Exception) {
                e.message?.let {
                    errorsMutableLiveData.value = it
                }
                loge("PreferencesTorBridgesViewModel requestRelayBridges", e)
            }
        }
    }

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

    fun requestTorBridgesCaptchaChallenge(transport: String) {

        showPleaseWaitDialog()

        torBridgesRequestJob?.cancel()
        torBridgesRequestJob = viewModelScope.launch {
            try {
                val result = requestBridgesInteractor.requestCaptchaChallenge(transport)
                dismissRequestBridgesDialogs()
                showCaptchaDialog(transport, result.first, result.second)
            } catch (e: CancellationException) {
                logw("PreferencesTorBridgesViewModel requestTorBridgesCaptchaChallenge", e)
            } catch (e: java.util.concurrent.CancellationException) {
                logw("PreferencesTorBridgesViewModel requestTorBridgesCaptchaChallenge", e)
            } catch (e: Exception) {
                e.message?.let { showErrorMessage(it) }
                loge("PreferencesTorBridgesViewModel requestTorBridgesCaptchaChallenge", e)
            }
        }
    }

    private fun showCaptchaDialog(transport: String, captcha: Bitmap, secretCode: String) {
        dialogsFlowMutableLiveData.value =
            DialogsFlowState.CaptchaDialog(transport, captcha, secretCode)
    }

    fun requestTorBridges(transport: String, captchaText: String, secretCode: String) {

        showPleaseWaitDialog()

        torBridgesRequestJob?.cancel()
        torBridgesRequestJob = viewModelScope.launch {
            try {
                val result = requestBridgesInteractor.requestBridges(
                    transport,
                    captchaText,
                    secretCode
                )

                dismissRequestBridgesDialogs()

                when (result) {
                    is ParseBridgesResult.BridgesReady ->
                        showBridgesReadyDialog(result.bridges)
                    is ParseBridgesResult.RecaptchaChallenge ->
                        showCaptchaDialog(transport, result.captcha, result.secretCode)
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

    private fun showBridgesReadyDialog(bridges: String) {
        dialogsFlowMutableLiveData.value = DialogsFlowState.BridgesReadyDialog(bridges)
    }

    private fun showPleaseWaitDialog() {
        dialogsFlowMutableLiveData.value = DialogsFlowState.PleaseWaitDialog
    }

    private fun showErrorMessage(message: String) {
        dialogsFlowMutableLiveData.value = DialogsFlowState.ErrorMessage(message)
    }
}
