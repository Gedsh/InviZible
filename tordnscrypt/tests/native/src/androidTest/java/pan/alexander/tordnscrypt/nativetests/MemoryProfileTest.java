package pan.alexander.tordnscrypt.nativetests;

import static org.junit.Assert.assertNull;

import org.junit.Test;

public class MemoryProfileTest {
    static { System.loadLibrary("invizible_memory_profile_tests"); }

    private static native String runProfileCase(int test);

    @Test public void firstRecordFailureDoesNotFailPayloadAllocation() { assertNull(runProfileCase(0)); }
    @Test public void growthFailureReleasesRecordsAndDisablesTracking() { assertNull(runProfileCase(1)); }
    @Test public void payloadFailuresPreserveOwnershipAndBalance() { assertNull(runProfileCase(2)); }
    @Test public void releaseBeforeFirstAllocationAndErrnoAreSafe() { assertNull(runProfileCase(3)); }
    @Test public void lockFailureLeavesRecordsUntouchedAndAllowsRetry() { assertNull(runProfileCase(4)); }
    @Test public void concurrentFirstUseAndChurnReleaseAllRecords() { assertNull(runProfileCase(5)); }
}
