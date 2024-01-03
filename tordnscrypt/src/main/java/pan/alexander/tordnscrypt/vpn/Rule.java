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

    Copyright 2019-2024 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.vpn;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import pan.alexander.tordnscrypt.App;
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository;
import pan.alexander.tordnscrypt.settings.tor_apps.ApplicationData;
import pan.alexander.tordnscrypt.utils.apps.InstalledApplicationsManager;

import static pan.alexander.tordnscrypt.proxy.ProxyFragmentKt.CLEARNET_APPS_FOR_PROXY;
import static pan.alexander.tordnscrypt.settings.tor_apps.UnlockTorAppsFragment.CLEARNET_APPS;
import static pan.alexander.tordnscrypt.settings.tor_apps.UnlockTorAppsFragment.UNLOCK_APPS;
import static pan.alexander.tordnscrypt.utils.logger.Logger.loge;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.ALL_THROUGH_TOR;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.USE_PROXY;

public class Rule {
    public int uid;
    public String packageName;
    public String appName;
    public boolean apply = true;

    private static boolean isSystem(String packageName, Context context) {
        return VpnUtils.isSystem(packageName, context);
    }

    private static boolean hasInternet(String packageName, Context context) {
        return VpnUtils.hasInternet(packageName, context);
    }

    private static boolean isEnabled(PackageInfo info, Context context) {
        return VpnUtils.isEnabled(info, context);
    }

    private Rule(ApplicationData info) {
        this.uid = info.getUid();
        this.packageName = info.getPack();
        this.appName = info.toString();
    }

    public static List<Rule> getRules(Context context) {
        synchronized (context.getApplicationContext()) {

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            boolean routeAllThroughIniZible = prefs.getBoolean(ALL_THROUGH_TOR, true);

            String unlockAppsStr;
            if (!routeAllThroughIniZible) {
                unlockAppsStr = UNLOCK_APPS;
            } else {
                unlockAppsStr = CLEARNET_APPS;
            }

            final PreferenceRepository preferences = App.getInstance().getDaggerComponent().getPreferenceRepository().get();

            Set<String> setUnlockApps = preferences.getStringSetPreference(unlockAppsStr);

            boolean useProxy = prefs.getBoolean(USE_PROXY, false);
            Set<String> setBypassProxy;
            if (useProxy) {
                setBypassProxy = preferences.getStringSetPreference(CLEARNET_APPS_FOR_PROXY);
            } else {
                setBypassProxy = new HashSet<>();
            }

            // Build rule list
            List<Rule> listRules = new ArrayList<>();

            List<ApplicationData> installedApps = new InstalledApplicationsManager.Builder()
                    .build()
                    .getInstalledApps();


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
                    loge("Rule getRules", ex, true);
                }

            return listRules;
        }
    }
}
