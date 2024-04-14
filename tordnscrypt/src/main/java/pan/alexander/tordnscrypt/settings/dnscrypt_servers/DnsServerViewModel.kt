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

    Copyright 2019-2024 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.settings.dnscrypt_servers

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import pan.alexander.tordnscrypt.di.CoroutinesModule
import pan.alexander.tordnscrypt.utils.logger.Logger.loge
import pan.alexander.tordnscrypt.utils.parsers.DnsCryptConfigurationParser
import javax.inject.Inject
import javax.inject.Named
import kotlin.coroutines.CoroutineContext

class DnsServerViewModel @Inject constructor(
    private val dnsCryptConfigurationParser: DnsCryptConfigurationParser,
    @Named(CoroutinesModule.DISPATCHER_IO)
    private val dispatcherIo: CoroutineDispatcher
) : ViewModel() {

    private val coroutineContext: CoroutineContext =
        SupervisorJob() + dispatcherIo + CoroutineName("DnsServerViewModelCoroutine")

    var searchQuery: String? = ""

    private val dnsCryptConfigurationMutable =
        MutableLiveData<DnsCryptConfigurationResult>(DnsCryptConfigurationResult.Undefined)
    val dnsCryptConfiguration: LiveData<DnsCryptConfigurationResult> get() = dnsCryptConfigurationMutable

    fun getDnsCryptConfigurations() {
        viewModelScope.launch {
            try {

                waitForObserverBecomeActive()

                dnsCryptConfigurationMutable.value = DnsCryptConfigurationResult.Loading

                val dnsCryptProxyToml = executeOnWorkerThread {
                    dnsCryptConfigurationParser.dnsCryptProxyToml
                }
                dnsCryptConfigurationMutable.value =
                    DnsCryptConfigurationResult.DnsCryptProxyToml(dnsCryptProxyToml)

                val dnsCryptServers = executeOnWorkerThread {
                    dnsCryptConfigurationParser.getDnsCryptServers(dnsCryptProxyToml)
                }
                dnsCryptConfigurationMutable.value =
                    DnsCryptConfigurationResult.DnsCryptServers(dnsCryptServers)

                val dnsCryptRoutes = executeOnWorkerThread {
                    dnsCryptConfigurationParser.getDnsCryptRoutes(dnsCryptProxyToml)
                }
                dnsCryptConfigurationMutable.value =
                    DnsCryptConfigurationResult.DnsCryptRoutes(dnsCryptRoutes)

                val publicResolversMd = executeOnWorkerThread {
                    dnsCryptConfigurationParser.publicResolversMd
                }
                val dnsCryptPublicResolvers = executeOnWorkerThread {
                    dnsCryptConfigurationParser.parseDnsCryptResolversMd(publicResolversMd)
                }
                dnsCryptConfigurationMutable.value =
                    DnsCryptConfigurationResult.DnsCryptPublicResolvers(dnsCryptPublicResolvers.toList())

                val ownResolversMd = executeOnWorkerThread {
                    dnsCryptConfigurationParser.ownResolversMd
                }
                val dnsCryptOwnResolvers = executeOnWorkerThread {
                    dnsCryptConfigurationParser.parseDnsCryptResolversMd(ownResolversMd)
                }
                dnsCryptConfigurationMutable.value =
                    DnsCryptConfigurationResult.DnsCryptOwnResolvers(dnsCryptOwnResolvers.toList())
            } catch (e: Exception) {
                loge("DnsServerViewModel dnsCryptConfiguration", e)
            } finally {
                dnsCryptConfigurationMutable.value = DnsCryptConfigurationResult.Finished
                delay(10)
                dnsCryptConfigurationMutable.value = DnsCryptConfigurationResult.Undefined
            }
        }
    }

    private suspend fun <T> executeOnWorkerThread(block: () -> T): T =
        withContext(coroutineContext) {
            block()
        }

    private suspend fun waitForObserverBecomeActive() {
        withTimeout(5000) {
            while (!dnsCryptConfiguration.hasActiveObservers()) {
                delay(100)
            }
        }
    }

    fun saveOwnResolversMd(lines: List<String>) {
        CoroutineScope(coroutineContext).launch {
            dnsCryptConfigurationParser.saveOwnResolversMd(lines)
        }
    }

    fun saveDnsCryptProxyToml(lines: List<String>) {
        CoroutineScope(coroutineContext).launch {
            dnsCryptConfigurationParser.saveDnsCryptProxyToml(lines)
        }
    }

    override fun onCleared() {
        super.onCleared()
        CoroutineScope(coroutineContext).launch {
            delay(3000)
            coroutineContext.cancel()
        }
    }
}
