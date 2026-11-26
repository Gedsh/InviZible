package pan.alexander.tordnscrypt.settings.tor_apps

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentHostCallback
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import pan.alexander.tordnscrypt.App
import pan.alexander.tordnscrypt.settings.tor_apps.TorAppData.Companion.mapToTorAppData
import pan.alexander.tordnscrypt.utils.apps.InstalledApplicationsManager
import pan.alexander.tordnscrypt.utils.apps.BlockedInventory
import pan.alexander.tordnscrypt.utils.executors.CoroutineExecutor
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository
import pan.alexander.tordnscrypt.modules.ModulesStatus
import pan.alexander.tordnscrypt.utils.enums.OperationMode
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.APPS_DIRECT_UDP
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.APPS_BYPASS_VPN
import java.lang.reflect.Proxy
import java.util.Collections
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(AndroidJUnit4::class)
class AppsRefreshTest {
    private class HostActivity(context: Context) : FragmentActivity() {
        init { attachBaseContext(context) }
        // This host is not attached to a window; older Android inflates Toast views locally.
        override fun getSystemService(name: String): Any? =
            if (name == Context.WINDOW_SERVICE) baseContext.getSystemService(name)
            else super.getSystemService(name)
    }

    private fun field(fragment: UnlockTorAppsFragment, name: String, value: Any) {
        UnlockTorAppsFragment::class.java.getDeclaredField(name).apply { isAccessible = true }.set(fragment, value)
    }

    @Test fun stopDuringFailedRefresh() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = App.instance
        val modules = ModulesStatus.getInstance()
        val mode = modules.mode
        val saved = mutableMapOf<String, Set<String>>()
        val repository = Proxy.newProxyInstance(
            PreferenceRepository::class.java.classLoader, arrayOf(PreferenceRepository::class.java)
        ) { _, method, args ->
            check(method.name == "setStringSetPreference") { "Unexpected call ${method.name}" }
            @Suppress("UNCHECKED_CAST")
            saved[args[0] as String] = (args[1] as Set<String>).toSet()
            null
        } as PreferenceRepository
        val edited = ApplicationData("Edited", "example.edited", 12345, null, false, true, false, false, 0)
            .mapToTorAppData().apply { torifyApp = true; directUdp = true; excludeFromAll = true }
        BlockedInventory().use { scan ->
            val scope = CoroutineScope(SupervisorJob() + scan.dispatcher)
            val fragment = UnlockTorAppsFragment().apply {
                executor = CoroutineExecutor(scope, CoroutineExceptionHandler { _, error -> throw AssertionError(error) })
                preferenceRepository = dagger.Lazy { repository }
                defaultPreferences = dagger.Lazy { app.getSharedPreferences("tor-app-refresh-test", Context.MODE_PRIVATE) }
                handler = dagger.Lazy { Handler(Looper.getMainLooper()) }
                savedAppsUnlockWhenSearch = CopyOnWriteArrayList(listOf(edited))
                unlockAppsStr = "tor-refresh-selection-test"
            }
            field(fragment, "setUnlockApps", hashSetOf<String>())
            field(fragment, "setDirectUdpApps", hashSetOf<String>())
            field(fragment, "setVpnBypassApps", hashSetOf<String>())
            field(fragment, "appsListComplete", true)
            val refresh = UnlockTorAppsFragment::class.java.getDeclaredMethod(
                "getDeviceApps", Set::class.java, Boolean::class.javaPrimitiveType
            ).apply { isAccessible = true }
            val task = UnlockTorAppsFragment::class.java.getDeclaredField("refreshTask").apply { isAccessible = true }
            try {
                instrumentation.runOnMainSync {
                    modules.mode = OperationMode.PROXY_MODE
                    val activity = HostActivity(app)
                    val host = object : FragmentHostCallback<FragmentActivity>(activity, Handler(Looper.getMainLooper()), 0) {
                        override fun onGetHost() = activity
                    }
                    Fragment::class.java.getDeclaredField("mHost").apply { isAccessible = true }.set(fragment, host)
                    refresh.invoke(fragment, Collections.emptySet<String>(), false)
                }
                scan.awaitScan()
                instrumentation.runOnMainSync {
                    fragment.onStop()
                    assertEquals(setOf("12345"), saved[fragment.unlockAppsStr])
                    assertEquals(setOf("12345"), saved[APPS_DIRECT_UDP])
                    assertEquals(setOf("example.edited"), saved[APPS_BYPASS_VPN])
                }
                scan.failScan()
                runBlocking { withTimeout(10_000) { (task.get(fragment) as Job).join() } }
                instrumentation.waitForIdleSync()
                instrumentation.runOnMainSync {
                    assertSame(edited, fragment.appsUnlock.single())
                    fragment.onStop()
                    assertEquals(setOf("12345"), saved[fragment.unlockAppsStr])
                    assertEquals(setOf("example.edited"), saved[APPS_BYPASS_VPN])
                }
            } finally {
                scope.cancel()
                instrumentation.runOnMainSync {
                    modules.mode = mode
                    fragment.handler.get().removeCallbacksAndMessages(null)
                }
            }
        }
    }

    @Test fun refreshKeepsFilteredEdits() {
        val app = App.instance
        val component = app.daggerComponent
        val data = InstalledApplicationsManager.Builder().build().getInstalledApps()
            .first { it.hasInternetPermission && it.uid >= android.os.Process.FIRST_APPLICATION_UID }
        val edited = data.mapToTorAppData().apply {
            torifyApp = true
            directUdp = true
            excludeFromAll = true
        }
        val fragment = UnlockTorAppsFragment().apply {
            executor = component.getCoroutineExecutor()
            preferenceRepository = component.getPreferenceRepository()
            defaultPreferences = dagger.Lazy { app.getSharedPreferences("tor-app-refresh-test", Context.MODE_PRIVATE) }
            handler = dagger.Lazy { Handler(Looper.getMainLooper()) }
            // A filtered list is empty while the backing list retains edited rows.
            savedAppsUnlockWhenSearch = CopyOnWriteArrayList(listOf(edited))
        }
        val refresh = UnlockTorAppsFragment::class.java.getDeclaredMethod(
            "getDeviceApps", Set::class.java, Boolean::class.javaPrimitiveType
        ).apply { isAccessible = true }
        val task = UnlockTorAppsFragment::class.java.getDeclaredField("refreshTask").apply { isAccessible = true }
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                refresh.invoke(fragment, Collections.emptySet<String>(), false)
            }
            runBlocking { withTimeout(30_000) { (task.get(fragment) as Job).join() } }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertSame(edited, fragment.appsUnlock.first { it.uid == data.uid })
            assertTrue(edited.torifyApp && edited.directUdp && edited.excludeFromAll)
        } finally {
            (task.get(fragment) as? Job)?.cancel()
        }
    }
}
