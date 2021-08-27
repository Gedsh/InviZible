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
import pan.alexander.tordnscrypt.data.connection_checker.CheckInternetConnectionRepositoryImpl
import pan.alexander.tordnscrypt.utils.executors.CachedExecutor
import pan.alexander.tordnscrypt.utils.root.RootExecService.LOG_TAG
import java.lang.Exception
import java.util.concurrent.Future

class CheckConnectionInteractor {
    @Volatile
    private var listener: OnInternetConnectionCheckedListener? = null
    private val checkConnectionRepository = CheckInternetConnectionRepositoryImpl()

    @Volatile
    private var task: Future<*>? = null

    fun setListener(listener: OnInternetConnectionCheckedListener?) {
        if (listener != this.listener && task?.isDone == false) {
            task?.cancel(true)
        }
        this.listener = listener
    }

    fun removeListener() {
        task?.let {
            if (!it.isDone) {
                it.cancel(true)
            }
        }
        task = null
        this.listener = null
    }

    @Synchronized
    fun checkConnection(site: String, withTor: Boolean) {
        if (isChecking()) {
            return
        }

        task = CachedExecutor.getExecutorService().submit {
            try {
                check(site, withTor)
            } catch (e: Exception) {
                Log.e(
                    LOG_TAG, "CheckConnectionInteractor checkConnection($site, $withTor)" +
                            " exception ${e.message} ${e.cause} ${e.stackTrace.joinToString { "," }}"
                )
            }
        }
    }

    fun isChecking(): Boolean {
        return task?.isDone == false
    }

    private fun check(site: String, withTor: Boolean) {
        val available = checkConnectionRepository.checkInternetAvailable(site, withTor)
        if (listener?.isActive() == true) {
            listener?.onConnectionChecked(available)
        }
    }
}
