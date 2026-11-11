package pan.alexander.tordnscrypt.nativetests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicReference;

/** Regressions for invizible.c. */
@RunWith(AndroidJUnit4.class)
public final class InvizibleTest extends NativeTestSupport {
    @Test public void libraryLoadFailuresReleaseReferencesAndCanRetry() { check("jniLoadFailures"); }
    @Test public void packetAllocationFailuresCleanUpAndCanRetry() { check("jniPacketFailures"); }
    @Test public void forwardingAllocationFailuresDenyAndCleanUp() { check("jniAllowedFailures"); }
    @Test public void dnsRecordAllocationFailuresCleanUpAndCanRetry() { check("jniDnsFailures"); }
    @Test public void usageAllocationFailuresCleanUpAndCanRetry() { check("jniUsageFailures"); }
    @Test public void socksCredentialByteLimits() { check("memorySocks5"); }
    @Test public void failedInitializationCleansUpAndCanRetry() { check("nativeInitialization"); }
    @Test public void failedArgumentsAllocationStopsWorkerWithoutUncaughtError() throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            // Match the production worker's Exception catch; Errors must not escape JNI.
            try {
                assertTrue(runArgumentsFailure());
            } catch (Exception exception) {
                failure.set(exception);
            }
        }, "VPN allocation failure test");
        worker.setUncaughtExceptionHandler((thread, error) -> failure.set(error));
        worker.start();
        worker.join(5000);
        assertFalse("VPN worker must return", worker.isAlive());
        assertNull("VPN worker must not throw", failure.get());
        assertEquals("Cannot allocate native tunnel arguments", exitReason);
    }
    @Test public void exceptionReporterHandlesSecondaryFailures() { check("exceptionFailures"); }
    @Test public void flagsArePublishedAcrossNativeThreads() { check("atomicFlags"); }
    @Test public void exceptionReporterAcceptsNullMessage() {
        assertTrue(reportException(new NullPointerException()));
    }
    @Test public void exceptionReporterAcceptsLongMessage() {
        assertTrue(reportException(new IllegalStateException(new String(new char[16384]).replace('\0', 'x'))));
    }
    @Test public void exceptionReporterHandlesThrowingGetter() {
        assertTrue(reportException(new RuntimeException() {
            @Override public String getMessage() { throw new IllegalStateException("getter failed"); }
        }));
    }
    private String exitReason;
    public void nativeExit(String reason) { exitReason = reason; }
    @Test public void exitReasonsAreBounded() {
        reportLongExit();
        assertEquals(1023, exitReason.length());
        assertEquals(new String(new char[1023]).replace('\0', 'x'), exitReason);
    }
    private static native boolean reportException(Throwable exception);
    private native boolean runArgumentsFailure();
    private native void reportLongExit();
}
