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

package pan.alexander.tordnscrypt.utils.apps

import kotlinx.coroutines.*
import pan.alexander.tordnscrypt.di.CoroutinesModule
import pan.alexander.tordnscrypt.settings.tor_apps.ApplicationData
import pan.alexander.tordnscrypt.utils.logger.Logger.loge
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

private const val APPS_HANDLING_MAX_TIME_SEC = 60

@Singleton
class InstalledAppNamesStorage @Inject constructor(
    @Named(CoroutinesModule.DISPATCHER_IO)
    dispatcherIo: CoroutineDispatcher
) {

    private val coroutineScope = CoroutineScope(
        SupervisorJob() +
                dispatcherIo +
                CoroutineName("InstalledAppNamesStorage") +
                CoroutineExceptionHandler { _, throwable ->
                    loge("InstalledAppNamesStorage uncaught exception", throwable, true)
                }
    )

    private val inProgress = AtomicBoolean(false)

    private val appUidToNames = ConcurrentHashMap<Int, String>()

    fun getAppNameByUid(uid: Int): String? {
        if (appUidToNames.isEmpty()) {
            updateAppUidToNames()
        }
        return appUidToNames[uid]
    }

    fun updateAppUidToNames(apps: List<ApplicationData>) {
        apps.forEach {
            appUidToNames[it.uid] = it.names.joinToString(", ")
        }
    }

    fun clearAppUidToNames() {
        appUidToNames.clear()
    }

    fun updateAppUidToNames() {
        if (inProgress.compareAndSet(false, true)) {
            coroutineScope.launch {
                withTimeout(APPS_HANDLING_MAX_TIME_SEC * 1000L) {
                    try {
                        InstalledApplicationsManager.Builder()
                            .build()
                            .getInstalledApps()
                    } catch (e: Exception) {
                        loge("InstalledAppNamesStorage updateAppUidToNames", e)
                    } finally {
                        inProgress.getAndSet(false)
                    }
                }
            }
        }
    }

}
