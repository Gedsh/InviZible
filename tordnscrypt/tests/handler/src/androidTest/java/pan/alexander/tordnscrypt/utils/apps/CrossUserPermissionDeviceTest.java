package pan.alexander.tordnscrypt.utils.apps;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Process;
import android.util.Log;

import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelStore;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceManager;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import kotlinx.coroutines.Dispatchers;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.util.List;
import java.util.TreeMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import pan.alexander.tordnscrypt.App;
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository;
import pan.alexander.tordnscrypt.modules.ModulesService;
import pan.alexander.tordnscrypt.modules.ModulesStatus;
import pan.alexander.tordnscrypt.settings.firewall.FirewallAppModel;
import pan.alexander.tordnscrypt.settings.firewall.FirewallState;
import pan.alexander.tordnscrypt.settings.firewall.FirewallViewModel;
import pan.alexander.tordnscrypt.settings.tor_apps.ApplicationData;
import pan.alexander.tordnscrypt.utils.Utils;
import pan.alexander.tordnscrypt.utils.root.RootExecService;
import pan.alexander.tordnscrypt.vpn.Rule;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.MULTI_USER_SUPPORT;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.ROOT_IS_AVAILABLE;

/** Opt-in integration check: authorizes a real su command in an isolated test installation. */
@RunWith(AndroidJUnit4.class)
public final class CrossUserPermissionDeviceTest {
    private static boolean rulesUpdateRequested(ModulesStatus modules) throws Exception {
        java.lang.reflect.Method getter = ModulesStatus.class.getDeclaredMethod("isIptablesRulesUpdateRequested");
        getter.setAccessible(true);
        return (boolean) getter.invoke(modules);
    }

    private static Map<Integer, Integer> counts(List<ApplicationData> apps) {
        Map<Integer, Integer> counts = new TreeMap<>();
        for (ApplicationData app : apps) {
            if (app.getUid() >= Process.FIRST_APPLICATION_UID) {
                int user = app.getUid() / 100_000;
                Integer count = counts.get(user);
                counts.put(user, count == null ? 1 : count + 1);
            }
        }
        return counts;
    }

    @Test public void automaticGrantRefreshesInventory() throws Exception {
        assumeTrue("Opt in only on a rooted device", "true".equals(
                InstrumentationRegistry.getArguments().getString("crossUserGrantProbe")));
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue("Use an isolated validation installation", context.getPackageName().endsWith(".profilescopetest"));
        assertFalse("Begin with the permission denied", Utils.isInteractAcrossUsersPermissionGranted(context));
        assertFalse("The isolated modules service must be idle", ModulesService.serviceIsRunning);
        App app = App.getInstance();
        PreferenceRepository preferences = app.getDaggerComponent().getPreferenceRepository().get();
        SharedPreferences defaults = PreferenceManager.getDefaultSharedPreferences(context);
        boolean root = preferences.getBoolPreference(ROOT_IS_AVAILABLE);
        boolean multiUserPresent = defaults.contains(MULTI_USER_SUPPORT);
        boolean multiUser = defaults.getBoolean(MULTI_USER_SUPPORT, true);
        ModulesStatus modules = ModulesStatus.getInstance();
        boolean update = rulesUpdateRequested(modules);
        ViewModelStore store = new ViewModelStore();
        FirewallViewModel model = new FirewallViewModel(app.getDaggerComponent().getPreferenceRepository(),
                Dispatchers.getDefault(), app.getDaggerComponent().getPathVars());
        CountDownLatch changed = new CountDownLatch(1), ready = new CountDownLatch(1);
        AtomicInteger notifications = new AtomicInteger();
        LocalBroadcastManager broadcasts = LocalBroadcastManager.getInstance(context);
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context ignored, Intent intent) {
                notifications.incrementAndGet();
                changed.countDown();
            }
        };
        Observer<FirewallState> observer = state -> {
            if (state == FirewallState.Ready.INSTANCE) ready.countDown();
        };
        try {
            preferences.setBoolPreference(ROOT_IS_AVAILABLE, false);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertTrue(defaults.edit().putBoolean(MULTI_USER_SUPPORT, true).commit());
            InstalledApplicationsManager beforeManager = new InstalledApplicationsManager.Builder().build();
            List<ApplicationData> before = beforeManager.getInstalledApps();
            assertFalse(before.isEmpty());
            Rule.Inventory inventory = new Rule.Inventory();
            assertFalse(inventory.getRules(context).isEmpty());
            Field versionField = Rule.class.getDeclaredField("inventoryVersion");
            versionField.setAccessible(true);
            AtomicLong version = (AtomicLong) versionField.get(null);
            long beforeVersion = version.get();
            ApplicationData selected = null;
            for (ApplicationData candidate : before) {
                if (candidate.getUid() >= Process.FIRST_APPLICATION_UID
                        && candidate.getUid() / 100_000 == Process.myUid() / 100_000) {
                    selected = candidate;
                    break;
                }
            }
            assertNotNull(selected);
            FirewallAppModel edited = new FirewallAppModel(selected, true, true, true, true, true, false);
            broadcasts.registerReceiver(receiver, new IntentFilter(CrossUserPermission.APPS_CHANGED));
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                store.put("live-permission-grant", model);
                model.getAppsCompleteSet().add(edited);
                model.getFirewallStateLiveData().observeForever(observer);
                preferences.setBoolPreference(ROOT_IS_AVAILABLE, true);
                assertTrue(CrossUserPermission.request(context, app.getDaggerComponent().getPathVars().get()));
                assertTrue("An overlapping request must share the pending grant",
                        CrossUserPermission.request(context, app.getDaggerComponent().getPathVars().get()));
            });
            assertTrue("Approve su for the isolated app if prompted", changed.await(45, TimeUnit.SECONDS));
            assertTrue(Utils.isInteractAcrossUsersPermissionGranted(context));
            assertEquals("FULL permission must not be necessary", PackageManager.PERMISSION_DENIED,
                    context.checkCallingOrSelfPermission("android.permission.INTERACT_ACROSS_USERS_FULL"));
            assertTrue("Verified completion must invalidate inventory", version.get() > beforeVersion);
            assertTrue(rulesUpdateRequested(modules));
            assertFalse("Grant must not start idle modules", ModulesService.serviceIsRunning);
            assertTrue("The real grant notification must refresh the app list", ready.await(45, TimeUnit.SECONDS));
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                assertTrue(model.getAppsCompleteSet().contains(edited));
                for (FirewallAppModel row : model.getAppsCompleteSet()) {
                    if (row.getApplicationData().getUid() == edited.getApplicationData().getUid()) assertSame(edited, row);
                }
                assertTrue(edited.getAllowLan() && edited.getAllowWifi() && edited.getAllowGsm()
                        && edited.getAllowRoaming() && edited.getAllowVPN());
            });
            assertEquals(1, notifications.get());
            assertFalse("A verified grant must not be queued again",
                    CrossUserPermission.request(context, app.getDaggerComponent().getPathVars().get()));
            InstalledApplicationsManager afterManager = new InstalledApplicationsManager.Builder().build();
            List<ApplicationData> after = afterManager.getInstalledApps();
            List<Rule> rules = inventory.getRules(context);
            assertFalse(rules.isEmpty());
            Map<Integer, Integer> beforeCounts = counts(before), afterCounts = counts(after);
            Log.i("CrossUserGrantDevice", "before=" + beforeCounts + " after=" + afterCounts
                    + " complete=" + afterManager.isInventoryComplete() + " rules=" + rules.size()
                    + " notifications=" + notifications.get() + " fullPermission=false");
            boolean foreign = false, foreignRule = false, foreignRow = false;
            int ownUser = Process.myUid() / 100_000;
            for (Integer user : afterCounts.keySet()) if (user != ownUser) foreign = true;
            for (Rule rule : rules) if (rule.uid >= Process.FIRST_APPLICATION_UID && rule.uid / 100_000 != ownUser) foreignRule = true;
            for (FirewallAppModel row : model.getAppsCompleteSet()) {
                int uid = row.getApplicationData().getUid();
                if (uid >= Process.FIRST_APPLICATION_UID && uid / 100_000 != ownUser) foreignRow = true;
            }
            assertTrue("This probe requires visible foreign-profile apps", foreign);
            assertTrue("VPN rules must include foreign-profile apps", foreignRule);
            assertTrue("The open app list must include foreign-profile apps", foreignRow);
        } finally {
            broadcasts.unregisterReceiver(receiver);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                model.getFirewallStateLiveData().removeObserver(observer);
                store.clear();
                context.stopService(new Intent(context, RootExecService.class));
                preferences.setBoolPreference(ROOT_IS_AVAILABLE, root);
                modules.setIptablesRulesUpdateRequested(update);
            });
            SharedPreferences.Editor editor = defaults.edit();
            if (multiUserPresent) editor.putBoolean(MULTI_USER_SUPPORT, multiUser);
            else editor.remove(MULTI_USER_SUPPORT);
            assertTrue(editor.commit());
        }
    }
}
