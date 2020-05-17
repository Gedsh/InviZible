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
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Process;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import pan.alexander.tordnscrypt.utils.PrefManager;

import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;

public class Rule {
    public int uid;
    public String packageName;
    public String appName;

    public boolean apply = true;

    private static PackageManager packageManager;

    private static List<PackageInfo> getPackages(Context context) {
        packageManager = context.getPackageManager();
        return new ArrayList<>(packageManager.getInstalledPackages(0));
    }

    private static String getLabel(PackageInfo info) {
        return info.applicationInfo.loadLabel(packageManager).toString();
    }

    private static boolean isSystem(String packageName, Context context) {
        return Util.isSystem(packageName, context);
    }

    private static boolean hasInternet(String packageName, Context context) {
        return Util.hasInternet(packageName, context);
    }

    private static boolean isEnabled(PackageInfo info, Context context) {
        return Util.isEnabled(info, context);
    }

    private Rule(PackageInfo info) {
        this.uid = info.applicationInfo.uid;
        this.packageName = info.packageName;

        if (info.applicationInfo.uid == 0) {
            this.appName = "Root";
        } else if (info.applicationInfo.uid == 1013) {
            this.appName = "Media server";
        } else if (info.applicationInfo.uid == 1020) {
            this.appName = "MulticastDNSResponder";
        } else if (info.applicationInfo.uid == 1021) {
            this.appName = "GPS";
        } else if (info.applicationInfo.uid == 1051) {
            this.appName = "DNS services";
        } else if (info.applicationInfo.uid == 9999) {
            this.appName = "Any app";
        } else {
            this.appName = getLabel(info);
        }
    }

    public static List<Rule> getRules(Context context) {
        synchronized (context.getApplicationContext()) {

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            boolean routeAllThroughIniZible = prefs.getBoolean("pref_fast_all_through_tor", true);

            String unlockAppsStr;
            if (!routeAllThroughIniZible) {
                unlockAppsStr = "unlockApps";
            } else {
                unlockAppsStr = "clearnetApps";
            }

            Set<String> setUnlockApps = new PrefManager(context).getSetStrPref(unlockAppsStr);

            // Build rule list
            List<Rule> listRules = new CopyOnWriteArrayList<>();
            List<PackageInfo> listPI = getPackages(context);

            int userId = Process.myUid() / 100000;

            // Add root
            PackageInfo root = new PackageInfo();
            root.packageName = "root";
            root.versionCode = Build.VERSION.SDK_INT;
            root.versionName = Build.VERSION.RELEASE;
            root.applicationInfo = new ApplicationInfo();
            root.applicationInfo.uid = 0;
            root.applicationInfo.icon = 0;
            listPI.add(root);

            // Add mediaserver
            PackageInfo media = new PackageInfo();
            media.packageName = "android.media";
            media.versionCode = Build.VERSION.SDK_INT;
            media.versionName = Build.VERSION.RELEASE;
            media.applicationInfo = new ApplicationInfo();
            media.applicationInfo.uid = 1013 + userId * 100000;
            media.applicationInfo.icon = 0;
            listPI.add(media);

            // MulticastDNSResponder
            PackageInfo mdr = new PackageInfo();
            mdr.packageName = "android.multicast";
            mdr.versionCode = Build.VERSION.SDK_INT;
            mdr.versionName = Build.VERSION.RELEASE;
            mdr.applicationInfo = new ApplicationInfo();
            mdr.applicationInfo.uid = 1020 + userId * 100000;
            mdr.applicationInfo.icon = 0;
            listPI.add(mdr);

            // Add GPS daemon
            PackageInfo gps = new PackageInfo();
            gps.packageName = "android.gps";
            gps.versionCode = Build.VERSION.SDK_INT;
            gps.versionName = Build.VERSION.RELEASE;
            gps.applicationInfo = new ApplicationInfo();
            gps.applicationInfo.uid = 1021 + userId * 100000;
            gps.applicationInfo.icon = 0;
            listPI.add(gps);

            // Add DNS daemon
            PackageInfo dns = new PackageInfo();
            dns.packageName = "android.dns";
            dns.versionCode = Build.VERSION.SDK_INT;
            dns.versionName = Build.VERSION.RELEASE;
            dns.applicationInfo = new ApplicationInfo();
            dns.applicationInfo.uid = 1051 + userId * 100000;
            dns.applicationInfo.icon = 0;
            listPI.add(dns);

            // Add nobody
            PackageInfo nobody = new PackageInfo();
            nobody.packageName = "nobody";
            nobody.versionCode = Build.VERSION.SDK_INT;
            nobody.versionName = Build.VERSION.RELEASE;
            nobody.applicationInfo = new ApplicationInfo();
            nobody.applicationInfo.uid = 9999;
            nobody.applicationInfo.icon = 0;
            listPI.add(nobody);

            for (PackageInfo info : listPI)
                try {
                    // Skip self
                    if (info.applicationInfo.uid == Process.myUid())
                        continue;

                    Rule rule = new Rule(info);

                    if (routeAllThroughIniZible) {
                        rule.apply = !setUnlockApps.contains(String.valueOf(info.applicationInfo.uid));
                    } else {
                        rule.apply = setUnlockApps.contains(String.valueOf(info.applicationInfo.uid));
                    }

                    listRules.add(rule);
                } catch (Throwable ex) {
                    Log.e(LOG_TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
                }

            packageManager = null;

            return listRules;
        }
    }
}
