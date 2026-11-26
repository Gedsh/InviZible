package pan.alexander.tordnscrypt.modules

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import pan.alexander.tordnscrypt.utils.apps.InstalledAppNamesStorage
import pan.alexander.tordnscrypt.utils.enums.OperationMode
import pan.alexander.tordnscrypt.utils.executors.CoroutineExecutor
import pan.alexander.tordnscrypt.vpn.Rule
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.CoroutineContext

/** Records root-rule scheduling and name refresh without running commands or app discovery. */
@RunWith(AndroidJUnit4::class)
class ModulesReceiverRootPackageTest {
    private class PausedDispatcher : CoroutineDispatcher() {
        val pending = ArrayDeque<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            pending.add(block)
        }
        fun drain() {
            while (pending.isNotEmpty()) pending.removeFirst().run()
        }
    }

    private class Fixture : AutoCloseable {
        val status = ModulesStatus.getInstance()
        private val savedMode = status.mode
        private val savedUseRoot = status.isUseModulesWithRoot
        private val fixTtlField = ModulesStatus::class.java.getDeclaredField("fixTTL").apply { isAccessible = true }
        private val savedFixTtl = fixTtlField.getBoolean(status)
        private val context = InstrumentationRegistry.getInstrumentation().targetContext
        private val preferences = context.getSharedPreferences("root-package-regression", Context.MODE_PRIVATE)
        val rulesDispatcher = PausedDispatcher()
        val namesDispatcher = PausedDispatcher()
        private val scope = CoroutineScope(SupervisorJob() + rulesDispatcher)
        private val names = InstalledAppNamesStorage(namesDispatcher)
        private val receiver = ModulesReceiver(
            null, dagger.Lazy { preferences }, null, null,
            CoroutineExecutor(scope, CoroutineExceptionHandler { _, error -> throw AssertionError(error) }),
            null, null, dagger.Lazy { names }, null
        )
        private val dispatch = ModulesReceiver::class.java.getDeclaredMethod("packageChanged", Intent::class.java)
            .apply { isAccessible = true }
        private val inventoryVersion = Rule::class.java.getDeclaredField("inventoryVersion")
            .apply { isAccessible = true }.get(null) as AtomicLong

        init {
            assertTrue(preferences.edit().clear().commit())
            ModulesReceiver::class.java.getDeclaredField("context").apply { isAccessible = true }
                .set(receiver, context)
            status.mode = OperationMode.ROOT_MODE
            status.isUseModulesWithRoot = false
            status.isFixTTL = false
        }

        fun deliver(action: String, replacing: Boolean?) {
            val version = inventoryVersion.get()
            val intent = Intent(action, Uri.parse("package:example.changed"))
            if (replacing != null) intent.putExtra(Intent.EXTRA_REPLACING, replacing)
            dispatch.invoke(receiver, intent)
            assertTrue("Every package event must invalidate inventory", inventoryVersion.get() > version)
        }

        override fun close() {
            scope.cancel()
            val namesScope = InstalledAppNamesStorage::class.java.getDeclaredField("coroutineScope")
                .apply { isAccessible = true }.get(names) as CoroutineScope
            namesScope.cancel()
            rulesDispatcher.drain()
            namesDispatcher.drain()
            status.mode = savedMode
            status.isUseModulesWithRoot = savedUseRoot
            status.isFixTTL = savedFixTtl
            assertTrue(preferences.edit().clear().commit())
        }
    }

    @Test fun replacementSkipsRulesButKeepsNameRefreshAndInvalidation() {
        for (action in listOf(Intent.ACTION_PACKAGE_REMOVED, Intent.ACTION_PACKAGE_ADDED)) {
            Fixture().use { fixture ->
                fixture.deliver(action, true)
                assertEquals(0, fixture.rulesDispatcher.pending.size)
                assertEquals(1, fixture.namesDispatcher.pending.size)
            }
        }
    }

    @Test fun genuineChangesForceRulesEvenWithoutOptionalRefreshSettings() {
        for (action in listOf(Intent.ACTION_PACKAGE_ADDED, Intent.ACTION_PACKAGE_REMOVED)) {
            for (replacing in listOf(false, null)) {
                Fixture().use { fixture ->
                    fixture.deliver(action, replacing)
                    assertEquals(1, fixture.rulesDispatcher.pending.size)
                    assertEquals(1, fixture.namesDispatcher.pending.size)
                }
            }
        }
    }

    @Test fun replacementDoesNotSuppressFollowingUninstall() {
        Fixture().use { fixture ->
            fixture.deliver(Intent.ACTION_PACKAGE_REMOVED, true)
            fixture.deliver(Intent.ACTION_PACKAGE_ADDED, true)
            assertTrue(fixture.rulesDispatcher.pending.isEmpty())
            fixture.deliver(Intent.ACTION_PACKAGE_REMOVED, false)
            assertEquals(1, fixture.rulesDispatcher.pending.size)
        }
    }
}
