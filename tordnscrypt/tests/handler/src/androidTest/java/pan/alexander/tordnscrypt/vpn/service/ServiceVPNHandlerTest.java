package pan.alexander.tordnscrypt.vpn.service;

import static org.junit.Assert.*;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.VPN_SERVICE_ENABLED;
import static pan.alexander.tordnscrypt.vpn.service.ServiceVPN.EXTRA_COMMAND;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.HandlerThread;
import android.os.Handler;
import android.os.Looper;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import pan.alexander.tordnscrypt.utils.enums.VPNCommand;

/** Exercises the production queue, timeout catch, retry dispatch and reload sequencing on a real Looper. */
@RunWith(AndroidJUnit4.class)
public final class ServiceVPNHandlerTest {
    private static void await(CountDownLatch latch) throws InterruptedException {
        assertTrue("Handler did not reach checkpoint", latch.await(5, TimeUnit.SECONDS));
    }

    private static final class Backend implements NativeTunnel.Backend {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch exit = new CountDownLatch(1);
        final AtomicInteger destroyed = new AtomicInteger();
        volatile Thread worker;
        @Override public long initialize() { return 42; }
        @Override public void start(long context) {}
        @Override public void stop(long context) {}
        @Override public void clear(long context) {
            assertTrue("Cleared before worker exit", worker == null || !worker.isAlive());
        }
        @Override public void destroy(long context) { clear(context); destroyed.incrementAndGet(); }
        void run(long context) {
            worker = Thread.currentThread();
            entered.countDown();
            try { exit.await(); }
            catch (InterruptedException error) { throw new AssertionError(error); }
        }
        void release() throws InterruptedException {
            exit.countDown();
            if (worker != null) {
                worker.join(5000);
                assertFalse(worker.isAlive());
            }
        }
    }

    private static final class TestService extends ServiceVPN {
        final Backend backend = new Backend();
        final NativeTunnel owner = new NativeTunnel(backend, 20);
        final BlockingQueue<Boolean> registrations = new LinkedBlockingQueue<>();
        final BlockingQueue<Long> failures = new LinkedBlockingQueue<>();
        volatile long commandGeneration;
        ServiceVPNHandler commandHandler;
        TestService(Context context) {
            attachBaseContext(context);
            // Suppress UI reporting after a deliberately failed start.
            handler = () -> new Handler(Looper.getMainLooper()) {
                @Override public void dispatchMessage(android.os.Message message) {}
            };
        }
        @Override VpnPreferenceHolder prepareNativeSettings() { owner.prepare(); return null; }
        @Override void withNativeOwnership(Runnable operation) { owner.withOwnership(operation); }
        @Override long cancelNativeRetry() {
            commandGeneration = owner.cancelPendingRetry();
            return commandGeneration;
        }
        @Override void nativeStartFailed(long generation) { failures.add(generation); }
        @Override public boolean isNetworkAvailable() { return false; }
        @Override boolean retryAfterNativeCleanup(NativeTunnel.ShutdownTimeoutException timeout,
                                                  long generation, Runnable retry) {
            boolean registered = owner.retryAfterCleanup(timeout, generation,
                    action -> commandHandler.post(action), retry);
            registrations.add(registered);
            return registered;
        }
        @Override boolean isNativeRunning() { return owner.isRunning(); }
        @Override void stopNative() { owner.stop(); }
        void blockWorker() throws InterruptedException {
            owner.start(() -> {}, backend::run);
            await(backend.entered);
        }
        void awaitRetry() throws InterruptedException {
            assertEquals("Timeout was not registered for retry", Boolean.TRUE,
                    registrations.poll(5, TimeUnit.SECONDS));
        }
    }

    private static final class TestHandler extends ServiceVPNHandler {
        final TestService service;
        final AtomicInteger starts = new AtomicInteger();
        final AtomicInteger reloads = new AtomicInteger();
        final AtomicInteger blocks = new AtomicInteger();
        final AtomicInteger unblocks = new AtomicInteger();
        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch reloaded = new CountDownLatch(1);
        final CountDownLatch unblocked = new CountDownLatch(1);
        final CountDownLatch stopped = new CountDownLatch(1);
        final List<String> events = Collections.synchronizedList(new ArrayList<>());
        volatile Thread restartThread;
        volatile boolean failStartup;
        TestHandler(Looper looper, TestService service, SharedPreferences preferences) {
            super(looper, service);
            this.service = service;
            defaultSharedPreferences = () -> preferences;
        }
        @Override void startPrepared(VpnPreferenceHolder preferences) {
            if (failStartup)
                throw new IllegalArgumentException("Regression startup failure");
            service.owner.start(() -> {}, context -> {});
            starts.incrementAndGet();
            restartThread = Thread.currentThread();
            started.countDown();
        }
        @Override boolean shouldFixTTL() { return true; }
        @Override ReloadPlan prepareReload() { return null; }
        @Override String blockTethering() {
            events.add("block");
            blocks.incrementAndGet();
            return "tun-test";
        }
        @Override void restartVpn(ReloadPlan plan, VpnPreferenceHolder preferences) {
            service.owner.start(() -> {}, context -> {});
            events.add("restart");
            reloads.incrementAndGet();
            restartThread = Thread.currentThread();
            reloaded.countDown();
        }
        @Override void allowTethering(String oldInterface) {
            assertEquals("tun-test", oldInterface);
            events.add("unblock");
            unblocks.incrementAndGet();
            unblocked.countDown();
        }
        @Override void stop() { service.owner.stop(); stopped.countDown(); }
        void barrier() throws InterruptedException {
            CountDownLatch delivered = new CountDownLatch(1);
            assertTrue(post(delivered::countDown));
            await(delivered);
        }
    }

    private static final class Fixture implements AutoCloseable {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final SharedPreferences preferences = context.getSharedPreferences("handler-regression", 0);
        final HandlerThread thread = new HandlerThread("VPN handler regression");
        final TestService service = new TestService(context);
        final TestHandler handler;
        Fixture() {
            assertTrue(preferences.edit().clear().putBoolean(VPN_SERVICE_ENABLED, true).commit());
            thread.start();
            handler = new TestHandler(thread.getLooper(), service, preferences);
            service.commandHandler = handler;
        }
        void queue(VPNCommand command) {
            Intent intent = new Intent().putExtra(EXTRA_COMMAND, command);
            handler.queue(intent);
        }
        @Override public void close() throws InterruptedException {
            service.owner.retire();
            service.backend.release();
            handler.removeCallbacksAndMessages(null);
            thread.quit();
            thread.join(5000);
            assertFalse("Handler leaked", thread.isAlive());
            service.owner.close();
            assertTrue(preferences.edit().clear().commit());
        }
    }

    @Test public void reloadTimeoutRetriesOnHandlerAndBalancesTethering() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.service.blockWorker();
            fixture.queue(VPNCommand.RELOAD);
            fixture.service.awaitRetry();
            assertEquals(0, fixture.handler.blocks.get());
            assertEquals(0, fixture.handler.reloads.get());
            fixture.service.backend.release();
            await(fixture.handler.reloaded);
            await(fixture.handler.unblocked);
            fixture.handler.barrier();
            assertSame(fixture.thread, fixture.handler.restartThread);
            assertEquals(1, fixture.handler.reloads.get());
            assertEquals(1, fixture.handler.blocks.get());
            assertEquals(1, fixture.handler.unblocks.get());
            assertTrue("Cleanup retry was treated as terminal", fixture.service.failures.isEmpty());
            assertArrayEquals(new String[]{"block", "restart", "unblock"}, fixture.handler.events.toArray());
            assertFalse(fixture.service.reloading);
        }
    }

    @Test public void startTimeoutRetriesAfterPreviousOwnerCleanup() throws Exception {
        Backend previousBackend = new Backend();
        NativeTunnel previous = new NativeTunnel(previousBackend, 20);
        try (Fixture fixture = new Fixture()) {
            previous.start(() -> {}, previousBackend::run);
            await(previousBackend.entered);
            fixture.queue(VPNCommand.START);
            fixture.service.awaitRetry();
            assertEquals(0, fixture.handler.starts.get());
            assertEquals(0, previousBackend.destroyed.get());
            previousBackend.release();
            await(fixture.handler.started);
            fixture.handler.barrier();
            assertEquals(1, previousBackend.destroyed.get());
            assertEquals(1, fixture.handler.starts.get());
            assertSame(fixture.thread, fixture.handler.restartThread);
        } finally {
            previousBackend.release();
            previous.close();
        }
    }

    @Test public void queuedStopCancelsReloadBeforeDelayedStopExecutes() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.service.blockWorker();
            fixture.queue(VPNCommand.RELOAD);
            fixture.service.awaitRetry();
            fixture.queue(VPNCommand.STOP);
            fixture.service.backend.release();
            await(fixture.handler.stopped);
            fixture.handler.barrier();
            assertEquals(0, fixture.handler.reloads.get());
            assertEquals(0, fixture.handler.blocks.get());
            assertEquals(0, fixture.handler.unblocks.get());
        }
    }

    @Test public void queuedStartSupersedesTimedOutReload() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.service.blockWorker();
            fixture.queue(VPNCommand.RELOAD);
            fixture.service.awaitRetry();
            fixture.queue(VPNCommand.START);
            fixture.service.awaitRetry();
            fixture.service.backend.release();
            await(fixture.handler.started);
            fixture.handler.barrier();
            assertEquals(1, fixture.handler.starts.get());
            assertEquals(0, fixture.handler.reloads.get());
            assertEquals(0, fixture.handler.blocks.get());
        }
    }

    @Test public void terminalStartupFailureNotifiesServiceWithCommandGeneration() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.handler.failStartup = true;
            fixture.queue(VPNCommand.START);
            Long generation = fixture.service.failures.poll(5, TimeUnit.SECONDS);
            assertNotNull("Startup failure did not reach service recovery state", generation);
            assertEquals(fixture.service.commandGeneration, generation.longValue());
            fixture.handler.barrier();
            assertEquals(0, fixture.handler.starts.get());
            assertTrue(fixture.service.registrations.isEmpty());
        }
    }
}
