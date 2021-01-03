package pan.alexander.tordnscrypt.utils

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

import android.util.Log
import pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object CachedExecutor {

    @Volatile private var instance: ExecutorService? = null

    fun getExecutorService(): ExecutorService {

        if (instance == null || instance?.isShutdown == true) {
            synchronized(CachedExecutor::class.java) {
                if (instance == null || instance?.isShutdown == true) {
                    instance = Executors.newCachedThreadPool()
                    Log.i(LOG_TAG, "CachedExecutor is restarted")
                }
            }
        }

        return instance ?: Executors.newCachedThreadPool()
    }

    fun startExecutorService() {
        if (instance == null || instance?.isShutdown == true) {
            synchronized(CachedExecutor::class.java) {
                if (instance == null || instance?.isShutdown == true) {
                    instance = Executors.newCachedThreadPool()
                    Log.i(LOG_TAG, "CachedExecutor is started")
                }
            }
        }
    }

    fun stopExecutorService() {
        Thread {
            if (instance != null && instance?.isShutdown == false) {
                instance?.shutdown()
                try {
                    instance?.awaitTermination(10, TimeUnit.SECONDS)
                } catch (e: InterruptedException) {
                    instance?.shutdownNow()
                    Log.w(LOG_TAG, "CachedExecutor awaitTermination has interrupted " + e.message)
                }
                Log.i(LOG_TAG, "CachedExecutor is stopped")
            }
        }.start()
    }
}