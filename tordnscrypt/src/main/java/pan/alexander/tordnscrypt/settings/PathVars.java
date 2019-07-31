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
import android.content.pm.PackageManager;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;

import java.util.Objects;

import pan.alexander.tordnscrypt.utils.PrefManager;

import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;

public class PathVars {
    public String appDataDir;
    public String SharedPreferences;
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
    public String storageDir;
    public String mediaDir;
    public String pathBackup;
    public String torSOCKSPort;
    public String torHTTPTunnelPort;
    public String itpdSOCKSPort;
    public final String rejectAddress = "10.191.0.2";

    public PathVars (Context context) {

        if(!new PrefManager(Objects.requireNonNull(context)).getBoolPref("DNSCrypt Installed")
                && !new PrefManager(Objects.requireNonNull(context)).getBoolPref("Tor Installed")
                && !new PrefManager(Objects.requireNonNull(context)).getBoolPref("I2PD Installed")){
            String dataDir = context.getApplicationInfo().dataDir;
            String storageDir = Environment.getExternalStorageDirectory().getPath();
            new PrefManager(Objects.requireNonNull(context)).setStrPref("AppDataDir",dataDir);
            new PrefManager(Objects.requireNonNull(context)).setStrPref("StorageDir",storageDir);

            String appUID = "";
            try {
                ApplicationInfo applicationInfo = context.getPackageManager().getApplicationInfo(context.getPackageName(),0);
                appUID = String.valueOf(applicationInfo.uid);
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }

            new PrefManager(Objects.requireNonNull(context)).setStrPref("appUID",appUID);

            Log.i(LOG_TAG, "PathVars AppDataDir " + dataDir + " AppUID " + appUID);
        }

        appDataDir = context.getApplicationInfo().dataDir;
        SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(context);
        dnsCryptPort = shPref.getString("listen_port","5354");
        torSOCKSPort = shPref.getString("SOCKSPort","9050");
        torHTTPTunnelPort = shPref.getString("HTTPTunnelPort","8118");
        itpdSOCKSPort = shPref.getString("Socks proxy port","4447");

        itpdHttpProxyPort = shPref.getString("HTTP proxy port","4444");
        torTransPort = shPref.getString("TransPort","9040").replaceAll(".+:","");
        dnsCryptFallbackRes = shPref.getString("fallback_resolver","9.9.9.9");
        torDNSPort = shPref.getString("DNSPort","5400");
        torVirtAdrNet = shPref.getString("VirtualAddrNetworkIPv4","10.0.0.0/10");
        dnscryptPath = appDataDir+"/app_bin/dnscrypt-proxy";
        torPath = appDataDir+"/app_bin/tor";
        itpdPath = appDataDir+"/app_bin/i2pd";
        obfsPath = appDataDir+"/app_bin/obfs4proxy";

        String selectBusyBox = shPref.getString("pref_common_use_busybox","1");

        switch (selectBusyBox) {
            case "1":
                if (new PrefManager(context).getBoolPref("bbOK")) {
                    busyboxPath = "busybox ";
                } else {
                    busyboxPath = appDataDir+"/app_bin/busybox ";
                }
                break;
            case "2":
                busyboxPath = "busybox ";
                break;
            case "3":
                busyboxPath = appDataDir+"/app_bin/busybox ";
                break;
            case "4":
                busyboxPath = "";
                break;

                default:
                    if (new PrefManager(context).getBoolPref("bbOK")) {
                        busyboxPath = "busybox ";
                    } else {
                        busyboxPath = appDataDir+"/app_bin/busybox ";
                    }
                    break;
        }

        String selectIptables = shPref.getString("pref_common_use_iptables","1");
        switch (selectIptables) {
            case "1":
                iptablesPath = appDataDir+"/app_bin/";
                break;
            case "2":
                iptablesPath = "";
                break;

                default:
                    iptablesPath = appDataDir+"/app_bin/";
                    break;
        }



        storageDir = Environment.getExternalStorageDirectory().getPath();
        //StringBuilder sb = new StringBuilder();
        //mediaDir = sb.append("/data/media/").append(storageDir.replaceAll("\\D+","")).toString();
        pathBackup = storageDir+"/TorDNSCrypt";
    }
}
