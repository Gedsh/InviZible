package pan.alexander.tordnscrypt.settings.firewall

import android.content.Intent
import android.os.Looper
import androidx.lifecycle.Observer
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelStore
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import pan.alexander.tordnscrypt.App
import pan.alexander.tordnscrypt.utils.apps.CrossUserPermission
import pan.alexander.tordnscrypt.utils.apps.InstalledApplicationsManager
import pan.alexander.tordnscrypt.utils.apps.BlockedInventory
import pan.alexander.tordnscrypt.settings.tor_apps.ApplicationData
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.lang.reflect.Proxy
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository

@RunWith(AndroidJUnit4::class)
class FirewallPermissionRefreshTest {
    private class Preferences {
        val reads = AtomicInteger()
        val offMain = AtomicBoolean()
        val repository = Proxy.newProxyInstance(
            PreferenceRepository::class.java.classLoader, arrayOf(PreferenceRepository::class.java)
        ) { _, method, _ ->
            check(method.name == "getStringSetPreference") { "Unexpected preference call ${method.name}" }
            reads.incrementAndGet()
            if (Looper.myLooper() != Looper.getMainLooper()) offMain.set(true)
            hashSetOf("67890")
        } as PreferenceRepository
    }

    private class MainThreadSet(private val offMain: AtomicBoolean) : HashSet<Int>() {
        override fun addAll(elements: Collection<Int>): Boolean {
            if (Looper.myLooper() != Looper.getMainLooper()) offMain.set(true)
            return super.addAll(elements)
        }
        override fun contains(element: Int): Boolean {
            if (Looper.myLooper() != Looper.getMainLooper()) offMain.set(true)
            return super.contains(element)
        }
    }

    @Test fun baselineAndProgressOnMain() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val preferences = Preferences()
        BlockedInventory().use { scan ->
            val store = ViewModelStore()
            val model = FirewallViewModel(dagger.Lazy { preferences.repository }, scan.dispatcher, App.instance.daggerComponent.getPathVars())
            val offMain = AtomicBoolean()
            val ready = CountDownLatch(1)
            val observer = Observer<FirewallState> { if (it == FirewallState.Ready) ready.countDown() }
            try {
                instrumentation.runOnMainSync {
                    store.put("baseline-thread", model)
                    model.appsAllowLan = MainThreadSet(offMain)
                    model.appsAllowWifi = MainThreadSet(offMain)
                    model.appsAllowGsm = MainThreadSet(offMain)
                    model.appsAllowRoaming = MainThreadSet(offMain)
                    model.appsAllowVpn = MainThreadSet(offMain)
                    model.firewallStateLiveData.observeForever(observer)
                    model.getDeviceApps()
                }
                scan.awaitScan()
                assertTrue(preferences.reads.get() >= 5)
                assertFalse("Initial baseline loading belongs to the main thread", preferences.offMain.get())
                val callback = Thread {
                    model.onAppAdded(ApplicationData("Progress", "example.progress", 67890, null, false, true, false, false, false, 0))
                }
                callback.start()
                callback.join(5000)
                instrumentation.waitForIdleSync()
                instrumentation.runOnMainSync {
                    val progress = model.appsCompleteSet.single()
                    assertTrue(progress.allowLan && progress.allowWifi && progress.allowGsm && progress.allowRoaming && progress.allowVPN)
                    assertFalse("Worker callbacks must not read UI-owned sets", offMain.get())
                }
                scan.failScan()
                assertTrue("Discovery must finish", ready.await(10, TimeUnit.SECONDS))
            } finally {
                instrumentation.runOnMainSync {
                    model.firewallStateLiveData.removeObserver(observer)
                    store.clear()
                }
            }
        }
    }

    @Test fun failedRefreshKeepsSavePrompt() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = App.instance
        val component = app.daggerComponent
        BlockedInventory().use { scan ->
            val store = ViewModelStore()
            val preferences = Preferences()
            val model = FirewallViewModel(dagger.Lazy { preferences.repository }, scan.dispatcher, component.getPathVars())
            val original = FirewallAppModel(
                ApplicationData("Edited", "example.edited", 12345, null, false, true, false, false, false, 0),
                true, true, true, true, true, false
            )
            val finished = CountDownLatch(1)
            val waitingForCompletion = AtomicBoolean()
            val observer = Observer<FirewallState> {
                if (waitingForCompletion.get() && it == FirewallState.Ready) finished.countDown()
            }
            @Suppress("UNCHECKED_CAST")
            val state = FirewallViewModel::class.java.getDeclaredField("firewallStateMutableLiveData")
                .apply { isAccessible = true }.get(model) as MutableLiveData<FirewallState>
            try {
                instrumentation.runOnMainSync {
                    store.put("failed-refresh", model)
                    model.appsCompleteSet.add(original)
                    model.appsAllowLan.add(12345)
                    state.value = FirewallState.Ready
                    model.firewallStateLiveData.observeForever(observer)
                    LocalBroadcastManager.getInstance(app).sendBroadcastSync(Intent(CrossUserPermission.APPS_CHANGED))
                }
                scan.awaitScan()
                instrumentation.runOnMainSync {
                    assertEquals("Refresh must not reread the saved baseline", 0, preferences.reads.get())
                    assertEquals(setOf(12345), model.appsAllowLan)
                    assertSame(original, model.appsCompleteSet.single())
                    assertTrue("Back must still offer to save edits during discovery", model.isFirewallChangesSavingRequired())
                }
                waitingForCompletion.set(true)
                scan.failScan()
                assertTrue("Failed scan must finish", finished.await(10, TimeUnit.SECONDS))
                instrumentation.runOnMainSync {
                    assertEquals(setOf(12345), model.appsAllowLan)
                    assertSame(original, model.appsCompleteSet.single())
                    assertTrue("Failed discovery must preserve pending edits", model.isFirewallChangesSavingRequired())
                }
            } finally {
                instrumentation.runOnMainSync {
                    model.firewallStateLiveData.removeObserver(observer)
                    store.clear()
                }
            }
        }
    }

    @Test fun refreshKeepsEdits() {
        checkRefresh(false)
    }

    @Test fun refreshUpdatesAppStateAndKeepsEdits() {
        checkRefresh(true)
    }

    private fun checkRefresh(staleState: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = App.instance
        val component = app.daggerComponent
        val store = ViewModelStore()
        val model = FirewallViewModel(component.getPreferenceRepository(), Dispatchers.Default, component.getPathVars())
        val ready = CountDownLatch(1)
        val observer = Observer<FirewallState> { if (it == FirewallState.Ready) ready.countDown() }
        val data = InstalledApplicationsManager.Builder().showSpecialApps(true).build()
            .getInstalledApps().first { it.uid >= android.os.Process.FIRST_APPLICATION_UID }
        val uid = data.uid
        val original = FirewallAppModel(
            if (staleState) data.copy(archived = !data.archived, disabled = !data.disabled) else data,
            true, true, true, true, true, false
        )
        try {
            instrumentation.runOnMainSync {
                store.put("permission-refresh", model)
                model.appsCompleteSet.add(original)
                model.firewallStateLiveData.observeForever(observer)
                LocalBroadcastManager.getInstance(app).sendBroadcastSync(Intent(CrossUserPermission.APPS_CHANGED))
            }
            assertTrue("Discovery must finish", ready.await(30, TimeUnit.SECONDS))
            val refreshed = model.appsCompleteSet.first { it.applicationData.uid == uid }
            if (staleState) assertNotSame(original, refreshed) else assertSame(original, refreshed)
            assertEquals(data.archived, refreshed.applicationData.archived)
            assertEquals(data.disabled, refreshed.applicationData.disabled)
            assertTrue(refreshed.allowLan && refreshed.allowWifi && refreshed.allowGsm && refreshed.allowRoaming && refreshed.allowVPN)
            instrumentation.runOnMainSync {
                model.firewallStateLiveData.removeObserver(observer)
                store.clear()
            }
            // A cleared ViewModel must not remain retained as a local broadcast receiver.
            model.appsCompleteSet.clear()
            instrumentation.runOnMainSync {
                LocalBroadcastManager.getInstance(app).sendBroadcastSync(Intent(CrossUserPermission.APPS_CHANGED))
            }
            assertTrue(model.appsCompleteSet.isEmpty())
        } finally {
            instrumentation.runOnMainSync {
                model.firewallStateLiveData.removeObserver(observer)
                store.clear()
            }
        }
    }
}
