package pan.alexander.tordnscrypt.vpn.service;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.lang.reflect.Field;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Constant MTU through the Java/JNI binding and interface-builder equality. */
@RunWith(AndroidJUnit4.class)
public final class VpnMtuTest {
    private static final class TestService extends ServiceVPN {
        TestService(Context context) { attachBaseContext(context); }
    }

    @Test public void mtuQueryReturns1500() {
        ServiceVPN service = new TestService(InstrumentationRegistry.getInstrumentation().getTargetContext());
        assertEquals(1500, service.jni_get_mtu());
    }

    @Test public void unchangedMtuPermitsInterfaceReuse() throws Exception {
        ServiceVPN service = new TestService(InstrumentationRegistry.getInstrumentation().getTargetContext());
        BuilderVPN previous = new BuilderVPN(service), next = new BuilderVPN(service);
        // Keep connectivity deterministic, including on a device with no active network.
        Field network = BuilderVPN.class.getDeclaredField("networkInfo");
        network.setAccessible(true);
        NetworkInfo info = new NetworkInfo(ConnectivityManager.TYPE_WIFI, 0, "WIFI", "");
        network.set(previous, info); network.set(next, info);
        previous.setMtu(service.jni_get_mtu()); next.setMtu(service.jni_get_mtu());
        assertEquals(previous, next);
        next.setMtu(1400); assertNotEquals(previous, next);
        next.setMtu(service.jni_get_mtu()); assertEquals(previous, next);
    }
}
