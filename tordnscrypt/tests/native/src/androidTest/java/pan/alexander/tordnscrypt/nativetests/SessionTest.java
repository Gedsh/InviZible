package pan.alexander.tordnscrypt.nativetests;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/** Regressions for session.c. */
@RunWith(AndroidJUnit4.class)
public final class SessionTest extends NativeTestSupport {
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
