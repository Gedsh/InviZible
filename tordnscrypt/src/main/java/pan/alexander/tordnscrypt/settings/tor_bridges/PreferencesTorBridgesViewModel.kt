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

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onEach
import pan.alexander.tordnscrypt.domain.bridges.BridgeCheckerInteractor
import pan.alexander.tordnscrypt.domain.bridges.BridgePingData
import javax.inject.Inject

@ExperimentalCoroutinesApi
class PreferencesTorBridgesViewModel
@Inject constructor(
    private val bridgeCheckerInteractor: BridgeCheckerInteractor
) : ViewModel() {

    private val timeouts = mutableListOf<BridgePingData>()

    private val timeoutMutableLiveData = MutableLiveData<List<BridgePingData>>()

    private var timeoutsMeasurementJob: Job? = null

    private var timeoutsObserveJob: Job? = null

    val timeoutLiveData: LiveData<List<BridgePingData>> get() = timeoutMutableLiveData

    fun measureTimeouts(bridges: List<ObfsBridge>) {

        timeoutsMeasurementJob?.cancelChildren()
        timeouts.clear()

        if (timeoutsObserveJob?.isCancelled != false) {
            initBridgeCheckerObserver()
        }

        timeoutsMeasurementJob = viewModelScope.launch {
            bridgeCheckerInteractor.measureTimeouts(bridges.map { it.bridge })
        }
    }

    private fun initBridgeCheckerObserver() {
        timeoutsObserveJob = viewModelScope.launch {
            bridgeCheckerInteractor.observeTimeouts()
                .filter { it.ping != 0 }
                .onEach {
                    timeouts.add(it)
                    timeoutMutableLiveData.value = timeouts
                }
                .collect()
        }
    }
}
