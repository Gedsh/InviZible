package pan.alexander.tordnscrypt.modules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RUNNING;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.VPN_MODE;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.VPN_SERVICE_ENABLED;

import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import pan.alexander.tordnscrypt.utils.enums.ModuleState;
import pan.alexander.tordnscrypt.utils.enums.OperationMode;
import pan.alexander.tordnscrypt.utils.enums.VPNCommand;
import pan.alexander.tordnscrypt.vpn.service.ServiceVPN;

/** Exercises package handling and the real VPN helper without starting services. */
@RunWith(AndroidJUnit4.class)
public final class ModulesReceiverPackageTest {
    private static final class Fixture implements AutoCloseable {
        final ModulesStatus status = ModulesStatus.getInstance();
        final OperationMode savedMode = status.getMode();
        final ModuleState savedDnsState = status.getDnsCryptState();
        final Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final SharedPreferences preferences = target.getSharedPreferences("package-reload-regression", 0);
        final List<Intent> commands = new ArrayList<>();
        final Context context = new ContextWrapper(target) {
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return preferences;
            }

            @Override public ComponentName startService(Intent intent) {
                commands.add(new Intent(intent));
                return intent.getComponent();
            }
        };
        final ModulesReceiver receiver = new ModulesReceiver(
                null, null, null, null, null, null, null, null, null);
        final Method packageChanged;

        Fixture() throws Exception {
            assertTrue(preferences.edit().clear().putBoolean(VPN_SERVICE_ENABLED, true).commit());
            Field receiverContext = ModulesReceiver.class.getDeclaredField("context");
            receiverContext.setAccessible(true);
            receiverContext.set(receiver, context);
            packageChanged = ModulesReceiver.class.getDeclaredMethod("packageChanged", Intent.class);
            packageChanged.setAccessible(true);
            status.setMode(VPN_MODE);
            status.setDnsCryptState(RUNNING);
        }

        void deliver(String action, String packageName, Boolean replacing) throws Exception {
            Intent intent = new Intent(action, Uri.parse("package:" + packageName));
            if (replacing != null)
                intent.putExtra(Intent.EXTRA_REPLACING, replacing);
            packageChanged.invoke(receiver, intent);
            // ServiceVPNHelper posts to the real main looper before sending its request.
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        }

        void assertReload(int index, String reason) {
            Intent command = commands.get(index);
            assertEquals(ServiceVPN.class.getName(), command.getComponent().getClassName());
            assertEquals(VPNCommand.RELOAD, command.getSerializableExtra("Command"));
            assertEquals(reason, command.getStringExtra("Reason"));
        }

        @Override public void close() {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            status.setMode(savedMode);
            status.setDnsCryptState(savedDnsState);
            assertTrue(preferences.edit().clear().commit());
        }
    }

    @Test public void replacementDoesNotReloadOnRemovalOrAddition() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.deliver(Intent.ACTION_PACKAGE_REMOVED, "example.updated", true);
            assertTrue(fixture.commands.isEmpty());
            fixture.deliver(Intent.ACTION_PACKAGE_ADDED, "example.updated", true);
            assertTrue(fixture.commands.isEmpty());
        }
    }

    @Test public void replacementAdditionDoesNotRequireAnEarlierRemoval() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.deliver(Intent.ACTION_PACKAGE_ADDED, "example.updated", true);
            assertTrue(fixture.commands.isEmpty());
        }
    }

    @Test public void freshInstallReloadsImmediately() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.deliver(Intent.ACTION_PACKAGE_ADDED, "example.installed", false);
            assertEquals(1, fixture.commands.size());
            fixture.assertReload(0, "Package added");
        }
    }

    @Test public void actualRemovalReloadsWithoutWaitingForAddition() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.deliver(Intent.ACTION_PACKAGE_REMOVED, "example.removed", false);
            assertEquals(1, fixture.commands.size());
            fixture.assertReload(0, "Package deleted");
        }
    }

    @Test public void missingReplacingExtraKeepsInstallAndRemovalBehavior() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.deliver(Intent.ACTION_PACKAGE_ADDED, "example.installed", null);
            fixture.deliver(Intent.ACTION_PACKAGE_REMOVED, "example.installed", null);
            assertEquals(2, fixture.commands.size());
            fixture.assertReload(0, "Package added");
            fixture.assertReload(1, "Package deleted");
        }
    }

    @Test public void overlappingReplacementsDoNotSuppressOtherPackageChanges() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.deliver(Intent.ACTION_PACKAGE_REMOVED, "example.first", true);
            fixture.deliver(Intent.ACTION_PACKAGE_REMOVED, "example.second", true);
            fixture.deliver(Intent.ACTION_PACKAGE_REMOVED, "example.uninstalled", false);
            assertEquals(1, fixture.commands.size());
            fixture.assertReload(0, "Package deleted");
            fixture.deliver(Intent.ACTION_PACKAGE_ADDED, "example.second", true);
            fixture.deliver(Intent.ACTION_PACKAGE_ADDED, "example.first", true);
            assertEquals(1, fixture.commands.size());
            fixture.deliver(Intent.ACTION_PACKAGE_ADDED, "example.installed", false);
            assertEquals(2, fixture.commands.size());
            fixture.assertReload(1, "Package added");
        }
    }
}
