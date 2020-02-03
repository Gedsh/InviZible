package pan.alexander.tordnscrypt.modules;

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

    Copyright 2019-2020 by Garmatin Oleksandr invizible.soft@gmail.com
*/

import android.content.Context;
import android.content.Intent;

import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.PrefManager;
import pan.alexander.tordnscrypt.utils.RootCommands;
import pan.alexander.tordnscrypt.utils.RootExecService;

class ContextUIDUpdater {
    private Context context;
    private String appDataDir;
    private String busyboxPath;

    ContextUIDUpdater(Context context) {
        this.context = context;
        PathVars pathVars = PathVars.getInstance(context);
        appDataDir = pathVars.getAppDataDir();
        busyboxPath = pathVars.getBusyboxPath();
    }

    void updateModulesContextAndUID() {

        String appUID = new PrefManager(context).getStrPref("appUID");
        String[] commands;
        if (ModulesStatus.getInstance().isUseModulesWithRoot()) {
            commands = new String[]{
                    busyboxPath + "chown -R 0.0 " + appDataDir + "/app_data/dnscrypt-proxy",
                    busyboxPath + "chown -R 0.0 " + appDataDir + "/dnscrypt-proxy.pid",
                    busyboxPath + "chown -R 0.0 " + appDataDir + "/tor_data",
                    busyboxPath + "chown -R 0.0 " + appDataDir + "/tor.pid",
                    busyboxPath + "chown -R 0.0 " + appDataDir + "/i2pd_data",
                    busyboxPath + "chown -R 0.0 " + appDataDir + "/i2pd.pid"
            };
        } else {
            commands = new String[]{
                    busyboxPath + "chown -R " + appUID + "." + appUID + " " + appDataDir + "/app_data/dnscrypt-proxy",
                    busyboxPath + "chown -R " + appUID + "." + appUID + " " + appDataDir + "/dnscrypt-proxy.pid",
                    "restorecon -R " + appDataDir + "/app_data/dnscrypt-proxy",
                    "restorecon -R " + appDataDir + "/dnscrypt-proxy.pid",

                    busyboxPath + "chown -R " + appUID + "." + appUID + " " + appDataDir + "/tor_data",
                    busyboxPath + "chown -R " + appUID + "." + appUID + " " + appDataDir + "/tor.pid",
                    "restorecon -R " + appDataDir + "/tor_data",
                    "restorecon -R " + appDataDir + "/tor.pid",

                    busyboxPath + "chown -R " + appUID + "." + appUID + " " + appDataDir + "/i2pd_data",
                    busyboxPath + "chown -R " + appUID + "." + appUID + " " + appDataDir + "/i2pd.pid",
                    "restorecon -R " + appDataDir + "/i2pd_data",
                    "restorecon -R " + appDataDir + "/i2pd.pid",

                    busyboxPath + "chown -R " + appUID + "." + appUID + " " + appDataDir + "/logs",
                    "restorecon -R " + appDataDir + "/logs"
            };
        }

        RootCommands rootCommands = new RootCommands(commands);
        Intent intent = new Intent(context, RootExecService.class);
        intent.setAction(RootExecService.RUN_COMMAND);
        intent.putExtra("Commands", rootCommands);
        intent.putExtra("Mark", RootExecService.NullMark);
        RootExecService.performAction(context, intent);
    }
}
