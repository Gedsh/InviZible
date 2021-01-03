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

    Copyright 2019-2021 by Garmatin Oleksandr invizible.soft@gmail.com
*/

import android.content.Context;
import android.os.Build;

import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.PrefManager;
import pan.alexander.tordnscrypt.utils.enums.OperationMode;

import static pan.alexander.tordnscrypt.utils.enums.OperationMode.PROXY_MODE;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.ROOT_MODE;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.UNDEFINED;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.VPN_MODE;

public class ModulesAux {

    public static void switchModes(Context context, boolean rootIsAvailable, boolean runModulesWithRoot, OperationMode operationMode) {
        ModulesStatus modulesStatus = ModulesStatus.getInstance();

        modulesStatus.setRootAvailable(rootIsAvailable);
        modulesStatus.setUseModulesWithRoot(runModulesWithRoot);

        if (operationMode != UNDEFINED && PathVars.isModulesInstalled(context)) {
            modulesStatus.setMode(operationMode);
        } else if (rootIsAvailable){
            modulesStatus.setMode(ROOT_MODE);
            new PrefManager(context).setStrPref("OPERATION_MODE", ROOT_MODE.toString());
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP){
            modulesStatus.setMode(VPN_MODE);
            new PrefManager(context).setStrPref("OPERATION_MODE", VPN_MODE.toString());
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
        ModulesServiceInteractor.INSTANCE.sendIntent(context, ModulesService.actionUpdateModulesStatus);
    }

    public static void recoverService(Context context) {
        ModulesServiceInteractor.INSTANCE.sendIntent(context, ModulesService.actionRecoverService);
    }

    public static void speedupModulesStateLoopTimer(Context context) {
        ModulesServiceInteractor.INSTANCE.sendIntent(context, ModulesService.speedupLoop);
    }

    public static void slowdownModulesStateLoopTimer(Context context) {
        ModulesServiceInteractor.INSTANCE.sendIntent(context, ModulesService.slowdownLoop);
    }

    public static void makeModulesStateExtraLoop(Context context) {
        ModulesServiceInteractor.INSTANCE.sendIntent(context, ModulesService.extraLoop);
    }

    public static void startArpDetection(Context context) {
        ModulesServiceInteractor.INSTANCE.sendIntent(context, ModulesService.startArpScanner);
    }

    public static void stopArpDetection(Context context) {
        ModulesServiceInteractor.INSTANCE.sendIntent(context, ModulesService.stopArpScanner);
    }
}
