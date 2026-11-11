package pan.alexander.tordnscrypt.vpn.service;

import static org.junit.Assert.*;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Uses a dispatch queue to model the VPN handler without establishing an Android VPN. */
@RunWith(AndroidJUnit4.class)
public final class NativeTunnelRetryTest {
    private static void startBlocked(NativeTunnel tunnel, NativeTunnelTestBackend backend)
            throws InterruptedException {
        backend.releaseOnStop = false;
        tunnel.start(() -> {}, backend::run);
        assertTrue(backend.entered.await(2, TimeUnit.SECONDS));
    }

    private static Runnable posted(BlockingQueue<Runnable> queue) throws InterruptedException {
        Runnable action = queue.poll(2, TimeUnit.SECONDS);
        assertNotNull("Cleanup did not post its retry", action);
        return action;
    }

    @Test public void replacementStartsOnlyAfterOldContextCleanup() throws Exception {
        NativeTunnelTestBackend oldBackend = new NativeTunnelTestBackend();
        NativeTunnelTestBackend nextBackend = new NativeTunnelTestBackend();
        NativeTunnel old = new NativeTunnel(oldBackend, 20);
        NativeTunnel next = new NativeTunnel(nextBackend, 20);
        BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
        AtomicInteger retries = new AtomicInteger();
        AtomicReference<Throwable> cleanupFailure = new AtomicReference<>();
        Thread closer = new Thread(() -> {
            try { old.close(); }
            catch (Throwable error) { cleanupFailure.set(error); }
        });
        try {
            startBlocked(old, oldBackend);
            closer.start();
            assertTrue(oldBackend.stopped.await(2, TimeUnit.SECONDS));
            long generation = next.cancelPendingRetry();
            NativeTunnel.ShutdownTimeoutException timeout = assertThrows(
                    NativeTunnel.ShutdownTimeoutException.class, next::prepare);
            assertTrue(next.retryAfterCleanup(timeout, generation, queue::add, () -> {
                assertEquals(1, oldBackend.destroyed);
                next.prepare();
                next.withOwnership(() -> next.start(() -> {}, nextBackend::run));
                retries.incrementAndGet();
            }));
            assertNull(queue.poll(100, TimeUnit.MILLISECONDS));
            assertEquals(0, oldBackend.destroyed);
            oldBackend.exit.countDown();
            Runnable retry = posted(queue);
            assertEquals(1, oldBackend.destroyed);
            retry.run();
            assertTrue(nextBackend.entered.await(2, TimeUnit.SECONDS));
            assertEquals(1, retries.get());
            closer.join(2000);
            assertFalse(closer.isAlive());
            assertNull(cleanupFailure.get());
            old.close();
            assertEquals(1, oldBackend.destroyed);
            assertEquals(0, nextBackend.destroyed);
        } finally {
            oldBackend.exit.countDown();
            closer.join(2000);
            old.close();
            next.close();
        }
    }

    @Test public void reloadRetriesAfterWorkerExitWithoutServiceDestruction() throws Exception {
        NativeTunnelTestBackend backend = new NativeTunnelTestBackend();
        NativeTunnel tunnel = new NativeTunnel(backend, 20);
        BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
        CountDownLatch restarted = new CountDownLatch(1);
        try {
            startBlocked(tunnel, backend);
            assertTrue(tunnel.isRunning());
            long generation = tunnel.cancelPendingRetry();
            NativeTunnel.ShutdownTimeoutException timeout = assertThrows(
                    NativeTunnel.ShutdownTimeoutException.class, tunnel::stop);
            assertFalse(tunnel.isRunning());
            assertTrue(tunnel.retryAfterCleanup(timeout, generation, queue::add, () -> {
                assertEquals(1, backend.cleared);
                tunnel.prepare();
                tunnel.withOwnership(() -> tunnel.start(() -> {}, context -> restarted.countDown()));
            }));
            assertNull(queue.poll(100, TimeUnit.MILLISECONDS));
            backend.exit.countDown();
            posted(queue).run();
            assertTrue(restarted.await(2, TimeUnit.SECONDS));
            assertEquals(1, backend.initialized);
            assertEquals(0, backend.destroyed);
        } finally {
            backend.exit.countDown();
            tunnel.close();
        }
        assertEquals(1, backend.destroyed);
    }

    @Test public void stopCancelsRetryBeforeWorkerCompletes() throws Exception {
        NativeTunnelTestBackend backend = new NativeTunnelTestBackend();
        NativeTunnel tunnel = new NativeTunnel(backend, 20);
        BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
        AtomicBoolean retried = new AtomicBoolean();
        try {
            startBlocked(tunnel, backend);
            long generation = tunnel.cancelPendingRetry();
            NativeTunnel.ShutdownTimeoutException timeout = assertThrows(
                    NativeTunnel.ShutdownTimeoutException.class, tunnel::stop);
            assertTrue(tunnel.retryAfterCleanup(timeout, generation, queue::add, () -> retried.set(true)));
            tunnel.cancelPendingRetry(); // STOP is enqueued, even if its handler is still busy.
            backend.exit.countDown();
            backend.worker.join(2000);
            assertNull(queue.poll(100, TimeUnit.MILLISECONDS));
            assertFalse(retried.get());
        } finally {
            backend.exit.countDown();
            tunnel.close();
        }
    }

    @Test public void stopCancelsRetryAlreadyPostedToHandler() throws Exception {
        NativeTunnelTestBackend backend = new NativeTunnelTestBackend();
        NativeTunnel tunnel = new NativeTunnel(backend, 20);
        BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
        AtomicBoolean retried = new AtomicBoolean();
        try {
            startBlocked(tunnel, backend);
            long generation = tunnel.cancelPendingRetry();
            NativeTunnel.ShutdownTimeoutException timeout = assertThrows(
                    NativeTunnel.ShutdownTimeoutException.class, tunnel::stop);
            tunnel.retryAfterCleanup(timeout, generation, queue::add, () -> retried.set(true));
            backend.exit.countDown();
            Runnable retry = posted(queue);
            tunnel.cancelPendingRetry();
            retry.run();
            assertFalse(retried.get());
        } finally {
            backend.exit.countDown();
            tunnel.close();
        }
    }

    @Test public void newerCommandPreventsLateTimeoutRegistration() throws Exception {
        NativeTunnelTestBackend backend = new NativeTunnelTestBackend();
        NativeTunnel tunnel = new NativeTunnel(backend, 20);
        BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
        try {
            startBlocked(tunnel, backend);
            long oldCommand = tunnel.cancelPendingRetry();
            tunnel.cancelPendingRetry(); // A later command arrives during the old stop attempt.
            NativeTunnel.ShutdownTimeoutException timeout = assertThrows(
                    NativeTunnel.ShutdownTimeoutException.class, tunnel::stop);
            assertFalse(tunnel.retryAfterCleanup(timeout, oldCommand, queue::add,
                    () -> fail("Superseded request was retried")));
            backend.exit.countDown();
            backend.worker.join(2000);
            assertNull(queue.poll(100, TimeUnit.MILLISECONDS));
        } finally {
            backend.exit.countDown();
            tunnel.close();
        }
    }

    @Test public void serviceRetirementCancelsPendingRetry() throws Exception {
        NativeTunnelTestBackend backend = new NativeTunnelTestBackend();
        NativeTunnel tunnel = new NativeTunnel(backend, 20);
        BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
        try {
            startBlocked(tunnel, backend);
            long generation = tunnel.cancelPendingRetry();
            NativeTunnel.ShutdownTimeoutException timeout = assertThrows(
                    NativeTunnel.ShutdownTimeoutException.class, tunnel::stop);
            tunnel.retryAfterCleanup(timeout, generation, queue::add,
                    () -> fail("Destroyed service was restarted"));
            tunnel.retire();
            assertFalse(tunnel.retryAfterCleanup(timeout, generation, queue::add,
                    () -> fail("Retry registered after retirement")));
            backend.exit.countDown();
            backend.worker.join(2000);
            assertNull(queue.poll(100, TimeUnit.MILLISECONDS));
        } finally {
            backend.exit.countDown();
            tunnel.close();
        }
    }

    @Test public void onlyLatestPendingRetryRuns() throws Exception {
        NativeTunnelTestBackend backend = new NativeTunnelTestBackend();
        NativeTunnel tunnel = new NativeTunnel(backend, 20);
        BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
        AtomicInteger retries = new AtomicInteger();
        try {
            startBlocked(tunnel, backend);
            long generation = tunnel.cancelPendingRetry();
            NativeTunnel.ShutdownTimeoutException timeout = assertThrows(
                    NativeTunnel.ShutdownTimeoutException.class, tunnel::stop);
            tunnel.retryAfterCleanup(timeout, generation, queue::add,
                    () -> fail("Earlier retry was not superseded"));
            tunnel.retryAfterCleanup(timeout, generation, queue::add, retries::incrementAndGet);
            backend.exit.countDown();
            posted(queue).run();
            assertEquals(1, retries.get());
            assertNull(queue.poll(100, TimeUnit.MILLISECONDS));
        } finally {
            backend.exit.countDown();
            tunnel.close();
        }
    }

    @Test public void postedRetryDoesNotTakeVpnFromNewerOwner() throws Exception {
        NativeTunnelTestBackend oldBackend = new NativeTunnelTestBackend();
        NativeTunnelTestBackend nextBackend = new NativeTunnelTestBackend();
        NativeTunnelTestBackend newerBackend = new NativeTunnelTestBackend();
        NativeTunnel old = new NativeTunnel(oldBackend, 20);
        NativeTunnel next = new NativeTunnel(nextBackend, 20);
        NativeTunnel newer = new NativeTunnel(newerBackend, 20);
        BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
        AtomicBoolean retried = new AtomicBoolean();
        try {
            startBlocked(old, oldBackend);
            long generation = next.cancelPendingRetry();
            NativeTunnel.ShutdownTimeoutException timeout = assertThrows(
                    NativeTunnel.ShutdownTimeoutException.class, next::prepare);
            next.retryAfterCleanup(timeout, generation, queue::add, () -> retried.set(true));
            oldBackend.exit.countDown();
            Runnable retry = posted(queue);
            newer.prepare();
            newer.withOwnership(() -> newer.start(() -> {}, newerBackend::run));
            assertTrue(newerBackend.entered.await(2, TimeUnit.SECONDS));
            retry.run();
            assertFalse(retried.get());
            assertEquals(0, nextBackend.initialized);
            assertEquals(0, newerBackend.destroyed);
        } finally {
            oldBackend.exit.countDown();
            old.close();
            next.close();
            newer.close();
        }
    }
}
