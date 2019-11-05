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

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.os.Environment;
import android.support.v7.preference.PreferenceManager;
import android.util.Log;

import java.util.Objects;

import pan.alexander.tordnscrypt.utils.PrefManager;

import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;

public class PathVars {
    public String appDataDir;
    public String dnsCryptPort;
    public String itpdHttpProxyPort;
    public String torTransPort;
    public String dnsCryptFallbackRes;
    public String torDNSPort;
    public String torVirtAdrNet;
    public String dnscryptPath;
    public String torPath;
    public String itpdPath;
    public String obfsPath;
    public String busyboxPath;
    public String iptablesPath;
    public String mediaDir;
    public String pathBackup;
    public String torSOCKSPort;
    public String torHTTPTunnelPort;
    public String itpdSOCKSPort;
    public final String rejectAddress = "10.191.0.2";

    public PathVars (Context context) {

        appDataDir = context.getApplicationInfo().dataDir;

        if (appDataDir == null) {
            appDataDir = "/data/data/" + context.getPackageName();
        }

        if(!isModulesInstalled(context)){
            saveAppUID(context);
        }

        SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(context);

        setAuxPaths(shPref);

        busyboxPath = getBusyBoxPath(context, shPref);

        iptablesPath = getIptablesPath(shPref);

        pathBackup = getDefaultBackupPath();
    }

    private void setAuxPaths(SharedPreferences shPref) {
        dnsCryptPort = shPref.getString("listen_port","5354");
        torSOCKSPort = shPref.getString("SOCKSPort","9050");
        torHTTPTunnelPort = shPref.getString("HTTPTunnelPort","8118");
        itpdSOCKSPort = shPref.getString("Socks proxy port","4447");

        itpdHttpProxyPort = shPref.getString("HTTP proxy port","4444");

        torTransPort = shPref.getString("TransPort","9040");
        if (torTransPort != null) {
            torTransPort = torTransPort.replaceAll(".+:","");
        }

        dnsCryptFallbackRes = shPref.getString("fallback_resolver","9.9.9.9");
        torDNSPort = shPref.getString("DNSPort","5400");
        torVirtAdrNet = shPref.getString("VirtualAddrNetworkIPv4","10.0.0.0/10");
        dnscryptPath = appDataDir+"/app_bin/dnscrypt-proxy";
        torPath = appDataDir+"/app_bin/tor";
        itpdPath = appDataDir+"/app_bin/i2pd";
        obfsPath = appDataDir+"/app_bin/obfs4proxy";
    }

    private String getDefaultBackupPath() {
        String storageDir = Environment.getExternalStorageDirectory().getPath();
        return storageDir +"/TorDNSCrypt";
    }

    private String getIptablesPath(SharedPreferences shPref) {
        String iptablesSelector = shPref.getString("pref_common_use_iptables","1");

        if (iptablesSelector == null) {
            return "";
        }

        String path;
        switch (iptablesSelector) {
            case "2":
                path = "";
                break;
            case "1":

            default:
                path = appDataDir+"/app_bin/";
                break;
        }

        return path;
    }

    private String getBusyBoxPath(Context context, SharedPreferences shPref) {

        String busyBoxSelector = shPref.getString("pref_common_use_busybox","1");

        if (busyBoxSelector == null) {
            return "";
        }

        String path;
        switch (busyBoxSelector) {
            case "2":
                path = "busybox ";
                break;
            case "3":
                path = appDataDir+"/app_bin/busybox ";
                break;
            case "4":
                path = "";
                break;
            case "1":

            default:
                if (new PrefManager(context).getBoolPref("bbOK")) {
                    path = "busybox ";
                } else {
                    path = appDataDir+"/app_bin/busybox ";
                }
                break;
        }
        return path;
    }

    private boolean isModulesInstalled(Context context) {
        return new PrefManager(Objects.requireNonNull(context)).getBoolPref("DNSCrypt Installed")
                && new PrefManager(Objects.requireNonNull(context)).getBoolPref("Tor Installed")
                && new PrefManager(Objects.requireNonNull(context)).getBoolPref("I2PD Installed");
    }

    public void saveAppUID(Context context) {
        String appUID = "";
        try {
            ApplicationInfo applicationInfo = context.getPackageManager().getApplicationInfo(context.getPackageName(),0);
            appUID = String.valueOf(applicationInfo.uid);
        } catch (Exception e) {
            Log.e(LOG_TAG, "saveAppUID function fault " + e.getMessage() + " " + e.getCause());
        }

        new PrefManager(Objects.requireNonNull(context)).setStrPref("appUID",appUID);

        Log.i(LOG_TAG, "PathVars AppDataDir " + appDataDir + " AppUID " + appUID);
    }
}
