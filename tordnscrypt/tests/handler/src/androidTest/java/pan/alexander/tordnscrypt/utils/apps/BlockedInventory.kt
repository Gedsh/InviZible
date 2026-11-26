package pan.alexander.tordnscrypt.utils.apps

import android.os.SystemClock
import kotlinx.coroutines.asCoroutineDispatcher
import org.junit.Assert.assertTrue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock

/** Hold real discovery at its interruptible scan lock, then inject its empty-on-error result. */
class BlockedInventory : AutoCloseable {
    private val lock = Class.forName("pan.alexander.tordnscrypt.utils.apps.InstalledApplicationsManagerKt")
        .getDeclaredField("reentrantLock").apply { isAccessible = true }.get(null) as ReentrantLock
    private val thread = AtomicReference<Thread>()
    private val worker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "blocked-inventory-test").also { thread.set(it) }
    }
    val dispatcher = worker.asCoroutineDispatcher()

    init { lock.lock() }

    fun awaitScan() {
        val deadline = SystemClock.uptimeMillis() + 5000
        while (SystemClock.uptimeMillis() < deadline) {
            val current = thread.get()
            if (current != null && lock.hasQueuedThread(current)) return
            SystemClock.sleep(10)
        }
        throw AssertionError("Discovery did not reach the scan lock")
    }

    fun failScan() { thread.get().interrupt() }

    override fun close() {
        lock.unlock()
        dispatcher.close()
        worker.shutdownNow()
        assertTrue("Discovery worker must stop", worker.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS))
    }
}
