package pan.alexander.tordnscrypt.utils.bootcomplete;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;
import static pan.alexander.tordnscrypt.modules.ModulesServiceActions.ACTION_STOP_SERVICE_FOREGROUND;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.PROXY_MODE;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.ROOT_MODE;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.VPN_MODE;

import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

import pan.alexander.tordnscrypt.modules.ModulesService;
import pan.alexander.tordnscrypt.vpn.service.ServiceVPN;

/** Records service requests without starting a VPN, module, or foreground service. */
@RunWith(AndroidJUnit4.class)
public final class BootCompleteManagerTest {
    private static final class RecordingContext extends ContextWrapper {
        final List<Intent> requests = new ArrayList<>();
        RecordingContext() { super(InstrumentationRegistry.getInstrumentation().getTargetContext()); }
        @Override public ComponentName startService(Intent intent) { return record(intent); }
        @Override public ComponentName startForegroundService(Intent intent) { return record(intent); }
        private ComponentName record(Intent intent) {
            requests.add(new Intent(intent));
            return intent.getComponent();
        }
    }

    private final BootCompleteManager manager = new BootCompleteManager(
            null, null, null, null, null, null, null);

    @Before public void requiresForegroundServiceApi() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O);
    }

    private static void assertVpnCleanup(RecordingContext context) throws Exception {
        assertEquals(2, context.requests.size());
        Intent vpn = context.requests.get(0);
        assertEquals(new ComponentName(context, ServiceVPN.class), vpn.getComponent());
        assertEquals(ACTION_STOP_SERVICE_FOREGROUND, vpn.getAction());
        assertTrue(vpn.getBooleanExtra("showNotification", false));
        ServiceInfo declared = context.getPackageManager().getServiceInfo(vpn.getComponent(), 0);
        assertEquals("android.permission.BIND_VPN_SERVICE", declared.permission);
        assertEquals(new ComponentName(context, ModulesService.class), context.requests.get(1).getComponent());
        assertEquals(ACTION_STOP_SERVICE_FOREGROUND, context.requests.get(1).getAction());
    }

    @Test public void vpnRecoveryCleanupTargetsDeclaredService() throws Exception {
        RecordingContext context = new RecordingContext();
        manager.stopServicesForeground(context, VPN_MODE, false);
        assertVpnCleanup(context);
    }

    @Test public void rootTtlRecoveryCleanupTargetsDeclaredService() throws Exception {
        RecordingContext context = new RecordingContext();
        manager.stopServicesForeground(context, ROOT_MODE, true);
        assertVpnCleanup(context);
    }

    @Test public void modesWithoutVpnOnlyCleanUpModulesService() {
        RecordingContext context = new RecordingContext();
        manager.stopServicesForeground(context, ROOT_MODE, false);
        manager.stopServicesForeground(context, PROXY_MODE, false);
        assertEquals(2, context.requests.size());
        for (Intent request : context.requests) {
            assertEquals(new ComponentName(context, ModulesService.class), request.getComponent());
            assertEquals(ACTION_STOP_SERVICE_FOREGROUND, request.getAction());
        }
    }
}
