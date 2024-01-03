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

    Copyright 2019-2024 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.iptables;

import android.content.Context;
import android.content.IntentFilter;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.List;

import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.root.RootCommands;

import static pan.alexander.tordnscrypt.utils.root.RootCommandsMark.IPTABLES_MARK;
import static pan.alexander.tordnscrypt.utils.root.RootExecService.COMMAND_RESULT;

abstract class IptablesRulesSender implements IptablesRules {
    private static boolean receiverIsRegistered;

    Context context;
    PathVars pathVars;
    String appDataDir;
    String rejectAddress;

    boolean runModulesWithRoot;
    Tethering tethering;
    IptablesReceiver receiver;
    boolean routeAllThroughTor;
    boolean blockHttp;
    boolean ignoreSystemDNS;
    boolean apIsOn;
    boolean modemIsOn;
    boolean lan;

    IptablesRulesSender(Context context, PathVars pathVars) {
        this.context = context;

        this.pathVars = pathVars;
        appDataDir = pathVars.getAppDataDir();
        rejectAddress = pathVars.getRejectAddress();

        tethering = new Tethering(context);

        registerReceiver();
    }

    private void registerReceiver() {

        if (receiverIsRegistered) {
            return;
        }

        receiverIsRegistered = true;

        receiver = new IptablesReceiver();

        IntentFilter intentFilterBckgIntSer = new IntentFilter(COMMAND_RESULT);
        LocalBroadcastManager.getInstance(context).registerReceiver(receiver, intentFilterBckgIntSer);
    }

    @Override
    public void unregisterReceiver() {
        if (receiver != null && receiverIsRegistered) {
            receiverIsRegistered = false;
            try {
                LocalBroadcastManager.getInstance(context).unregisterReceiver(receiver);
            } catch (Exception ignored){}
        }
    }


    @Override
    public boolean isLastIptablesCommandsReturnError() {
        if (receiver == null) {
            return false;
        } else {
            return receiver.getLastIptablesCommandsReturnError();
        }
    }

    @Override
    public void sendToRootExecService(List<String> commands) {
        RootCommands.execute(context, commands, IPTABLES_MARK);
    }
}
