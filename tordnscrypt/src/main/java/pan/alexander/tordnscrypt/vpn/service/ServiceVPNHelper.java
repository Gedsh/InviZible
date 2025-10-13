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

    Copyright 2019-2025 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.vpn.service;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.locks.ReentrantLock;

import pan.alexander.tordnscrypt.MainActivity;
import pan.alexander.tordnscrypt.modules.ModulesStatus;
import pan.alexander.tordnscrypt.utils.Utils;
import pan.alexander.tordnscrypt.utils.enums.ModuleState;
import pan.alexander.tordnscrypt.utils.enums.OperationMode;
import pan.alexander.tordnscrypt.utils.enums.VPNCommand;

import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STARTING;
import static pan.alexander.tordnscrypt.utils.logger.Logger.loge;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.VPN_SERVICE_ENABLED;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RUNNING;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.ROOT_MODE;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.VPN_MODE;
import static pan.alexander.tordnscrypt.vpn.service.ServiceVPN.EXTRA_COMMAND;
import static pan.alexander.tordnscrypt.vpn.service.ServiceVPN.EXTRA_REASON;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

public class ServiceVPNHelper {

    private static final ReentrantLock reentrantLock = new ReentrantLock();

    public static void start(String reason, Context context) {
        Handler handler = getMainHandler(context);
        if (handler != null) {
            handler.post(() -> startVpnService(reason, context));
        } else {
            startVpnService(reason, context);
        }
    }

    private static void startVpnService(String reason, Context context) {
        Intent intent = new Intent(context, ServiceVPN.class);
        intent.putExtra(EXTRA_COMMAND, VPNCommand.START);
        intent.putExtra(EXTRA_REASON, reason);
        sendIntent(context, intent, true);
    }

    public static void reload(String reason, Context context) {
        Handler handler = getMainHandler(context);
        if (handler != null) {
            handler.post(() -> reloadVpnService(reason, context));
        } else {
            reloadVpnService(reason, context);
        }
    }

    private static void reloadVpnService(String reason, Context context) {
        ModulesStatus modulesStatus = ModulesStatus.getInstance();
        OperationMode operationMode = modulesStatus.getMode();
        ModuleState dnsCryptState = modulesStatus.getDnsCryptState();
        ModuleState torState = modulesStatus.getTorState();
        ModuleState firewallState = modulesStatus.getFirewallState();
        boolean vpnServiceEnabled = isVpnServiceEnabled(context);

        boolean fixTTL = modulesStatus.isFixTTL() && (modulesStatus.getMode() == ROOT_MODE)
                && !modulesStatus.isUseModulesWithRoot();

        if (((operationMode == VPN_MODE) || fixTTL)
                && vpnServiceEnabled
                && (dnsCryptState == RUNNING || torState == RUNNING
                || firewallState == RUNNING || firewallState == STARTING)) {
            Intent intent = new Intent(context, ServiceVPN.class);
            intent.putExtra(EXTRA_COMMAND, VPNCommand.RELOAD);
            intent.putExtra(EXTRA_REASON, reason);
            sendIntent(context, intent, false);
        }
    }

    public static void stop(String reason, Context context) {
        Handler handler = getMainHandler(context);
        if (handler != null) {
            handler.post(() -> stopVpnService(reason, context));
        } else {
            stopVpnService(reason, context);
        }
    }

    private static void stopVpnService(String reason, Context context) {
        boolean vpnServiceEnabled = isVpnServiceEnabled(context);
        if (vpnServiceEnabled) {
            Intent intent = new Intent(context, ServiceVPN.class);
            intent.putExtra(EXTRA_COMMAND, VPNCommand.STOP);
            intent.putExtra(EXTRA_REASON, reason);
            sendIntent(context, intent, false);
        }
    }

    public static void prepareVPNServiceIfRequired(Activity activity, ModulesStatus modulesStatus) {

        Handler handler = getMainHandler(activity);
        if (handler == null || !reentrantLock.tryLock()) {
            return;
        }

        handler.post(() -> {
            try {
                OperationMode operationMode = modulesStatus.getMode();

                boolean fixTTL = modulesStatus.isFixTTL() && (modulesStatus.getMode() == ROOT_MODE)
                        && !modulesStatus.isUseModulesWithRoot();

                if (((operationMode == VPN_MODE) || fixTTL)
                        && activity instanceof MainActivity
                        && !isVpnServiceEnabled(activity)) {
                    ((MainActivity) activity).prepareVPNService();
                }
            } catch (Exception e) {
                loge("ServiceVPNHelper prepareVPNServiceIfRequired", e);
            } finally {
                reentrantLock.unlock();
            }
        });
    }

    private static void sendIntent(Context context, Intent intent, boolean showNotification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && showNotification) {
                intent.putExtra("showNotification", true);
                context.startForegroundService(intent);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                intent.putExtra("showNotification", false);
                context.startService(intent);
            } else {
                intent.putExtra("showNotification", Utils.INSTANCE.isShowNotification(context) && showNotification);
                context.startService(intent);
            }
        } catch (Exception e) {
            loge("ServiceVPNHelper sendIntent", e, true);
        }
    }

    private static boolean isVpnServiceEnabled(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(VPN_SERVICE_ENABLED, false);
    }

    @Nullable
    private static Handler getMainHandler(Context context) {
        Looper looper = context.getMainLooper();
        if (looper != null) {
            return new Handler(looper);
        }
        return null;
    }
}
