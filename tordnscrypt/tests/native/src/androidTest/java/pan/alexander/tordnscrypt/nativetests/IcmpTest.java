package pan.alexander.tordnscrypt.nativetests;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/** Regressions for icmp.c. */
@RunWith(AndroidJUnit4.class)
public final class IcmpTest extends NativeTestSupport {
    @Test public void allocationFailuresReleaseResources() { check("icmpAllocationFailure"); }
    @Test public void socketSetupFailureCleanup() { check("icmpSocketCleanup"); }
    @Test public void ipv4IcmpPacket() { check("icmp4"); }
    @Test public void ipv6IcmpPacket() { check("icmp6"); }
}
