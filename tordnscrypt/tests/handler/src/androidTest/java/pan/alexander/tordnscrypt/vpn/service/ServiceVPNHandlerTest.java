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
import android.os.ParcelFileDescriptor;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import pan.alexander.tordnscrypt.utils.enums.VPNCommand;

/** Exercises descriptor ownership, the production queue, cleanup retries and reload sequencing. */
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
        volatile boolean uidCachesPresent;
        @Override public long initialize() { return 42; }
        @Override public void start(long context) {}
        @Override public void stop(long context) {}
        @Override public void clear(long context, boolean preserveUidCaches) {
            assertTrue("Cleared before worker exit", worker == null || !worker.isAlive());
            if (!preserveUidCaches) uidCachesPresent = false;
        }
        @Override public void destroy(long context) { clear(context, false); destroyed.incrementAndGet(); }
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
        @Override void stopNativeForReload() { owner.stopForReload(); }
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
        @Override ReloadPlan prepareReload(VpnPreferenceHolder preferences) { return null; }
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

    private static final class DescriptorHandler extends ServiceVPNHandler {
        RuntimeException networkFailure;

        DescriptorHandler(Fixture fixture) {
            super(fixture.thread.getLooper(), fixture.service);
        }

        @Override void setUnderlyingNetwork() {
            if (networkFailure != null) {
                throw networkFailure;
            }
        }
    }

    private static ServiceVPN.Builder descriptorBuilder(ServiceVPN service,
                                                        ParcelFileDescriptor descriptor) {
        return service.new Builder() {
            @Override public ParcelFileDescriptor establish() { return descriptor; }
        };
    }

    private static void assertClosed(ParcelFileDescriptor descriptor) {
        try {
            descriptor.getFd();
            fail("Startup failure left the VPN descriptor open");
        } catch (IllegalStateException expected) {
            // ParcelFileDescriptor rejects access after close.
        }
    }

    @Test public void networkSetupFailureClosesEstablishedDescriptor() throws Exception {
        try (Fixture fixture = new Fixture()) {
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            try (ParcelFileDescriptor read = pipe[0]; ParcelFileDescriptor write = pipe[1]) {
                DescriptorHandler handler = new DescriptorHandler(fixture);
                handler.networkFailure = new IllegalStateException("Network setup failed");
                assertNull(handler.startVPN(descriptorBuilder(fixture.service, read)));
                assertClosed(read);
            }
        }
    }

    @Test public void networkSecurityFailureClosesDescriptorAndPropagates() throws Exception {
        try (Fixture fixture = new Fixture()) {
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            try (ParcelFileDescriptor read = pipe[0]; ParcelFileDescriptor write = pipe[1]) {
                DescriptorHandler handler = new DescriptorHandler(fixture);
                SecurityException failure = new SecurityException("VPN permission revoked");
                handler.networkFailure = failure;
                try {
                    handler.startVPN(descriptorBuilder(fixture.service, read));
                    fail("SecurityException was swallowed");
                } catch (SecurityException expected) {
                    assertSame(failure, expected);
                }
                assertClosed(read);
            }
        }
    }

    @Test public void successfulSetupTransfersOpenDescriptorToCaller() throws Exception {
        try (Fixture fixture = new Fixture()) {
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            try (ParcelFileDescriptor read = pipe[0]; ParcelFileDescriptor write = pipe[1]) {
                DescriptorHandler handler = new DescriptorHandler(fixture);
                assertSame(read, handler.startVPN(descriptorBuilder(fixture.service, read)));
                assertTrue("Successful startup closed the VPN descriptor", read.getFd() >= 0);
            }
        }
    }

    @Test public void failedEstablishmentReturnsNull() throws Exception {
        try (Fixture fixture = new Fixture()) {
            DescriptorHandler handler = new DescriptorHandler(fixture);
            assertNull(handler.startVPN(descriptorBuilder(fixture.service, null)));
        }
    }

    @Test public void reloadTimeoutRetriesOnHandlerAndBalancesTethering() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.service.blockWorker();
            fixture.service.backend.uidCachesPresent = true;
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
            assertTrue(fixture.service.backend.uidCachesPresent);
        }
    }

    @Test public void nativeCleanupBindsBothCacheModesAndDestroysContext() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Method initialize = ServiceVPN.class.getDeclaredMethod("jni_init", int.class);
            Method clear = ServiceVPN.class.getDeclaredMethod("jni_clear", long.class, boolean.class);
            Method destroy = ServiceVPN.class.getDeclaredMethod("jni_done", long.class);
            initialize.setAccessible(true); clear.setAccessible(true); destroy.setAccessible(true);
            long context = (long) initialize.invoke(fixture.service, android.os.Build.VERSION.SDK_INT);
            assertNotEquals(0L, context);
            try {
                clear.invoke(fixture.service, context, true);
                clear.invoke(fixture.service, context, false);
            } finally {
                destroy.invoke(fixture.service, context);
            }
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
