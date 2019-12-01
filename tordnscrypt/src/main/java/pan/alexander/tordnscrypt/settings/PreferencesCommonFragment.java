package pan.alexander.tordnscrypt.settings;
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

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.SettingsActivity;
import pan.alexander.tordnscrypt.dialogs.NotificationHelper;
import pan.alexander.tordnscrypt.modules.ModulesAux;
import pan.alexander.tordnscrypt.modules.ModulesRestarter;
import pan.alexander.tordnscrypt.modules.ModulesService;
import pan.alexander.tordnscrypt.modules.ModulesStatus;
import pan.alexander.tordnscrypt.utils.PrefManager;
import pan.alexander.tordnscrypt.utils.Verifier;
import pan.alexander.tordnscrypt.utils.enums.FileOperationsVariants;
import pan.alexander.tordnscrypt.utils.file_operations.FileOperations;
import pan.alexander.tordnscrypt.utils.file_operations.OnTextFileOperationsCompleteListener;

import static pan.alexander.tordnscrypt.TopFragment.TOP_BROADCAST;
import static pan.alexander.tordnscrypt.TopFragment.wrongSign;
import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;
import static pan.alexander.tordnscrypt.utils.enums.FileOperationsVariants.readTextFile;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.ROOT_MODE;


public class PreferencesCommonFragment extends PreferenceFragmentCompat
        implements Preference.OnPreferenceChangeListener, OnTextFileOperationsCompleteListener {
    private String torTransPort;
    private String torSocksPort;
    private String appDataDir;
    private boolean allowTorTether = false;
    private boolean allowITPDtether = false;

    public PreferencesCommonFragment() {
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.preferences_common);

        FileOperations.setOnFileOperationCompleteListener(this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        if (getActivity() == null) {
            super.onCreateView(inflater, container, savedInstanceState);
        }

        getActivity().setTitle(R.string.drawer_menu_commonSettings);

        PreferenceCategory others = (PreferenceCategory) findPreference("common_other");
        Preference swShowNotification = findPreference("swShowNotification");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            others.removePreference(swShowNotification);
        } else {
            swShowNotification.setOnPreferenceChangeListener(this);
        }

        if (ModulesStatus.getInstance().getMode() == ROOT_MODE) {
            registerPreferences();
        } else {
            removePreferences();
        }

        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {

    }

    @Override
    public void onResume() {
        super.onResume();

        if (getActivity() == null) {
            return;
        }

        PathVars pathVars = new PathVars(getActivity());
        appDataDir = pathVars.appDataDir;
        torTransPort = pathVars.torTransPort;
        torSocksPort = pathVars.torSOCKSPort;

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Verifier verifier = new Verifier(getActivity());
                    String appSign = verifier.getApkSignatureZipModern();
                    String appSignAlt = verifier.getApkSignature();
                    if (!verifier.decryptStr(wrongSign, appSign, appSignAlt).equals(TOP_BROADCAST)) {
                        NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                                getActivity(), getText(R.string.verifier_error).toString(), "5889");
                        if (notificationHelper != null) {
                            if (getFragmentManager() != null) {
                                notificationHelper.show(getFragmentManager(), NotificationHelper.TAG_HELPER);
                            }
                        }
                    }

                } catch (Exception e) {
                    NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                            getActivity(), getText(R.string.verifier_error).toString(), "5804");
                    if (notificationHelper != null) {
                        if (getFragmentManager() != null) {
                            notificationHelper.show(getFragmentManager(), NotificationHelper.TAG_HELPER);
                        }
                    }
                    Log.e(LOG_TAG, "PreferencesCommonFragment fault " + e.getMessage() + " " + e.getCause() + System.lineSeparator() +
                            Arrays.toString(e.getStackTrace()));
                }
            }
        });
        thread.start();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {

        if (getActivity() == null) {
            return false;
        }

        switch (preference.getKey()) {
            case "swShowNotification":
                if (!Boolean.valueOf(newValue.toString())) {
                    Intent intent = new Intent(getActivity(), ModulesService.class);
                    intent.setAction(ModulesService.actionDismissNotification);
                    getActivity().startService(intent);
                    InfoNotificationProtectService infoNotification = new InfoNotificationProtectService();
                    if (getFragmentManager() != null) {
                        infoNotification.show(getFragmentManager(), "dialogProtectService");
                    }
                }
                break;
            case "pref_common_tor_tethering":
                allowTorTether = Boolean.valueOf(newValue.toString());
                readTorConf();
                if (new PrefManager(getActivity()).getBoolPref("Tor Running")) {
                    ModulesRestarter.restartTor(getActivity());
                    ModulesStatus.getInstance().setIptablesRulesUpdateRequested(true);
                    ModulesAux.requestModulesStatusUpdate(getActivity());
                }
                break;
            case "pref_common_itpd_tethering":
                allowITPDtether = Boolean.valueOf(newValue.toString());
                readITPDConf();
                readITPDTunnelsConf();
                if (new PrefManager(getActivity()).getBoolPref("I2PD Running")) {
                    ModulesRestarter.restartITPD(getActivity());
                    ModulesStatus.getInstance().setIptablesRulesUpdateRequested(true);
                    ModulesAux.requestModulesStatusUpdate(getActivity());
                }
                break;
            case "pref_common_tor_route_all":
                if (Boolean.valueOf(newValue.toString())) {
                    findPreference("prefTorSiteUnlockTether").setEnabled(false);
                } else {
                    findPreference("prefTorSiteUnlockTether").setEnabled(true);
                }
                if (new PrefManager(getActivity()).getBoolPref("Tor Running")) {
                    ModulesStatus.getInstance().setIptablesRulesUpdateRequested(true);
                    ModulesAux.requestModulesStatusUpdate(getActivity());
                }
                break;
            case "pref_common_block_http":
                if (new PrefManager(getActivity()).getBoolPref("DNSCrypt Running")
                        || new PrefManager(getActivity()).getBoolPref("Tor Running")) {
                    ModulesStatus.getInstance().setIptablesRulesUpdateRequested(true);
                    ModulesAux.requestModulesStatusUpdate(getActivity());
                }
                break;
            case "swUseModulesRoot":
                ModulesStatus modulesStatus = ModulesStatus.getInstance();
                ModulesAux.stopModulesIfRunning(getActivity());
                modulesStatus.setUseModulesWithRoot(Boolean.valueOf(newValue.toString()));
                modulesStatus.setContextUIDUpdateRequested(true);
                ModulesAux.requestModulesStatusUpdate(getActivity());
                Log.i(LOG_TAG, "PreferencesCommonFragment switch to "
                        + (Boolean.valueOf(newValue.toString())? "Root" : "No Root"));
                break;
        }
        return true;
    }

    private void readTorConf() {
        FileOperations.readTextFile(getActivity(), appDataDir + "/app_data/tor/tor.conf", SettingsActivity.tor_conf_tag);
    }

    private void allowTorTethering(List<String> torConf) {

        if (getActivity() == null) {
            return;
        }

        String line;
        for (int i = 0; i < torConf.size(); i++) {
            line = torConf.get(i);
            if (line.contains("TransPort")) {
                if (allowTorTether) {
                    line = "TransPort " + "0.0.0.0:" + torTransPort;
                } else {
                    line = line.trim().replaceAll(" .+:", " ");
                }
                torConf.set(i, line);
            } else if (line.contains("SOCKSPort")) {
                if (allowTorTether) {
                    line = "SOCKSPort " + "0.0.0.0:" + torSocksPort;
                } else {
                    line = line.trim().replaceAll(" .+:", " ");
                }
                torConf.set(i, line);
            }
        }

        FileOperations.writeToTextFile(getActivity(), appDataDir + "/app_data/tor/tor.conf", torConf, "ignored");
    }

    private void readITPDConf() {
        FileOperations.readTextFile(getActivity(), appDataDir + "/app_data/i2pd/i2pd.conf", SettingsActivity.itpd_conf_tag);
    }

    private void allowITPDTethering(List<String> itpdConf) {

        if (getActivity() == null) {
            return;
        }

        String line;
        String head = "";
        for (int i = 0; i < itpdConf.size(); i++) {
            line = itpdConf.get(i);
            if (line.matches("\\[.+]"))
                head = line.replace("[", "").replace("]", "");
            if (head.equals("httpproxy") && line.contains("address")) {
                if (allowITPDtether) {
                    line = line.replace("127.0.0.1", "0.0.0.0");
                } else {
                    line = line.replace("0.0.0.0", "127.0.0.1");
                }
                itpdConf.set(i, line);
            } else if (head.equals("socksproxy") && line.contains("address")) {
                if (allowITPDtether) {
                    line = line.replace("127.0.0.1", "0.0.0.0");
                } else {
                    line = line.replace("0.0.0.0", "127.0.0.1");
                }
                itpdConf.set(i, line);
            }
        }

        FileOperations.writeToTextFile(getActivity(), appDataDir + "/app_data/i2pd/i2pd.conf", itpdConf, "ignored");
    }

    private void readITPDTunnelsConf() {
        FileOperations.readTextFile(getActivity(), appDataDir + "/app_data/i2pd/tunnels.conf", SettingsActivity.itpd_tunnels_tag);
    }

    private void allowITPDTunnelsTethering(List<String> itpdTunnels) {

        if (getActivity() == null) {
            return;
        }

        String line;
        for (int i = 0; i <  itpdTunnels.size(); i++) {
            line =  itpdTunnels.get(i);
            if (line.contains("address")) {
                if (allowITPDtether) {
                    line = line.replace("127.0.0.1", "0.0.0.0");
                } else {
                    line = line.replace("0.0.0.0", "127.0.0.1");
                }
                itpdTunnels.set(i, line);
            }
        }

        FileOperations.writeToTextFile(getActivity(), appDataDir + "/app_data/i2pd/tunnels.conf",  itpdTunnels, "ignored");
    }

    @Override
    public void OnFileOperationComplete(FileOperationsVariants currentFileOperation,
                                        boolean fileOperationResult, String path, final String tag, final List<String> lines) {

        if (getActivity() == null) {
            return;
        }

        if (fileOperationResult && currentFileOperation == readTextFile) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (lines != null) {
                        switch (tag) {
                            case SettingsActivity.tor_conf_tag:
                                allowTorTethering(lines);
                                break;
                            case SettingsActivity.itpd_conf_tag:
                                allowITPDTethering(lines);
                                break;
                            case SettingsActivity.itpd_tunnels_tag:
                                allowITPDTunnelsTethering(lines);
                                break;
                        }
                    }
                }
            });
        }
    }

    public static class InfoNotificationProtectService extends DialogFragment {

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {

            if (getActivity() == null) {
                return super.onCreateDialog(savedInstanceState);
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(), R.style.CustomAlertDialogTheme);
            builder.setMessage(R.string.pref_common_notification_helper)
                    .setTitle(R.string.helper_dialog_title)
                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {

                            if (getActivity() == null) {
                                return;
                            }

                            final String packageName = getActivity().getPackageName();
                            final PowerManager pm = (PowerManager) getActivity().getSystemService(Context.POWER_SERVICE);
                            try {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && (pm != null && !pm.isIgnoringBatteryOptimizations(packageName))) {
                                    Intent intent = new Intent();
                                    intent.setAction(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                                    getActivity().startActivity(intent);
                                }
                            } catch (Exception e) {
                                Log.e(LOG_TAG, "PreferencesCommonFragment InfoNotificationProtectService Exception " + e.getMessage());
                            }
                        }
                    });
            // Create the AlertDialog object and return it
            return builder.create();
        }

        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            Objects.requireNonNull(getDialog().getWindow()).setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            return super.onCreateView(inflater, container, savedInstanceState);
        }
    }

    private void registerPreferences() {

        if (getActivity() == null) {
            return;
        }

        Preference swTorTethering = findPreference("pref_common_tor_tethering");
        swTorTethering.setOnPreferenceChangeListener(this);

        Preference swRouteAllThroughTor = findPreference("pref_common_tor_route_all");
        swRouteAllThroughTor.setOnPreferenceChangeListener(this);

        Preference swITPDTethering = findPreference("pref_common_itpd_tethering");
        swITPDTethering.setOnPreferenceChangeListener(this);

        Preference pref_common_block_http = findPreference("pref_common_block_http");
        pref_common_block_http.setOnPreferenceChangeListener(this);

        Preference pref_common_use_modules_with_root = findPreference("swUseModulesRoot");
        pref_common_use_modules_with_root.setOnPreferenceChangeListener(this);

        SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(getActivity());
        if (shPref.getBoolean("pref_common_tor_route_all", false)) {
            findPreference("prefTorSiteUnlockTether").setEnabled(false);
        } else {
            findPreference("prefTorSiteUnlockTether").setEnabled(true);
        }
    }

    private void removePreferences() {
        PreferenceScreen preferenceScreen = (PreferenceScreen) findPreference("pref_common");
        PreferenceCategory hotspotSettingsCategory = (PreferenceCategory) findPreference("HOTSPOT");
        preferenceScreen.removePreference(hotspotSettingsCategory);

        if (ModulesStatus.getInstance().isRootAvailable()) {
            Preference pref_common_use_modules_with_root = findPreference("swUseModulesRoot");
            pref_common_use_modules_with_root.setOnPreferenceChangeListener(this);
        } else {
            PreferenceCategory categoryUseModulesRoot = (PreferenceCategory) findPreference("categoryUseModulesRoot");
            preferenceScreen.removePreference(categoryUseModulesRoot);
        }

        PreferenceCategory categoryOther = (PreferenceCategory) findPreference("common_other");
        Preference selectIptables = findPreference("pref_common_use_iptables");
        Preference selectBusybox = findPreference("pref_common_use_busybox");
        categoryOther.removePreference(selectIptables);
        categoryOther.removePreference(selectBusybox);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        FileOperations.deleteOnFileOperationCompleteListener();
    }
}
