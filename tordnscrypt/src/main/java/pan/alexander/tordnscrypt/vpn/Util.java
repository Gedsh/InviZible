package pan.alexander.tordnscrypt.vpn;
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

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkInfo;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import androidx.core.net.ConnectivityManagerCompat;

import java.io.File;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

import pan.alexander.tordnscrypt.utils.CachedExecutor;
import pan.alexander.tordnscrypt.vpn.service.ServiceVPN;

import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;

public class Util {

    private static native String jni_getprop(String name);

    private static native boolean is_numeric_address(String ip);

    public static String getSelfVersionName(Context context) {
        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return pInfo.versionName;
        } catch (PackageManager.NameNotFoundException ex) {
            return ex.toString();
        }
    }

    public static int getSelfVersionCode(Context context) {
        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return pInfo.versionCode;
        } catch (PackageManager.NameNotFoundException ex) {
            return -1;
        }
    }

    public static boolean isConnected(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo ni = (cm == null ? null : cm.getActiveNetworkInfo());
        return (ni != null && ni.isConnected());
    }

    public static List<String> getDefaultDNS(Context context) {
        List<String> listDns = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network an = null;
            if (cm != null) {
                an = cm.getActiveNetwork();
            }
            if (an != null) {
                LinkProperties lp = cm.getLinkProperties(an);
                if (lp != null) {
                    List<InetAddress> dns = lp.getDnsServers();
                    for (InetAddress d : dns) {
                        Log.i(LOG_TAG, "DNS from LP: " + d.getHostAddress());
                        listDns.add(d.getHostAddress().split("%")[0]);
                    }
                }
            }
        } else {
            String dns1 = jni_getprop("net.dns1");
            String dns2 = jni_getprop("net.dns2");
            if (dns1 != null)
                listDns.add(dns1.split("%")[0]);
            if (dns2 != null)
                listDns.add(dns2.split("%")[0]);
        }

        return listDns;
    }

    static boolean isNumericAddress(String ip) {
        return is_numeric_address(ip);
    }

    static boolean isSystem(String packageName, Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo info = pm.getPackageInfo(packageName, 0);
            return ((info.applicationInfo.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0);
        } catch (PackageManager.NameNotFoundException ignore) {
            return false;
        }
    }

    static boolean hasInternet(String packageName, Context context) {
        PackageManager pm = context.getPackageManager();
        return (pm.checkPermission("android.permission.INTERNET", packageName) == PackageManager.PERMISSION_GRANTED);
    }

    static boolean isEnabled(PackageInfo info, Context context) {
        int setting;
        try {
            PackageManager pm = context.getPackageManager();
            setting = pm.getApplicationEnabledSetting(info.packageName);
        } catch (IllegalArgumentException ex) {
            setting = PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
            Log.w(LOG_TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
        }
        if (setting == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
            return info.applicationInfo.enabled;
        else
            return (setting == PackageManager.COMPONENT_ENABLED_STATE_ENABLED);
    }

    public static void canFilterAsynchronous(ServiceVPN serviceVPN) {

        CachedExecutor.INSTANCE.getExecutorService().submit(() -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && serviceVPN != null) {
                serviceVPN.canFilter = true;
                return;
            }

            // https://android-review.googlesource.com/#/c/206710/1/untrusted_app.te
            File tcp = new File("/proc/net/tcp");
            File tcp6 = new File("/proc/net/tcp6");

            try {
                if (tcp.exists() && tcp.canRead() && serviceVPN != null)
                    serviceVPN.canFilter = true;
                    return;
            } catch (SecurityException ignored) {}

            try {
                if (tcp6.exists() && tcp6.canRead() && serviceVPN != null){
                    serviceVPN.canFilter = true;
                }
            } catch (SecurityException ignored) {
                if (serviceVPN != null){
                    serviceVPN.canFilter = false;
                }
            }
        });
    }

    public static boolean canFilter(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            return true;

        // https://android-review.googlesource.com/#/c/206710/1/untrusted_app.te
        File tcp = new File("/proc/net/tcp");
        File tcp6 = new File("/proc/net/tcp6");
        try {
            if (tcp.exists() && tcp.canRead())
                return true;
        } catch (SecurityException ignored) {
        }
        try {
            return (tcp6.exists() && tcp6.canRead());
        } catch (SecurityException ignored) {
            return false;
        }
    }

    public static boolean isPrivateDns(Context context) {
        String dns_mode = Settings.Global.getString(context.getContentResolver(), "private_dns_mode");
        Log.i(LOG_TAG, "Private DNS mode=" + dns_mode);
        if (dns_mode == null) {
            dns_mode = "off";
        }
        return (!"off".equals(dns_mode));
    }

    public static boolean isMeteredNetwork(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        return (cm != null && ConnectivityManagerCompat.isActiveNetworkMetered(cm));
    }

    public static boolean isIpInSubnet(final String ip, final String network) {
        boolean result = false;

        try {
            String net = network;
            int prefix = 0;
            if (network.contains("/")) {
                net = network.substring(0, network.indexOf("/"));
                prefix = Integer.parseInt(network.substring(network.indexOf("/") + 1));
            }

            final byte[] ipBin = java.net.InetAddress.getByName(ip).getAddress();
            final byte[] netBin = java.net.InetAddress.getByName(net).getAddress();
            if (ipBin.length != netBin.length) return false;
            int p = prefix;
            int i = 0;
            while (p >= 8) {
                if (ipBin[i] != netBin[i]) return false;
                ++i;
                p -= 8;
            }
            final int m = (65280 >> p) & 255;
            result = (ipBin[i] & m) == (netBin[i] & m);
        } catch (Exception e) {
            Log.e(LOG_TAG, "VPN UTIL isIpInSubnet exception " + e.getMessage() + e.getCause());
        }

        return result;
    }
}
