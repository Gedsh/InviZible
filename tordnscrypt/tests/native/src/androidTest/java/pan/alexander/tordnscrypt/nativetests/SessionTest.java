package pan.alexander.tordnscrypt.nativetests;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/** Regressions for session.c. */
@RunWith(AndroidJUnit4.class)
public final class SessionTest extends NativeTestSupport {
    @Test public void ipv4IndexPreservesExactKeysAndLingeringAndStoppedStates() { check("sessionIndex4"); }
    @Test public void ipv6IndexPreservesExactKeysAndLingeringAndStoppedStates() { check("sessionIndex6"); }
    @Test public void indexHandlesCollisionsRemovalChurnAndContextReuse() { check("sessionIndexChurn"); }
    @Test public void policyReloadRemovesBlockedKeysAndKeepsClosedTcpLookup() { check("sessionIndexPolicy"); }
    @Test public void cleanupReleasesBudgetAndRescansEarlierBlockedFlowsOnce() { check("tcpMemoryRescan"); }
    @Test public void unchangedFlowsSkipMonitoringAndPacketAndSocketEventsRefreshOneFlow() { check("tcpMonitorCache"); }
    @Test public void bufferedDnsKeepsDrainingBeforeFallbackDeadline() { check("tcpMonitorDnsOutput"); }
    @Test public void cleanupWithoutMemoryWaitersDoesNotRescan() { check("tcpMemoryNoWaiters"); }
    @Test public void dnsReleaseDoesNotRescanQueueWaiters() { check("tcpMemoryQueueWaitDnsRelease"); }
    @Test public void queueReleaseDoesNotRescanDnsWaiters() { check("tcpMemoryDnsWaitQueueRelease"); }
    @Test public void dnsReleaseRescansEarlierDnsWaitersOnce() { check("tcpMemoryDnsRescan"); }
    @Test public void queueReleaseDoesNotRescanSocketWaiters() { check("tcpMemorySocketWait"); }
    @Test public void earliestTcpProbeDeadlineWinsAndStopPipeRemainsReady() { check("tcpProbeDeadlines"); }
    @Test public void eventLoopFreesScratchBuffersOnStopAndPollFailure() { check("eventBufferCleanup"); }
    @Test public void closedUdpRecordsStayBoundedDuringChurn() { check("closedUdpLimit"); }
    @Test public void closedTcpRecordsStayBoundedDuringChurn() { check("closedTcpLimit"); }
    @Test public void mixedClosedRecordsShareLimitAndPreserveActiveFlows() { check("closedMixedLimit"); }
    @Test public void stoppedEventLoopReleasesArguments() { check("eventLoopShutdown"); }
    @Test public void idleClosedUdpExpiresOnTime() { check("idleUdpClosed"); }
    @Test public void idleBlockedUdpExpiresOnTime() { check("idleUdpBlocked"); }
    @Test public void idleClosedTcpExpiresOnTime() { check("idleTcpClosed"); }
    @Test public void finishingUdpSchedulesCleanup() { check("idleUdpFinishing"); }
    @Test public void closingTcpSchedulesCleanup() { check("idleTcpClosing"); }
    @Test public void earliestCleanupDeadlineWins() { check("idleMixed"); }
}
