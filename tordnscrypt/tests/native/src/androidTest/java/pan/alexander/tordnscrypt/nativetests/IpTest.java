package pan.alexander.tordnscrypt.nativetests;

import android.os.Build;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assume.assumeTrue;

/** Regressions for ip.c. */
@RunWith(AndroidJUnit4.class)
public final class IpTest extends NativeTestSupport {
    private static void checkLiveProc(String name) {
        assumeTrue("Android 10+ restricts app access to /proc/net; legacy UID lookup is for older devices",
                Build.VERSION.SDK_INT < Build.VERSION_CODES.Q);
        check(name);
    }

    @Test public void liveIpv4TcpProcRestoresSocketOwnerAndCachesIt() { checkLiveProc("uidLiveTcp4"); }
    @Test public void liveIpv6TcpProcRestoresSocketOwnerAndCachesIt() { checkLiveProc("uidLiveTcp6"); }
    @Test public void liveIpv4UdpProcRestoresSocketOwnerAndCachesIt() { checkLiveProc("uidLiveUdp4"); }
    @Test public void liveIpv6UdpProcRestoresSocketOwnerAndCachesIt() { checkLiveProc("uidLiveUdp6"); }
    @Test public void liveIpv4IcmpProcRestoresSocketOwnerAndCachesIt() { checkLiveProc("uidLiveIcmp4"); }
    @Test public void liveIpv6IcmpProcRestoresOwnerOrHandlesMissingTable() { checkLiveProc("uidLiveIcmp6"); }
    @Test public void liveMappedTcpProcRestoresIpv4SocketOwner() { checkLiveProc("uidLiveMappedTcp"); }
    @Test public void liveMappedUdpProcRestoresIpv4SocketOwner() { checkLiveProc("uidLiveMappedUdp"); }
    @Test public void uidRestorationMatchesUnalignedIpv4AddressBits() { check("restoreUidAddresses"); }
    @Test public void procUidAddressesDecodeNativeWordsForBothFamilies() { check("uidProcAddresses"); }
    @Test public void malformedProcUidRowsCloseWithoutCaching() { check("uidProcMalformed"); }
    @Test public void procUidWildcardAndDuplicateMatchOrderIsPreserved() { check("uidProcMatchOrder"); }
    @Test public void ipv4LargeUidUpdatesMatchPreviousReplacementPolicy() { check("uidUpdate4"); }
    @Test public void ipv6LargeUidUpdatesMatchPreviousReplacementPolicy() { check("uidUpdate6"); }
    @Test public void uidUpdateScratchFailureFallsBackAndMalformedScanFreesScratch() { check("uidUpdateFallback"); }
    @Test public void uidCacheGrowthFailurePreservesActiveUpdateIndex() { check("uidUpdateGrowthFailure"); }
    @Test public void udpFlowLookupIsSharedAcrossLimitUidPolicyAndForwarding() { check("udpPacketLookup"); }
    @Test public void dnsKeepsForwardingPolicyAndSessionLimitRules() { check("udpDnsLookupPolicy"); }
    @Test public void tunnelReadsReuseInputThroughRetriesAndEof() { check("tunBufferReuse"); }
    @Test public void ipv4ChecksumIncludesHeaderOptionsAndPadding() { check("ipChecksumHeader"); }
    @Test public void ipv4UdpChecksumValidationAllowsOmission() { check("ipChecksumUdp4"); }
    @Test public void ipv6UdpChecksumValidationRejectsOmission() { check("ipChecksumUdp6"); }
    @Test public void ipv4TcpChecksumsCoverPseudoHeaderAndPayload() { check("ipChecksumTcp4"); }
    @Test public void ipv6TcpChecksumsCoverPseudoHeaderAndPayload() { check("ipChecksumTcp6"); }
    @Test public void ipv4IcmpChecksumsCoverEchoAndPayload() { check("ipChecksumIcmp4"); }
    @Test public void ipv6IcmpChecksumsCoverPseudoHeaderAndPayload() { check("ipChecksumIcmp6"); }
    @Test public void transportChecksumsExcludeIpv6ExtensionHeaders() { check("ipChecksumExtensions"); }
    @Test public void checksumsUseFinalRouteAndHomeAddresses() { check("ipChecksumRouting"); }
    @Test public void generatedZeroUdpChecksumIsEncodedAsAllOnes() { check("ipChecksumZero"); }
    @Test public void tunnelReadAllocationFailureStopsCleanly() { check("tunAllocationFailure"); }
    @Test public void packetHeaderBounds() { check("memoryPackets"); }
    @Test public void uidLookupClosesMalformedProcFile() { check("uidFileCleanup"); }
    @Test public void uidLookupSurvivesCacheAllocationFailure() { check("uidCacheAllocationFailure"); }
    @Test public void uidCacheRefreshDoesNotDuplicateEntries() { check("uidCacheRefresh"); }
    @Test public void uidCacheRemainsBounded() { check("uidCacheLimit"); }
    @Test public void uidCacheSeparatesProtocolsAndAddressFamilies() { check("uidCacheKeys"); }
    @Test public void ipv4HeaderOptions() { check("udp4Options"); }
    @Test public void ipv6ExtensionHeaders() { check("udp6Extensions"); }
}
