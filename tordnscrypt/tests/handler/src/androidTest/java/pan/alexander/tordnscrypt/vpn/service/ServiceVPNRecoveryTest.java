package pan.alexander.tordnscrypt.vpn.service;

import static org.junit.Assert.*;
import static pan.alexander.tordnscrypt.modules.ModulesServiceActions.ACTION_STOP_SERVICE_FOREGROUND;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.VPN_SERVICE_ENABLED;
import static pan.alexander.tordnscrypt.vpn.service.ServiceVPN.EXTRA_COMMAND;
import static pan.alexander.tordnscrypt.vpn.service.ServiceVPN.EXTRA_REASON;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import pan.alexander.tordnscrypt.App;
import pan.alexander.tordnscrypt.utils.enums.VPNCommand;

/** Runs the production onStartCommand on the main thread without starting services or native workers. */
@RunWith(AndroidJUnit4.class)
public final class ServiceVPNRecoveryTest {
    private static final class TestService extends ServiceVPN {
        final List<Intent> commands = new ArrayList<>();
        int cleanupCalls;
        int cleanupStartId;
        int recoveryCalls;
        int recoveryStartId;
        long commandGeneration;
        boolean nativeRunning;
        TestService(Context context, SharedPreferences preferences) {
            attachBaseContext(context);
            App.getInstance().getSubcomponentsManager().modulesServiceSubcomponent().inject(this);
            defaultPreferences = () -> preferences;
        }
        @Override void queueNativeCommand(Intent intent) {
            commandGeneration = cancelNativeRetry();
            commands.add(new Intent(intent));
        }
        @Override boolean isNativeRunning() { return nativeRunning; }
        @Override void requestVpnRecovery(int startId) {
            recoveryCalls++;
            recoveryStartId = startId;
        }
        @Override void stopForegroundForRecovery(int startId) {
            cleanupCalls++;
            cleanupStartId = startId;
        }
    }

    private static final class Fixture implements AutoCloseable {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final SharedPreferences preferences = context.getSharedPreferences("vpn-recovery-regression", 0);
        final TestService service;
        int nextStartId;
        Fixture() {
            assertTrue(preferences.edit().clear().putBoolean(VPN_SERVICE_ENABLED, true).commit());
            service = new TestService(context, preferences);
        }
        int deliver(Intent intent) {
            AtomicInteger result = new AtomicInteger();
            int startId = ++nextStartId;
            InstrumentationRegistry.getInstrumentation().runOnMainSync(
                    () -> result.set(service.onStartCommand(intent, 0, startId)));
            return result.get();
        }
        int command(VPNCommand command, String reason) {
            return deliver(new Intent(context, ServiceVPN.class)
                    .putExtra(EXTRA_COMMAND, command).putExtra(EXTRA_REASON, reason)
                    .putExtra("showNotification", false));
        }
        int cleanup() {
            return deliver(new Intent(context, ServiceVPN.class)
                    .setAction(ACTION_STOP_SERVICE_FOREGROUND).putExtra("showNotification", true));
        }
        int alwaysOn() {
            return deliver(new Intent(android.net.VpnService.SERVICE_INTERFACE)
                    .setClass(context, ServiceVPN.class).putExtra("showNotification", false));
        }
        @Override public void close() { assertTrue(preferences.edit().clear().commit()); }
    }

    @Test public void bootstrapCleanupStopsOnlyUnstartedService() {
        try (Fixture fixture = new Fixture()) {
            assertEquals(Service.START_NOT_STICKY, fixture.cleanup());
            assertEquals(1, fixture.service.cleanupCalls);
            assertEquals(1, fixture.service.cleanupStartId);
            assertTrue(fixture.service.commands.isEmpty());
        }
    }

    @Test public void overlappingPackageAndAlwaysOnCleanupCannotUndoStart() {
        try (Fixture fixture = new Fixture()) {
            assertEquals(Service.START_STICKY, fixture.command(VPNCommand.START, "MY_PACKAGE_REPLACED"));
            assertEquals(Service.START_STICKY, fixture.cleanup());
            assertEquals(Service.START_STICKY, fixture.command(VPNCommand.START, "ALWAYS_ON_VPN"));
            assertEquals(Service.START_STICKY, fixture.cleanup());
            assertEquals(0, fixture.service.cleanupCalls);
            assertEquals(2, fixture.service.commands.size());
            assertTrue(fixture.preferences.getBoolean(VPN_SERVICE_ENABLED, false));
        }
    }

    @Test public void cleanupCannotUndoQueuedReload() {
        try (Fixture fixture = new Fixture()) {
            fixture.command(VPNCommand.RELOAD, "Network available");
            assertEquals(Service.START_STICKY, fixture.cleanup());
            assertEquals(0, fixture.service.cleanupCalls);
            assertEquals(1, fixture.service.commands.size());
        }
    }

    @Test public void systemAlwaysOnCannotUndoAcceptedStart() {
        assertSystemAlwaysOnPreservesCommand(VPNCommand.START);
    }

    @Test public void systemAlwaysOnCannotUndoAcceptedReload() {
        assertSystemAlwaysOnPreservesCommand(VPNCommand.RELOAD);
    }

    private void assertSystemAlwaysOnPreservesCommand(VPNCommand command) {
        try (Fixture fixture = new Fixture()) {
            fixture.command(command, "MY_PACKAGE_REPLACED");
            assertEquals(Service.START_STICKY, fixture.alwaysOn());
            assertEquals(Service.START_STICKY, fixture.cleanup());
            assertEquals(0, fixture.service.recoveryCalls);
            assertEquals(0, fixture.service.cleanupCalls);
            assertEquals(1, fixture.service.commands.size());
            assertEquals(command, fixture.service.commands.get(0).getSerializableExtra(EXTRA_COMMAND));
        }
    }

    @Test public void nullRestartCannotUndoAcceptedStartOrReload() {
        for (VPNCommand command : new VPNCommand[]{VPNCommand.START, VPNCommand.RELOAD}) {
            try (Fixture fixture = new Fixture()) {
                fixture.command(command, "MY_PACKAGE_REPLACED");
                assertEquals(Service.START_STICKY, fixture.deliver(null));
                assertEquals(0, fixture.service.recoveryCalls);
                assertEquals(1, fixture.service.commands.size());
            }
        }
    }

    @Test public void systemAlwaysOnRequestsRecoveryBeforeStartAndAfterStop() {
        try (Fixture fixture = new Fixture()) {
            assertEquals(Service.START_NOT_STICKY, fixture.alwaysOn());
            assertEquals(1, fixture.service.recoveryCalls);
            assertEquals(1, fixture.service.recoveryStartId);
            assertTrue(fixture.service.commands.isEmpty());
            fixture.command(VPNCommand.START, "MY_PACKAGE_REPLACED");
            fixture.command(VPNCommand.STOP, "All modules stopped");
            assertEquals(Service.START_NOT_STICKY, fixture.alwaysOn());
            assertEquals(2, fixture.service.recoveryCalls);
            assertEquals(4, fixture.service.recoveryStartId);
        }
    }

    @Test public void failedStartOrReloadAllowsAlwaysOnRecoveryAndCleanup() {
        for (VPNCommand command : new VPNCommand[]{VPNCommand.START, VPNCommand.RELOAD}) {
            try (Fixture fixture = new Fixture()) {
                fixture.command(command, "MY_PACKAGE_REPLACED");
                fixture.service.nativeStartFailed(fixture.service.commandGeneration);
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                assertEquals(Service.START_NOT_STICKY, fixture.alwaysOn());
                assertEquals(1, fixture.service.recoveryCalls);
                assertEquals(Service.START_NOT_STICKY, fixture.cleanup());
                assertEquals(1, fixture.service.cleanupCalls);
                assertTrue(fixture.preferences.getBoolean(VPN_SERVICE_ENABLED, false));
            }
        }
    }

    @Test public void failedReloadPreservesRunningTunnel() {
        try (Fixture fixture = new Fixture()) {
            fixture.command(VPNCommand.RELOAD, "Settings changed");
            fixture.service.nativeRunning = true;
            fixture.service.nativeStartFailed(fixture.service.commandGeneration);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertEquals(Service.START_STICKY, fixture.alwaysOn());
            assertEquals(Service.START_STICKY, fixture.cleanup());
            assertEquals(0, fixture.service.recoveryCalls);
            assertEquals(0, fixture.service.cleanupCalls);
        }
    }

    @Test public void postedFailureCannotClearNewerStartOrReload() {
        for (VPNCommand command : new VPNCommand[]{VPNCommand.START, VPNCommand.RELOAD}) {
            try (Fixture fixture = new Fixture()) {
                fixture.command(VPNCommand.START, "MY_PACKAGE_REPLACED");
                // Hold the main thread until the newer command supersedes the posted failure.
                InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                    fixture.service.nativeStartFailed(fixture.service.commandGeneration);
                    fixture.service.onStartCommand(new Intent(fixture.context, ServiceVPN.class)
                            .putExtra(EXTRA_COMMAND, command).putExtra("showNotification", false),
                            0, ++fixture.nextStartId);
                });
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                assertEquals(Service.START_STICKY, fixture.alwaysOn());
                assertEquals(Service.START_STICKY, fixture.cleanup());
                assertEquals(0, fixture.service.recoveryCalls);
                assertEquals(0, fixture.service.cleanupCalls);
            }
        }
    }

    @Test public void explicitStopAllowsRecoveryCleanup() {
        try (Fixture fixture = new Fixture()) {
            fixture.command(VPNCommand.START, "MY_PACKAGE_REPLACED");
            fixture.command(VPNCommand.STOP, "All modules stopped");
            assertEquals(Service.START_NOT_STICKY, fixture.cleanup());
            assertEquals(1, fixture.service.cleanupCalls);
            assertEquals(3, fixture.service.cleanupStartId);
            assertEquals(VPNCommand.STOP, fixture.service.commands.get(1).getSerializableExtra(EXTRA_COMMAND));
        }
    }

    @Test public void commandlessStartWhileDisabledAllowsCleanupAfterGeneratedStop() {
        try (Fixture fixture = new Fixture()) {
            fixture.command(VPNCommand.START, "MY_PACKAGE_REPLACED");
            assertTrue(fixture.preferences.edit().putBoolean(VPN_SERVICE_ENABLED, false).commit());
            fixture.deliver(new Intent(fixture.context, ServiceVPN.class).putExtra("showNotification", false));
            assertEquals(Service.START_NOT_STICKY, fixture.cleanup());
            assertEquals(1, fixture.service.cleanupCalls);
            assertEquals(VPNCommand.STOP, fixture.service.commands.get(1).getSerializableExtra(EXTRA_COMMAND));
        }
    }
}
