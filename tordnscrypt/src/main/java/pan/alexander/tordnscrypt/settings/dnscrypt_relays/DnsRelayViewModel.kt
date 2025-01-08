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

package pan.alexander.tordnscrypt.settings.dnscrypt_relays

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeout
import pan.alexander.tordnscrypt.di.CoroutinesModule
import pan.alexander.tordnscrypt.settings.dnscrypt_relays.PreferencesDNSCryptRelays.RelayType
import pan.alexander.tordnscrypt.utils.logger.Logger.loge
import pan.alexander.tordnscrypt.utils.parsers.DnsCryptConfigurationParser
import javax.inject.Inject
import javax.inject.Named

class DnsRelayViewModel @Inject constructor(
    private val dnsCryptConfigurationParser: DnsCryptConfigurationParser,
    @Named(CoroutinesModule.DISPATCHER_IO)
    private val dispatcherIo: CoroutineDispatcher
) : ViewModel() {

    private val relaysConfigurationMutable =
        MutableLiveData<RelayConfigurationResult>(RelayConfigurationResult.Undefined)
    val relaysConfigurationState: LiveData<RelayConfigurationResult> get() = relaysConfigurationMutable

    fun getRelaysConfiguration(type: RelayType) {
        viewModelScope.launch {
            try {

                relaysConfigurationMutable.value = RelayConfigurationResult.Loading

                val relaysMd = executeOnWorkerThread {
                    when (type) {
                        RelayType.DNSCRYPT_RELAY -> dnsCryptConfigurationParser.dnsCryptRelaysMd
                        RelayType.ODOH_RELAY -> dnsCryptConfigurationParser.odohRelaysMd
                    }
                }

                val relays = executeOnWorkerThread {
                    dnsCryptConfigurationParser.parseDnsCryptRelaysMd(relaysMd)
                }

                waitForObserverBecomeActive()

                relaysConfigurationMutable.value = RelayConfigurationResult.Relays(relays.toList())
            } catch (e: Exception) {
                loge("DnsRelayViewModel getRelaysConfiguration", e)
            } finally {
                relaysConfigurationMutable.value = RelayConfigurationResult.Finished
                delay(10)
                relaysConfigurationMutable.value = RelayConfigurationResult.Undefined
            }
        }
    }

    private suspend fun <T> executeOnWorkerThread(block: () -> T): T =
        runInterruptible(viewModelScope.coroutineContext + dispatcherIo) {
            block()
        }

    private suspend fun waitForObserverBecomeActive() {
        withTimeout(5000) {
            while (!relaysConfigurationState.hasActiveObservers()) {
                delay(100)
            }
        }
    }
}
