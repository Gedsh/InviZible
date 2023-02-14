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

package pan.alexander.tordnscrypt.utils.executors

import android.util.Log
import pan.alexander.tordnscrypt.utils.root.RootExecService.LOG_TAG
import java.lang.Exception
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CachedExecutor @Inject constructor() {

    val executorService: ExecutorService by lazy { Executors.newCachedThreadPool() }

    @Synchronized
    fun submit(block: Runnable): Future<*>? =
        try {
            executorService.submit(block)
        } catch (e: Exception) {
            Log.e(LOG_TAG, "CachedExecutor ${e.javaClass} ${e.message} ${e.cause}")
            null
        }
}
