package pan.alexander.tordnscrypt.utils.web;
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

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.os.Build;
import androidx.annotation.RequiresApi;

import pan.alexander.tordnscrypt.utils.web.TorRefreshIPsWork;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class GetIPsJobService extends JobService {

    private JobParameters params;

    public GetIPsJobService() {
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        this.params = params;

        TorRefreshIPsWork torRefreshIPsWork = new TorRefreshIPsWork(getApplicationContext(),this);
        torRefreshIPsWork.refreshIPs();

        return true;
    }

    public void finishJob() {
        jobFinished(params,false);
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }

}
