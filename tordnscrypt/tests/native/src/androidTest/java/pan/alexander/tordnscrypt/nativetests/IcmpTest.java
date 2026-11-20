package pan.alexander.tordnscrypt.nativetests;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/** Regressions for icmp.c. */
@RunWith(AndroidJUnit4.class)
public final class IcmpTest extends NativeTestSupport {
    @Test public void epollRegistrationFailureReleasesResourcesAndNextPingWorks() { check("icmpEpollCleanup"); }
    @Test public void echoRequestsRequireMatchingFamilyAndZeroCode() { check("icmpEchoValidation"); }
    @Test public void blockedUdpErrorsQuoteOriginalPacketAndHaveValidChecksums() { check("icmpBlockedUnreachable"); }
    @Test public void upstreamErrorsUseBoundedOriginalQuotesAfterBufferReuse() { check("icmpUpstreamUnreachable"); }
    @Test public void unreachableBoundsMulticastAndAllocationFailures() { check("icmpUnreachableBounds"); }
    @Test public void resolvedUidFillsUnknownLiveOwnerAndPreservesKnownOwner() { check("icmpUidUpdate"); }
    @Test public void truncatedHeadersAndOffsetsPastPacketEndAreRejected() { check("icmpPacketBounds"); }
    @Test public void repliesReuseBuffersAndRestoreIdentifiers() { check("icmpBufferReuse"); }
    @Test public void shortRepliesAreDroppedAndLaterRepliesStillWork() { check("icmpShortReplies"); }
    @Test public void ipv6ForwardChecksumsExcludeExtensionHeaders() { check("icmp6ForwardChecksums"); }
    @Test public void destinationAddressesAreFullyInitialized() { check("icmpDestination"); }
    @Test public void allocationFailuresReleaseResources() { check("icmpAllocationFailure"); }
    @Test public void socketSetupFailureCleanup() { check("icmpSocketCleanup"); }
    @Test public void ipv4IcmpPacket() { check("icmp4"); }
    @Test public void ipv6IcmpPacket() { check("icmp6"); }
}
