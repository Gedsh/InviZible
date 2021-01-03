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

    Copyright 2019-2021 by Garmatin Oleksandr invizible.soft@gmail.com
*/

import android.app.Activity;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import androidx.preference.SwitchPreference;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.SettingsActivity;
import pan.alexander.tordnscrypt.dialogs.NotificationHelper;
import pan.alexander.tordnscrypt.modules.ModulesAux;
import pan.alexander.tordnscrypt.modules.ModulesRestarter;
import pan.alexander.tordnscrypt.modules.ModulesService;
import pan.alexander.tordnscrypt.modules.ModulesStatus;
import pan.alexander.tordnscrypt.proxy.ProxyHelper;
import pan.alexander.tordnscrypt.utils.CachedExecutor;
import pan.alexander.tordnscrypt.utils.PrefManager;
import pan.alexander.tordnscrypt.utils.Utils;
import pan.alexander.tordnscrypt.utils.Verifier;
import pan.alexander.tordnscrypt.utils.enums.FileOperationsVariants;
import pan.alexander.tordnscrypt.utils.file_operations.FileOperations;
import pan.alexander.tordnscrypt.utils.file_operations.OnTextFileOperationsCompleteListener;
import pan.alexander.tordnscrypt.vpn.service.ServiceVPNHelper;

import static pan.alexander.tordnscrypt.TopFragment.TOP_BROADCAST;
import static pan.alexander.tordnscrypt.TopFragment.appVersion;
import static pan.alexander.tordnscrypt.TopFragment.wrongSign;
import static pan.alexander.tordnscrypt.settings.tor_preferences.PreferencesTorFragment.ISOLATE_DEST_ADDRESS;
import static pan.alexander.tordnscrypt.settings.tor_preferences.PreferencesTorFragment.ISOLATE_DEST_PORT;
import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;
import static pan.alexander.tordnscrypt.utils.enums.FileOperationsVariants.readTextFile;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.PROXY_MODE;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.ROOT_MODE;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.VPN_MODE;


public class PreferencesCommonFragment extends PreferenceFragmentCompat
        implements Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener,
        OnTextFileOperationsCompleteListener {
    private String torTransPort;
    private String torSocksPort;
    private String torHTTPTunnelPort;
    private boolean allowTorTether = false;
    private boolean allowITPDtether = false;
    private String torConfPath = "";
    private String itpdConfPath = "";
    private String itpdTunnelsPath = "";
    private boolean commandDisableProxy;
    private Handler handler;

    public PreferencesCommonFragment() {
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRetainInstance(true);

        addPreferencesFromResource(R.xml.preferences_common);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        FileOperations.setOnFileOperationCompleteListener(this);

        Activity activity = getActivity();

        ModulesStatus modulesStatus = ModulesStatus.getInstance();

        if (activity == null) {
            return super.onCreateView(inflater, container, savedInstanceState);
        }

        Looper looper = Looper.getMainLooper();
        if (looper != null) {
            handler = new Handler(looper);
        }

        activity.setTitle(R.string.drawer_menu_commonSettings);

        PreferenceCategory others = findPreference("common_other");
        Preference swShowNotification = findPreference("swShowNotification");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (others != null && swShowNotification != null) {
                others.removePreference(swShowNotification);
            }
        } else {
            if (swShowNotification != null) {
                swShowNotification.setOnPreferenceChangeListener(this);
            }
        }


        Preference swCompatibilityMode = findPreference("swCompatibilityMode");
        if (modulesStatus.getMode() != VPN_MODE && others != null && swCompatibilityMode != null) {
            others.removePreference(swCompatibilityMode);
        } else if (swCompatibilityMode != null) {
            swCompatibilityMode.setOnPreferenceChangeListener(this);
        }


        boolean fixTTL = modulesStatus.isFixTTL() && (modulesStatus.getMode() == ROOT_MODE)
                && !modulesStatus.isUseModulesWithRoot();
        PreferenceScreen preferenceScreen = findPreference("pref_common");
        PreferenceCategory proxySettingsCategory = findPreference("categoryCommonProxy");
        Preference swUseProxy = findPreference("swUseProxy");
        if (preferenceScreen != null && proxySettingsCategory != null) {
            if ((modulesStatus.getMode() == VPN_MODE || fixTTL) && swUseProxy != null) {
                swUseProxy.setOnPreferenceChangeListener(this);
            } else {
                preferenceScreen.removePreference(proxySettingsCategory);
            }
        }

        Preference tetheringSettings = findPreference("pref_common_tethering_settings");
        if (tetheringSettings != null) {
            if (modulesStatus.getMode() == VPN_MODE || modulesStatus.getMode() == ROOT_MODE) {
                tetheringSettings.setOnPreferenceClickListener(this);
            }
        }

        PreferenceCategory otherCategory = findPreference("common_other");
        Preference multiUser = findPreference("pref_common_multi_user");
        if (otherCategory != null && multiUser != null) {
            if (modulesStatus.getMode() == PROXY_MODE) {
                otherCategory.removePreference(multiUser);
            } else {
                multiUser.setOnPreferenceChangeListener(this);
            }
        }

        PreferenceCategory mitmCategory = findPreference("pref_common_mitm_categ");
        Preference mitmDetection = findPreference("pref_common_arp_spoofing_detection");
        Preference mitmBlockInternet = findPreference("pref_common_arp_block_internet");
        if (mitmCategory != null && mitmDetection != null && mitmBlockInternet != null) {
            mitmDetection.setOnPreferenceChangeListener(this);
            if (modulesStatus.getMode() == PROXY_MODE) {
                mitmCategory.removePreference(mitmBlockInternet);
            } else {
                mitmBlockInternet.setOnPreferenceChangeListener(this);
            }
        }

        if (modulesStatus.getMode() == ROOT_MODE) {
            registerPreferences();
        } else {
            removePreferences();
        }

        manageLANDeviceAddressPreference(fixTTL);

        PreferenceCategory otherSettingsCategory = findPreference("common_other");
        Preference shellControl = findPreference("pref_common_shell_control");

        if (appVersion.startsWith("g")) {
            PreferenceCategory hotspotSettingsCategory = findPreference("HOTSPOT");
            Preference blockHTTP = findPreference("pref_common_block_http");
            if (hotspotSettingsCategory != null && blockHTTP != null) {
                hotspotSettingsCategory.removePreference(blockHTTP);
            }

            if (otherSettingsCategory != null && shellControl != null) {
                otherSettingsCategory.removePreference(shellControl);
            }

        } else if (shellControl != null) {
            shellControl.setSummary(String.format(getString(R.string.pref_common_shell_control_summ), activity.getPackageName()));
        }

        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {

    }

    @Override
    public void onResume() {
        super.onResume();

        Context context = getActivity();

        if (context == null) {
            return;
        }

        PathVars pathVars = PathVars.getInstance(context);
        torTransPort = pathVars.getTorTransPort();
        torSocksPort = pathVars.getTorSOCKSPort();
        torHTTPTunnelPort = pathVars.getTorHTTPTunnelPort();
        torConfPath = pathVars.getTorConfPath();
        itpdConfPath = pathVars.getItpdConfPath();
        itpdTunnelsPath = pathVars.getItpdTunnelsPath();

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        boolean swUseProxy = sharedPreferences.getBoolean("swUseProxy", false);
        String proxyServer = sharedPreferences.getString("ProxyServer", "");
        String proxyPort = sharedPreferences.getString("ProxyPort", "");
        Set<String> setBypassProxy = new PrefManager(context).getSetStrPref("clearnetAppsForProxy");
        if (swUseProxy && ModulesStatus.getInstance().getMode() == VPN_MODE
                && (proxyServer == null || proxyServer.isEmpty()
                || proxyPort == null || proxyPort.isEmpty()
                || setBypassProxy == null
                || setBypassProxy.isEmpty() && proxyServer.equals("127.0.0.1"))) {

            Preference swUseProxyPreference = findPreference("swUseProxy");
            if (swUseProxyPreference != null) {
                ((SwitchPreference) swUseProxyPreference).setChecked(false);
            }
        }

        CachedExecutor.INSTANCE.getExecutorService().submit(() -> {
            try {
                Verifier verifier = new Verifier(context);
                String appSign = verifier.getApkSignatureZip();
                String appSignAlt = verifier.getApkSignature();
                if (!verifier.decryptStr(wrongSign, appSign, appSignAlt).equals(TOP_BROADCAST)) {
                    NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                            context, getString(R.string.verifier_error), "5889");
                    if (notificationHelper != null && isAdded()) {
                        notificationHelper.show(getParentFragmentManager(), NotificationHelper.TAG_HELPER);
                    }
                }

            } catch (Exception e) {
                NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                       context, getString(R.string.verifier_error), "5804");
                if (notificationHelper != null && isAdded()) {
                    notificationHelper.show(getParentFragmentManager(), NotificationHelper.TAG_HELPER);
                }
                Log.e(LOG_TAG, "PreferencesCommonFragment fault " + e.getMessage() + " " + e.getCause() + System.lineSeparator() +
                        Arrays.toString(e.getStackTrace()));
            }
        });

    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {

        Context context = getActivity();

        if (context == null) {
            return false;
        }

        switch (preference.getKey()) {
            case "swShowNotification":
                if (!Boolean.parseBoolean(newValue.toString())) {
                    Intent intent = new Intent(context, ModulesService.class);
                    intent.setAction(ModulesService.actionDismissNotification);
                    context.startService(intent);
                    InfoNotificationProtectService infoNotification = new InfoNotificationProtectService();
                    if (isAdded()) {
                        infoNotification.show(getParentFragmentManager(), "dialogProtectService");
                    }
                }
                break;
            case "pref_common_tor_tethering":
                allowTorTether = Boolean.parseBoolean(newValue.toString());
                readTorConf(context);
                if (new PrefManager(context).getBoolPref("Tor Running")) {
                    ModulesRestarter.restartTor(context);
                    ModulesStatus.getInstance().setIptablesRulesUpdateRequested(context, true);
                }
                break;
            case "pref_common_itpd_tethering":
                allowITPDtether = Boolean.parseBoolean(newValue.toString());
                readITPDConf(context);
                readITPDTunnelsConf(context);
                if (new PrefManager(context).getBoolPref("I2PD Running")) {
                    ModulesRestarter.restartITPD(context);
                    ModulesStatus.getInstance().setIptablesRulesUpdateRequested(context, true);
                }
                break;
            case "pref_common_tor_route_all":
                Preference prefTorSiteUnlockTether = findPreference("prefTorSiteUnlockTether");
                if (prefTorSiteUnlockTether != null) {
                    prefTorSiteUnlockTether.setEnabled(!Boolean.parseBoolean(newValue.toString()));
                }

                if (new PrefManager(context).getBoolPref("Tor Running")) {
                    ModulesStatus.getInstance().setIptablesRulesUpdateRequested(context, true);
                }
                break;
            case "pref_common_block_http":
                if (new PrefManager(context).getBoolPref("DNSCrypt Running")
                        || new PrefManager(context).getBoolPref("Tor Running")) {
                    ModulesStatus.getInstance().setIptablesRulesUpdateRequested(context, true);
                }
                break;
            case "swUseModulesRoot":
                ModulesStatus modulesStatus = ModulesStatus.getInstance();
                ModulesAux.stopModulesIfRunning(context);
                boolean newOptionValue = Boolean.parseBoolean(newValue.toString());
                modulesStatus.setUseModulesWithRoot(newOptionValue);
                modulesStatus.setContextUIDUpdateRequested(true);
                ModulesAux.makeModulesStateExtraLoop(context);

                Preference fixTTLPreference = findPreference("pref_common_fix_ttl");
                if (fixTTLPreference != null) {
                    fixTTLPreference.setEnabled(!newOptionValue);
                }

                Log.i(LOG_TAG, "PreferencesCommonFragment switch to "
                        + (Boolean.parseBoolean(newValue.toString()) ? "Root" : "No Root"));
                break;
            case "pref_common_fix_ttl":
                modulesStatus = ModulesStatus.getInstance();
                boolean fixed = Boolean.parseBoolean(newValue.toString());
                modulesStatus.setFixTTL(fixed);
                modulesStatus.setIptablesRulesUpdateRequested(context, true);

                activityCurrentRecreate();
                break;
            case "pref_common_local_eth_device_addr":
            case "swCompatibilityMode":
            case "pref_common_multi_user":
                ModulesStatus.getInstance().setIptablesRulesUpdateRequested(context, true);
                break;
            case "swWakelock":
                ModulesAux.requestModulesStatusUpdate(context);
                break;
            case "pref_common_arp_spoofing_detection":
                if (Boolean.parseBoolean(newValue.toString())) {
                    ModulesAux.startArpDetection(context);
                } else {
                    ModulesAux.stopArpDetection(context);
                }
                break;
            case "pref_common_arp_block_internet":
                modulesStatus = ModulesStatus.getInstance();
                boolean fixTTL = modulesStatus.isFixTTL() && (modulesStatus.getMode() == ROOT_MODE)
                        && !modulesStatus.isUseModulesWithRoot();
                if (fixTTL) {
                    //Manually reload the VPN service because setIptablesRulesUpdateRequested does not do this in case of an ARP attack detected
                    ServiceVPNHelper.reload("Internet blocking settings for ARP attacks changed", context);
                }
                ModulesStatus.getInstance().setIptablesRulesUpdateRequested(context, true);
                break;
            case "swUseProxy":
                if (Boolean.parseBoolean(newValue.toString())) {
                    commandDisableProxy = false;

                    Intent intent = new Intent(context, SettingsActivity.class);
                    intent.setAction("use_proxy");
                    context.startActivity(intent);
                } else {
                    commandDisableProxy = true;
                }
                break;
        }
        return true;
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        Context context = getActivity();

        if (context == null) {
            return false;
        }

        if ("pref_common_tethering_settings".equals(preference.getKey())) {
            Intent intent_tether = new Intent(Intent.ACTION_MAIN, null);
            intent_tether.addCategory(Intent.CATEGORY_LAUNCHER);
            ComponentName cn = new ComponentName("com.android.settings", "com.android.settings.TetherSettings");
            intent_tether.setComponent(cn);
            intent_tether.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(intent_tether);
            } catch (Exception e) {
                Log.e(LOG_TAG, "PreferencesCommonFragment startHOTSPOT exception " + e.getMessage() + " " + e.getCause());
            }
        }
        return false;
    }

    private void disableProxy(Context context) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        String proxyServer = sharedPreferences.getString("ProxyServer", "");
        String proxyPort = sharedPreferences.getString("ProxyPort", "");

        if (proxyServer != null && proxyPort != null) {
            ProxyHelper.INSTANCE.manageProxy(context, proxyServer, proxyPort, false,
                    false, false, false);
        }
    }

    private void activityCurrentRecreate() {

        Activity activity = getActivity();

        if (activity == null || activity.isFinishing()) {
            return;
        }

        Intent intent = activity.getIntent();
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_NO_ANIMATION | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        activity.overridePendingTransition(0, 0);
        activity.finish();

        activity.overridePendingTransition(0, 0);
        startActivity(intent);

        new PrefManager(activity).setBoolPref("refresh_main_activity", true);
    }

    private void readTorConf(Context context) {
        FileOperations.readTextFile(context, torConfPath, SettingsActivity.tor_conf_tag);
    }

    private void allowTorTethering(List<String> torConf) {

        Context context = getActivity();

        if (context == null) {
            return;
        }

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        boolean isolateDestAddress = sharedPreferences.getBoolean("pref_tor_isolate_dest_address", false);
        boolean isolateDestPort = sharedPreferences.getBoolean("pref_tor_isolate_dest_port", false);

        String line;
        for (int i = 0; i < torConf.size(); i++) {
            line = torConf.get(i);
            if (line.contains("TransPort")) {
                line = "TransPort " + addIsolateFlags(torTransPort, allowTorTether, isolateDestAddress, isolateDestPort);
                torConf.set(i, line);
            } else if (line.contains("SOCKSPort")) {
                line = "SOCKSPort " + addIsolateFlags(torSocksPort, allowTorTether, isolateDestAddress, isolateDestPort);
                torConf.set(i, line);
            } else if (line.contains("HTTPTunnelPort")) {
                line = "HTTPTunnelPort " + addIsolateFlags(torHTTPTunnelPort, allowTorTether, isolateDestAddress, isolateDestPort);
                torConf.set(i, line);
            }
        }

        FileOperations.writeToTextFile(context, torConfPath, torConf, "ignored");
    }

    private String addIsolateFlags(String port, boolean allowTorTethering, boolean isolateDestinationAddress, boolean isolateDestinationPort) {
        String value = port;
        if (allowTorTethering) {
            value = "0.0.0.0:" + value;
        }
        if (isolateDestinationAddress) {
            value += " " + ISOLATE_DEST_ADDRESS;
        }
        if (isolateDestinationPort) {
            value += " " + ISOLATE_DEST_PORT;
        }
        return value;
    }

    private void readITPDConf(Context context) {
        FileOperations.readTextFile(context, itpdConfPath, SettingsActivity.itpd_conf_tag);
    }

    private void allowITPDTethering(List<String> itpdConf) {

        Context context = getActivity();

        if (context == null) {
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

        FileOperations.writeToTextFile(context, itpdConfPath, itpdConf, "ignored");
    }

    private void readITPDTunnelsConf(Context context) {
        FileOperations.readTextFile(context, itpdTunnelsPath, SettingsActivity.itpd_tunnels_tag);
    }

    private void allowITPDTunnelsTethering(List<String> itpdTunnels) {

        Context context = getActivity();

        if (getActivity() == null) {
            return;
        }

        String line;
        for (int i = 0; i < itpdTunnels.size(); i++) {
            line = itpdTunnels.get(i);
            if (line.contains("address")) {
                if (allowITPDtether) {
                    line = line.replace("127.0.0.1", "0.0.0.0");
                } else {
                    line = line.replace("0.0.0.0", "127.0.0.1");
                }
                itpdTunnels.set(i, line);
            }
        }

        FileOperations.writeToTextFile(context, itpdTunnelsPath, itpdTunnels, "ignored");
    }

    @Override
    public void OnFileOperationComplete(FileOperationsVariants currentFileOperation,
                                        boolean fileOperationResult, String path, final String tag, final List<String> lines) {

        if (handler == null) {
            return;
        }

        if (fileOperationResult && currentFileOperation == readTextFile) {
            handler.post(() -> {
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
            });
        }
    }

    public static class InfoNotificationProtectService extends DialogFragment {

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {

            Context context = getActivity();

            if (context == null) {
                return super.onCreateDialog(savedInstanceState);
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.CustomAlertDialogTheme);
            builder.setMessage(R.string.pref_common_notification_helper)
                    .setTitle(R.string.helper_dialog_title)
                    .setPositiveButton(R.string.ok, (dialog, which) -> {
                        final String packageName = context.getPackageName();
                        final PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && (pm != null && !pm.isIgnoringBatteryOptimizations(packageName))) {
                            Intent intent = new Intent();
                            intent.setAction(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                            try {
                                context.startActivity(intent);
                            } catch (Exception e) {
                                Log.e(LOG_TAG, "PreferencesCommonFragment InfoNotificationProtectService exception " + e.getMessage() + " " + e.getCause());
                            }
                        }
                    });
            // Create the AlertDialog object and return it
            return builder.create();
        }

        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            return super.onCreateView(inflater, container, savedInstanceState);
        }
    }

    private void registerPreferences() {

        Context context = getActivity();

        if (context == null) {
            return;
        }

        Preference swFixTTL = findPreference("pref_common_fix_ttl");
        if (swFixTTL != null) {
            swFixTTL.setOnPreferenceChangeListener(this);

            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
            swFixTTL.setEnabled(!sharedPreferences.getBoolean("swUseModulesRoot", false));
        }

        Preference prefTorSiteUnlockTether = findPreference("prefTorSiteUnlockTether");
        if (prefTorSiteUnlockTether != null) {
            SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(context);
            prefTorSiteUnlockTether.setEnabled(!shPref.getBoolean("pref_common_tor_route_all", false));
        }

        ArrayList<Preference> preferences = new ArrayList<>();
        preferences.add(findPreference("pref_common_tor_tethering"));
        preferences.add(findPreference("pref_common_tor_route_all"));
        preferences.add(findPreference("pref_common_itpd_tethering"));
        preferences.add(findPreference("pref_common_block_http"));
        preferences.add(findPreference("swUseModulesRoot"));
        preferences.add(findPreference("swWakelock"));
        preferences.add(findPreference("pref_common_local_eth_device_addr"));

        for (Preference preference : preferences) {
            if (preference != null) {
                preference.setOnPreferenceChangeListener(this);
            }
        }
    }

    private void removePreferences() {
        PreferenceScreen preferenceScreen = findPreference("pref_common");

        if (ModulesStatus.getInstance().getMode() != VPN_MODE) {
            PreferenceCategory hotspotSettingsCategory = findPreference("HOTSPOT");
            if (preferenceScreen != null && hotspotSettingsCategory != null) {
                preferenceScreen.removePreference(hotspotSettingsCategory);
            }
        } else {
            PreferenceCategory hotspotSettingsCategory = findPreference("HOTSPOT");

            ArrayList<Preference> preferencesHOTSPOT = new ArrayList<>();
            preferencesHOTSPOT.add(findPreference("pref_common_tor_route_all"));
            preferencesHOTSPOT.add(findPreference("prefTorSiteUnlockTether"));
            preferencesHOTSPOT.add(findPreference("prefTorSiteExcludeTether"));
            preferencesHOTSPOT.add(findPreference("pref_common_itpd_tethering"));
            preferencesHOTSPOT.add(findPreference("pref_common_block_http"));
            preferencesHOTSPOT.add(findPreference("pref_common_fix_ttl"));
            preferencesHOTSPOT.add(findPreference("pref_common_local_eth_device_addr"));

            if (hotspotSettingsCategory != null) {
                for (Preference preference : preferencesHOTSPOT) {
                    if (preference != null) {
                        hotspotSettingsCategory.removePreference(preference);
                    }
                }
            }

            Preference pref_common_tor_tethering = findPreference("pref_common_tor_tethering");

            if (pref_common_tor_tethering != null) {
                pref_common_tor_tethering.setSummary(getText(R.string.vpn_tor_tether_summ));
                pref_common_tor_tethering.setOnPreferenceChangeListener(this);
            }
        }


        if (ModulesStatus.getInstance().isRootAvailable()
                && ModulesStatus.getInstance().getMode() != VPN_MODE) {
            Preference pref_common_use_modules_with_root = findPreference("swUseModulesRoot");
            if (pref_common_use_modules_with_root != null) {
                pref_common_use_modules_with_root.setOnPreferenceChangeListener(this);
            }
        } else {
            PreferenceCategory categoryUseModulesRoot = findPreference("categoryUseModulesRoot");
            if (preferenceScreen != null && categoryUseModulesRoot != null) {
                preferenceScreen.removePreference(categoryUseModulesRoot);
            }
        }

        PreferenceCategory categoryOther = findPreference("common_other");
        Preference selectIptables = findPreference("pref_common_use_iptables");
        Preference selectBusybox = findPreference("pref_common_use_busybox");
        if (categoryOther != null && selectIptables != null) {
            categoryOther.removePreference(selectIptables);
        }
        if (categoryOther != null && selectBusybox != null) {
            categoryOther.removePreference(selectBusybox);
        }
    }

    private void manageLANDeviceAddressPreference(boolean fixTTL) {

        Activity activity = getActivity();
        PreferenceCategory hotspotSettingsCategory = findPreference("HOTSPOT");
        Preference localEthernetDeviceAddress = findPreference("pref_common_local_eth_device_addr");

        if (activity == null || hotspotSettingsCategory == null || localEthernetDeviceAddress == null) {
            return;
        }

        if (!fixTTL
                || Utils.INSTANCE.getScreenOrientation(activity) == Configuration.ORIENTATION_PORTRAIT
                || !Utils.INSTANCE.isLANInterfaceExist()) {
            hotspotSettingsCategory.removePreference(localEthernetDeviceAddress);
        } else {
            String deviceIP = Utils.INSTANCE.getDeviceIP();
            String summary = String.format(getString(R.string.pref_common_local_eth_device_addr_summ), deviceIP, deviceIP);
            localEthernetDeviceAddress.setSummary(summary);
        }


    }

    @Override
    public void onStop() {
        super.onStop();

        Context context = getActivity();

        if (context != null && commandDisableProxy) {
            commandDisableProxy = false;
            disableProxy(context);
            Toast.makeText(context, R.string.toastSettings_saved, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
            handler = null;
        }

        FileOperations.deleteOnFileOperationCompleteListener(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}
