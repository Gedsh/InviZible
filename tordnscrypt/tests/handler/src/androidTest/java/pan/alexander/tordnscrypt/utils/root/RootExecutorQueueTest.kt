package pan.alexander.tordnscrypt.utils.root

import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.ArrayDeque
import kotlin.coroutines.CoroutineContext

/** Exercise queue overflow and teardown without opening a root shell. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class RootExecutorQueueTest {
    private class PausedDispatcher : CoroutineDispatcher() {
        val pending = ArrayDeque<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) { pending.add(block) }
        fun drain() { while (pending.isNotEmpty()) pending.removeFirst().run() }
    }

    @Test fun overflowReleasesOldestAndShutdownReleasesRemainingReceivers() {
        val dispatcher = PausedDispatcher()
        val executor = RootExecutor(
            { error("No root shell should be opened") },
            { error("No root preferences should be read") },
            { error("No root preferences should be read") },
            Dispatchers.Main, dispatcher
        )
        val results = IntArray(12)
        val codes = IntArray(12) { -1 }
        fun receiver(index: Int) = object : ResultReceiver(Handler(Looper.getMainLooper())) {
            override fun onReceiveResult(resultCode: Int, resultData: android.os.Bundle?) {
                results[index]++
                codes[index] = resultCode
            }
        }
        try {
            for (index in 0..10) executor.execute(listOf("echo $index"), 0, receiver(index))
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertEquals(1, results[0])
            assertEquals(1, codes[0])
            for (index in 1..10) assertEquals(0, results[index])
            executor.stopExecutor()
            dispatcher.drain()
            executor.execute(listOf("echo closed"), 0, receiver(11))
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            for (index in results.indices) {
                assertEquals("Receiver $index must be released once", 1, results[index])
                assertEquals(1, codes[index])
            }
        } finally {
            executor.stopExecutor()
            dispatcher.drain()
        }
    }
}
