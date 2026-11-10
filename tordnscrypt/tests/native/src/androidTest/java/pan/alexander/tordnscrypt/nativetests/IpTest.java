package pan.alexander.tordnscrypt.nativetests;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/** Regressions for ip.c. */
@RunWith(AndroidJUnit4.class)
public final class IpTest extends NativeTestSupport {
    @Test public void packetHeaderBounds() { check("memoryPackets"); }
    @Test public void uidLookupClosesMalformedProcFile() { check("uidFileCleanup"); }
    @Test public void ipv4HeaderOptions() { check("udp4Options"); }
    @Test public void ipv6ExtensionHeaders() { check("udp6Extensions"); }
}
