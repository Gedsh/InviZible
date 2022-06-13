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

    Copyright 2019-2022 by Garmatin Oleksandr invizible.soft@gmail.com
*/

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;

import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import dagger.Lazy;
import pan.alexander.tordnscrypt.App;
import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.dialogs.NotificationHelper;
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository;
import pan.alexander.tordnscrypt.modules.ModulesAux;
import pan.alexander.tordnscrypt.modules.ModulesRestarter;
import pan.alexander.tordnscrypt.modules.ModulesService;
import pan.alexander.tordnscrypt.modules.ModulesServiceActions;
import pan.alexander.tordnscrypt.modules.ModulesStatus;
import pan.alexander.tordnscrypt.proxy.ProxyHelper;
import pan.alexander.tordnscrypt.utils.executors.CachedExecutor;
import pan.alexander.tordnscrypt.utils.Utils;
import pan.alexander.tordnscrypt.utils.integrity.Verifier;
import pan.alexander.tordnscrypt.utils.enums.FileOperationsVariants;
import pan.alexander.tordnscrypt.utils.filemanager.FileManager;
import pan.alexander.tordnscrypt.utils.filemanager.OnTextFileOperationsCompleteListener;
import pan.alexander.tordnscrypt.vpn.service.ServiceVPNHelper;

import static pan.alexander.tordnscrypt.TopFragment.TOP_BROADCAST;
import static pan.alexander.tordnscrypt.TopFragment.appVersion;
import static pan.alexander.tordnscrypt.TopFragment.wrongSign;
import static pan.alexander.tordnscrypt.di.SharedPreferencesModule.DEFAULT_PREFERENCES_NAME;
import static pan.alexander.tordnscrypt.proxy.ProxyFragmentKt.CLEARNET_APPS_FOR_PROXY;
import static pan.alexander.tordnscrypt.settings.tor_preferences.PreferencesTorFragment.ISOLATE_DEST_ADDRESS;
import static pan.alexander.tordnscrypt.settings.tor_preferences.PreferencesTorFragment.ISOLATE_DEST_PORT;
import static pan.alexander.tordnscrypt.utils.Constants.LOOPBACK_ADDRESS;
import static pan.alexander.tordnscrypt.utils.Constants.META_ADDRESS;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.ARP_SPOOFING_BLOCK_INTERNET;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.ARP_SPOOFING_DETECTION;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.ARP_SPOOFING_NOT_SUPPORTED;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.COMPATIBILITY_MODE;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.DNS_REBIND_PROTECTION;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.FIX_TTL;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.ITPD_TETHERING;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.KILL_SWITCH;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.MAIN_ACTIVITY_RECREATE;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.MULTI_USER_SUPPORT;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.PROXY_ADDRESS;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.PROXY_PORT;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.RUN_MODULES_WITH_ROOT;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.TOR_TETHERING;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.USE_IPTABLES;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.USE_PROXY;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.WAIT_IPTABLES;
import static pan.alexander.tordnscrypt.utils.root.RootExecService.LOG_TAG;
import static pan.alexander.tordnscrypt.utils.enums.FileOperationsVariants.readTextFile;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.PROXY_MODE;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.ROOT_MODE;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.VPN_MODE;

import javax.inject.Inject;
import javax.inject.Named;


public class PreferencesCommonFragment extends PreferenceFragmentCompat
        implements Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener,
        OnTextFileOperationsCompleteListener {

    @Inject
    public Lazy<PreferenceRepository> preferenceRepository;
    @Inject
    public Lazy<PathVars> pathVars;
    @Inject
    public CachedExecutor cachedExecutor;
    @Inject
    public Lazy<Handler> handler;
    @Inject
    @Named(DEFAULT_PREFERENCES_NAME)
    public Lazy<SharedPreferences> defaultPreferences;
    @Inject
    public Lazy<ProxyHelper> proxyHelper;

    private static final int ARP_SCANNER_CHANGE_STATE_DELAY_SEC = 5;

    private String torTransPort;
    private String torSocksPort;
    private String torHTTPTunnelPort;
    private boolean allowTorTether = false;
    private boolean allowITPDtether = false;
    private String torConfPath = "";
    private String itpdConfPath = "";
    private String itpdTunnelsPath = "";
    private boolean commandDisableProxy;

    public PreferencesCommonFragment() {
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        App.getInstance().getDaggerComponent().inject(this);
        super.onCreate(savedInstanceState);

        setRetainInstance(true);

        addPreferencesFromResource(R.xml.preferences_common);
    }

    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        FileManager.setOnFileOperationCompleteListener(this);

        Activity activity = getActivity();

        ModulesStatus modulesStatus = ModulesStatus.getInstance();

        if (activity == null) {
            return super.onCreateView(inflater, container, savedInstanceState);
        }

        activity.setTitle(R.string.drawer_menu_commonSettings);

        PreferenceCategory otherCategory = findPreference("common_other");
        Preference swShowNotification = findPreference("swShowNotification");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (otherCategory != null && swShowNotification != null) {
                otherCategory.removePreference(swShowNotification);
            }
        } else {
            if (swShowNotification != null) {
                swShowNotification.setOnPreferenceChangeListener(this);
            }
        }


        Preference swCompatibilityMode = findPreference(COMPATIBILITY_MODE);
        if (modulesStatus.getMode() != VPN_MODE && otherCategory != null && swCompatibilityMode != null) {
            otherCategory.removePreference(swCompatibilityMode);
        } else if (swCompatibilityMode != null) {
            swCompatibilityMode.setOnPreferenceChangeListener(this);
        }


        boolean fixTTL = modulesStatus.isFixTTL() && (modulesStatus.getMode() == ROOT_MODE)
                && !modulesStatus.isUseModulesWithRoot();
        PreferenceScreen preferenceScreen = findPreference("pref_common");
        PreferenceCategory proxySettingsCategory = findPreference("categoryCommonProxy");
        Preference swUseProxy = findPreference(USE_PROXY);
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

        Preference multiUser = findPreference(MULTI_USER_SUPPORT);
        if (otherCategory != null && multiUser != null) {
            if (modulesStatus.getMode() == PROXY_MODE) {
                otherCategory.removePreference(multiUser);
            } else {
                multiUser.setOnPreferenceChangeListener(this);
            }
        }

        PreferenceCategory mitmCategory = findPreference("pref_common_mitm_categ");
        Preference mitmDetection = findPreference(ARP_SPOOFING_DETECTION);
        Preference mitmBlockInternet = findPreference(ARP_SPOOFING_BLOCK_INTERNET);
        if (mitmCategory != null && mitmDetection != null && mitmBlockInternet != null) {
            mitmDetection.setOnPreferenceChangeListener(this);

            if (preferenceRepository.get().getBoolPreference(ARP_SPOOFING_NOT_SUPPORTED)) {
                mitmDetection.setTitle(R.string.pref_common_rogue_dhcp_detection);
                mitmDetection.setSummary(R.string.pref_common_rogue_dhcp_detection_summ);
            } else {
                mitmDetection.setTitle(R.string.pref_common_arp_spoofing_detection);
                mitmDetection.setSummary(R.string.pref_common_arp_spoofing_detection_summ);
            }

            if (modulesStatus.getMode() == PROXY_MODE) {
                mitmCategory.removePreference(mitmBlockInternet);
            } else {
                mitmBlockInternet.setOnPreferenceChangeListener(this);
            }
        }

        Preference rebindDetection = findPreference(DNS_REBIND_PROTECTION);
        if (mitmCategory != null && rebindDetection != null) {
            if (modulesStatus.getMode() == VPN_MODE || fixTTL) {
                rebindDetection.setOnPreferenceChangeListener(this);
            } else {
                mitmCategory.removePreference(rebindDetection);
            }
        }

        if (modulesStatus.getMode() == ROOT_MODE) {
            registerPreferences();
        } else {
            removePreferences();
        }

        manageLANDeviceAddressPreference(fixTTL);

        Preference shellControl = findPreference("pref_common_shell_control");

        if (appVersion.startsWith("g")) {
            PreferenceCategory hotspotSettingsCategory = findPreference("HOTSPOT");
            Preference blockHTTP = findPreference("pref_common_block_http");
            if (hotspotSettingsCategory != null && blockHTTP != null) {
                hotspotSettingsCategory.removePreference(blockHTTP);
            }

            if (otherCategory != null && shellControl != null) {
                otherCategory.removePreference(shellControl);
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

        torTransPort = pathVars.get().getTorTransPort();
        torSocksPort = pathVars.get().getTorSOCKSPort();
        torHTTPTunnelPort = pathVars.get().getTorHTTPTunnelPort();
        torConfPath = pathVars.get().getTorConfPath();
        itpdConfPath = pathVars.get().getItpdConfPath();
        itpdTunnelsPath = pathVars.get().getItpdTunnelsPath();

        SharedPreferences sharedPreferences = defaultPreferences.get();
        boolean swUseProxy = sharedPreferences.getBoolean(USE_PROXY, false);
        String proxyServer = sharedPreferences.getString(PROXY_ADDRESS, "");
        String proxyPort = sharedPreferences.getString(PROXY_PORT, "");
        Set<String> setBypassProxy = preferenceRepository.get().getStringSetPreference(CLEARNET_APPS_FOR_PROXY);
        if (swUseProxy && ModulesStatus.getInstance().getMode() == VPN_MODE
                && (proxyServer == null || proxyServer.isEmpty()
                || proxyPort == null || proxyPort.isEmpty()
                || setBypassProxy.isEmpty() && proxyServer.equals(LOOPBACK_ADDRESS))) {

            Preference swUseProxyPreference = findPreference(USE_PROXY);
            if (swUseProxyPreference != null) {
                ((SwitchPreference) swUseProxyPreference).setChecked(false);
            }
        }

        cachedExecutor.submit(() -> {
            try {
                Verifier verifier = new Verifier(context);
                String appSign = verifier.getApkSignatureZip();
                String appSignAlt = verifier.getApkSignature();
                if (!verifier.decryptStr(wrongSign, appSign, appSignAlt).equals(TOP_BROADCAST)) {
                    NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                            context, getString(R.string.verifier_error), "5889");
                    if (notificationHelper != null && isAdded()) {
                        handler.get().post(() -> notificationHelper.show(getParentFragmentManager(), NotificationHelper.TAG_HELPER));
                    }
                }

            } catch (Exception e) {
                NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                        context, getString(R.string.verifier_error), "5804");
                if (notificationHelper != null && isAdded()) {
                    handler.get().post(() -> notificationHelper.show(getParentFragmentManager(), NotificationHelper.TAG_HELPER));
                }
                Log.e(LOG_TAG, "PreferencesCommonFragment fault " + e.getMessage() + " " + e.getCause() + System.lineSeparator() +
                        Arrays.toString(e.getStackTrace()));
            }
        });

    }

    @Override
    public boolean onPreferenceChange(@NonNull Preference preference, Object newValue) {

        Context context = getActivity();

        if (context == null) {
            return false;
        }

        switch (preference.getKey()) {
            case "swShowNotification":
                if (!Boolean.parseBoolean(newValue.toString())) {
                    Intent intent = new Intent(context, ModulesService.class);
                    intent.setAction(ModulesServiceActions.ACTION_DISMISS_NOTIFICATION);
                    context.startService(intent);
                }
                break;
            case TOR_TETHERING:
                allowTorTether = Boolean.parseBoolean(newValue.toString());
                readTorConf(context);
                break;
            case ITPD_TETHERING:
                allowITPDtether = Boolean.parseBoolean(newValue.toString());
                readITPDConf(context);
                readITPDTunnelsConf(context);
                break;
            case "pref_common_tor_route_all":
                Preference prefTorSiteUnlockTether = findPreference("prefTorSiteUnlockTether");
                if (prefTorSiteUnlockTether != null) {
                    prefTorSiteUnlockTether.setEnabled(!Boolean.parseBoolean(newValue.toString()));
                }

                if (ModulesAux.isTorSavedStateRunning()) {
                    ModulesStatus.getInstance().setIptablesRulesUpdateRequested(context, true);
                }
                break;
            case "pref_common_block_http":
                if (ModulesAux.isDnsCryptSavedStateRunning()
                        || ModulesAux.isTorSavedStateRunning()) {
                    ModulesStatus.getInstance().setIptablesRulesUpdateRequested(context, true);
                }
                break;
            case RUN_MODULES_WITH_ROOT:
                ModulesStatus modulesStatus = ModulesStatus.getInstance();
                ModulesAux.stopModulesIfRunning(context);
                boolean newOptionValue = Boolean.parseBoolean(newValue.toString());
                modulesStatus.setUseModulesWithRoot(newOptionValue);
                modulesStatus.setContextUIDUpdateRequested(true);
                ModulesAux.makeModulesStateExtraLoop(context);

                Preference fixTTLPreference = findPreference(FIX_TTL);
                if (fixTTLPreference != null) {
                    fixTTLPreference.setEnabled(!newOptionValue);
                }

                Log.i(LOG_TAG, "PreferencesCommonFragment switch to "
                        + (Boolean.parseBoolean(newValue.toString()) ? "Root" : "No Root"));
                break;
            case FIX_TTL:
                modulesStatus = ModulesStatus.getInstance();
                boolean fixed = Boolean.parseBoolean(newValue.toString());
                modulesStatus.setFixTTL(fixed);
                modulesStatus.setIptablesRulesUpdateRequested(context, true);

                activityCurrentRecreate();
                break;
            case MULTI_USER_SUPPORT:
                if (Boolean.parseBoolean(newValue.toString())) {
                    Utils.allowInteractAcrossUsersPermissionIfRequired(context);
                }
                ModulesStatus.getInstance().setIptablesRulesUpdateRequested(context, true);
                break;
            case "pref_common_local_eth_device_addr":
            case COMPATIBILITY_MODE:
            case DNS_REBIND_PROTECTION:
            case KILL_SWITCH:
                ModulesStatus.getInstance().setIptablesRulesUpdateRequested(context, true);
                break;
            case "swWakelock":
                ModulesAux.requestModulesStatusUpdate(context);
                break;
            case ARP_SPOOFING_DETECTION:
                if (Boolean.parseBoolean(newValue.toString())) {
                    ModulesAux.startArpDetection(context);
                } else {
                    ModulesAux.stopArpDetection(context);
                }
                handler.get().postDelayed(() -> {
                    ModulesStatus status = ModulesStatus.getInstance();
                    boolean fixTTL = status.isFixTTL() && (status.getMode() == ROOT_MODE)
                            && !status.isUseModulesWithRoot();
                    if (fixTTL) {
                        //Manually reload the VPN service because setIptablesRulesUpdateRequested does not do this in case of an ARP attack detected
                        ServiceVPNHelper.reload("Internet blocking settings for ARP attacks changed", context);
                    }
                    ModulesStatus.getInstance()
                            .setIptablesRulesUpdateRequested(context, true);

                }, ARP_SCANNER_CHANGE_STATE_DELAY_SEC * 1000);
                break;
            case ARP_SPOOFING_BLOCK_INTERNET:
                modulesStatus = ModulesStatus.getInstance();
                boolean fixTTL = modulesStatus.isFixTTL() && (modulesStatus.getMode() == ROOT_MODE)
                        && !modulesStatus.isUseModulesWithRoot();
                if (fixTTL) {
                    //Manually reload the VPN service because setIptablesRulesUpdateRequested does not do this in case of an ARP attack detected
                    ServiceVPNHelper.reload("Internet blocking settings for ARP attacks changed", context);
                }
                ModulesStatus.getInstance().setIptablesRulesUpdateRequested(context, true);
                break;
            case USE_PROXY:
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
    public boolean onPreferenceClick(@NonNull Preference preference) {
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

    private void disableProxy() {
        SharedPreferences sharedPreferences = defaultPreferences.get();
        String proxyServer = sharedPreferences.getString(PROXY_ADDRESS, "");
        String proxyPort = sharedPreferences.getString(PROXY_PORT, "");

        if (proxyServer != null && proxyPort != null) {
            proxyHelper.get().manageProxy(proxyServer, proxyPort, false,
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

        preferenceRepository.get().setBoolPreference(MAIN_ACTIVITY_RECREATE, true);
    }

    private void readTorConf(Context context) {
        FileManager.readTextFile(context, torConfPath, SettingsActivity.tor_conf_tag);
    }

    private void allowTorTethering(List<String> torConf) {

        Context context = getActivity();

        if (context == null) {
            return;
        }

        SharedPreferences sharedPreferences = defaultPreferences.get();
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

        FileManager.writeToTextFile(context, torConfPath, torConf, "ignored");

        if (ModulesAux.isTorSavedStateRunning()) {
            ModulesRestarter.restartTor(context);
            ModulesStatus.getInstance().setIptablesRulesUpdateRequested(context, true);
        }
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
        FileManager.readTextFile(context, itpdConfPath, SettingsActivity.itpd_conf_tag);
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
                    line = line.replace(LOOPBACK_ADDRESS, META_ADDRESS);
                } else {
                    line = line.replace(META_ADDRESS, LOOPBACK_ADDRESS);
                }
                itpdConf.set(i, line);
            } else if (head.equals("socksproxy") && line.contains("address")) {
                if (allowITPDtether) {
                    line = line.replace(LOOPBACK_ADDRESS, META_ADDRESS);
                } else {
                    line = line.replace(META_ADDRESS, LOOPBACK_ADDRESS);
                }
                itpdConf.set(i, line);
            }
        }

        FileManager.writeToTextFile(context, itpdConfPath, itpdConf, "ignored");

        if (ModulesAux.isITPDSavedStateRunning()) {
            ModulesRestarter.restartITPD(context);
            ModulesStatus.getInstance().setIptablesRulesUpdateRequested(context, true);
        }
    }

    private void readITPDTunnelsConf(Context context) {
        FileManager.readTextFile(context, itpdTunnelsPath, SettingsActivity.itpd_tunnels_tag);
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
                    line = line.replace(LOOPBACK_ADDRESS, META_ADDRESS);
                } else {
                    line = line.replace(META_ADDRESS, LOOPBACK_ADDRESS);
                }
                itpdTunnels.set(i, line);
            }
        }

        FileManager.writeToTextFile(context, itpdTunnelsPath, itpdTunnels, "ignored");
    }

    @Override
    public void OnFileOperationComplete(FileOperationsVariants currentFileOperation,
                                        boolean fileOperationResult, String path, final String tag, final List<String> lines) {

        if (fileOperationResult && currentFileOperation == readTextFile) {
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
    }

    private void registerPreferences() {

        Context context = getActivity();

        if (context == null) {
            return;
        }

        Preference swFixTTL = findPreference(FIX_TTL);
        if (swFixTTL != null) {
            swFixTTL.setOnPreferenceChangeListener(this);

            SharedPreferences sharedPreferences = defaultPreferences.get();
            swFixTTL.setEnabled(!sharedPreferences.getBoolean(RUN_MODULES_WITH_ROOT, false));
        }

        Preference prefTorSiteUnlockTether = findPreference("prefTorSiteUnlockTether");
        if (prefTorSiteUnlockTether != null) {
            SharedPreferences shPref = defaultPreferences.get();
            prefTorSiteUnlockTether.setEnabled(!shPref.getBoolean("pref_common_tor_route_all", false));
        }

        if (!defaultPreferences.get().getBoolean(RUN_MODULES_WITH_ROOT, false)) {
            PreferenceScreen preferenceScreen = findPreference("pref_common");
            PreferenceCategory categoryUseModulesRoot = findPreference("categoryUseModulesRoot");
            if (preferenceScreen != null && categoryUseModulesRoot != null) {
                preferenceScreen.removePreference(categoryUseModulesRoot);
            }
        }

        ArrayList<Preference> preferences = new ArrayList<>();
        preferences.add(findPreference(TOR_TETHERING));
        preferences.add(findPreference("pref_common_tor_route_all"));
        preferences.add(findPreference(ITPD_TETHERING));
        preferences.add(findPreference("pref_common_block_http"));
        preferences.add(findPreference(RUN_MODULES_WITH_ROOT));
        preferences.add(findPreference("swWakelock"));
        preferences.add(findPreference("pref_common_local_eth_device_addr"));
        preferences.add(findPreference(KILL_SWITCH));

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
            preferencesHOTSPOT.add(findPreference(ITPD_TETHERING));
            preferencesHOTSPOT.add(findPreference("pref_common_block_http"));
            preferencesHOTSPOT.add(findPreference(FIX_TTL));
            preferencesHOTSPOT.add(findPreference("pref_common_local_eth_device_addr"));

            if (hotspotSettingsCategory != null) {
                for (Preference preference : preferencesHOTSPOT) {
                    if (preference != null) {
                        hotspotSettingsCategory.removePreference(preference);
                    }
                }
            }

            Preference pref_common_tor_tethering = findPreference(TOR_TETHERING);

            if (pref_common_tor_tethering != null) {
                pref_common_tor_tethering.setSummary(getText(R.string.vpn_tor_tether_summ));
                pref_common_tor_tethering.setOnPreferenceChangeListener(this);
            }
        }


        if (ModulesStatus.getInstance().isRootAvailable()
                && ModulesStatus.getInstance().getMode() != VPN_MODE
                && defaultPreferences.get().getBoolean(RUN_MODULES_WITH_ROOT, false)) {
            Preference pref_common_use_modules_with_root = findPreference(RUN_MODULES_WITH_ROOT);
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
        Preference selectIptables = findPreference(USE_IPTABLES);
        Preference waitIptables = findPreference(WAIT_IPTABLES);
        Preference selectBusybox = findPreference("pref_common_use_busybox");
        Preference killSwitch = findPreference(KILL_SWITCH);

        if (categoryOther != null && selectIptables != null) {
            categoryOther.removePreference(selectIptables);
        }
        if (categoryOther != null && waitIptables != null) {
            categoryOther.removePreference(waitIptables);
        }
        if (categoryOther != null && selectBusybox != null) {
            categoryOther.removePreference(selectBusybox);
        }
        if (categoryOther != null && killSwitch != null) {
            categoryOther.removePreference(killSwitch);
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
            disableProxy();
            Toast.makeText(context, R.string.toastSettings_saved, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        FileManager.deleteOnFileOperationCompleteListener(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}
