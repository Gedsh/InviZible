package pan.alexander.tordnscrypt.nativetests;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/** Regressions for dhcp.c. */
@RunWith(AndroidJUnit4.class)
public final class DhcpTest extends NativeTestSupport {
    @Test public void responseBoundsAndAllocationCleanup() { check("dhcpResponseBounds"); }
    @Test public void dhcpOfferOptions() { check("dhcpOffer"); }
}
