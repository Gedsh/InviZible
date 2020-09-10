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
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import pan.alexander.tordnscrypt.settings.tor_apps.ApplicationData;
import pan.alexander.tordnscrypt.settings.tor_apps.UnlockTorAppsFragment;
import pan.alexander.tordnscrypt.utils.InstalledApplications;
import pan.alexander.tordnscrypt.utils.PrefManager;

import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;

public class Rule {
    public int uid;
    public String packageName;
    public String appName;
    public boolean apply = true;

    private static boolean isSystem(String packageName, Context context) {
        return Util.isSystem(packageName, context);
    }

    private static boolean hasInternet(String packageName, Context context) {
        return Util.hasInternet(packageName, context);
    }

    private static boolean isEnabled(PackageInfo info, Context context) {
        return Util.isEnabled(info, context);
    }

    private Rule(ApplicationData info) {
        this.uid = info.getUid();
        this.packageName = info.getPack();
        this.appName = info.toString();
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

            Set<String> setBypassProxy = new PrefManager(context).getSetStrPref("clearnetAppsForProxy");

            // Build rule list
            List<Rule> listRules = new CopyOnWriteArrayList<>();

            InstalledApplications installedApplications = new InstalledApplications(context, Collections.emptySet());
            List<ApplicationData> installedApps = installedApplications.getInstalledApps(false);


            for (ApplicationData info : installedApps)
                try {

                    Rule rule = new Rule(info);

                    String UID = String.valueOf(info.getUid());
                    if (routeAllThroughIniZible) {
                        rule.apply = !setUnlockApps.contains(UID) && !setBypassProxy.contains(UID);
                    } else {
                        rule.apply = setUnlockApps.contains(UID) && !setBypassProxy.contains(UID);
                    }

                    listRules.add(rule);
                } catch (Throwable ex) {
                    Log.e(LOG_TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
                }

            return listRules;
        }
    }
}
