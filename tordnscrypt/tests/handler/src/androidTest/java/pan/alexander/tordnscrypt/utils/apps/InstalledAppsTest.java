package pan.alexander.tordnscrypt.utils.apps;

import static org.junit.Assert.*;

import android.Manifest;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.test.mock.MockPackageManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.Assume;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

import kotlinx.coroutines.Dispatchers;

import pan.alexander.tordnscrypt.settings.tor_apps.ApplicationData;
import pan.alexander.tordnscrypt.settings.tor_apps.TorAppData;

/** Runs the production discovery helpers against controlled Android package-manager responses. */
@RunWith(AndroidJUnit4.class)
public final class InstalledAppsTest {
    private static final int MATCH_ANY_USER = 0x00400000;

    private static ApplicationInfo app(int uid, String name) {
        ApplicationInfo info = new ApplicationInfo();
        info.uid = uid;
        info.packageName = name;
        info.flags = ApplicationInfo.FLAG_INSTALLED | ApplicationInfo.FLAG_HAS_CODE;
        info.enabled = true;
        info.nonLocalizedLabel = name;
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
        List<ApplicationInfo> candidates;

        @Override public List<ApplicationInfo> getInstalledApplications(int flags) {
            flagsSeen.add(flags);
            if (candidates != null) return candidates;
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

    private static InstalledApplicationsManager manager() throws Exception {
        return manager(false);
    }

    private static InstalledApplicationsManager manager(boolean rejectMetadata) throws Exception {
        InstalledApplicationsManager manager = new InstalledApplicationsManager.Builder().build();
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        field(manager, "context", new ContextWrapper(target) {
            @Override public Object getSystemService(String name) {
                // Controlled owner fixtures must not consult the real profile service.
                if (Context.LAUNCHER_APPS_SERVICE.equals(name)) {
                    if (rejectMetadata) throw new SecurityException("Injected profile metadata denial");
                    return null;
                }
                return super.getSystemService(name);
            }
        });
        return manager;
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
        InstalledApplicationsManager manager = manager();
        Packages packages = packages();
        assertEquals(Collections.singletonList(packages.local), load(manager, packages, false));
        assertEquals(0, packages.flagsSeen.get(0) & MATCH_ANY_USER);
        assertEquals(Arrays.asList(packages.local, packages.workOnly), load(manager, packages, true));
        assertNotEquals(0, packages.flagsSeen.get(1) & MATCH_ANY_USER);
        assertTrue(manager.isInventoryComplete());
    }

    @Test public void deniedQueryFallbackAndRecovery() throws Exception {
        InstalledApplicationsManager manager = manager();
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
        InstalledApplicationsManager manager = manager();
        field(manager, "ownUID", 10001);
        Packages packages = packages();
        packages.owners.put(1001000, new String[]{"android"});
        Map<Integer, ApplicationData> found = discover(manager, packages, app(1000, "android"), 0, 10);
        assertEquals(Collections.singletonList(1001000), packages.uidsSeen);
        assertEquals("android", found.get(1001000).getPack());
        assertEquals(10, found.get(1001000).getUser());
    }

    @Test public void secondaryProfileDiscovery() throws Exception {
        InstalledApplicationsManager manager = manager();
        field(manager, "ownUID", 1010001);
        Packages packages = packages();
        packages.owners.put(10002, new String[]{"example.work"});
        packages.owners.put(1110002, new String[]{"example.work"});
        Map<Integer, ApplicationData> found = discover(manager, packages,
                app(1010002, "example.work"), 0, 10, 11);
        assertEquals(Arrays.asList(10002, 1110002), packages.uidsSeen);
        assertEquals(2, found.size());
        assertEquals(0, found.get(10002).getUser());
        assertEquals(11, found.get(1110002).getUser());
    }

    @Test public void overflowAndMissingOwners() throws Exception {
        InstalledApplicationsManager manager = manager();
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
        InstalledApplicationsManager manager = manager();
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

    @Test public void metadataDenialKeepsSuccessfulOwnershipFallbackComplete() throws Exception {
        Assume.assumeTrue(android.os.Build.VERSION.SDK_INT >= 26);
        InstalledApplicationsManager manager = manager(true);
        field(manager, "ownUID", 10000);
        field(manager, "crossUserQueriesAllowed", true);
        Packages packages = packages();
        ApplicationInfo source = app(10001, "example.foreign");
        source.enabled = false;
        packages.owners.put(1010001, new String[]{source.packageName});
        load(manager, packages, false);
        ApplicationData row = discover(manager, packages, source, 10).get(1010001);
        assertNotNull(row);
        assertFalse(row.getDisabled());
        assertEquals(10, row.getUser());
        assertTrue("Optional metadata denial must not invalidate confirmed ownership",
                manager.isInventoryComplete());

        packages.owners.clear();
        assertTrue(discover(manager, packages, source, 10).isEmpty());
        assertTrue("A successful owner query may confirm absence", manager.isInventoryComplete());

        packages.rejectOwner = true;
        assertTrue(discover(manager, packages, source, 10).isEmpty());
        assertFalse("Ownership query denial must still invalidate the scan", manager.isInventoryComplete());
    }

    @Test public void ownershipFallbackDoesNotRepairEarlierIncompleteScan() throws Exception {
        Assume.assumeTrue(android.os.Build.VERSION.SDK_INT >= 26);
        InstalledApplicationsManager manager = manager(true);
        field(manager, "ownUID", 10000);
        field(manager, "crossUserQueriesAllowed", true);
        Packages packages = packages();
        packages.rejectCrossUser = true;
        load(manager, packages, true);
        assertFalse(manager.isInventoryComplete());
        packages.owners.put(1010001, new String[]{"example.foreign"});
        assertNotNull(discover(manager, packages, app(10001, "example.foreign"), 10).get(1010001));
        assertFalse("Fallback must preserve earlier enumeration failure", manager.isInventoryComplete());
    }

    @SuppressWarnings("unchecked")
    private static List<ApplicationData> scan(ApplicationInfo... candidates) throws Exception {
        InstalledApplicationsManager manager = manager();
        Packages packages = packages();
        packages.candidates = Arrays.asList(candidates);
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        field(manager, "context", new ContextWrapper(target) {
            @Override public PackageManager getPackageManager() { return packages; }
        });
        field(manager, "ownUID", 10000);
        field(manager, "multiUserSupport", false);
        field(manager, "installedAppNamesStorage", new InstalledAppNamesStorage(Dispatchers.getUnconfined()));
        Field finished = InstalledApplicationsManager.class.getDeclaredField("captivePortalUidSearchingFinished");
        finished.setAccessible(true);
        Field portals = InstalledApplicationsManager.class.getDeclaredField("portalUids");
        portals.setAccessible(true);
        boolean savedFinished = finished.getBoolean(null);
        Set<Integer> portalUids = (Set<Integer>) portals.get(null);
        Set<Integer> savedPortals = new HashSet<>(portalUids);
        try {
            List<ApplicationData> rows = new ArrayList<>();
            for (ApplicationData row : manager.getInstalledApps()) {
                if (row.getPack().startsWith("example.")) rows.add(row);
            }
            assertTrue(manager.isInventoryComplete());
            return rows;
        } finally {
            finished.setBoolean(null, savedFinished);
            portalUids.clear();
            portalUids.addAll(savedPortals);
        }
    }

    @Test public void uninstalledStoppedRecordsAreExcludedButDisabledAppsRemain() throws Exception {
        ApplicationInfo removed = app(10001, "example.removed");
        removed.flags &= ~ApplicationInfo.FLAG_INSTALLED;
        removed.flags |= ApplicationInfo.FLAG_STOPPED;
        ApplicationInfo disabled = app(10002, "example.disabled");
        disabled.enabled = false;
        ApplicationInfo stopped = app(10003, "example.stopped");
        stopped.flags |= ApplicationInfo.FLAG_STOPPED;
        List<ApplicationData> rows = scan(removed, disabled, stopped);
        assertEquals(2, rows.size());
        for (ApplicationData row : rows) {
            assertFalse(row.getArchived());
            assertEquals(row.getPack().equals("example.disabled"), row.getDisabled());
            assertEquals(row.getDisabled(), TorAppData.mapToTorAppData(row).getDisabled());
        }
    }

    @Test public void sharedUidIgnoresUninstalledNamesAndMergesDisabledStateInEitherOrder() throws Exception {
        ApplicationInfo removed = app(10001, "example.a");
        removed.flags &= ~ApplicationInfo.FLAG_INSTALLED;
        removed.flags |= ApplicationInfo.FLAG_STOPPED;
        ApplicationInfo disabled = app(10001, "example.disabled");
        disabled.enabled = false;
        ApplicationInfo enabled = app(10001, "example.enabled");
        for (List<ApplicationInfo> order : Arrays.asList(Arrays.asList(removed, disabled, enabled),
                Arrays.asList(enabled, disabled, removed))) {
            ApplicationData row = scan(order.toArray(new ApplicationInfo[0])).get(0);
            assertFalse(row.getDisabled());
            assertFalse(row.getNames().contains("example.a"));
            assertEquals(2, row.getNames().size());
        }
        assertTrue(scan(disabled).get(0).getDisabled());
    }

    @Test public void foreignOwnershipDoesNotCopyLocalDisabledOrStoppedState() throws Exception {
        InstalledApplicationsManager manager = manager();
        field(manager, "ownUID", 10000);
        Packages packages = packages();
        ApplicationInfo source = app(10001, "example.foreign");
        source.flags = ApplicationInfo.FLAG_HAS_CODE | ApplicationInfo.FLAG_STOPPED;
        source.enabled = false;
        packages.owners.put(1010001, new String[]{source.packageName});
        ApplicationData row = discover(manager, packages, source, 10).get(1010001);
        assertNotNull(row);
        assertFalse(row.getArchived());
        assertFalse(row.getDisabled());
        assertEquals(10, row.getUser());
        packages.owners.put(1010001, new String[]{"example.sibling"});
        assertTrue(discover(manager, packages, source, 10).isEmpty());
    }

    @Test public void matchingForeignRecordUsesItsOwnStateAndRejectsUninstalledRecord() throws Exception {
        InstalledApplicationsManager manager = manager();
        field(manager, "ownUID", 10000);
        Packages packages = packages();
        ApplicationInfo foreign = app(1010001, "example.foreign");
        foreign.enabled = false;
        assertTrue(discover(manager, packages, foreign, 10).get(1010001).getDisabled());
        foreign.flags &= ~ApplicationInfo.FLAG_INSTALLED;
        foreign.flags |= ApplicationInfo.FLAG_STOPPED;
        assertTrue(discover(manager, packages, foreign, 10).isEmpty());
    }

    private static ApplicationInfo archived(int uid, String name) throws Exception {
        ApplicationInfo info = app(uid, name);
        info.flags &= ~ApplicationInfo.FLAG_INSTALLED;
        info.enabled = false;
        ApplicationInfo.class.getField("isArchived").setBoolean(info, true);
        return info;
    }

    @Test public void confirmedArchiveIsIncludedWithArchiveLabelState() throws Exception {
        Assume.assumeTrue(android.os.Build.VERSION.SDK_INT >= 35);
        ApplicationData row = scan(archived(10001, "example.archived")).get(0);
        assertTrue(row.getArchived());
        assertFalse(row.getDisabled());
        assertTrue(TorAppData.mapToTorAppData(row).getArchived());
    }

    @Test public void archiveAndDisabledSharedUidStatusIsOrderIndependent() throws Exception {
        Assume.assumeTrue(android.os.Build.VERSION.SDK_INT >= 35);
        ApplicationInfo archive = archived(10001, "example.archived");
        ApplicationInfo enabled = app(10001, "example.enabled");
        ApplicationInfo disabled = app(10001, "example.disabled");
        disabled.enabled = false;
        for (ApplicationInfo[] order : Arrays.asList(new ApplicationInfo[]{archive, enabled},
                new ApplicationInfo[]{enabled, archive})) {
            ApplicationData row = scan(order).get(0);
            assertFalse(row.getArchived());
            assertFalse(row.getDisabled());
        }
        ApplicationData disabledRow = scan(archive, disabled).get(0);
        assertFalse(disabledRow.getArchived());
        assertTrue(disabledRow.getDisabled());
        assertTrue(scan(archive, archived(10001, "example.other")).get(0).getArchived());
    }
}
