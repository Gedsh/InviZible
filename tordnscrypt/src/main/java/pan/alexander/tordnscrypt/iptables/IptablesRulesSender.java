package pan.alexander.tordnscrypt.iptables;

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
import pan.alexander.tordnscrypt.utils.RootCommands;
import pan.alexander.tordnscrypt.utils.RootExecService;

abstract class IptablesRulesSender implements IptablesRules {
    Context context;
    PathVars pathVars;
    String appDataDir;
    String busybox;
    String iptables;
    String ip6tables;
    String rejectAddress;

    boolean runModulesWithRoot;
    Tethering tethering;
    boolean routeAllThroughTor;
    boolean blockHttp;
    boolean apIsOn;
    boolean modemIsOn;

    IptablesRulesSender(Context context) {
        this.context = context;

        pathVars = PathVars.getInstance(context);
        appDataDir = pathVars.getAppDataDir();
        busybox = pathVars.getBusyboxPath();
        iptables = pathVars.getIptablesPath();
        ip6tables = pathVars.getIp6tablesPath();
        rejectAddress = pathVars.getRejectAddress();

        tethering = new Tethering(context);
    }

    @Override
    public void sendToRootExecService(String[] commands) {
        RootCommands rootCommands = new RootCommands(commands);
        Intent intent = new Intent(context, RootExecService.class);
        intent.setAction(RootExecService.RUN_COMMAND);
        intent.putExtra("Commands", rootCommands);
        intent.putExtra("Mark", RootExecService.NullMark);
        RootExecService.performAction(context, intent);
    }
}
