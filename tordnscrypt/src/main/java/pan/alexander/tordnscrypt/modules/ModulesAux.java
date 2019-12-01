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
import android.content.SharedPreferences;
import android.os.Build;
import androidx.preference.PreferenceManager;

import pan.alexander.tordnscrypt.utils.PrefManager;
import pan.alexander.tordnscrypt.utils.enums.OperationMode;

import static pan.alexander.tordnscrypt.utils.enums.OperationMode.PROXY_MODE;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.ROOT_MODE;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.UNDEFINED;

public class ModulesAux {

    public static void switchModes(Context context, boolean rootIsAvailable, boolean runModulesWithRoot, OperationMode operationMode) {
        ModulesStatus modulesStatus = ModulesStatus.getInstance();

        modulesStatus.setRootAvailable(rootIsAvailable);
        modulesStatus.setUseModulesWithRoot(runModulesWithRoot);

        if (operationMode != UNDEFINED) {
            modulesStatus.setMode(operationMode);
        } else if (rootIsAvailable){
            modulesStatus.setMode(ROOT_MODE);
            new PrefManager(context).setStrPref("OPERATION_MODE", ROOT_MODE.toString());
        } else {
            modulesStatus.setMode(PROXY_MODE);
            new PrefManager(context).setStrPref("OPERATION_MODE", PROXY_MODE.toString());
        }

    }

    public static void stopModulesIfRunning(Context context) {
        boolean dnsCryptRunning = new PrefManager(context).getBoolPref("DNSCrypt Running");
        boolean torRunning = new PrefManager(context).getBoolPref("Tor Running");
        boolean itpdRunning = new PrefManager(context).getBoolPref("I2PD Running");

        if (dnsCryptRunning) {
            ModulesKiller.stopDNSCrypt(context);
        }

        if (torRunning) {
            ModulesKiller.stopTor(context);
        }

        if (itpdRunning) {
            ModulesKiller.stopITPD(context);
        }
    }

    public static void requestModulesStatusUpdate(Context context) {
        sendIntent(context, ModulesService.actionUpdateModulesStatus);
    }

    private static void sendIntent(Context context, String action) {
        Intent intent = new Intent(context, ModulesService.class);
        intent.setAction(action);
        intent.putExtra("showNotification", isShowNotification(context));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    private static boolean isShowNotification(Context context) {
        SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(context);
        return shPref.getBoolean("swShowNotification", true);
    }
}
