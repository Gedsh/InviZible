package pan.alexander.tordnscrypt.vpn.service;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

final class NativeTunnelTestBackend implements NativeTunnel.Backend {
    final List<String> events = Collections.synchronizedList(new ArrayList<>());
    final CountDownLatch entered = new CountDownLatch(1);
    final CountDownLatch stopped = new CountDownLatch(1);
    final CountDownLatch exit = new CountDownLatch(1);
    boolean releaseOnStop = true;
    volatile Thread worker;
    int initialized, cleared, destroyed;

    @Override public long initialize() { events.add("initialize"); initialized++; return 42; }
    @Override public void start(long context) { assertEquals(42, context); events.add("start"); }
    @Override public void stop(long context) {
        assertEquals(42, context);
        events.add("stop"); stopped.countDown();
        if (releaseOnStop) exit.countDown();
    }
    @Override public void clear(long context) {
        assertStopped(); events.add("clear"); cleared++;
    }
    @Override public void destroy(long context) {
        assertStopped(); events.add("destroy"); destroyed++;
    }
    void assertStopped() { assertTrue(worker == null || !worker.isAlive()); }
    void run(long context) {
        assertEquals(42, context);
        worker = Thread.currentThread();
        entered.countDown();
        try { assertTrue(exit.await(5, TimeUnit.SECONDS)); }
        catch (InterruptedException error) { throw new AssertionError(error); }
    }
}
