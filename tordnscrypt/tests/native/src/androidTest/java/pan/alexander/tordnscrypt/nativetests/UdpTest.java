package pan.alexander.tordnscrypt.nativetests;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/** Regressions for udp.c. */
@RunWith(AndroidJUnit4.class)
public final class UdpTest extends NativeTestSupport {
    @Test public void cachedLookupPreservesActiveFinishingClosedAndBlockedFlows() { check("udpLookupStates"); }
    @Test public void datagramsReuseBuffersThroughMaximumPayloadAndSmallerReplies() { check("udpBufferReuse"); }
    @Test public void disabledLogsSkipFormattingButAccountingKeepsDestination() { check("lazyUdpLogging"); }
    @Test public void ipv4MultiQuestionDnsRequestsGetFormerr() { check("udpDnsRequest4"); }
    @Test public void ipv6MultiQuestionDnsRequestsGetFormerr() { check("udpDnsRequest6"); }
    @Test public void ipv4MultiQuestionDnsResponsesAreDropped() { check("udpDnsResponse4"); }
    @Test public void ipv6MultiQuestionDnsResponsesAreDropped() { check("udpDnsResponse6"); }
    @Test public void destinationAddressesAreFullyInitialized() { check("udpDestination"); }
    @Test public void allocationFailuresReleaseResources() { check("udpAllocationFailure"); }
    @Test public void truncatedDnsHeader() { check("udpShortDns"); }
    @Test public void socketSetupFailureCleanup() { check("udpSocketCleanup"); }
    @Test public void ipv4BlockedFlowsStayBounded() { check("udpBlocked4Limit"); }
    @Test public void ipv6BlockedFlowsStayBounded() { check("udpBlocked6Limit"); }
    @Test public void repeatedBlockedDnsReusesRecord() { check("udpBlockedDuplicates"); }
    @Test public void ipv4UdpForwarding() { check("udp4Direct"); }
    @Test public void ipv4UdpRedirect() { check("udp4Redirect"); }
    @Test public void ipv6UdpForwarding() { check("udp6Direct"); }
    @Test public void ipv6LoopbackRedirect() { check("udp6Redirect"); }
}
