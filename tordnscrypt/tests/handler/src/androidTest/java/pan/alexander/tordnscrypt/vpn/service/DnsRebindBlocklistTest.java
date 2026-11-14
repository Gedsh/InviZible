package pan.alexander.tordnscrypt.vpn.service;

import static org.junit.Assert.*;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public final class DnsRebindBlocklistTest {
    private static final int LIMIT = 1024;

    @Test public void membershipIsExactIncludingAllHashSigns() {
        DnsRebindBlocklist blocklist = new DnsRebindBlocklist();
        blocklist.add(Integer.MIN_VALUE);
        blocklist.add(Integer.MAX_VALUE);
        for (int hash = -500; hash < 500; hash++) {
            blocklist.add(hash);
        }
        assertTrue(blocklist.contains(Integer.MIN_VALUE));
        assertTrue(blocklist.contains(Integer.MAX_VALUE));
        for (int hash = -501; hash <= 500; hash++) {
            assertEquals(hash >= -500 && hash < 500, blocklist.contains(hash));
        }
    }

    @Test public void duplicateAddsDoNotEvictOrRefreshOldestEntry() {
        DnsRebindBlocklist blocklist = new DnsRebindBlocklist();
        for (int hash = 0; hash < LIMIT; hash++) blocklist.add(hash);
        for (int i = 0; i < 10000; i++) blocklist.add(0);
        for (int hash = 0; hash < LIMIT; hash++) assertTrue(blocklist.contains(hash));
        blocklist.add(LIMIT);
        assertFalse(blocklist.contains(0));
        assertTrue(blocklist.contains(1));
        assertTrue(blocklist.contains(LIMIT));
    }

    @Test public void overflowRetainsOnlyNewestEntriesWithoutFalsePositives() {
        DnsRebindBlocklist blocklist = new DnsRebindBlocklist();
        for (int hash = 0; hash < 20000; hash++) blocklist.add(hash);
        for (int hash = -1000; hash < 21000; hash++) {
            assertEquals("Unexpected membership for hash " + hash,
                    hash >= 20000 - LIMIT && hash < 20000, blocklist.contains(hash));
        }
    }

    @Test public void lookupsDoNotRefreshInsertionOrder() {
        DnsRebindBlocklist blocklist = new DnsRebindBlocklist();
        for (int hash = 0; hash < LIMIT; hash++) blocklist.add(hash);
        assertTrue(blocklist.contains(0));
        blocklist.add(LIMIT);
        assertFalse(blocklist.contains(0));
        assertTrue(blocklist.contains(1));
    }

    @Test public void clearRemovesEntriesAndResetsEvictionOrder() {
        DnsRebindBlocklist blocklist = new DnsRebindBlocklist();
        for (int hash = 0; hash < 2048; hash++) blocklist.add(hash);
        blocklist.clear();
        for (int hash = 0; hash < 2048; hash++) assertFalse(blocklist.contains(hash));
        for (int hash = LIMIT - 1; hash >= 0; hash--) blocklist.add(hash);
        blocklist.add(LIMIT);
        assertFalse(blocklist.contains(LIMIT - 1));
        assertTrue(blocklist.contains(0));
        assertTrue(blocklist.contains(LIMIT));
    }

    private static DnsRebindBlocklist addConcurrently(int countPerWorker) throws Exception {
        DnsRebindBlocklist blocklist = new DnsRebindBlocklist();
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> negative = workers.submit(() -> {
                start.await();
                for (int hash = -countPerWorker; hash < 0; hash++) blocklist.add(hash);
                return null;
            });
            Future<?> positive = workers.submit(() -> {
                start.await();
                for (int hash = 0; hash < countPerWorker; hash++) blocklist.add(hash);
                return null;
            });
            start.countDown();
            negative.get(20, TimeUnit.SECONDS);
            positive.get(20, TimeUnit.SECONDS);
            return blocklist;
        } finally {
            start.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test public void concurrentAddsBelowLimitDoNotLoseEntries() throws Exception {
        DnsRebindBlocklist blocklist = addConcurrently(500);
        for (int hash = -500; hash < 500; hash++) assertTrue(blocklist.contains(hash));
        assertFalse(blocklist.contains(-501));
        assertFalse(blocklist.contains(500));
    }

    @Test public void concurrentOverflowKeepsSizeBounded() throws Exception {
        DnsRebindBlocklist blocklist = addConcurrently(10000);
        int retained = 0;
        for (int hash = -10000; hash < 10000; hash++) {
            if (blocklist.contains(hash)) retained++;
        }
        assertEquals(LIMIT, retained);
        assertFalse(blocklist.contains(-10001));
        assertFalse(blocklist.contains(10000));
    }
}
