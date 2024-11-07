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

package pan.alexander.tordnscrypt.settings.itpd_settings

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import pan.alexander.tordnscrypt.R
import pan.alexander.tordnscrypt.di.CoroutinesModule
import pan.alexander.tordnscrypt.modules.ModulesAux
import pan.alexander.tordnscrypt.modules.ModulesRestarter
import pan.alexander.tordnscrypt.settings.PathVars
import pan.alexander.tordnscrypt.utils.Constants.URL_REGEX
import pan.alexander.tordnscrypt.utils.logger.Logger.loge
import java.io.File
import javax.inject.Inject
import javax.inject.Named

class ItpdSubscriptionsViewModel @Inject constructor(
    private val pathVars: PathVars,
    @Named(CoroutinesModule.DISPATCHER_IO)
    private val dispatcherIo: CoroutineDispatcher,
    @Named(CoroutinesModule.SUPERVISOR_JOB_IO_DISPATCHER_SCOPE)
    private val baseCoroutineScope: CoroutineScope,
    coroutineExceptionHandler: CoroutineExceptionHandler
) : ViewModel() {

    private val scope: CoroutineScope =
        baseCoroutineScope + CoroutineName("ItpdSubscriptionsViewModelCoroutine") + coroutineExceptionHandler

    private val subscriptionsMutable = MutableLiveData<List<ItpdSubscriptionRecycleItem>?>(null)
    val subscriptions: LiveData<List<ItpdSubscriptionRecycleItem>?> get() = subscriptionsMutable

    fun requestSubscriptions() {
        viewModelScope.launch(dispatcherIo) {
            val itpdConf = getItpdConf()
            var header = ""
            for (line in itpdConf) {
                if (line.startsWith("[") && line.endsWith("]")) {
                    header = line.trim { it == '[' || it == ']' }
                } else if (header == "addressbook" && line.startsWith("subscriptions = ")) {
                    subscriptionsMutable.postValue(
                        line.removePrefix("subscriptions = ")
                            .split(",")
                            .map { ItpdSubscriptionRecycleItem(it.trim()) }
                    )
                    break
                }
            }
        }
    }

    fun saveSubscriptions(context: Context, subscriptions: List<ItpdSubscriptionRecycleItem>) {
        scope.launch {

            val savedSubscriptions = this@ItpdSubscriptionsViewModel.subscriptions.value

            if (subscriptions.size == savedSubscriptions?.size
                && subscriptions.containsAll(savedSubscriptions)
            ) {
                return@launch
            }

            val subscriptionLine = subscriptions.toSet().filter {
                it.text.matches(Regex(URL_REGEX))
            }.takeIf {
                it.isNotEmpty()
            }?.joinToString(", ") {
                it.text
            } ?: context.resources.getStringArray(R.array.default_itpd_subscriptions)
                .joinToString(", ")

            val itpdConf = getItpdConf().toMutableList()
            if (itpdConf.isEmpty()) {
                return@launch
            }

            var header = ""
            for (i in itpdConf.indices) {
                val line = itpdConf[i]
                if (line.startsWith("[") && line.endsWith("]")) {
                    header = line.trim { it == '[' || it == ']' }
                } else if (header == "addressbook" && line.startsWith("subscriptions = ")) {
                    itpdConf[i] = "subscriptions = $subscriptionLine"
                    if (line != itpdConf[i]) {
                        break
                    } else {
                        return@launch
                    }
                }
            }

            saveItpdConf(itpdConf)

            restartItpdIfNeeded(context)
        }
    }

    private fun getItpdConf(): List<String> = try {
        File(pathVars.itpdConfPath).readLines()
    } catch (e: Exception) {
        loge("ItpdSubscriptionsViewModel getItpdConf", e)
        emptyList()
    }

    private fun saveItpdConf(lines: List<String>) = try {
        File(pathVars.itpdConfPath).printWriter().use {
            lines.forEach { line -> it.println(line) }
        }
    } catch (e: Exception) {
        loge("ItpdSubscriptionsViewModel saveItpdConf", e)
    }

    private fun restartItpdIfNeeded(context: Context) {
        val itpdRunning = ModulesAux.isITPDSavedStateRunning()
        if (itpdRunning) {
            ModulesRestarter.restartITPD(context)
        }
    }
}
