package pan.alexander.tordnscrypt.utils.jobscheduler

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

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.os.Build
import androidx.preference.PreferenceManager
import pan.alexander.tordnscrypt.utils.Constants.DEFAULT_SITES_IPS_REFRESH_INTERVAL
import pan.alexander.tordnscrypt.utils.Constants.SITES_IPS_REFRESH_JOB_ID
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.SITES_IPS_REFRESH_INTERVAL
import pan.alexander.tordnscrypt.utils.web.GetIPsJobService
import pan.alexander.tordnscrypt.utils.web.TorRefreshIPsWork

object JobSchedulerManager {

    @JvmStatic
    fun startRefreshTorUnlockIPs(context: Context) {
        var refreshPeriodHours = DEFAULT_SITES_IPS_REFRESH_INTERVAL
        val refreshPeriod = PreferenceManager.getDefaultSharedPreferences(context).getString(
            SITES_IPS_REFRESH_INTERVAL,
            DEFAULT_SITES_IPS_REFRESH_INTERVAL.toString()
        )

        refreshPeriod?.let {
            refreshPeriodHours = it.toInt()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && refreshPeriodHours > 0) {
            val jobService = ComponentName(context, GetIPsJobService::class.java)
            val getIPsJobBuilder: JobInfo.Builder =
                JobInfo.Builder(SITES_IPS_REFRESH_JOB_ID, jobService)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPeriodic(refreshPeriodHours * 60 * 60 * 1000L)

            val jobScheduler =
                context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            jobScheduler.schedule(getIPsJobBuilder.build())
        } else if (refreshPeriodHours > 0) {
            TorRefreshIPsWork(context, null).refreshIPs()
        }
    }

    @JvmStatic
    fun stopRefreshTorUnlockIPs(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val jobScheduler =
                context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            jobScheduler.cancel(SITES_IPS_REFRESH_JOB_ID)
        }
    }
}
