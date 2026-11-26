package pan.alexander.tordnscrypt.utils.apps;

import static org.junit.Assert.*;

import android.Manifest;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.test.mock.MockPackageManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import pan.alexander.tordnscrypt.settings.tor_apps.ApplicationData;

/** Runs the production discovery helpers against controlled Android package-manager responses. */
@RunWith(AndroidJUnit4.class)
public final class InstalledAppsTest {
    private static final int MATCH_ANY_USER = 0x00400000;

    private static ApplicationInfo app(int uid, String name) {
        ApplicationInfo info = new ApplicationInfo();
        info.uid = uid;
        info.packageName = name;
        info.flags = ApplicationInfo.FLAG_INSTALLED | ApplicationInfo.FLAG_HAS_CODE;
        return info;
    }

    private static class Packages extends MockPackageManager {
        final ApplicationInfo local = app(10001, "example.local");
        final ApplicationInfo workOnly = app(10002, "example.work");
        final List<Integer> flagsSeen = new ArrayList<>();
        final List<Integer> uidsSeen = new ArrayList<>();
        final Map<Integer, String[]> owners = new HashMap<>();
        boolean rejectCrossUser;
        boolean rejectOwner;

        @Override public List<ApplicationInfo> getInstalledApplications(int flags) {
            flagsSeen.add(flags);
            if ((flags & MATCH_ANY_USER) != 0) {
                if (rejectCrossUser) throw new SecurityException("Injected cross-user denial");
                return Arrays.asList(local, workOnly);
            }
            // Model newer Android: MATCH_UNINSTALLED_PACKAGES does not imply MATCH_ANY_USER.
            return Collections.singletonList(local);
        }

        @Override public String[] getPackagesForUid(int uid) {
            uidsSeen.add(uid);
            if (rejectOwner) throw new SecurityException("Injected owner denial");
            return owners.get(uid);
        }

        @Override public PackageInfo getPackageInfo(String name, int flags) {
            PackageInfo info = new PackageInfo();
            info.requestedPermissions = new String[]{Manifest.permission.INTERNET};
            return info;
        }

    }

    // Keep API 33 types out of the class loaded on older Android versions.
    private static final class Packages33 extends Packages {
        @Override public List<ApplicationInfo> getInstalledApplications(PackageManager.ApplicationInfoFlags flags) {
            return getInstalledApplications((int) flags.getValue());
        }

        @Override public PackageInfo getPackageInfo(String name, PackageManager.PackageInfoFlags flags) {
            return getPackageInfo(name, (int) flags.getValue());
        }
    }

    private static Packages packages() {
        return android.os.Build.VERSION.SDK_INT >= 33 ? new Packages33() : new Packages();
    }

    private static void field(InstalledApplicationsManager manager, String name, Object value) throws Exception {
        Field field = InstalledApplicationsManager.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(manager, value);
    }

    @SuppressWarnings("unchecked")
    private static List<ApplicationInfo> load(InstalledApplicationsManager manager, Packages packages,
                                              boolean across) throws Exception {
        Method method = InstalledApplicationsManager.class.getDeclaredMethod(
                "loadApplications", PackageManager.class, boolean.class);
        method.setAccessible(true);
        return (List<ApplicationInfo>) method.invoke(manager, packages, across);
    }

    @SuppressWarnings("unchecked")
    private static Map<Integer, ApplicationData> discover(InstalledApplicationsManager manager,
                                                         Packages packages, ApplicationInfo app,
                                                         Integer... users) throws Exception {
        Method method = InstalledApplicationsManager.class.getDeclaredMethod("checkPartOfMultiUser",
                ApplicationInfo.class, String.class, android.graphics.drawable.Drawable.class,
                List.class, PackageManager.class, Map.class);
        method.setAccessible(true);
        return (Map<Integer, ApplicationData>) method.invoke(manager, app, "App", null,
                Arrays.asList(users), packages, Collections.emptyMap());
    }

    @Test public void explicitCrossUserCandidates() throws Exception {
        InstalledApplicationsManager manager = new InstalledApplicationsManager.Builder().build();
        Packages packages = packages();
        assertEquals(Collections.singletonList(packages.local), load(manager, packages, false));
        assertEquals(0, packages.flagsSeen.get(0) & MATCH_ANY_USER);
        assertEquals(Arrays.asList(packages.local, packages.workOnly), load(manager, packages, true));
        assertNotEquals(0, packages.flagsSeen.get(1) & MATCH_ANY_USER);
        assertTrue(manager.isInventoryComplete());
    }

    @Test public void deniedQueryFallbackAndRecovery() throws Exception {
        InstalledApplicationsManager manager = new InstalledApplicationsManager.Builder().build();
        Packages packages = packages();
        packages.rejectCrossUser = true;
        assertEquals(Collections.singletonList(packages.local), load(manager, packages, true));
        assertFalse(manager.isInventoryComplete());
        assertEquals(2, packages.flagsSeen.size());
        assertNotEquals(0, packages.flagsSeen.get(0) & MATCH_ANY_USER);
        assertEquals(0, packages.flagsSeen.get(1) & MATCH_ANY_USER);
        packages.rejectCrossUser = false;
        assertEquals(2, load(manager, packages, true).size());
        assertTrue(manager.isInventoryComplete());
    }

    @Test public void systemUidAndCurrentProfile() throws Exception {
        InstalledApplicationsManager manager = new InstalledApplicationsManager.Builder().build();
        field(manager, "ownUID", 10001);
        Packages packages = packages();
        packages.owners.put(1001000, new String[]{"android"});
        Map<Integer, ApplicationData> found = discover(manager, packages, app(1000, "android"), 0, 10);
        assertEquals(Collections.singletonList(1001000), packages.uidsSeen);
        assertEquals("android", found.get(1001000).getPack());
        assertEquals(10, found.get(1001000).getUser());
    }

    @Test public void secondaryProfileDiscovery() throws Exception {
        InstalledApplicationsManager manager = new InstalledApplicationsManager.Builder().build();
        field(manager, "ownUID", 1010001);
        Packages packages = packages();
        packages.owners.put(10002, new String[]{"example.parent"});
        packages.owners.put(1110002, new String[]{"example.other"});
        Map<Integer, ApplicationData> found = discover(manager, packages,
                app(1010002, "example.work"), 0, 10, 11);
        assertEquals(Arrays.asList(10002, 1110002), packages.uidsSeen);
        assertEquals(2, found.size());
        assertEquals(0, found.get(10002).getUser());
        assertEquals(11, found.get(1110002).getUser());
    }

    @Test public void overflowAndMissingOwners() throws Exception {
        InstalledApplicationsManager manager = new InstalledApplicationsManager.Builder().build();
        field(manager, "ownUID", 10001);
        Packages packages = packages();
        load(manager, packages, false);
        assertTrue(discover(manager, packages, app(10002, "example.local"), Integer.MAX_VALUE).isEmpty());
        assertTrue(packages.uidsSeen.isEmpty());
        assertFalse(manager.isInventoryComplete());
        packages.owners.put(1010002, new String[0]);
        assertTrue(discover(manager, packages, app(10002, "example.local"), 10, 11).isEmpty());
    }

    @Test public void ownerDenialCompleteness() throws Exception {
        InstalledApplicationsManager manager = new InstalledApplicationsManager.Builder().build();
        field(manager, "ownUID", 10001);
        Packages packages = packages();
        packages.rejectOwner = true;
        load(manager, packages, false);
        assertTrue(discover(manager, packages, app(10002, "example.local"), 10).isEmpty());
        assertTrue(manager.isInventoryComplete());
        field(manager, "crossUserQueriesAllowed", true);
        assertTrue(discover(manager, packages, app(10002, "example.local"), 10).isEmpty());
        assertFalse(manager.isInventoryComplete());
    }
}
