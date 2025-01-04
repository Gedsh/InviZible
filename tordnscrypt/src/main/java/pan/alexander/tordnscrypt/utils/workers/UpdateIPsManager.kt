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

package pan.alexander.tordnscrypt.utils.workers

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest.Companion.DEFAULT_BACKOFF_DELAY_MILLIS
import pan.alexander.tordnscrypt.utils.Constants.DEFAULT_SITES_IPS_REFRESH_INTERVAL
import pan.alexander.tordnscrypt.utils.logger.Logger.loge
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.SITES_IPS_REFRESH_INTERVAL
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val REFRESH_IPS_WORK = "pan.alexander.tordnscrypt.REFRESH_IPS_WORK"
private const val REFRESH_INITIAL_DELAY_MINUTES = 1L

@Singleton
class UpdateIPsManager @Inject constructor(
    private val context: Context
) {
    fun startRefreshTorUnlockIPs() {

        val interval = getInterval()
        if (interval == 0L) {
            return
        }

        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val updateRequest = PeriodicWorkRequestBuilder<UpdateIPsWorker>(interval, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setInitialDelay(REFRESH_INITIAL_DELAY_MINUTES, TimeUnit.MINUTES)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                DEFAULT_BACKOFF_DELAY_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                REFRESH_IPS_WORK,
                ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
                updateRequest
            )
    }

    fun stopRefreshTorUnlockIPs() {

        val interval = getInterval()
        if (interval == 0L) {
            return
        }

        WorkManager.getInstance(context)
            .cancelUniqueWork(REFRESH_IPS_WORK)
    }

    private fun getInterval(): Long = try {
        val refreshPeriod = PreferenceManager.getDefaultSharedPreferences(context).getString(
            SITES_IPS_REFRESH_INTERVAL,
            DEFAULT_SITES_IPS_REFRESH_INTERVAL.toString()
        )

        refreshPeriod?.toLong() ?: DEFAULT_SITES_IPS_REFRESH_INTERVAL.toLong()
    } catch (e: Exception) {
        loge("UpdateIPsManager getInterval", e)
        DEFAULT_SITES_IPS_REFRESH_INTERVAL.toLong()
    }
}
