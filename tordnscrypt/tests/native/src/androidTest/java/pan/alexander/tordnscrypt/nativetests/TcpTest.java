package pan.alexander.tordnscrypt.nativetests;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/** Regressions for tcp.c. */
@RunWith(AndroidJUnit4.class)
public final class TcpTest extends NativeTestSupport {
    @Test public void destinationAddressesAreFullyInitialized() { check("tcpDestination"); }
    @Test public void fullPayloadQueueAcceptsMissingHeadAndRecovers() { check("tcpGapBytes"); }
    @Test public void fullSegmentQueueAcceptsMissingHeadAndRecovers() { check("tcpGapSegments"); }
    @Test public void gapRecoveryPreservesOverlappingBytesAndReplaysTail() { check("tcpGapOverlap"); }
    @Test public void queuedPayloadBytesAreBounded() { check("tcpQueueBytes"); }
    @Test public void queuedSegmentMetadataIsBounded() { check("tcpQueueSegments"); }
    @Test public void incomingDataRespectsReceiveWindowAndSequenceWrap() { check("tcpQueueWindow"); }
    @Test public void partialSendsSurviveShorterAndLongerRetransmissions() { check("tcpPartialRetransmission"); }
    @Test public void allocationFailuresPreserveQueuesAndReleasePartialSessions() { check("tcpAllocationFailure"); }
    @Test public void tcpOptionsAndFailedSetupCleanup() { check("memoryTcp"); }
    @Test public void socketSetupFailureCleanup() { check("tcpSocketCleanup"); }
    @Test public void queuedDataCleanupIsIdempotent() { check("tcpDataCleanup"); }
    @Test public void closedSessionsReleaseQueuedPayloads() { check("tcpClosedDataCleanup"); }
    @Test public void ipv4TcpConnectionAndData() { check("tcp4"); }
    @Test public void ipv6TcpConnectionAndData() { check("tcp6"); }
    @Test public void socksWithoutAuthentication() { check("socks4NoAuth"); }
    @Test public void socksWithUnicodeCredentials() { check("socks4Auth"); }
    @Test public void socksWithMaximumCredentials() { check("socks6Auth"); }
}
