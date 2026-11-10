package pan.alexander.tordnscrypt.nativetests;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/** Regressions for udp.c. */
@RunWith(AndroidJUnit4.class)
public final class UdpTest extends NativeTestSupport {
    @Test public void truncatedDnsHeader() { check("udpShortDns"); }
    @Test public void socketSetupFailureCleanup() { check("udpSocketCleanup"); }
    @Test public void ipv4UdpForwarding() { check("udp4Direct"); }
    @Test public void ipv4UdpRedirect() { check("udp4Redirect"); }
    @Test public void ipv6UdpForwarding() { check("udp6Direct"); }
    @Test public void ipv6LoopbackRedirect() { check("udp6Redirect"); }
}
