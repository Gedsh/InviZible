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

package pan.alexander.tordnscrypt.domain.log_reader

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class ScheduledExecutor(private val initialDelay: Long, private val period: Long) {
    @Volatile
    private var stopTimer = false

    private val timer: ScheduledExecutorService? = Executors.newScheduledThreadPool(0)

    fun execute(execute: () -> Unit) {
        timer?.scheduleWithFixedDelay({
            if (stopTimer) {
                if (!timer.isShutdown) {
                    timer.shutdown()
                }

                TimeUnit.SECONDS.sleep(5)

                if (!timer.isShutdown) {
                    timer.shutdownNow()
                }
            } else {
                execute()
            }
        }, initialDelay, period, TimeUnit.SECONDS)
    }

    fun stopExecutor() {
        stopTimer = true
    }

    fun isLooping(): Boolean {
        return timer?.isShutdown == false
    }
}