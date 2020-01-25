package pan.alexander.tordnscrypt;
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

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;

import android.net.VpnService;
import android.util.Log;

import java.util.Objects;

import pan.alexander.tordnscrypt.modules.ModulesAux;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.settings.PreferencesFastFragment;
import pan.alexander.tordnscrypt.utils.ApManager;
import pan.alexander.tordnscrypt.utils.GetIPsJobService;
import pan.alexander.tordnscrypt.utils.OwnFileReader;
import pan.alexander.tordnscrypt.utils.PrefManager;
import pan.alexander.tordnscrypt.modules.ModulesKiller;
import pan.alexander.tordnscrypt.modules.ModulesRunner;
import pan.alexander.tordnscrypt.utils.enums.OperationMode;
import pan.alexander.tordnscrypt.vpn.service.ServiceVPNHelper;

import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.UNDEFINED;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.VPN_MODE;

public class BootCompleteReceiver extends BroadcastReceiver {
    private final int mJobId = PreferencesFastFragment.mJobId;

    private Context context;
    private String appDataDir;
    private int refreshPeriodHours = 12;

    @Override
    public void onReceive(final Context context, Intent intent) {

        final String BOOT_COMPLETE = "android.intent.action.BOOT_COMPLETED";
        this.context = context.getApplicationContext();

        final PathVars pathVars = new PathVars(context.getApplicationContext());
        appDataDir = pathVars.appDataDir;

        final boolean tethering_autostart;

        SharedPreferences shPref1 = PreferenceManager.getDefaultSharedPreferences(this.context);
        String refreshPeriod = shPref1.getString("pref_fast_site_refresh_interval", "12");
        if (!refreshPeriod.isEmpty()) {
            refreshPeriodHours = Integer.parseInt(refreshPeriod);
        }


        if (Objects.requireNonNull(intent.getAction()).equalsIgnoreCase(BOOT_COMPLETE)) {

            new PrefManager(context).setBoolPref("APisON", false);

            final SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(context);
            tethering_autostart = shPref.getBoolean("pref_common_tethering_autostart", false);

            boolean rootIsAvailable = new PrefManager(context).getBoolPref("rootIsAvailable");
            boolean runModulesWithRoot = shPref.getBoolean("swUseModulesRoot", false);
            String operationMode = new PrefManager(context).getStrPref("OPERATION_MODE");

            OperationMode mode = UNDEFINED;
            if (!operationMode.isEmpty()) {
                mode = OperationMode.valueOf(operationMode);
            }

            ModulesAux.switchModes(context, rootIsAvailable, runModulesWithRoot, mode);

            boolean autoStartDNSCrypt = shPref.getBoolean("swAutostartDNS", false);
            boolean autoStartTor = shPref.getBoolean("swAutostartTor", false);
            boolean autoStartITPD = shPref.getBoolean("swAutostartITPD", false);

            if ((autoStartDNSCrypt || autoStartTor || autoStartITPD) && (mode == VPN_MODE)) {
                final Intent prepareIntent = VpnService.prepare(context);

                if (prepareIntent == null) {
                    shPref.edit().putBoolean("VPNServiceEnabled", true).apply();

                    ServiceVPNHelper.start("Boot complete", context);
                } else {
                    return;
                }
            }

            if (autoStartITPD) {
                shortenTooLongITPDLog();
            }

            if (tethering_autostart) {
                startHOTSPOT();
            }

            if (autoStartDNSCrypt && autoStartTor && autoStartITPD) {

                startStopRestartModules(true, true, true);

                startRefreshTorUnlockIPs(context);

            } else if (autoStartDNSCrypt && autoStartTor) {

                startStopRestartModules(true, true, false);

                startRefreshTorUnlockIPs(context);

            } else if (autoStartDNSCrypt && !autoStartITPD) {

                startStopRestartModules(true, false, false);

                stopRefreshTorUnlockIPs(context);

            } else if (!autoStartDNSCrypt && autoStartTor && !autoStartITPD) {

                startStopRestartModules(false, true, false);

                startRefreshTorUnlockIPs(context);

            } else if (!autoStartDNSCrypt && !autoStartTor && autoStartITPD) {

                startStopRestartModules(false, false, true);

                stopRefreshTorUnlockIPs(context);

            } else if (!autoStartDNSCrypt && autoStartTor) {

                startStopRestartModules(false, true, true);

                startRefreshTorUnlockIPs(context);

            } else if (autoStartDNSCrypt) {

                startStopRestartModules(true, false, true);

                stopRefreshTorUnlockIPs(context);

            }
        }
    }

    private void shortenTooLongITPDLog() {
        OwnFileReader ofr = new OwnFileReader(context, appDataDir + "/logs/i2pd.log");
        ofr.shortenToToLongFile();
    }

    private void startHOTSPOT() {

        new PrefManager(context).setBoolPref("APisON", true);

        try {
            ApManager apManager = new ApManager(context);
            if (!apManager.configApState()) {
                Intent intent_tether = new Intent(Intent.ACTION_MAIN, null);
                intent_tether.addCategory(Intent.CATEGORY_LAUNCHER);
                ComponentName cn = new ComponentName("com.android.settings", "com.android.settings.TetherSettings");
                intent_tether.setComponent(cn);
                intent_tether.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent_tether);
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "BootCompleteReceiver ApManager exception " + e.getMessage() + " " + e.getCause());
        }
    }

    private void startStopRestartModules(boolean autoStartDNSCrypt, boolean autoStartTor, boolean autoStartITPD) {

        saveModulesStateRunning(autoStartDNSCrypt, autoStartTor, autoStartITPD);

        if (autoStartDNSCrypt) {
            runDNSCrypt();
        } else {
            if (isDnsCryptSavedStateRunning()) {
                stopDNSCrypt();
            }
        }

        if (autoStartTor) {
            runTor();
        } else {
            if (isTorSavedStateRunning()) {
                stopTor();
            }
        }

        if (autoStartITPD) {
            runITPD();
        } else {
            if (isITPDSavedStateRunning()) {
                stopITPD();
            }
        }

    }

    private void saveModulesStateRunning(boolean saveDNSCryptRunning, boolean saveTorRunning, boolean saveITPDRunning) {
        new PrefManager(context).setBoolPref("DNSCrypt Running", saveDNSCryptRunning);
        new PrefManager(context).setBoolPref("Tor Running", saveTorRunning);
        new PrefManager(context).setBoolPref("I2PD Running", saveITPDRunning);
    }

    private void runDNSCrypt() {
        ModulesRunner.runDNSCrypt(context);
    }

    private void runTor() {
        ModulesRunner.runTor(context);
    }

    private void runITPD() {
        ModulesRunner.runITPD(context);
    }

    private void stopDNSCrypt() {
        ModulesKiller.stopDNSCrypt(context);
    }

    private void stopTor() {
        ModulesKiller.stopTor(context);
    }

    private void stopITPD() {
        ModulesKiller.stopITPD(context);
    }

    private boolean isDnsCryptSavedStateRunning() {
        return new PrefManager(context).getBoolPref("DNSCrypt Running");
    }

    private boolean isTorSavedStateRunning() {
        return new PrefManager(context).getBoolPref("Tor Running");
    }

    private boolean isITPDSavedStateRunning() {
        return new PrefManager(context).getBoolPref("I2PD Running");
    }

    private void startRefreshTorUnlockIPs(Context context) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.LOLLIPOP || refreshPeriodHours == 0) {
            return;
        }
        ComponentName jobService = new ComponentName(context, GetIPsJobService.class);
        JobInfo.Builder getIPsJobBuilder = new JobInfo.Builder(mJobId, jobService);
        getIPsJobBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
        getIPsJobBuilder.setPeriodic(refreshPeriodHours * 60 * 60 * 1000);

        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);

        if (jobScheduler != null) {
            jobScheduler.schedule(getIPsJobBuilder.build());
        }
    }

    private void stopRefreshTorUnlockIPs(Context context) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.LOLLIPOP) {
            return;
        }
        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (jobScheduler != null) {
            jobScheduler.cancel(mJobId);
        }
    }
}
