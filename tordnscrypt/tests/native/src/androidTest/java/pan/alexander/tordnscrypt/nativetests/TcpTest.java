package pan.alexander.tordnscrypt.nativetests;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/** Regressions for tcp.c. */
@RunWith(AndroidJUnit4.class)
public final class TcpTest extends NativeTestSupport {
    @Test public void ipv4ScaledWindowCombinesSocketAndRetainedCapacity() { check("tcpScaledCapacity4"); }
    @Test public void ipv6ScaledWindowCombinesSocketAndRetainedCapacity() { check("tcpScaledCapacity6"); }
    @Test public void aggregatePayloadLimitKeepsGapAndDnsProgressReserve() { check("tcpMemoryBytes"); }
    @Test public void aggregateNodeLimitBoundsTinySegmentsAndAllowsRecovery() { check("tcpMemoryNodes"); }
    @Test public void replacementPeakAndAllocationFailuresPreserveBudget() { check("tcpMemoryReplacement"); }
    @Test public void sharedMemoryReleaseReopensScaledWindowsWithoutWritableSpin() { check("tcpMemoryWakeup"); }
    @Test public void dnsPrefixWaitsForMemoryAndResumesFiltering() { check("tcpDnsMemoryPrefix"); }
    @Test public void dnsGrowthWaitsForMemoryAndTracksActualFilteredCapacity() { check("tcpDnsMemoryGrowth"); }
    @Test public void dnsMemoryWaitDoesNotSpinOnHangupOrLoseBufferedResponse() { check("tcpDnsMemoryHangup"); }
    @Test public void readBatchReusesCapacityAndWritesAndNewBatchesRefreshIt() { check("tcpCapacityReuse"); }
    @Test public void ipv4EmptyQueueReopensAdvertisedZeroWindow() { check("tcpWindowReopen4"); }
    @Test public void ipv6EmptyQueueReopensAdvertisedZeroWindow() { check("tcpWindowReopen6"); }
    @Test public void closedAppWindowBacksOffAndAckResumesReadsImmediately() { check("tcpWindowProbes"); }
    @Test public void upstreamBackpressureUsesReadinessAndPreservesPartialWrites() { check("tcpWriteBackpressure"); }
    @Test public void reusedOutputKeepsDnsFilteringAndPartialFrameOwnership() { check("tcpDnsBufferReuse"); }
    @Test public void socketBuffersReuseStorageWithoutOverwritingQueuedTcpData() { check("tcpBufferReuse"); }
    @Test public void normalTrafficSkipsFormattingAndErrorsKeepContext() { check("lazyTcpLogging"); }
    @Test public void coalescedDnsRepliesKeepCallbacksAndDomainAndHttpsBlocking() { check("tcpDnsCoalesced"); }
    @Test public void completeDnsReplyAndPartialTailAreBothFiltered() { check("tcpDnsPartialTail"); }
    @Test public void dnsRepliesSplitAtEveryByteAreFiltered() { check("tcpDnsByteSplits"); }
    @Test public void dnsPrefixAllocationFailureClosesWithoutLeaking() { check("tcpDnsAllocFailure"); }
    @Test public void dnsFrameResizeFailureClosesWithoutLeaking() { check("tcpDnsResizeFailure"); }
    @Test public void partialDnsReplyExpiresAndReleasesMemory() { check("tcpDnsPartialTimeout"); }
    @Test public void partialDnsReplyAtEofResetsAndReleasesMemory() { check("tcpDnsPartialEof"); }
    @Test public void inspectedDnsReplyResumesWhenAppWindowReopens() { check("tcpDnsWindow"); }
    @Test public void dnsReplyResumesDespiteFullUpstreamSendBuffer() { check("tcpDnsUpstreamBackpressure"); }
    @Test public void dnsOutputAllocationFailureReleasesBufferedReply() { check("tcpDnsWriteFailure"); }
    @Test public void tunnelTeardownReleasesPartialDnsReply() { check("tcpDnsTeardown"); }
    @Test public void maximumDnsFrameIsForwardedInBoundedPackets() { check("tcpDnsMaxFrame"); }
    @Test public void ipv4DnsResponsesValidateFramesAndReleaseInvalidStreams() { check("tcpDnsResponse4"); }
    @Test public void ipv6DnsResponsesValidateFramesAndReleaseInvalidStreams() { check("tcpDnsResponse6"); }
    @Test public void ipv4DefinitiveConnectFailuresResetAndReleaseResources() { check("tcpConnectFailure4"); }
    @Test public void ipv6DefinitiveConnectFailuresResetAndReleaseResources() { check("tcpConnectFailure6"); }
    @Test public void ipv4TemporaryConnectFailuresRemainRetryable() { check("tcpConnectTransient4"); }
    @Test public void ipv6TemporaryConnectFailuresRemainRetryable() { check("tcpConnectTransient6"); }
    @Test public void ipv4LocalSetupFailuresDoNotResetUsingStaleErrno() { check("tcpSetupRetry4"); }
    @Test public void ipv6LocalSetupFailuresDoNotResetUsingStaleErrno() { check("tcpSetupRetry6"); }
    @Test public void ipv4AppDataContinuesAfterUpstreamEof() { check("tcpHalfClose4"); }
    @Test public void ipv6AppDataContinuesAfterUpstreamEof() { check("tcpHalfClose6"); }
    @Test public void finWaitsForMissingAndQueuedBytes() { check("tcpFinGap"); }
    @Test public void appFinStillAllowsUpstreamResponse() { check("tcpAppFinFirst"); }
    @Test public void simultaneousAndRepeatedFinDoNotLoseData() { check("tcpSimultaneousFin"); }
    @Test public void halfClosedSocketAndQueueExpire() { check("tcpHalfCloseTimeout"); }
    @Test public void failedWriteShutdownReleasesSocket() { check("tcpShutdownFailure"); }
    @Test public void resetReleasesHalfClosedSocketAndQueue() { check("tcpHalfCloseReset"); }
    @Test public void existingFlowErrorsLogStoredUid() { check("tcpSessionUid"); }
    @Test public void upstreamEofPreservesAlreadyQueuedData() { check("tcpEofQueued"); }
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
