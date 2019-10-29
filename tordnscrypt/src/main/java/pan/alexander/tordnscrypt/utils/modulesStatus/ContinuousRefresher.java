package pan.alexander.tordnscrypt.utils.modulesStatus;

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

    Copyright 2019 by Garmatin Oleksandr invizible.soft@gmail.com
*/

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import pan.alexander.tordnscrypt.settings.PathVars;

public class ContinuousRefresher extends TimerTask {
    private static Timer timer;
    private PathVars pathVars;
    private static ModulesStatus modulesStatus;
    private static ContinuousRefresher refresher;
    private static boolean stopRefresher;

    private ContinuousRefresher(PathVars pathVars) {
        this.pathVars = pathVars;
    }

    public static void startRefreshModulesStatus(PathVars pathVars, int periodSec) {
        stopRefresher = false;

        if (refresher == null) {
            refresher = new ContinuousRefresher(pathVars);
        }

        if (modulesStatus == null) {
            modulesStatus = ModulesStatus.getInstance();
        }

        if (timer == null) {
            timer = new Timer();
            timer.schedule(refresher, 1, TimeUnit.SECONDS.toMillis(periodSec));
        }
    }

    private void startTimer() {
        if (modulesStatus != null) {
            modulesStatus.refresh(pathVars);
        }
    }

    public static void stopRefreshModulesStatus() {
        stopRefresher = true;
    }

    private void stopTimer() {
        modulesStatus.closeSHShell();
        if (timer != null && refresher != null) {
            if (refresher.cancel()) {
                timer.cancel();
                timer = null;
                refresher = null;
            }
        }
    }

    @Override
    public void run() {
        if (stopRefresher) {
            stopTimer();
        } else {
            startTimer();
        }
    }
}
