package pan.alexander.tordnscrypt.utils.modulesStatus;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;

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

import java.util.List;

import pan.alexander.tordnscrypt.utils.enums.ModuleState;

import static pan.alexander.tordnscrypt.TopFragment.TOP_BROADCAST;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RUNNING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPED;

public final class Status {
    private final String DNSCRYPT_MODULE_REGEX = "pan.alexander.tordnscrypt.*/app_bin/dnscrypt-proxy";
    private final String TOR_MODULE_REGEX = "pan.alexander.tordnscrypt.*/app_bin/tor";
    private final String ITPD_MODULE_REGEX = "pan.alexander.tordnscrypt.*/app_bin/i2pd";

    private ModuleState dnsCryptState = STOPPED;
    private ModuleState torState = STOPPED;
    private ModuleState itpdState = STOPPED;

    private static volatile Status modulesStatus;

    private Status() {
    }

    public static Status getInstance() {
        if (modulesStatus == null) {
            synchronized (Status.class) {
                if (modulesStatus == null) {
                    modulesStatus = new Status();
                }
            }
        }
        return new Status();
    }

    public void refreshViews(Context context) {
        Intent intent = new Intent(TOP_BROADCAST);
        context.sendBroadcast(intent);
    }

    void refresh(final Context context) {
        final ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        final List<ActivityManager.RunningAppProcessInfo> procInfos = activityManager.getRunningAppProcesses();
        if (procInfos != null)
        {
            ModuleState dnsCryptStateLocal = STOPPED;
            ModuleState torStateLocal = STOPPED;
            ModuleState itpdStateLocal = STOPPED;

            for (final ActivityManager.RunningAppProcessInfo processInfo : procInfos) {
                if (processInfo.processName.matches(DNSCRYPT_MODULE_REGEX)) {
                    dnsCryptStateLocal = RUNNING;
                } else if (processInfo.processName.matches(TOR_MODULE_REGEX)) {
                    torStateLocal = RUNNING;
                } else if (processInfo.processName.matches(ITPD_MODULE_REGEX)) {
                    itpdStateLocal = RUNNING;
                }
            }

            dnsCryptState = dnsCryptStateLocal;
            torState = torStateLocal;
            itpdState = itpdStateLocal;
        }
    }

    public ModuleState getDnsCryptState() {
        return dnsCryptState;
    }

    public ModuleState getTorState() {
        return torState;
    }

    public ModuleState getItpdState() {
        return itpdState;
    }
}
