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

    Copyright 2019-2025 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.vpn;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.UserHandle;
import android.os.UserManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import pan.alexander.tordnscrypt.App;
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository;
import pan.alexander.tordnscrypt.modules.ModulesStatus;
import pan.alexander.tordnscrypt.settings.tor_apps.ApplicationData;
import pan.alexander.tordnscrypt.utils.apps.InstalledApplicationsManager;
import pan.alexander.tordnscrypt.utils.Utils;

import static pan.alexander.tordnscrypt.proxy.ProxyFragmentKt.CLEARNET_APPS_FOR_PROXY;
import static pan.alexander.tordnscrypt.utils.logger.Logger.loge;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.ALL_THROUGH_TOR;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.CLEARNET_APPS;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.UNLOCK_APPS;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.USE_PROXY_NON_TOR;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.MULTI_USER_SUPPORT;

import androidx.preference.PreferenceManager;

public class Rule {
    public int uid;
    public String packageName;
    public String appName;
    public boolean apply = true;
    private static final AtomicLong inventoryVersion = new AtomicLong();

    private static boolean isSystem(String packageName, Context context) {
        return VpnUtils.isSystem(packageName, context);
    }

    private static boolean hasInternet(String packageName, Context context) {
        return VpnUtils.hasInternet(packageName, context);
    }

    private static boolean isEnabled(PackageInfo info, Context context) {
        return VpnUtils.isEnabled(info, context);
    }

    private Rule(AppIdentity info) {
        this.uid = info.uid;
        this.packageName = info.packageName;
        this.appName = info.appName;
    }

    private static final class AppIdentity {
        final int uid;
        final String packageName;
        final String appName;

        AppIdentity(ApplicationData app) {
            uid = app.getUid();
            packageName = app.getPack();
            appName = app.toString();
        }
    }

    public static void invalidateInstalledApps() {
        inventoryVersion.incrementAndGet();
    }

    /** Owned by one VPN handler; retains no Context, icons or routing decisions. */
    public static final class Inventory {
        // Android partitions application UIDs into ranges of this size per user.
        private static final int UIDS_PER_USER = 100_000;
        static final class Scan {
            final List<ApplicationData> apps;
            final boolean complete;

            Scan(List<ApplicationData> apps, boolean complete) {
                this.apps = apps;
                this.complete = complete;
            }
        }

        interface Source {
            Scan load();

            default List<UserHandle> profiles(Context context) {
                UserManager users = (UserManager) context.getSystemService(Context.USER_SERVICE);
                return users == null ? Collections.emptyList() : users.getUserProfiles();
            }
        }

        private final Source source;
        private List<AppIdentity> apps = Collections.emptyList();
        private long version = -1;
        private boolean multiUser;
        private boolean rootAvailable;
        private boolean acrossUsersAllowed;
        private List<UserHandle> profiles = Collections.emptyList();
        private Locale locale;

        public Inventory() {
            this(() -> {
                InstalledApplicationsManager manager = new InstalledApplicationsManager.Builder().build();
                return new Scan(manager.getInstalledApps(), manager.isInventoryComplete());
            });
        }

        Inventory(Source source) {
            this.source = source;
        }

        public List<Rule> getRules(Context context) {
            try {
                return getRules(context, PreferenceManager.getDefaultSharedPreferences(context),
                        App.getInstance().getDaggerComponent().getPreferenceRepository().get());
            } catch (Exception e) {
                loge("Rule.Inventory getRules", e);
                return Collections.emptyList();
            }
        }

        synchronized List<Rule> getRules(Context context, SharedPreferences prefs,
                                         PreferenceRepository preferences) {
            try {
                long currentVersion = inventoryVersion.get();
                boolean currentMultiUser = prefs.getBoolean(MULTI_USER_SUPPORT, true);
                boolean currentRoot = ModulesStatus.getInstance().isRootAvailable();
                boolean currentAcrossUsers = !currentMultiUser
                        || Utils.isInteractAcrossUsersPermissionGranted(context);
                List<UserHandle> currentProfiles = currentMultiUser
                        ? source.profiles(context) : Collections.emptyList();
                Locale currentLocale = Locale.getDefault();
                if (version != currentVersion || multiUser != currentMultiUser
                        || rootAvailable != currentRoot || acrossUsersAllowed != currentAcrossUsers
                        || !profiles.equals(currentProfiles)
                        || !currentLocale.equals(locale)) {
                    apps = Collections.emptyList();
                    version = -1;
                    Scan scan = source.load();
                    List<ApplicationData> installed = scan.apps;
                    // The manager includes synthetic system identities on success; empty means failure.
                    if (installed.isEmpty()) {
                        return Collections.emptyList();
                    }
                    List<AppIdentity> fresh = new ArrayList<>(installed.size());
                    int currentUser = context.getApplicationInfo().uid / UIDS_PER_USER;
                    boolean hasCrossProfileApps = false;
                    for (ApplicationData app : installed) {
                        fresh.add(new AppIdentity(app));
                        // Ignore synthetic kernel/root/native-system identities.
                        if (app.getUid() >= android.os.Process.FIRST_APPLICATION_UID
                                && app.getUid() / UIDS_PER_USER != currentUser) {
                            hasCrossProfileApps = true;
                        }
                    }
                    // This profile's broadcasts cannot reliably invalidate other profiles' apps.
                    if (hasCrossProfileApps || !scan.complete) {
                        return buildRules(prefs, preferences, fresh);
                    }
                    apps = fresh;
                    profiles = new ArrayList<>(currentProfiles);
                    multiUser = currentMultiUser;
                    rootAvailable = currentRoot;
                    acrossUsersAllowed = currentAcrossUsers;
                    locale = currentLocale;
                    // If invalidated during the scan, the next call must refresh again.
                    version = currentVersion;
                }
                return buildRules(prefs, preferences, apps);
            } catch (Exception e) {
                loge("Rule.Inventory getRules", e);
                return Collections.emptyList();
            }
        }
    }

    private static List<Rule> buildRules(SharedPreferences prefs, PreferenceRepository preferences,
                                         List<AppIdentity> installedApps) {
        boolean routeAllThroughInviZible = prefs.getBoolean(ALL_THROUGH_TOR, true);

        String routingPreferenceKey;
        if (!routeAllThroughInviZible) {
            routingPreferenceKey = UNLOCK_APPS;
        } else {
            routingPreferenceKey = CLEARNET_APPS;
        }

        Set<String> setUnlockApps = preferences.getStringSetPreference(routingPreferenceKey);

        boolean useProxy = prefs.getBoolean(USE_PROXY_NON_TOR, false);
        Set<String> setBypassProxy;
        if (useProxy) {
            setBypassProxy = preferences.getStringSetPreference(CLEARNET_APPS_FOR_PROXY);
        } else {
            setBypassProxy = Collections.emptySet();
        }

        // Build rule list
        List<Rule> listRules = new ArrayList<>(installedApps.size());

        for (AppIdentity info : installedApps) {
            try {

                Rule rule = new Rule(info);

                String uidString = String.valueOf(info.uid);
                if (routeAllThroughInviZible) {
                    rule.apply = !setUnlockApps.contains(uidString) && !setBypassProxy.contains(uidString);
                } else {
                    rule.apply = setUnlockApps.contains(uidString) && !setBypassProxy.contains(uidString);
                }

                listRules.add(rule);
            } catch (Throwable ex) {
                loge("Rule buildRules", ex, true);
            }
        }

        return listRules;
    }
}
