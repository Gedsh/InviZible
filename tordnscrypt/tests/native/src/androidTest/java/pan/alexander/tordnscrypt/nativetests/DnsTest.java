package pan.alexander.tordnscrypt.nativetests;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/** Regressions for dns.c. */
@RunWith(AndroidJUnit4.class)
public final class DnsTest extends NativeTestSupport {
    @Test public void hinfoAllocationFailure() { check("dnsAllocationFailure"); }
    @Test public void dnsNameBoundsAndCompression() { check("memoryNames"); }
    @Test public void malformedDnsResponses() { check("memoryDns"); }
    @Test public void dnsAnswersAndBlocking() { check("dnsResponses"); }
}
