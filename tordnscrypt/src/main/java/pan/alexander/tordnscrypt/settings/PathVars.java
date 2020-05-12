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

    Copyright 2019-2020 by Garmatin Oleksandr invizible.soft@gmail.com
*/

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.os.Environment;
import android.util.Log;

import androidx.preference.PreferenceManager;

import java.io.File;
import java.util.Objects;

import pan.alexander.tordnscrypt.utils.PrefManager;

import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;

public class PathVars {
    private static volatile PathVars pathVars;

    private final SharedPreferences preferences;

    private String appDataDir;
    private String dnscryptPath;
    private String torPath;
    private String itpdPath;
    private String obfsPath;
    private String snowflakePath;
    private boolean bbOK;


    @SuppressLint("SdCardPath")
    private PathVars(Context context) {

        preferences = PreferenceManager.getDefaultSharedPreferences(context);

        appDataDir = context.getApplicationInfo().dataDir;

        if (appDataDir == null) {
            appDataDir = "/data/data/" + context.getPackageName();
        }

        String nativeLibPath = context.getApplicationInfo().nativeLibraryDir;

        if (!isModulesInstalled(context) || new PrefManager(context).getStrPref("appUID").isEmpty()) {
            saveAppUID(context);
        }

        bbOK = new PrefManager(context).getBoolPref("bbOK");

        dnscryptPath = nativeLibPath + "/libdnscrypt-proxy.so";
        torPath = nativeLibPath + "/libtor.so";
        itpdPath = nativeLibPath + "/libi2pd.so";
        obfsPath = nativeLibPath + "/libobfs4proxy.so";
        snowflakePath = nativeLibPath + "/libsnowflake.so";
    }

    public static PathVars getInstance(Context context) {


        if (pathVars == null) {
            synchronized (PathVars.class) {
                if (pathVars == null) {
                    pathVars = new PathVars(context);
                }
            }
        }
        return pathVars;
    }

    public String getDefaultBackupPath() {
        return Environment.getExternalStorageDirectory().getPath() + "/TorDNSCrypt";
    }

    public String getIptablesPath() {
        String iptablesSelector = preferences.getString("pref_common_use_iptables", "1");

        String path;
        switch (iptablesSelector) {
            case "2":
                path = "iptables ";
                break;
            case "1":

            default:
                path = appDataDir + "/app_bin/iptables ";
                break;
        }

        return path;
    }

    public String getIp6tablesPath() {
        String iptablesSelector = preferences.getString("pref_common_use_iptables", "1");

        String path;
        switch (iptablesSelector) {
            case "2":
                path = "ip6tables ";
                break;
            case "1":

            default:
                if (new File(appDataDir + "/app_bin/ip6tables").isFile()) {
                    path = appDataDir + "/app_bin/ip6tables ";
                } else {
                    path = "ip6tables ";
                }
                break;
        }

        return path;
    }

    public String getBusyboxPath() {

        String busyBoxSelector = preferences.getString("pref_common_use_busybox", "1");

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

    public static boolean isModulesInstalled(Context context) {
        return new PrefManager(Objects.requireNonNull(context)).getBoolPref("DNSCrypt Installed")
                && new PrefManager(Objects.requireNonNull(context)).getBoolPref("Tor Installed")
                && new PrefManager(Objects.requireNonNull(context)).getBoolPref("I2PD Installed");
    }

    public void saveAppUID(Context context) {
        String appUID = "";
        try {
            ApplicationInfo applicationInfo = context.getPackageManager().getApplicationInfo(context.getPackageName(), 0);
            appUID = String.valueOf(applicationInfo.uid);
        } catch (Exception e) {
            Log.e(LOG_TAG, "saveAppUID function fault " + e.getMessage() + " " + e.getCause());
        }

        new PrefManager(Objects.requireNonNull(context)).setStrPref("appUID", appUID);

        Log.i(LOG_TAG, "PathVars AppDataDir " + appDataDir + " AppUID " + appUID);
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

    public String getTorVirtAdrNet() {
        return preferences.getString("VirtualAddrNetwork", "10.192.0.0/10");
    }

    public String getDNSCryptPort() {
        return preferences.getString("listen_port", "5354");
    }

    public String getITPDHttpProxyPort() {
        String itpdHttpProxyPort = preferences.getString("HTTP proxy port", "4444");
        return itpdHttpProxyPort.replaceAll(".+:", "");
    }

    public String getTorTransPort() {
        String torTransPort = preferences.getString("TransPort", "9040");
        return torTransPort.split(" ")[0].replaceAll(".+:", "").replaceAll("\\D+", "");
    }

    public String getDNSCryptFallbackRes() {
        String dnsCryptFallbackResolver = preferences.getString("fallback_resolver", "9.9.9.9");
        if (dnsCryptFallbackResolver.contains(":")) {
            dnsCryptFallbackResolver = dnsCryptFallbackResolver.substring(0, dnsCryptFallbackResolver.indexOf(":"));
        }
        return dnsCryptFallbackResolver;
    }

    public String getTorDNSPort() {
        return preferences.getString("DNSPort", "5400");
    }

    public String getTorSOCKSPort() {
        String torSocksPort = preferences.getString("SOCKSPort", "9050");
        return torSocksPort.split(" ")[0].replaceAll(".+:", "").replaceAll("\\D+", "");
    }

    public String getTorHTTPTunnelPort() {
        String torHttpTunnelPort = preferences.getString("HTTPTunnelPort", "8118");
        return torHttpTunnelPort.split(" ")[0].replaceAll(".+:", "").replaceAll("\\D+", "");
    }

    public String getITPDSOCKSPort() {
        String itpdSocksPort = preferences.getString("Socks proxy port", "4447");
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
}
