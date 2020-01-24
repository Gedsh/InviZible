package pan.alexander.tordnscrypt.dnscrypt;

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

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.fragment.app.FragmentManager;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

import pan.alexander.tordnscrypt.MainActivity;
import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.dialogs.NotificationHelper;
import pan.alexander.tordnscrypt.modules.ModulesAux;
import pan.alexander.tordnscrypt.modules.ModulesKiller;
import pan.alexander.tordnscrypt.modules.ModulesRunner;
import pan.alexander.tordnscrypt.modules.ModulesStatus;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.OwnFileReader;
import pan.alexander.tordnscrypt.utils.PrefManager;
import pan.alexander.tordnscrypt.utils.enums.ModuleState;
import pan.alexander.tordnscrypt.vpn.ResourceRecord;
import pan.alexander.tordnscrypt.vpn.service.ServiceVPN;
import pan.alexander.tordnscrypt.vpn.service.ServiceVPNHelper;

import static pan.alexander.tordnscrypt.TopFragment.DNSCryptVersion;
import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RESTARTING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RUNNING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STARTING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPED;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPING;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.ROOT_MODE;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.VPN_MODE;

class DNSCryptFragmentPresenter implements DNSCryptFragmentPresenterCallbacks {
    private DNSCryptFragmentView view;
    private BroadcastReceiver br = null;
    private Timer timer = null;
    private String appDataDir;
    private boolean routeAllThroughTor = true;

    private OwnFileReader logFile;

    private ModulesStatus modulesStatus;
    private ModuleState fixedModuleState;
    private int displayLogPeriod = -1;

    private ServiceConnection serviceConnection;
    private ServiceVPN serviceVPN;
    private boolean bound;
    private ArrayList<ResourceRecord> savedResourceRecords;

    DNSCryptFragmentPresenter(DNSCryptFragmentView view) {
        this.view = view;
    }

    void onStart(Context context) {
        if (context == null || view == null) {
            return;
        }

        PathVars pathVars = new PathVars(context);
        appDataDir = pathVars.appDataDir;

        modulesStatus = ModulesStatus.getInstance();


        SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(context);
        routeAllThroughTor = shPref.getBoolean("pref_fast_all_through_tor", true);

        savedResourceRecords = new ArrayList<>();

        logFile = new OwnFileReader(context, appDataDir + "/logs/DnsCrypt.log");

        if (isDNSCryptInstalled(context)) {
            view.setDNSCryptInstalled(true);

            if (modulesStatus.getDnsCryptState() == STOPPING){
                view.setDnsCryptStopping();

                if (logFile != null) {
                    view.setLogViewText(Html.fromHtml(logFile.readLastLines()));
                }

                displayLog(1000);
            } else if (isSavedDNSStatusRunning(context) || modulesStatus.getDnsCryptState() == RUNNING) {
                view.setDnsCryptRunning();

                if (logFile != null) {
                    view.setLogViewText(Html.fromHtml(logFile.readLastLines()));
                }

                if (modulesStatus.getDnsCryptState() != RESTARTING) {
                    modulesStatus.setDnsCryptState(RUNNING);
                }

                displayLog(1000);

            } else {
                view.setDnsCryptStopped();
                modulesStatus.setDnsCryptState(STOPPED);
            }

        } else {
            view.setDNSCryptInstalled(false);
        }
    }

    void onDestroy(Context context) {
        view = null;

        try {
            unbindVPNService(context);
            stopDisplayLog();
            if (br != null) Objects.requireNonNull(context).unregisterReceiver(br);
        } catch (Exception e) {
            Log.e(LOG_TAG, "DNSCryptRunFragment onDestroy exception " + e.getMessage() + " " + e.getCause());
        }
    }

    @Override
    public boolean isDNSCryptInstalled(Context context) {
        return new PrefManager(context).getBoolPref("DNSCrypt Installed");
    }

    @Override
    public boolean isSavedDNSStatusRunning(Context context) {
        return new PrefManager(context).getBoolPref("DNSCrypt Running");
    }

    @Override
    public void saveDNSStatusRunning(Context context, boolean running) {
        new PrefManager(context).setBoolPref("DNSCrypt Running", running);
    }

    @Override
    public void displayLog(int period) {

        if (period == displayLogPeriod) {
            return;
        }

        displayLogPeriod = period;

        if (timer != null) {
            timer.purge();
            timer.cancel();
        }

        timer = new Timer();

        timer.schedule(new TimerTask() {
            int loop = 0;
            String previousLastLines = "";

            @Override
            public void run() {
                if (view.getFragmentActivity() == null) {
                    return;
                }

                final String lastLines = logFile.readLastLines();

                if (++loop > 120) {
                    loop = 0;
                    displayLog(10000);
                }

                displayDnsResponses(lastLines);

                view.getFragmentActivity().runOnUiThread(() -> {

                    if (view.getFragmentActivity() == null) {
                        return;
                    }

                    if (!previousLastLines.contentEquals(lastLines)) {

                        dnsCryptStartedSuccessfully(lastLines);

                        dnsCryptStartedWithError(view.getFragmentActivity(), lastLines);

                        if (!previousLastLines.isEmpty()) {
                            view.setLogViewText(Html.fromHtml(lastLines));
                        }

                        previousLastLines = lastLines;
                    }

                    refreshDNSCryptState(view.getFragmentActivity());

                });

            }
        }, 1, period);

    }

    @Override
    public void stopDisplayLog() {
        if (timer != null) {
            timer.purge();
            timer.cancel();
            timer = null;

            displayLogPeriod = -1;
        }
    }

    private void dnsCryptStartedSuccessfully(String lines) {

        if (view == null) {
            return;
        }

        if ((modulesStatus.getDnsCryptState() == STARTING
                || modulesStatus.getDnsCryptState() == RUNNING)
                && lines.contains("lowest initial latency")) {

            if (!modulesStatus.isUseModulesWithRoot()) {
                view.setProgressBarIndeterminate(false);
            }

            view.setDnsCryptRunning();
        }
    }

    private void dnsCryptStartedWithError(Context context, String lastLines) {

        if (context == null || view == null) {
            return;
        }

        FragmentManager fragmentManager = view.getDNSCryptFragmentManager();

        if ((lastLines.contains("connect: connection refused")
                || lastLines.contains("ERROR"))
                && !lastLines.contains(" OK ")) {
            Log.e(LOG_TAG, "DNSCrypt Error: " + lastLines);

            if (fragmentManager != null) {
                NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                        context, context.getText(R.string.helper_dnscrypt_no_internet).toString(), "helper_dnscrypt_no_internet");
                if (notificationHelper != null) {
                    notificationHelper.show(fragmentManager, NotificationHelper.TAG_HELPER);
                }
            }

        } else if (lastLines.contains("[CRITICAL]") && lastLines.contains("[FATAL]")) {

            if (fragmentManager != null) {
                NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                        context, context.getText(R.string.helper_dnscrypt_no_internet).toString(), "helper_dnscrypt_no_internet");
                if (notificationHelper != null) {
                    notificationHelper.show(fragmentManager, NotificationHelper.TAG_HELPER);
                }
            }

            Log.e(LOG_TAG, "DNSCrypt FATAL Error: " + lastLines);

            stopDNSCrypt(context);
        }
    }

    private void displayDnsResponses(String savedLines) {
        if (view == null) {
            return;
        }

        if (modulesStatus.getMode() != VPN_MODE) {
            if (!savedResourceRecords.isEmpty() && view.getFragmentActivity() != null) {
                savedResourceRecords.clear();
                view.getFragmentActivity().runOnUiThread(() -> {
                    if (view.getFragmentActivity() != null) {
                        view.setLogViewText(Html.fromHtml(logFile.readLastLines()));
                    }
                });
            }
            return;
        } else if (view.getFragmentActivity() != null && modulesStatus.getMode() == VPN_MODE && !bound) {
            bindToVPNService(view.getFragmentActivity());
        }

        ArrayList<ResourceRecord> resourceRecords = new ArrayList<>(getResourceRecords());

        if (resourceRecords.equals(savedResourceRecords) || resourceRecords.isEmpty()) {
            return;
        }

        savedResourceRecords = resourceRecords;

        ResourceRecord rr;
        StringBuilder lines = new StringBuilder();

        lines.append(savedLines);

        lines.append("<br />");

        for (int i = 0; i < savedResourceRecords.size(); i++) {
            rr = savedResourceRecords.get(i);

            if (rr.Resource.equals("0.0.0.0") || rr.Resource.equals("127.0.0.1") || rr.HInfo.contains("dnscrypt") || rr.Rcode != 0) {
                if (!rr.AName.isEmpty()) {
                    lines.append("<font color=#f08080>").append(rr.AName);

                    if (rr.HInfo.contains("block_ipv6")) {
                        lines.append(" ipv6");
                    }

                    lines.append("</font>");
                } else {
                    lines.append("<font color=#f08080>").append(rr.QName).append("</font>");
                }
            } else {
                lines.append("<font color=#0f7f7f>").append(rr.AName).append("</font>");
            }

            if (i < savedResourceRecords.size() - 1) {
                lines.append("<br />");
            }
        }

        if (view.getFragmentActivity() != null) {
            view.getFragmentActivity().runOnUiThread(() -> {
                if (view.getFragmentActivity() != null) {
                    view.setLogViewText(Html.fromHtml(lines.toString()));
                }
            });
        }
    }

    private LinkedList<ResourceRecord> getResourceRecords() {
        if (serviceVPN != null) {
            return serviceVPN.getResourceRecords();
        }
        return new LinkedList<>();
    }

    @Override
    public void refreshDNSCryptState(Context context) {

        if (modulesStatus == null || view == null) {
            return;
        }

        ModuleState currentModuleState = modulesStatus.getDnsCryptState();

        if (currentModuleState.equals(fixedModuleState) && currentModuleState != STOPPED) {
            return;
        }

        if (currentModuleState == STARTING) {

            displayLog(1000);

        } else if (currentModuleState == RUNNING) {

            ServiceVPNHelper.prepareVPNServiceIfRequired(view.getFragmentActivity(), modulesStatus);

            view.setStartButtonEnabled(true);

            saveDNSStatusRunning(context, true);

            view.setStartButtonText(R.string.btnDNSCryptStop);

            displayLog(5000);

            if (modulesStatus.getMode() == VPN_MODE && !bound) {
                bindToVPNService(view.getFragmentActivity());
            }

        } else if (currentModuleState == STOPPED) {

            stopDisplayLog();

            if (isSavedDNSStatusRunning(view.getFragmentActivity())) {
                setDNSCryptStoppedBySystem(context);
            } else {
                view.setDnsCryptStopped();
            }

            view.setProgressBarIndeterminate(false);

            saveDNSStatusRunning(context, false);

            view.setStartButtonEnabled(true);
        }

        fixedModuleState = currentModuleState;
    }

    private void setDNSCryptStoppedBySystem(Context context) {
        if (view == null) {
            return;
        }

        view.setDnsCryptStopped();

        FragmentManager fragmentManager = view.getDNSCryptFragmentManager();

        if (context!= null) {

            modulesStatus.setDnsCryptState(STOPPED);

            ModulesAux.requestModulesStatusUpdate(context);

            if (fragmentManager != null) {
                NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                        context, context.getText(R.string.helper_dnscrypt_stopped).toString(), "dnscrypt_suddenly_stopped");
                if (notificationHelper != null) {
                    notificationHelper.show(fragmentManager, NotificationHelper.TAG_HELPER);
                }
            }

            Log.e(LOG_TAG, context.getText(R.string.helper_dnscrypt_stopped).toString());
        }

    }

    private void runDNSCrypt(Context context) {
        if (context == null) {
            return;
        }

        ModulesRunner.runDNSCrypt(context);
    }

    private void stopDNSCrypt(Context context) {
        if (context == null) {
            return;
        }

        ModulesKiller.stopDNSCrypt(context);
    }

    private void bindToVPNService(Context context) {
        serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                serviceVPN = ((ServiceVPN.VPNBinder) service).getService();
                bound = true;
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                bound = false;
            }
        };

        if (context != null) {
            Intent intent = new Intent(context, ServiceVPN.class);
            context.bindService(intent, serviceConnection, 0);
        }
    }

    private void unbindVPNService(Context context) {
        if (bound && serviceConnection != null && context != null) {
            context.unbindService(serviceConnection);
            bound = false;
        }
    }

    void startButtonOnClick(Context context, View v) {
        if (context == null || view == null) {
            return;
        }

        FragmentManager fragmentManager = view.getDNSCryptFragmentManager();

        if (((MainActivity) context).childLockActive) {
            Toast.makeText(context, context.getText(R.string.action_mode_dialog_locked), Toast.LENGTH_LONG).show();
            return;
        }


        if (v.getId() == R.id.btnDNSCryptStart) {

            view.setStartButtonEnabled(false);

            cleanLogFileNoRootMethod(context);

            boolean rootMode = modulesStatus.getMode() == ROOT_MODE;


            if (new PrefManager(context).getBoolPref("Tor Running")
                    && !new PrefManager(context).getBoolPref("DNSCrypt Running")) {

                if (modulesStatus.isContextUIDUpdateRequested()|| fixedModuleState == RUNNING) {
                    Toast.makeText(context, R.string.please_wait, Toast.LENGTH_SHORT).show();
                    view.setStartButtonEnabled(true);
                    return;
                }

                if (!routeAllThroughTor) {
                    if (rootMode && fragmentManager != null) {
                        NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                                context, context.getText(R.string.helper_dnscrypt_tor).toString(), "dnscrypt_tor");
                        if (notificationHelper != null) {
                            notificationHelper.show(fragmentManager, NotificationHelper.TAG_HELPER);
                        }
                    }
                } else {
                    if (rootMode && fragmentManager != null) {
                        NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                                context, context.getText(R.string.helper_dnscrypt_tor_privacy).toString(), "dnscrypt_tor_privacy");
                        if (notificationHelper != null) {
                            notificationHelper.show(fragmentManager, NotificationHelper.TAG_HELPER);
                        }
                    }
                }

                view.setDnsCryptStarting();

                runDNSCrypt(context);

                displayLog(1000);
            } else if (!new PrefManager(context).getBoolPref("Tor Running")
                    && !new PrefManager(context).getBoolPref("DNSCrypt Running")) {

                if (modulesStatus.isContextUIDUpdateRequested() || fixedModuleState == RUNNING) {
                    Toast.makeText(context, R.string.please_wait, Toast.LENGTH_SHORT).show();
                    view.setStartButtonEnabled(true);
                    return;
                }

                if (rootMode && fragmentManager != null) {
                    NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                            context, context.getText(R.string.helper_dnscrypt).toString(), "dnscrypt");
                    if (notificationHelper != null) {
                        notificationHelper.show(fragmentManager, NotificationHelper.TAG_HELPER);
                    }
                }
                view.setDnsCryptStarting();

                runDNSCrypt(context);

                displayLog(1000);
            } else if (!new PrefManager(context).getBoolPref("Tor Running")
                    && new PrefManager(context).getBoolPref("DNSCrypt Running")) {
                view.setDnsCryptStopping();
                stopDNSCrypt(context);
            } else if (new PrefManager(context).getBoolPref("Tor Running")
                    && new PrefManager(context).getBoolPref("DNSCrypt Running")) {

                if (rootMode && fragmentManager != null) {
                    NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                            context, context.getText(R.string.helper_tor).toString(), "tor");
                    if (notificationHelper != null) {
                        notificationHelper.show(fragmentManager, NotificationHelper.TAG_HELPER);
                    }
                }

                view.setDnsCryptStopping();
                stopDNSCrypt(context);
            }

            view.setProgressBarIndeterminate(true);
        }
    }

    private void cleanLogFileNoRootMethod(Context context) {
        try {
            File f = new File(appDataDir + "/logs");

            if (f.mkdirs() && f.setReadable(true) && f.setWritable(true))
                Log.i(LOG_TAG, "log dir created");

            PrintWriter writer = new PrintWriter(appDataDir + "/logs/DnsCrypt.log", "UTF-8");
            writer.println(context.getResources().getString(R.string.tvDNSDefaultLog) + " " + DNSCryptVersion);
            writer.close();
        } catch (IOException e) {
            Log.e(LOG_TAG, "Unable to create dnsCrypt log file " + e.getMessage());
        }
    }

}
