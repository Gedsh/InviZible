package pan.alexander.tordnscrypt.utils.apps;

import android.os.ResultReceiver;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class CrossUserPermissionTest {
    private static final class Environment implements CrossUserPermission.Environment {
        volatile boolean granted;
        boolean eligible = true;
        boolean accepted = true;
        boolean throwDispatch;
        boolean throwRefresh;
        final AtomicInteger dispatches = new AtomicInteger();
        final AtomicInteger refreshes = new AtomicInteger();
        volatile ResultReceiver receiver;

        @Override public boolean granted() { return granted; }
        @Override public boolean eligible() { return eligible; }
        @Override public boolean dispatch(ResultReceiver result) {
            dispatches.incrementAndGet();
            receiver = result;
            if (throwDispatch) throw new IllegalStateException("dispatch regression");
            return accepted;
        }
        @Override public void refresh() {
            refreshes.incrementAndGet();
            if (throwRefresh) throw new IllegalStateException("refresh regression");
        }
        void finish(int code) {
            receiver.send(code, null);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        }
    }

    @Test public void grantedOrIneligibleNeverDispatches() {
        CrossUserPermission.Requests requests = new CrossUserPermission.Requests();
        Environment environment = new Environment();
        environment.granted = true;
        assertFalse(requests.request(environment));
        environment.granted = false;
        environment.eligible = false;
        assertFalse(requests.request(environment));
        assertEquals(0, environment.dispatches.get());
    }

    @Test public void duplicateRequestsShareOnePendingCommand() {
        CrossUserPermission.Requests requests = new CrossUserPermission.Requests();
        Environment environment = new Environment();
        assertTrue(requests.request(environment));
        assertTrue(requests.request(environment));
        assertEquals(1, environment.dispatches.get());
        environment.granted = true;
        environment.finish(0);
        assertEquals(1, environment.refreshes.get());
        assertFalse(requests.request(environment));
    }

    @Test public void concurrentRequestsDispatchOnlyOnce() throws Exception {
        CrossUserPermission.Requests requests = new CrossUserPermission.Requests();
        Environment environment = new Environment();
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        AtomicInteger accepted = new AtomicInteger();
        for (int index = 0; index < 12; index++) {
            Thread thread = new Thread(() -> {
                try { start.await(); } catch (InterruptedException e) { throw new AssertionError(e); }
                if (requests.request(environment)) accepted.incrementAndGet();
            });
            threads.add(thread);
            thread.start();
        }
        start.countDown();
        for (Thread thread : threads) thread.join(5000);
        assertEquals(12, accepted.get());
        assertEquals(1, environment.dispatches.get());
        environment.finish(1);
    }

    @Test public void rejectedDispatchCanBeRetried() {
        CrossUserPermission.Requests requests = new CrossUserPermission.Requests();
        Environment environment = new Environment();
        environment.accepted = false;
        assertFalse(requests.request(environment));
        environment.accepted = true;
        assertTrue(requests.request(environment));
        assertEquals(2, environment.dispatches.get());
        environment.finish(1);
    }

    @Test public void dispatchExceptionCanBeRetried() {
        CrossUserPermission.Requests requests = new CrossUserPermission.Requests();
        Environment environment = new Environment();
        environment.throwDispatch = true;
        assertFalse(requests.request(environment));
        environment.throwDispatch = false;
        assertTrue(requests.request(environment));
        environment.finish(1);
    }

    @Test public void commandSuccessDoesNotProvePermission() {
        CrossUserPermission.Requests requests = new CrossUserPermission.Requests();
        Environment environment = new Environment();
        assertTrue(requests.request(environment));
        environment.finish(0);
        assertEquals(0, environment.refreshes.get());
        assertTrue(requests.request(environment));
        environment.finish(1);
    }

    @Test public void droppedCommandReleasesPendingRequest() {
        CrossUserPermission.Requests requests = new CrossUserPermission.Requests();
        Environment environment = new Environment();
        assertTrue(requests.request(environment));
        environment.finish(1);
        assertTrue(requests.request(environment));
        assertEquals(2, environment.dispatches.get());
        environment.finish(1);
    }

    @Test public void staleCallbackDoesNotReleaseNewRequest() {
        CrossUserPermission.Requests requests = new CrossUserPermission.Requests();
        Environment environment = new Environment();
        assertTrue(requests.request(environment));
        ResultReceiver old = environment.receiver;
        environment.finish(1);
        assertTrue(requests.request(environment));
        old.send(0, null);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        assertTrue(requests.request(environment));
        assertEquals(2, environment.dispatches.get());
        environment.granted = true;
        environment.finish(0);
        environment.finish(0);
        assertEquals(1, environment.refreshes.get());
    }

    @Test public void refreshFailureDoesNotLeaveRequestPending() {
        CrossUserPermission.Requests requests = new CrossUserPermission.Requests();
        Environment environment = new Environment();
        environment.throwRefresh = true;
        assertTrue(requests.request(environment));
        environment.granted = true;
        environment.finish(0);
        environment.granted = false;
        assertTrue(requests.request(environment));
        environment.finish(1);
    }
}
