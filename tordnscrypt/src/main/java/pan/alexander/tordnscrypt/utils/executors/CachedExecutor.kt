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

package pan.alexander.tordnscrypt.utils.executors

import pan.alexander.tordnscrypt.utils.logger.Logger.loge
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.Exception

@Singleton
class CachedExecutor @Inject constructor() {

    private val executorService: ExecutorService by lazy { Executors.newCachedThreadPool() }

    fun submit(block: Runnable): Future<*>? =
        try {
            executorService.submit(block)
        } catch (e: Exception) {
            loge("CachedExecutor submit", e)
            null
        }

    //For testing purposes
    @Suppress("unused")
    private fun checkTimeout(future: Future<*>) {
        executorService.submit {
            try {
                future.get(2, TimeUnit.MINUTES)
            } catch (e: TimeoutException) {
                loge("CachedExecutor checkTimeout", e)
            } catch (ignored: Exception) {
            }
        }
    }
}
