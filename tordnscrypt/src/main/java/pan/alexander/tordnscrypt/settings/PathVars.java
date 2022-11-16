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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.os.Process;
import android.util.Log;

import androidx.preference.PreferenceManager;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pan.alexander.tordnscrypt.App;
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository;

import static pan.alexander.tordnscrypt.utils.Constants.QUAD_DNS_41;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.USE_IPTABLES;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.WAIT_IPTABLES;
import static pan.alexander.tordnscrypt.utils.root.RootExecService.LOG_TAG;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class PathVars {
    private final SharedPreferences preferences;

    private String appDataDir;
    private final String dnscryptPath;
    private final String torPath;
    private final String itpdPath;
    private final String obfsPath;
    private final String snowflakePath;
    private final String nflogPath;
    private final boolean bbOK;
    private volatile int appUid = -1;
    private volatile String appUidStr = "";

    @SuppressLint("SdCardPath")
    @Inject
    PathVars(Context context) {

        preferences = PreferenceManager.getDefaultSharedPreferences(context);

        appDataDir = context.getApplicationInfo().dataDir;

        if (appDataDir == null) {
            appDataDir = "/data/data/" + context.getPackageName();
        }

        String nativeLibPath = context.getApplicationInfo().nativeLibraryDir;

        bbOK = App.getInstance().getDaggerComponent().getPreferenceRepository().get().getBoolPreference("bbOK");

        dnscryptPath = nativeLibPath + "/libdnscrypt-proxy.so";
        torPath = nativeLibPath + "/libtor.so";
        itpdPath = nativeLibPath + "/libi2pd.so";
        obfsPath = nativeLibPath + "/libobfs4proxy.so";
        snowflakePath = nativeLibPath + "/libsnowflake.so";
        nflogPath = nativeLibPath + "/libnflog.so";
    }

    public String getDefaultBackupPath() {
        return Environment.getExternalStorageDirectory().getPath() + "/TorDNSCrypt";
    }

    public String getIptablesPath() {
        String iptablesSelector = preferences.getString(USE_IPTABLES, "2");
        if (iptablesSelector == null) {
            iptablesSelector = "2";
        }

        boolean waitIptables = preferences.getBoolean(WAIT_IPTABLES, true);

        String path;
        switch (iptablesSelector) {
            case "1":
                path = appDataDir + "/app_bin/iptables ";
                break;
            case "2":
            default:
                path = "iptables ";
                break;
        }

        if (waitIptables) {
            path += "-w ";
        }

        return path;
    }

    public String getIp6tablesPath() {
        String iptablesSelector = preferences.getString(USE_IPTABLES, "2");
        if (iptablesSelector == null) {
            iptablesSelector = "2";
        }

        boolean waitIptables = preferences.getBoolean(WAIT_IPTABLES, true);

        String path;
        switch (iptablesSelector) {
            case "1":
                path = appDataDir + "/app_bin/ip6tables ";
                break;
            case "2":
            default:
                path = "ip6tables ";
                break;
        }

        if (waitIptables) {
            path += "-w ";
        }

        return path;
    }

    public String getBusyboxPath() {

        String busyBoxSelector = preferences.getString("pref_common_use_busybox", "1");
        if (busyBoxSelector == null) {
            busyBoxSelector = "1";
        }

        String path;
        switch (busyBoxSelector) {
            case "2":
                path = "busybox ";
                break;
            case "3":
                path = appDataDir + "/app_bin/busybox ";
                break;
            case "4":
                path = "";
                break;
            case "1":

            default:
                if (bbOK) {
                    path = "busybox ";
                } else {
                    path = appDataDir + "/app_bin/busybox ";
                }
                break;
        }
        return path;
    }

    public static boolean isModulesInstalled(PreferenceRepository preferences) {
        return preferences.getBoolPreference("DNSCrypt Installed")
                && preferences.getBoolPreference("Tor Installed")
                && preferences.getBoolPreference("I2PD Installed");
    }

    public String getRejectAddress() {
        return "10.191.0.2";
    }

    public String getAppDataDir() {
        return appDataDir;
    }

    public String getDNSCryptPath() {
        return dnscryptPath;
    }

    public String getTorPath() {
        return torPath;
    }

    public String getITPDPath() {
        return itpdPath;
    }

    public String getObfsPath() {
        return obfsPath;
    }

    public String getSnowflakePath() {
        return snowflakePath;
    }

    public String getNflogPath() {
        return nflogPath;
    }

    public String getTorVirtAdrNet() {
        return preferences.getString("VirtualAddrNetwork", "10.192.0.0/10");
    }

    public String getDNSCryptPort() {
        return preferences.getString("listen_port", "5354");
    }

    public String getITPDHttpProxyPort() {
        String itpdHttpProxyPort = preferences.getString("HTTP proxy port", "4444");
        if (itpdHttpProxyPort == null) {
            itpdHttpProxyPort = "4444";
        }
        return itpdHttpProxyPort.replaceAll(".+:", "");
    }

    public String getTorTransPort() {
        String torTransPort = preferences.getString("TransPort", "9040");
        if (torTransPort == null) {
            torTransPort = "9040";
        }
        return torTransPort.split(" ")[0].replaceAll(".+:", "").replaceAll("\\D+", "");
    }

    public String getDNSCryptFallbackRes() {

        Pattern pattern =
                Pattern.compile("((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)");

        String dnsCryptFallbackResolver = preferences.getString("bootstrap_resolvers", QUAD_DNS_41);
        if (dnsCryptFallbackResolver == null) {
            dnsCryptFallbackResolver = QUAD_DNS_41;
        }

        Matcher matcher = pattern.matcher(dnsCryptFallbackResolver);
        String fallbackResolver = QUAD_DNS_41;
        if (matcher.find()) {
            fallbackResolver = matcher.group();
        }

        return fallbackResolver;
    }

    public String getTorDNSPort() {
        return preferences.getString("DNSPort", "5400");
    }

    public String getTorSOCKSPort() {
        String torSocksPort = preferences.getString("SOCKSPort", "9050");
        if (torSocksPort == null) {
            torSocksPort = "9050";
        }
        return torSocksPort.split(" ")[0].replaceAll(".+:", "").replaceAll("\\D+", "");
    }

    public String getTorHTTPTunnelPort() {
        String torHttpTunnelPort = preferences.getString("HTTPTunnelPort", "8118");
        if (torHttpTunnelPort == null) {
            torHttpTunnelPort = "8118";
        }
        return torHttpTunnelPort.split(" ")[0].replaceAll(".+:", "").replaceAll("\\D+", "");
    }

    public String getITPDSOCKSPort() {
        String itpdSocksPort = preferences.getString("Socks proxy port", "4447");
        if (itpdSocksPort == null) {
            itpdSocksPort = "4447";
        }
        return itpdSocksPort.replaceAll(".+:", "");
    }

    public String getDNSCryptBlackListPath() {
        return appDataDir + "/app_data/dnscrypt-proxy/blacklist.txt";
    }

    public String getDNSCryptLocalBlackListPath() {
        return appDataDir + "/app_data/dnscrypt-proxy/blacklist-local.txt";
    }

    public String getDNSCryptRemoteBlackListPath() {
        return appDataDir + "/app_data/dnscrypt-proxy/blacklist-remote.txt";
    }

    public String getDNSCryptIPBlackListPath() {
        return appDataDir + "/app_data/dnscrypt-proxy/ip-blacklist.txt";
    }

    public String getDNSCryptLocalIPBlackListPath() {
        return appDataDir + "/app_data/dnscrypt-proxy/ip-blacklist-local.txt";
    }

    public String getDNSCryptRemoteIPBlackListPath() {
        return appDataDir + "/app_data/dnscrypt-proxy/ip-blacklist-remote.txt";
    }

    public String getDNSCryptWhiteListPath() {
        return appDataDir + "/app_data/dnscrypt-proxy/whitelist.txt";
    }

    public String getDNSCryptLocalWhiteListPath() {
        return appDataDir + "/app_data/dnscrypt-proxy/whitelist-local.txt";
    }

    public String getDNSCryptRemoteWhiteListPath() {
        return appDataDir + "/app_data/dnscrypt-proxy/whitelist-remote.txt";
    }

    public String getDNSCryptCloakingRulesPath() {
        return appDataDir + "/app_data/dnscrypt-proxy/cloaking-rules.txt";
    }

    public String getDNSCryptLocalCloakingRulesPath() {
        return appDataDir + "/app_data/dnscrypt-proxy/cloaking-rules-local.txt";
    }

    public String getDNSCryptRemoteCloakingRulesPath() {
        return appDataDir + "/app_data/dnscrypt-proxy/cloaking-rules-remote.txt";
    }

    public String getDNSCryptForwardingRulesPath() {
        return appDataDir + "/app_data/dnscrypt-proxy/forwarding-rules.txt";
    }

    public String getDNSCryptLocalForwardingRulesPath() {
        return appDataDir + "/app_data/dnscrypt-proxy/forwarding-rules-local.txt";
    }

    public String getDNSCryptRemoteForwardingRulesPath() {
        return appDataDir + "/app_data/dnscrypt-proxy/forwarding-rules-remote.txt";
    }

    public String getDNSCryptCaptivePortalsPath() {
        return appDataDir + "/app_data/dnscrypt-proxy/captive-portals.txt";
    }

    public String getCacheDirPath(Context context) {
        String cacheDirPath = "/storage/emulated/0/Android/data/" + context.getPackageName() + "/cache";

        try {
            File cacheDir = context.getExternalCacheDir();
            if (cacheDir == null) {
                cacheDir = context.getCacheDir();
            }

            if (!cacheDir.isDirectory()) {
                if (cacheDir.mkdirs()) {
                    Log.i(LOG_TAG, "PathVars getCacheDirPath create cache dir success");
                    if (cacheDir.setReadable(true) && cacheDir.setWritable(true)) {
                        Log.i(LOG_TAG, "PathVars getCacheDirPath chmod cache dir success");
                    } else {
                        Log.e(LOG_TAG, "PathVars getCacheDirPath chmod cache dir failed");
                    }
                } else {
                    Log.e(LOG_TAG, "PathVars getCacheDirPath create cache dir failed");
                }
            }

            cacheDirPath = cacheDir.getCanonicalPath();

        } catch (Exception e) {
            Log.e(LOG_TAG, "PathVars getCacheDirPath exception " + e.getMessage() + " " + e.getCause());
        }

        return cacheDirPath;
    }

    public String getDnscryptConfPath() {
        return appDataDir + "/app_data/dnscrypt-proxy/dnscrypt-proxy.toml";
    }

    public String getTorConfPath() {
        return appDataDir + "/app_data/tor/tor.conf";
    }

    public String getItpdConfPath() {
        return appDataDir + "/app_data/i2pd/i2pd.conf";
    }

    public String getItpdTunnelsPath() {
        return appDataDir + "/app_data/i2pd/tunnels.conf";
    }

    public synchronized int getAppUid() {
        if (appUid < 0) {
            appUid = Process.myUid();
        }
        return appUid;
    }

    public synchronized String getAppUidStr() {
        if (appUidStr.isEmpty()) {
            appUidStr = String.valueOf(getAppUid());
        }
        return appUidStr;
    }
}
