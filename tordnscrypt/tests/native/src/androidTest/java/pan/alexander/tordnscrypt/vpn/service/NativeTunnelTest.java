package pan.alexander.tordnscrypt.vpn.service;

import static org.junit.Assert.*;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Exercises the production owner with controllable workers instead of a VPN interface. */
@RunWith(AndroidJUnit4.class)
public final class NativeTunnelTest {
    private static void await(CountDownLatch latch) throws InterruptedException {
        assertTrue("Worker did not reach expected checkpoint", latch.await(2, TimeUnit.SECONDS));
    }

    @Test public void initializesBeforeConfiguringAndRestartsSameContext() throws Exception {
        NativeTunnelTestBackend backend = new NativeTunnelTestBackend();
        try (NativeTunnel tunnel = new NativeTunnel(backend)) {
            tunnel.start(() -> backend.events.add("configure"), backend::run);
            await(backend.entered);
            assertEquals("initialize", backend.events.get(0));
            assertEquals("configure", backend.events.get(1));
            assertEquals("start", backend.events.get(2));
            tunnel.stop();
            assertEquals(1, backend.cleared);
            tunnel.start(() -> backend.events.add("configure-again"), context -> {});
            assertEquals(1, backend.initialized);
        }
        assertEquals(1, backend.destroyed);
    }

    @Test public void preparingBeforeVpnSetupDoesNotReinitializeAtStart() throws Exception {
        NativeTunnelTestBackend backend = new NativeTunnelTestBackend();
        try (NativeTunnel tunnel = new NativeTunnel(backend)) {
            tunnel.prepare();
            tunnel.prepare();
            tunnel.start(() -> backend.events.add("configure"), backend::run);
            await(backend.entered);
            assertEquals(1, backend.initialized);
            assertTrue(backend.events.indexOf("initialize") < backend.events.indexOf("configure"));
        }
        assertEquals(1, backend.destroyed);
    }

    @Test public void timeoutKeepsContextAndWorkerUntilTermination() throws Exception {
        NativeTunnelTestBackend backend = new NativeTunnelTestBackend();
        backend.releaseOnStop = false;
        NativeTunnel tunnel = new NativeTunnel(backend, 20);
        NativeTunnelTestBackend nextBackend = new NativeTunnelTestBackend();
        NativeTunnel replacement = new NativeTunnel(nextBackend);
        try {
            tunnel.start(() -> {}, backend::run);
            await(backend.entered);
            assertThrows(IllegalStateException.class, tunnel::stop);
            assertEquals(0, backend.cleared);
            assertEquals(0, backend.destroyed);
            assertThrows(IllegalStateException.class,
                    () -> replacement.start(() -> {}, nextBackend::run));
            assertEquals(0, nextBackend.initialized);
            assertEquals(0, backend.destroyed);
            backend.exit.countDown();
            backend.worker.join(2000);
            tunnel.stop();
            assertEquals(1, backend.cleared);
        } finally {
            backend.exit.countDown();
            tunnel.close();
            replacement.close();
        }
        assertEquals(1, backend.destroyed);
    }

    @Test public void delayedOldDestroyCannotFreeReplacement() throws Exception {
        NativeTunnelTestBackend oldBackend = new NativeTunnelTestBackend();
        NativeTunnelTestBackend nextBackend = new NativeTunnelTestBackend();
        NativeTunnel old = new NativeTunnel(oldBackend);
        NativeTunnel next = new NativeTunnel(nextBackend);
        try {
            old.start(() -> {}, oldBackend::run);
            await(oldBackend.entered);
            next.start(() -> {}, nextBackend::run);
            await(nextBackend.entered);
            assertEquals(1, oldBackend.destroyed);
            old.close();
            old.close();
            assertEquals(1, oldBackend.destroyed);
            assertEquals(0, nextBackend.destroyed);
            assertTrue(nextBackend.worker.isAlive());
            assertThrows(IllegalStateException.class, () -> old.start(() -> {}, oldBackend::run));
        } finally {
            old.close();
            next.close();
        }
        assertEquals(1, nextBackend.destroyed);
    }

    @Test public void replacementWaitsForPreviousWorkerBeforeInitializing() throws Exception {
        NativeTunnelTestBackend oldBackend = new NativeTunnelTestBackend();
        oldBackend.releaseOnStop = false;
        NativeTunnelTestBackend nextBackend = new NativeTunnelTestBackend();
        NativeTunnel old = new NativeTunnel(oldBackend);
        NativeTunnel next = new NativeTunnel(nextBackend);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread starter = new Thread(() -> {
            try { next.start(() -> {}, nextBackend::run); }
            catch (Throwable error) { failure.set(error); }
        });
        try {
            old.start(() -> {}, oldBackend::run);
            await(oldBackend.entered);
            starter.start();
            await(oldBackend.stopped);
            assertEquals(0, oldBackend.destroyed);
            assertEquals(0, nextBackend.initialized);
            oldBackend.exit.countDown();
            starter.join(2000);
            assertFalse(starter.isAlive());
            assertNull(failure.get());
            await(nextBackend.entered);
            assertEquals(1, oldBackend.destroyed);
        } finally {
            oldBackend.exit.countDown();
            starter.join(2000);
            old.close();
            next.close();
        }
    }

    @Test public void closeWaitsEvenWhenInterruptedAndFreesExactlyOnce() throws Exception {
        NativeTunnelTestBackend backend = new NativeTunnelTestBackend();
        backend.releaseOnStop = false;
        NativeTunnel tunnel = new NativeTunnel(backend);
        AtomicBoolean interrupted = new AtomicBoolean();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread closer = new Thread(() -> {
            try { tunnel.close(); interrupted.set(Thread.currentThread().isInterrupted()); }
            catch (Throwable error) { failure.set(error); }
        });
        try {
            tunnel.start(() -> {}, backend::run);
            await(backend.entered);
            closer.start();
            await(backend.stopped);
            closer.interrupt();
            assertEquals(0, backend.destroyed);
            backend.exit.countDown();
            closer.join(2000);
            assertFalse(closer.isAlive());
            assertNull(failure.get());
            assertTrue(interrupted.get());
            assertEquals(1, backend.destroyed);
            tunnel.close();
            assertEquals(1, backend.destroyed);
        } finally {
            backend.exit.countDown();
            closer.join(2000);
            tunnel.close();
        }
    }

    @Test public void rejectedConfigurationNeverStartsAndReleasesContext() {
        NativeTunnelTestBackend backend = new NativeTunnelTestBackend();
        try (NativeTunnel tunnel = new NativeTunnel(backend)) {
            assertThrows(IllegalArgumentException.class, () -> tunnel.start(() -> {
                throw new IllegalArgumentException("Invalid credentials");
            }, backend::run));
            assertFalse(backend.events.contains("start"));
            assertEquals(1, backend.destroyed);
            assertNull(backend.worker);
        }
        assertEquals(1, backend.destroyed);
    }

    @Test public void exitedWorkerCanRestartWithoutStaleThreadReference() throws Exception {
        NativeTunnelTestBackend backend = new NativeTunnelTestBackend();
        try (NativeTunnel tunnel = new NativeTunnel(backend)) {
            tunnel.start(() -> {}, context -> {
                backend.worker = Thread.currentThread();
                backend.entered.countDown();
            });
            await(backend.entered);
            backend.worker.join(2000);
            AtomicReference<Thread> restarted = new AtomicReference<>();
            CountDownLatch entered = new CountDownLatch(1);
            tunnel.start(() -> {}, context -> { restarted.set(Thread.currentThread()); entered.countDown(); });
            await(entered);
            restarted.get().join(2000);
            assertEquals(1, backend.initialized);
            assertEquals(1, backend.cleared);
        }
        assertEquals(1, backend.destroyed);
    }

    @Test public void supersededPreparedCommandCannotCreateInterface() {
        NativeTunnelTestBackend oldBackend = new NativeTunnelTestBackend();
        NativeTunnelTestBackend nextBackend = new NativeTunnelTestBackend();
        AtomicBoolean oldInterfaceCreated = new AtomicBoolean();
        AtomicBoolean newInterfaceCreated = new AtomicBoolean();
        try (NativeTunnel old = new NativeTunnel(oldBackend);
             NativeTunnel next = new NativeTunnel(nextBackend)) {
            old.prepare();
            next.prepare();
            next.withOwnership(() -> newInterfaceCreated.set(true));
            assertThrows(IllegalStateException.class,
                    () -> old.withOwnership(() -> oldInterfaceCreated.set(true)));
            assertFalse(oldInterfaceCreated.get());
            assertTrue(newInterfaceCreated.get());
            assertEquals(1, oldBackend.destroyed);
        }
    }

    @Test public void destructionRejectsPreparedCommandBeforeAsyncCleanup() {
        NativeTunnelTestBackend backend = new NativeTunnelTestBackend();
        AtomicBoolean interfaceCreated = new AtomicBoolean();
        try (NativeTunnel tunnel = new NativeTunnel(backend)) {
            tunnel.prepare();
            tunnel.retire();
            // Destruction has only marked retirement; executor cleanup has not run yet.
            assertEquals(0, backend.destroyed);
            assertThrows(IllegalStateException.class,
                    () -> tunnel.withOwnership(() -> interfaceCreated.set(true)));
            assertFalse(interfaceCreated.get());
        }
        assertEquals(1, backend.destroyed);
    }

    @Test public void interfaceCreationAndStartCannotBeSplitByReplacement() throws Exception {
        NativeTunnelTestBackend oldBackend = new NativeTunnelTestBackend();
        NativeTunnelTestBackend nextBackend = new NativeTunnelTestBackend();
        NativeTunnel old = new NativeTunnel(oldBackend);
        NativeTunnel next = new NativeTunnel(nextBackend);
        CountDownLatch interfaceCreated = new CountDownLatch(1);
        CountDownLatch allowStart = new CountDownLatch(1);
        CountDownLatch replacementAttempted = new CountDownLatch(1);
        CountDownLatch replacementPrepared = new CountDownLatch(1);
        AtomicReference<String> activeInterface = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread starter = new Thread(() -> {
            try {
                old.withOwnership(() -> {
                    activeInterface.set("old");
                    interfaceCreated.countDown();
                    try { await(allowStart); }
                    catch (InterruptedException error) { throw new AssertionError(error); }
                    old.start(() -> {}, oldBackend::run);
                });
            } catch (Throwable error) { failure.set(error); }
        });
        Thread replacement = new Thread(() -> {
            try {
                replacementAttempted.countDown();
                next.prepare();
                replacementPrepared.countDown();
                next.withOwnership(() -> {
                    activeInterface.set("new");
                    next.start(() -> {}, nextBackend::run);
                });
            } catch (Throwable error) { failure.set(error); }
        });
        try {
            old.prepare();
            starter.start();
            await(interfaceCreated);
            replacement.start();
            await(replacementAttempted);
            assertFalse(replacementPrepared.await(100, TimeUnit.MILLISECONDS));
            assertEquals("old", activeInterface.get());
            assertEquals(0, nextBackend.initialized);
            allowStart.countDown();
            starter.join(2000);
            replacement.join(2000);
            assertFalse(starter.isAlive());
            assertFalse(replacement.isAlive());
            assertNull(failure.get());
            await(nextBackend.entered);
            assertEquals("new", activeInterface.get());
            assertEquals(1, oldBackend.destroyed);
        } finally {
            allowStart.countDown();
            starter.join(2000);
            replacement.join(2000);
            old.close();
            next.close();
        }
    }

    @Test public void closingWorkerDoesNotBlockBoundedReplacementAttempt() throws Exception {
        NativeTunnelTestBackend backend = new NativeTunnelTestBackend();
        backend.releaseOnStop = false;
        NativeTunnelTestBackend nextBackend = new NativeTunnelTestBackend();
        NativeTunnel tunnel = new NativeTunnel(backend, 20);
        NativeTunnel next = new NativeTunnel(nextBackend);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch attempted = new CountDownLatch(1);
        Thread closer = new Thread(() -> {
            try { tunnel.close(); }
            catch (Throwable error) { failure.set(error); }
        });
        Thread replacement = new Thread(() -> {
            try { assertThrows(IllegalStateException.class, next::prepare); }
            catch (Throwable error) { failure.set(error); }
            finally { attempted.countDown(); }
        });
        try {
            tunnel.start(() -> {}, backend::run);
            await(backend.entered);
            closer.start();
            await(backend.stopped);
            replacement.start();
            // This must finish while the old worker remains blocked, before releasing it.
            await(attempted);
            assertNull(failure.get());
            assertEquals(0, backend.destroyed);
            assertEquals(0, nextBackend.initialized);
            assertTrue(closer.isAlive());
            backend.exit.countDown();
            // Race takeover against the old closer finishing its wait.
            next.prepare();
            next.withOwnership(() -> next.start(() -> {}, nextBackend::run));
            await(nextBackend.entered);
            closer.join(2000);
            assertFalse(closer.isAlive());
            assertNull(failure.get());
            assertEquals(1, backend.destroyed);
            assertEquals(0, nextBackend.destroyed);
            tunnel.close();
            assertEquals(0, nextBackend.destroyed);
        } finally {
            backend.exit.countDown();
            closer.join(2000);
            replacement.join(2000);
            tunnel.close();
            next.close();
        }
        assertEquals(1, backend.destroyed);
        assertEquals(1, nextBackend.destroyed);
    }
}
