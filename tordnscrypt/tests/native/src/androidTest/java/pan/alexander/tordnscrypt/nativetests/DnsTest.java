package pan.alexander.tordnscrypt.nativetests;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/** Regressions for dns.c. */
@RunWith(AndroidJUnit4.class)
public final class DnsTest extends NativeTestSupport {
    @Test public void oversizedAnswersBatchGuardsWithoutChangingPackets() { check("dnsUidOverflowBatches"); }
    @Test public void uidMappingLimitCountsDistinctAddressesAndPreservesShortestTtl() { check("dnsUidDistinct"); }
    @Test public void uidMappingsPreserveConflictingOwnersRegardlessOfAnswerOrder() { check("dnsUidOrder"); }
    @Test public void uidMappingsUseNormalizedQuestionNamesAndValidatedAliases() { check("dnsUidMappings"); }
    @Test public void uidMappingsRejectIncompleteMalformedAndFilteredResponses() { check("dnsUidInvalid"); }
    @Test public void uidMappingsRespectAddressAndAliasTtls() { check("dnsUidTtl"); }
    @Test public void questionCountsRespectOpcodeAndDoNotRewriteMalformedReplies() { check("dnsQuestionCounts"); }
    @Test public void tcpQuestionValidationHandlesSplitAndCoalescedFrames() { check("dnsTcpFraming"); }
    @Test public void hinfoAllocationFailure() { check("dnsAllocationFailure"); }
    @Test public void dnsNameBoundsAndCompression() { check("memoryNames"); }
    @Test public void malformedDnsResponses() { check("memoryDns"); }
    @Test public void dnsAnswersAndBlocking() { check("dnsResponses"); }
}
