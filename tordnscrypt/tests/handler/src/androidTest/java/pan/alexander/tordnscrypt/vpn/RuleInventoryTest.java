package pan.alexander.tordnscrypt.vpn;

import static org.junit.Assert.*;
import static pan.alexander.tordnscrypt.proxy.ProxyFragmentKt.CLEARNET_APPS_FOR_PROXY;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.*;

import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Parcel;
import android.os.Build;
import android.os.UserHandle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository;
import pan.alexander.tordnscrypt.modules.ModulesReceiver;
import pan.alexander.tordnscrypt.modules.ModulesStatus;
import pan.alexander.tordnscrypt.settings.tor_apps.ApplicationData;
import pan.alexander.tordnscrypt.utils.apps.InstalledApplicationsManager;
import pan.alexander.tordnscrypt.utils.enums.ModuleState;
import pan.alexander.tordnscrypt.utils.enums.OperationMode;

/** Exercises the production inventory cache and routing decisions without starting services. */
@RunWith(AndroidJUnit4.class)
public final class RuleInventoryTest {
    private static ApplicationData app(String name, String pack, int uid) {
        return new ApplicationData(name, pack, uid, null, false, true, false, false, 0);
    }

    private static final class Fixture implements AutoCloseable {
        final Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final SharedPreferences prefs = target.getSharedPreferences("rule-inventory-regression", 0);
        final Map<String, HashSet<String>> sets = new HashMap<>();
        final List<Intent> commands = new ArrayList<>();
        volatile int acrossUsersPermission = PackageManager.PERMISSION_DENIED;
        int ownUid = android.os.Process.myUid();
        final Context context = new ContextWrapper(target) {
            @Override public android.content.pm.ApplicationInfo getApplicationInfo() {
                android.content.pm.ApplicationInfo info =
                        new android.content.pm.ApplicationInfo(super.getApplicationInfo());
                info.uid = ownUid;
                return info;
            }
            @Override public SharedPreferences getSharedPreferences(String name, int mode) { return prefs; }
            @Override public int checkPermission(String permission, int pid, int uid) {
                if (permission.equals("android.permission.INTERACT_ACROSS_USERS"))
                    return acrossUsersPermission;
                return super.checkPermission(permission, pid, uid);
            }
            @Override public ComponentName startService(Intent intent) {
                commands.add(new Intent(intent));
                return intent.getComponent();
            }
        };
        final PreferenceRepository repository = (PreferenceRepository) Proxy.newProxyInstance(
                PreferenceRepository.class.getClassLoader(), new Class[]{PreferenceRepository.class},
                (object, method, args) -> {
                    if (method.getName().equals("getStringSetPreference")) {
                        Set<String> saved = sets.get((String) args[0]);
                        return saved == null ? new HashSet<>() : new HashSet<>(saved);
                    }
                    throw new AssertionError("Unexpected preference access: " + method.getName());
                });
        final AtomicInteger loads = new AtomicInteger();
        volatile List<ApplicationData> installed = Arrays.asList(
                app("First", "example.first", 10001), app("Second", "example.second", 10002));
        volatile boolean fail;
        boolean complete = true;
        volatile Runnable duringLoad;
        List<UserHandle> profiles = Collections.singletonList(android.os.Process.myUserHandle());
        final Rule.Inventory inventory = new Rule.Inventory(new Rule.Inventory.Source() {
            @Override public Rule.Inventory.Scan load() {
                loads.incrementAndGet();
                Runnable action = duringLoad;
                if (action != null) action.run();
                if (fail) throw new IllegalStateException("Injected inventory failure");
                return new Rule.Inventory.Scan(installed, complete);
            }
            @Override public List<UserHandle> profiles(Context context) { return profiles; }
        });

        Fixture() {
            assertTrue(prefs.edit().clear().putBoolean(MULTI_USER_SUPPORT, false)
                    .putBoolean(VPN_SERVICE_ENABLED, true).commit());
        }

        List<Rule> rules() { return inventory.getRules(context, prefs, repository); }
        void set(String name, String... uids) { sets.put(name, new HashSet<>(Arrays.asList(uids))); }

        @Override public void close() {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertTrue(prefs.edit().clear().commit());
        }
    }

    @Test public void reloadsReuseIdentityButReadCurrentTorAndProxyPreferences() {
        try (Fixture f = new Fixture()) {
            assertTrue(f.rules().get(0).apply);
            f.set(CLEARNET_APPS, "10001");
            List<Rule> rules = f.rules();
            assertFalse(rules.get(0).apply);
            assertTrue(rules.get(1).apply);
            assertTrue(f.prefs.edit().putBoolean(ALL_THROUGH_TOR, false).commit());
            f.set(UNLOCK_APPS, "10002");
            rules = f.rules();
            assertFalse(rules.get(0).apply);
            assertTrue(rules.get(1).apply);
            assertTrue(f.prefs.edit().putBoolean(USE_PROXY_NON_TOR, true).commit());
            f.set(CLEARNET_APPS_FOR_PROXY, "10002");
            assertFalse(f.rules().get(1).apply);
            assertTrue(f.prefs.edit().putBoolean(USE_PROXY_NON_TOR, false).commit());
            assertTrue(f.rules().get(1).apply);
            assertEquals(1, f.loads.get());
        }
    }

    @Test public void returnedRulesCannotMutateCachedIdentities() {
        try (Fixture f = new Fixture()) {
            List<Rule> rules = f.rules();
            rules.get(0).uid = -1;
            rules.get(0).packageName = "changed";
            rules.get(0).appName = "changed";
            rules.get(0).apply = false;
            rules.clear();
            f.installed.get(0).addName("Not yet refreshed");
            Rule original = f.rules().get(0);
            assertEquals(10001, original.uid);
            assertEquals("example.first", original.packageName);
            assertEquals("First", original.appName);
            assertTrue(original.apply);
            assertEquals(1, f.loads.get());
        }
    }

    @Test public void invalidationRefreshesOnNextRead() {
        try (Fixture f = new Fixture()) {
            assertEquals(2, f.rules().size());
            f.installed = Collections.singletonList(app("New", "example.new", 10003));
            Rule.invalidateInstalledApps();
            assertEquals(1, f.loads.get());
            List<Rule> fresh = f.rules();
            assertEquals(1, fresh.size());
            assertEquals(10003, fresh.get(0).uid);
            assertEquals("New", fresh.get(0).appName);
            assertEquals(2, f.loads.get());
            f.rules();
            assertEquals(2, f.loads.get());
        }
    }

    @Test public void failedAndEmptyScansAreRetried() {
        try (Fixture f = new Fixture()) {
            f.installed = Collections.emptyList();
            assertTrue(f.rules().isEmpty());
            f.fail = true;
            assertTrue(f.rules().isEmpty());
            f.fail = false;
            f.installed = Collections.singletonList(app("Recovered", "example.recovered", 10004));
            assertEquals(10004, f.rules().get(0).uid);
            f.rules();
            assertEquals(3, f.loads.get());
        }
    }

    @Test public void invalidationDuringScanDoesNotMarkTheCacheFresh() throws Exception {
        try (Fixture f = new Fixture()) {
            CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            f.duringLoad = () -> {
                entered.countDown();
                try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
                catch (InterruptedException e) { throw new AssertionError(e); }
            };
            Thread worker = new Thread(() -> {
                try { assertEquals(2, f.rules().size()); }
                catch (Throwable e) { failure.set(e); }
            }, "inventory-regression");
            worker.start();
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                Rule.invalidateInstalledApps();
            } finally {
                release.countDown();
                worker.join(6000);
            }
            assertFalse(worker.isAlive());
            if (failure.get() != null) throw new AssertionError(failure.get());
            f.duringLoad = null;
            f.installed = Collections.singletonList(app("Updated", "example.updated", 10005));
            assertEquals(10005, f.rules().get(0).uid);
            f.rules();
            assertEquals(2, f.loads.get());
        }
    }

    @Test public void failedRefreshDoesNotReturnStaleIdentitiesOrSuppressRetry() {
        try (Fixture f = new Fixture()) {
            assertEquals(10001, f.rules().get(0).uid);
            Rule.invalidateInstalledApps();
            f.fail = true;
            assertTrue(f.rules().isEmpty());
            f.fail = false;
            f.installed = Collections.emptyList();
            assertTrue(f.rules().isEmpty());
            f.installed = Collections.singletonList(app("Recovered", "example.recovered", 10006));
            assertEquals(10006, f.rules().get(0).uid);
            f.rules();
            assertEquals(4, f.loads.get());
        }
    }

    @Test public void preferenceAccessFailureDoesNotLoadOrInvalidateInventory() {
        try (Fixture f = new Fixture()) {
            Context unavailable = new ContextWrapper(f.context) {
                @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                    throw new IllegalStateException("Injected preference access failure");
                }
            };
            assertTrue(f.inventory.getRules(unavailable).isEmpty());
            assertEquals(0, f.loads.get());
            assertEquals(2, f.rules().size());
            assertEquals(1, f.loads.get());
            assertTrue(f.inventory.getRules(unavailable).isEmpty());
            f.rules();
            assertEquals(1, f.loads.get());
        }
    }

    @Test public void scopeAndLocaleChangesRefreshInventory() throws Exception {
        ModulesStatus modules = ModulesStatus.getInstance();
        Field root = ModulesStatus.class.getDeclaredField("rootAvailable");
        root.setAccessible(true);
        boolean savedRoot = modules.isRootAvailable();
        Locale savedLocale = Locale.getDefault();
        try (Fixture f = new Fixture()) {
            f.rules();
            assertTrue(f.prefs.edit().putBoolean(MULTI_USER_SUPPORT, true).commit());
            f.rules();
            assertEquals(2, f.loads.get());
            root.setBoolean(modules, !savedRoot);
            f.rules();
            assertEquals(3, f.loads.get());
            Locale.setDefault(savedLocale.equals(Locale.FRENCH) ? Locale.ENGLISH : Locale.FRENCH);
            f.rules();
            f.rules();
            assertEquals(4, f.loads.get());
        } finally {
            root.setBoolean(modules, savedRoot);
            Locale.setDefault(savedLocale);
        }
    }

    @Test public void eachInventoryStartsFresh() {
        try (Fixture first = new Fixture(); Fixture second = new Fixture()) {
            first.rules(); second.rules(); first.rules(); second.rules();
            assertEquals(1, first.loads.get());
            assertEquals(1, second.loads.get());
        }
    }

    @Test public void crossProfilePermissionChangesRefreshInventory() {
        try (Fixture f = new Fixture()) {
            assertTrue(f.prefs.edit().putBoolean(MULTI_USER_SUPPORT, true).commit());
            f.rules(); f.rules();
            assertEquals(1, f.loads.get());
            f.acrossUsersPermission = PackageManager.PERMISSION_GRANTED;
            f.rules(); f.rules();
            assertEquals(2, f.loads.get());
            f.acrossUsersPermission = PackageManager.PERMISSION_DENIED;
            f.rules();
            assertEquals(3, f.loads.get());
        }
    }

    private static UserHandle profile(int id) {
        Parcel parcel = Parcel.obtain();
        try {
            parcel.writeInt(id);
            parcel.setDataPosition(0);
            return UserHandle.CREATOR.createFromParcel(parcel);
        } finally {
            parcel.recycle();
        }
    }

    @Test public void multipleProfilesWithOnlyLocalAppsReuseInventoryWithoutPermission() {
        try (Fixture f = new Fixture()) {
            assertTrue(f.prefs.edit().putBoolean(MULTI_USER_SUPPORT, true).commit());
            f.profiles = Arrays.asList(profile(0), profile(100));
            f.rules(); f.rules();
            assertEquals(1, f.loads.get());
            f.installed = Collections.singletonList(app("New local app", "example.new", 10003));
            Rule.invalidateInstalledApps();
            assertEquals(10003, f.rules().get(0).uid);
            f.rules();
            assertEquals(2, f.loads.get());
        }
    }

    @Test public void workProfileLocalInventoryIgnoresSyntheticSystemIdentities() {
        try (Fixture f = new Fixture()) {
            f.ownUid = 10010001;
            assertTrue(f.prefs.edit().putBoolean(MULTI_USER_SUPPORT, true).commit());
            f.profiles = Arrays.asList(profile(0), profile(100));
            f.installed = Arrays.asList(app("Kernel", "uid -1", -1),
                    app("Root", "root", 0), app("Media", "media", 1013),
                    app("Work app", "example.work", 10010002));
            assertEquals(4, f.rules().size());
            f.rules();
            assertEquals(1, f.loads.get());
            // A package event reveals cross-profile entries even without a granted permission.
            f.installed = Arrays.asList(f.installed.get(3), app("Main app", "example.main", 10002));
            Rule.invalidateInstalledApps();
            f.rules(); f.rules();
            assertEquals(3, f.loads.get());
        }
    }

    @Test public void authorizedLocalInventoryIsCachedUntilInvalidated() {
        try (Fixture f = new Fixture()) {
            assertTrue(f.prefs.edit().putBoolean(MULTI_USER_SUPPORT, true).commit());
            f.profiles = Arrays.asList(profile(0), profile(100));
            f.acrossUsersPermission = PackageManager.PERMISSION_GRANTED;
            f.rules(); f.rules();
            assertEquals(1, f.loads.get());
            f.installed = Collections.singletonList(app("Work app", "example.work", 10010002));
            // A local-only snapshot remains cached until a normal invalidation or scope change.
            assertEquals(10001, f.rules().get(0).uid);
            assertEquals(1, f.loads.get());
            Rule.invalidateInstalledApps();
            assertEquals(10010002, f.rules().get(0).uid);
            f.rules();
            assertEquals(3, f.loads.get());
            f.installed = Collections.singletonList(app("Local app", "example.local", 10001));
            f.rules(); f.rules();
            assertEquals(4, f.loads.get());
        }
    }

    @Test public void incompleteDiscoveryIsReturnedButNeverCached() {
        try (Fixture f = new Fixture()) {
            f.rules();
            Rule.invalidateInstalledApps();
            f.complete = false;
            f.installed = Collections.singletonList(app("Partial", "example.partial", 10003));
            assertEquals(10003, f.rules().get(0).uid);
            f.installed = Collections.singletonList(app("Recovered", "example.recovered", 10004));
            f.complete = true;
            assertEquals(10004, f.rules().get(0).uid);
            f.rules();
            assertEquals(3, f.loads.get());
        }
    }

    @Test public void crossProfileEntriesKeepFreshEnumerationUntilOnlyLocalAppsRemain() {
        try (Fixture f = new Fixture()) {
            assertTrue(f.prefs.edit().putBoolean(MULTI_USER_SUPPORT, true).commit());
            UserHandle main = android.os.Process.myUserHandle();
            f.rules();
            UserHandle other = profile(100);
            assertNotEquals(main, other);
            f.profiles = Arrays.asList(main, other);
            f.installed = Collections.singletonList(app("New profile app", "example.profile", 10010001));
            assertEquals(10010001, f.rules().get(0).uid);
            f.installed = Collections.singletonList(app("Updated profile app", "example.updated", 10010002));
            assertEquals(10010002, f.rules().get(0).uid);
            assertEquals(3, f.loads.get());
            f.fail = true;
            assertTrue(f.rules().isEmpty());
            f.fail = false;
            f.installed = Collections.singletonList(app("Local app", "example.local", 10001));
            f.rules(); f.rules();
            assertEquals(5, f.loads.get());
        }
    }

    @Test public void packageUpdatesInvalidateInventoryWithoutReloadingVpn() throws Exception {
        ModulesStatus modules = ModulesStatus.getInstance();
        OperationMode savedMode = modules.getMode();
        ModuleState savedDns = modules.getDnsCryptState();
        try (Fixture f = new Fixture()) {
            modules.setMode(OperationMode.VPN_MODE);
            modules.setDnsCryptState(ModuleState.RUNNING);
            ModulesReceiver receiver = new ModulesReceiver(null, null, null, null, null, null, null, null, null);
            Field context = ModulesReceiver.class.getDeclaredField("context");
            context.setAccessible(true); context.set(receiver, f.context);
            Method changed = ModulesReceiver.class.getDeclaredMethod("packageChanged", Intent.class);
            changed.setAccessible(true);
            f.rules();
            for (String action : Arrays.asList(Intent.ACTION_PACKAGE_REMOVED, Intent.ACTION_PACKAGE_ADDED)) {
                int loads = f.loads.get();
                changed.invoke(receiver, new Intent(action, Uri.parse("package:example.first"))
                        .putExtra(Intent.EXTRA_REPLACING, true));
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                assertTrue(f.commands.isEmpty());
                assertEquals(loads, f.loads.get());
                f.rules();
                assertEquals(loads + 1, f.loads.get());
            }
        } finally {
            modules.setMode(savedMode); modules.setDnsCryptState(savedDns);
        }
    }

    @Test public void realInstalledAppInventoryHonorsDiscoveredProfileScope() {
        try (Fixture f = new Fixture()) {
            AtomicInteger loads = new AtomicInteger();
            AtomicReference<Rule.Inventory.Scan> lastScan = new AtomicReference<>();
            Rule.Inventory real = new Rule.Inventory(() -> {
                loads.incrementAndGet();
                InstalledApplicationsManager manager = new InstalledApplicationsManager.Builder().build();
                List<ApplicationData> apps = manager.getInstalledApps();
                Map<Integer, Integer> users = new java.util.TreeMap<>();
                for (ApplicationData app : apps) {
                    if (app.getUid() >= android.os.Process.FIRST_APPLICATION_UID) {
                        int user = app.getUid() / 100_000;
                        Integer count = users.get(user);
                        users.put(user, count == null ? 1 : count + 1);
                    }
                }
                android.os.UserManager userManager =
                        (android.os.UserManager) f.target.getSystemService(Context.USER_SERVICE);
                android.util.Log.i("InventoryDeviceTest", "profiles=" + userManager.getUserProfiles()
                        + " crossUserPermission=" + pan.alexander.tordnscrypt.utils.Utils
                                .isInteractAcrossUsersPermissionGranted(f.target)
                        + " complete=" + manager.isInventoryComplete() + " appCounts=" + users);
                String probeUser = InstrumentationRegistry.getArguments().getString("inventoryProbeUser");
                String probePackage = InstrumentationRegistry.getArguments().getString("inventoryProbePackage");
                if (probeUser != null && probePackage != null) {
                    try {
                        PackageManager packages = f.target.getPackageManager();
                        int appId = packages.getApplicationInfo(probePackage, 0).uid % 100_000;
                        long uid = Long.parseLong(probeUser) * 100_000 + appId;
                        if (uid < 0 || uid > Integer.MAX_VALUE)
                            throw new IllegalArgumentException("Invalid probe UID");
                        android.util.Log.i("InventoryDeviceTest", "probeUser=" + probeUser
                                + " packagesForUid=" + Arrays.toString(packages.getPackagesForUid((int) uid)));
                    } catch (Exception e) {
                        android.util.Log.i("InventoryDeviceTest", "profile probe rejected: " + e);
                    }
                }
                Rule.Inventory.Scan scan = new Rule.Inventory.Scan(apps, manager.isInventoryComplete());
                lastScan.set(scan);
                return scan;
            });
            List<Rule> first = real.getRules(f.context, f.prefs, f.repository);
            assertFalse(first.isEmpty());
            // Some devices expose work-profile packages even without cross-user permissions.
            int expectedLoads = lastScan.get().complete ? 1 : 2;
            for (Rule rule : first) {
                if (rule.uid >= android.os.Process.FIRST_APPLICATION_UID
                        && rule.uid / 100_000 != f.ownUid / 100_000)
                    expectedLoads = 2;
            }
            List<Rule> second = real.getRules(f.context, f.prefs, f.repository);
            assertEquals(first.size(), second.size());
            for (int i = 0; i < first.size(); i++) {
                assertEquals(first.get(i).uid, second.get(i).uid);
                assertEquals(first.get(i).packageName, second.get(i).packageName);
                assertNotSame(first.get(i), second.get(i));
            }
            assertEquals(expectedLoads, loads.get());
        }
    }

    @Test public void profileAvailabilityInvalidatesWithoutReloadingVpn() throws Exception {
        try (Fixture f = new Fixture()) {
            ModulesReceiver receiver = new ModulesReceiver(null, null, null, null, null, null, null, null, null);
            Field context = ModulesReceiver.class.getDeclaredField("context");
            context.setAccessible(true); context.set(receiver, f.context);
            Field mode = ModulesReceiver.class.getDeclaredField("savedOperationMode");
            mode.setAccessible(true); mode.set(receiver, ModulesStatus.getInstance().getMode());
            Method dispatch = ModulesReceiver.class.getDeclaredMethod("intentOnReceive", Intent.class, String.class);
            dispatch.setAccessible(true);
            f.rules();
            // Literal names also exercise ignoring unsupported actions on API 23.
            for (String action : Arrays.asList("android.intent.action.MANAGED_PROFILE_AVAILABLE",
                    "android.intent.action.MANAGED_PROFILE_UNAVAILABLE",
                    "android.intent.action.MANAGED_PROFILE_UNLOCKED", "android.intent.action.USER_UNLOCKED")) {
                int loads = f.loads.get();
                dispatch.invoke(receiver, new Intent(action), action);
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                assertTrue(f.commands.isEmpty());
                assertEquals(loads, f.loads.get());
                f.rules();
                assertEquals(loads + (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ? 1 : 0), f.loads.get());
            }
        }
    }
}
