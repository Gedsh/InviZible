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

    Copyright 2019 by Garmatin Oleksandr invizible.soft@gmail.com
*/

import android.content.Context;
import android.content.Intent;

import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.PrefManager;
import pan.alexander.tordnscrypt.utils.RootCommands;
import pan.alexander.tordnscrypt.utils.RootExecService;

class ContextUIDUpdater {
    private Context context;
    private PathVars pathVars;

    ContextUIDUpdater(Context context) {
        this.context = context;
        this.pathVars = new PathVars(context);
    }

    void updateModulesContextAndUID() {

        String appUID = new PrefManager(context).getStrPref("appUID");
        String[] commands;
        if (ModulesStatus.getInstance().isUseModulesWithRoot()) {
            commands = new String[]{
                    pathVars.busyboxPath + "chown -R 0.0 " + pathVars.appDataDir + "/app_data/dnscrypt-proxy",
                    pathVars.busyboxPath + "chown -R 0.0 " + pathVars.appDataDir + "/dnscrypt-proxy.pid",
                    pathVars.busyboxPath + "chown -R 0.0 " + pathVars.appDataDir + "/tor_data",
                    pathVars.busyboxPath + "chown -R 0.0 " + pathVars.appDataDir + "/tor.pid",
                    pathVars.busyboxPath + "chown -R 0.0 " + pathVars.appDataDir + "/i2pd_data",
                    pathVars.busyboxPath + "chown -R 0.0 " + pathVars.appDataDir + "/i2pd.pid"
            };
        } else {
            commands = new String[]{
                    pathVars.busyboxPath + "chown -R " + appUID + "." + appUID + " " + pathVars.appDataDir + "/app_data/dnscrypt-proxy",
                    pathVars.busyboxPath + "chown -R " + appUID + "." + appUID + " " + pathVars.appDataDir + "/dnscrypt-proxy.pid",
                    "restorecon -R " + pathVars.appDataDir + "/app_data/dnscrypt-proxy",
                    "restorecon -R " + pathVars.appDataDir + "/dnscrypt-proxy.pid",

                    pathVars.busyboxPath + "chown -R " + appUID + "." + appUID + " " + pathVars.appDataDir + "/tor_data",
                    pathVars.busyboxPath + "chown -R " + appUID + "." + appUID + " " + pathVars.appDataDir + "/tor.pid",
                    "restorecon -R " + pathVars.appDataDir + "/tor_data",
                    "restorecon -R " + pathVars.appDataDir + "/tor.pid",

                    pathVars.busyboxPath + "chown -R " + appUID + "." + appUID + " " + pathVars.appDataDir + "/i2pd_data",
                    pathVars.busyboxPath + "chown -R " + appUID + "." + appUID + " " + pathVars.appDataDir + "/i2pd.pid",
                    "restorecon -R " + pathVars.appDataDir + "/i2pd_data",
                    "restorecon -R " + pathVars.appDataDir + "/i2pd.pid",

                    pathVars.busyboxPath + "chown -R " + appUID + "." + appUID + " " + pathVars.appDataDir + "/logs",
                    "restorecon -R " + pathVars.appDataDir + "/logs"
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
