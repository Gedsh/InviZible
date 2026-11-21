package pan.alexander.tordnscrypt.nativetests;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Real local SOCKS5 control and UDP relay regressions for udp_proxy.c. */
@RunWith(AndroidJUnit4.class)
public final class UdpProxyTest extends NativeTestSupport {
    @Test public void ipv4RelayPreservesDatagramsAndRejectsMalformedFrames() { check("udpProxy4"); }
    @Test public void authenticatedIpv6RelayAndControlLifetime() { check("udpProxy6"); }
    @Test public void ipv6DestinationThroughIpv4Proxy() { check("udpProxyCross"); }
    @Test public void ipv4ControlCanNegotiateIpv6Relay() { check("udpProxyRelayFamily"); }
    @Test public void fatalSendWhileFlushingQueueReleasesRemainingDatagrams() { check("udpProxyQueueSendFailure"); }
    @Test public void relaySocketErrorReportsItsActualCause() { check("udpProxyRelayError"); }
    @Test public void relayReceiveFailureReportsItsActualCause() { check("udpProxyReceiveError"); }
    @Test public void controlSocketErrorsPreserveCauseAndReportOnce() { check("udpProxySocketErrors"); }
    @Test public void bufferedRejectionsTakePrecedenceOverControlReset() { check("udpProxyResetReplies"); }
    @Test public void ipv4MtuErrorsKeepAssociationUsable() { check("udpProxyMtu4"); }
    @Test public void ipv6MtuErrorsKeepAssociationUsable() { check("udpProxyMtu6"); }
    @Test public void ipv6MtuThroughIpv4RelayAccountsForFraming() { check("udpProxyMtu6over4"); }
    @Test public void ipv4MtuThroughIpv6RelayAndRealKernelError() { check("udpProxyMtu4over6"); }
    @Test public void malformedHandshakesAuthAndEventFailuresStayClosed() { check("udpProxyBadHandshakes"); }
    @Test public void uidRecoveryCanSwitchProxyDirectAndBlockedRoutesSafely() { check("udpProxyUidRoutes"); }
    @Test public void associationAndPendingMemoryStayBounded() { check("udpProxyLimits"); }
    @Test public void expiredAssociationsAreReclaimedBeforeRejectingNewFlows() { check("udpProxyExpiredCapacity"); }
    @Test public void capacityReplacesOnlyTheOldestReadyIdleAssociation() { check("udpProxyIdleCapacity"); }
    @Test public void failedAndBackwardClocksDoNotReclaimAssociations() { check("udpProxyCapacityClocks"); }
    @Test public void allocationProtectionAndEpollFailuresReleaseResources() { check("udpProxyAllocations"); }
    @Test public void handshakeTimesOutWithoutTrafficExtendingDeadline() { check("udpProxyTimeout"); }
    @Test public void refusedAuthenticationNeverFallsBackToDirect() { check("udpProxyRefused"); }
}
