package pan.alexander.tordnscrypt.settings.tor_apps

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository
import pan.alexander.tordnscrypt.modules.ModulesStatus
import pan.alexander.tordnscrypt.settings.firewall.adapter.FirewallAdapter
import pan.alexander.tordnscrypt.settings.tor_apps.TorAppData.Companion.mapToTorAppData
import pan.alexander.tordnscrypt.settings.tor_apps.TorAppData.Companion.sortByName
import pan.alexander.tordnscrypt.settings.tor_apps.TorAppData.Companion.sortByUid
import pan.alexander.tordnscrypt.utils.enums.OperationMode
import java.lang.reflect.Proxy
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(AndroidJUnit4::class)
class AppSortingTest {
    private val apps = listOf(
        app(3, "Bravo", disabled = true),
        app(6, "alpha"),
        app(1, "alpha", archived = true),
        app(5, "Charlie"),
        app(2, "beta", archived = true, disabled = true),
        app(4, "alpha", disabled = true)
    )
    private val nameOrder = listOf(6, 5, 4, 3, 1, 2)
    private val selectedOrder = listOf(1, 3, 5, 6, 4, 2)

    private fun app(uid: Int, name: String, archived: Boolean = false, disabled: Boolean = false) =
        ApplicationData(name = name, pack = "example.$uid", uid = uid,
            active = uid % 2 != 0, archived = archived, disabled = disabled)

    @Test fun discoveryKeepsSelectedTogetherBeforeUnselectedStates() {
        assertEquals(selectedOrder, apps.sorted().map { it.uid })
        assertEquals(selectedOrder, apps.reversed().sorted().map { it.uid })
        assertEquals(nameOrder, apps.map { it.copy(active = false) }.sorted().map { it.uid })
    }

    private fun torApps() = CopyOnWriteArrayList(apps.map { data ->
        data.mapToTorAppData().apply {
            directUdp = data.active
            excludeFromAll = data.active
        }
    })

    @Test fun torNameSortPrioritizesSelectionOverState() {
        val rows = torApps()
        rows.sortByName()
        assertEquals(selectedOrder, rows.map { it.uid })
    }

    @Test fun torNameSortAlphabetizesEqualSelections() {
        val rows = torApps()
        rows.forEach { it.torifyApp = false; it.directUdp = false; it.excludeFromAll = false }
        rows.sortByName()
        assertEquals(nameOrder, rows.map { it.uid })
    }

    @Test fun torNameSortKeepsFlagHierarchyInRootAndVpnModes() {
        val modules = ModulesStatus.getInstance()
        val previousMode = modules.mode
        try {
            for (mode in listOf(OperationMode.VPN_MODE, OperationMode.ROOT_MODE)) {
                modules.mode = mode
                val rows = CopyOnWriteArrayList(listOf(
                    app(6, "Alpha").mapToTorAppData().copy(torifyApp = false, directUdp = false, excludeFromAll = false),
                    app(5, "Bravo").mapToTorAppData().copy(torifyApp = false, directUdp = true, excludeFromAll = false),
                    app(4, "Charlie").mapToTorAppData().copy(torifyApp = true, directUdp = false, excludeFromAll = false),
                    app(3, "Delta").mapToTorAppData().copy(torifyApp = false, directUdp = false, excludeFromAll = true),
                    app(2, "Echo").mapToTorAppData().copy(torifyApp = true, directUdp = true, excludeFromAll = false),
                    app(1, "Foxtrot").mapToTorAppData().copy(torifyApp = true, directUdp = false, excludeFromAll = true)
                ))
                rows.sortByName()
                val expected = if (mode == OperationMode.VPN_MODE) listOf(1, 3, 2, 4, 5, 6)
                    else listOf(2, 4, 1, 5, 6, 3)
                assertEquals(expected, rows.map { it.uid })
            }
        } finally {
            modules.mode = previousMode
        }
    }

    @Test fun torUidSortKeepsSelectionPriority() {
        val rows = torApps()
        rows.sortByUid()
        assertEquals(listOf(1, 3, 5, 2, 4, 6), rows.map { it.uid })
    }

    private fun firewallItems() = apps.map { data ->
        FirewallAdapter.AdapterItem(
            uid = data.uid, label = data.toString(), icon = null, system = false,
            archived = data.archived, disabled = data.disabled, user = 0,
            hasInternetPermission = true, lan = data.active, wifi = data.active,
            gsm = data.active, roaming = data.active, vpn = data.active,
            newlyInstalled = data.active, bypassed = data.active
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun firewallComparator(name: String): Comparator<FirewallAdapter.AdapterItem> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = Proxy.newProxyInstance(PreferenceRepository::class.java.classLoader,
            arrayOf(PreferenceRepository::class.java)) { _, method, _ ->
            when (method.name) {
                "getStringSetPreference" -> hashSetOf<String>()
                "setStringSetPreference" -> null
                else -> error("Unexpected preference call: ${method.name}")
            }
        } as PreferenceRepository
        val adapter = FirewallAdapter(context,
            context.getSharedPreferences("app-sorting-test", Context.MODE_PRIVATE), preferences,
            {}, {}, {}, {}, {}, {})
        return FirewallAdapter::class.java.getDeclaredField(name).apply { isAccessible = true }
            .get(adapter) as Comparator<FirewallAdapter.AdapterItem>
    }

    @Test fun firewallNameSortPrioritizesRulesOverState() {
        val rows = firewallItems().sortedWith(firewallComparator("comparatorByName"))
        assertEquals(selectedOrder, rows.map { it.uid })
    }

    @Test fun firewallNameSortAlphabetizesEqualRules() {
        val rows = firewallItems().map { it.copy(bypassed = false, newlyInstalled = false,
            lan = false, wifi = false, gsm = false, roaming = false, vpn = false) }
            .sortedWith(firewallComparator("comparatorByName"))
        assertEquals(nameOrder, rows.map { it.uid })
    }

    @Test fun firewallSortsRespectModeForBypassAndVpnAllowance() {
        val template = firewallItems().first().copy(archived = false, disabled = false,
            bypassed = false, newlyInstalled = false, lan = false, wifi = false,
            gsm = false, roaming = false, vpn = false)
        val rows = listOf(
            template.copy(uid = 4, label = "Alpha"),
            template.copy(uid = 3, label = "Bravo", lan = true),
            template.copy(uid = 2, label = "Charlie", newlyInstalled = true),
            template.copy(uid = 1, label = "Delta", bypassed = true, archived = true),
            template.copy(uid = 5, label = "Echo", vpn = true),
            template.copy(uid = 6, label = "Foxtrot", bypassed = true, disabled = true)
        )
        val modules = ModulesStatus.getInstance()
        val previousMode = modules.mode
        try {
            for (mode in listOf(OperationMode.VPN_MODE, OperationMode.ROOT_MODE)) {
                modules.mode = mode
                val expected = if (mode == OperationMode.VPN_MODE) listOf(1, 6, 2, 3, 4, 5)
                    else listOf(2, 3, 5, 4, 6, 1)
                for (name in listOf("comparatorByName", "comparatorByUid")) {
                    assertEquals(expected, rows.sortedWith(firewallComparator(name)).map { it.uid })
                }
            }
        } finally {
            modules.mode = previousMode
        }
    }

    @Test fun firewallUidSortPrioritizesRulesThenUnselectedStateThenUid() {
        val rows = firewallItems().sortedWith(firewallComparator("comparatorByUid"))
        assertEquals(listOf(1, 3, 5, 6, 4, 2), rows.map { it.uid })
    }

    @Test fun firewallUidSortOrdersWithinEachUnselectedStateByUid() {
        val rows = firewallItems().map { it.copy(bypassed = false, newlyInstalled = false,
            lan = false, wifi = false, gsm = false, roaming = false, vpn = false) }
            .sortedWith(firewallComparator("comparatorByUid"))
        assertEquals(listOf(5, 6, 3, 4, 1, 2), rows.map { it.uid })
    }
}
