package pan.alexander.tordnscrypt.vpn.service;

import static org.junit.Assert.*;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.*;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.concurrent.ConcurrentSkipListSet;
import pan.alexander.tordnscrypt.App;
import pan.alexander.tordnscrypt.domain.connection_checker.ConnectionCheckerInteractor;
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository;
import pan.alexander.tordnscrypt.modules.ModulesStatus;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.enums.ModuleState;
import pan.alexander.tordnscrypt.utils.enums.OperationMode;
import pan.alexander.tordnscrypt.vpn.Allowed;
import pan.alexander.tordnscrypt.vpn.Forward;
import pan.alexander.tordnscrypt.vpn.Packet;

/** Exercises production policy without establishing a VPN or sending network traffic. */
@RunWith(AndroidJUnit4.class)
public final class UdpProxyPolicyTest {
    private static final class TestService extends ServiceVPN {
        boolean tor, proxy = true;
        TestService(Context context) { attachBaseContext(context); }
        @Override public boolean isRedirectToTor(int uid, String destination, int port) { return tor; }
        @Override public boolean isRedirectToProxy(int uid, String destination, int port) { return proxy; }
        @Override void addUIDtoDNSQueryRawRecords(int uid, String destination, int port,
                                                String source, boolean allowed, int protocol) {}
    }

    private static final class Fixture implements AutoCloseable {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final SharedPreferences preferences = context.getSharedPreferences("udp-proxy-policy-test", 0);
        final ModulesStatus modules = ModulesStatus.getInstance();
        final ModuleState tor = modules.getTorState(), dns = modules.getDnsCryptState(), itpd = modules.getItpdState();
        final OperationMode mode = modules.getMode();
        final boolean fixTTL = modules.isFixTTL();
        final ConcurrentSkipListSet<String> dnsSet = VpnBuilder.vpnDnsSet;
        final TestService service = new TestService(context);
        final VpnRulesHolder rules;
        final PreferenceRepository repository;
        Fixture() {
            VpnBuilder.vpnDnsSet = new ConcurrentSkipListSet<>();
            modules.setMode(OperationMode.VPN_MODE); modules.setFixTTL(false);
            modules.setTorState(ModuleState.STOPPED); modules.setDnsCryptState(ModuleState.STOPPED);
            modules.setItpdState(ModuleState.STOPPED);
            assertTrue(preferences.edit().clear().putBoolean(USE_PROXY_NON_TOR, true)
                    .putBoolean(CONNECTION_LOGS, false).commit());
            repository = (PreferenceRepository) Proxy.newProxyInstance(
                    PreferenceRepository.class.getClassLoader(), new Class[]{PreferenceRepository.class},
                    (object, method, args) -> {
                        switch (method.getName()) {
                            case "getBoolPreference": return false;
                            case "getStringSetPreference": return new HashSet<String>();
                            case "getStringPreference": return "";
                            case "getIntPreference": return 0;
                            case "getFloatPreference": return 0f;
                            default: return null;
                        }
                    });
            ConnectionCheckerInteractor connection = (ConnectionCheckerInteractor) Proxy.newProxyInstance(
                    ConnectionCheckerInteractor.class.getClassLoader(), new Class[]{ConnectionCheckerInteractor.class},
                    (object, method, args) -> method.getName().equals("getNetworkConnectionResult"));
            PathVars paths = App.getInstance().getDaggerComponent().getPathVars().get();
            service.vpnPreferences = new VpnPreferenceHolder(preferences, repository, paths);
            rules = new VpnRulesHolder(preferences, repository, paths, () -> null, () -> connection);
        }
        Allowed packet(int protocol, int port) {
            return packet(protocol, port, 12345);
        }
        Allowed packet(int protocol, int port, int uid) {
            Packet packet = new Packet(); packet.version = 4; packet.protocol = protocol;
            packet.uid = uid; packet.saddr = "10.1.10.1"; packet.daddr = "93.184.216.34";
            packet.sport = 40000; packet.dport = port; packet.flags = "";
            return rules.isAddressAllowed(service, packet);
        }
        @Override public void close() {
            VpnBuilder.vpnDnsSet = dnsSet;
            modules.setTorState(tor); modules.setDnsCryptState(dns); modules.setItpdState(itpd);
            modules.setMode(mode); modules.setFixTTL(fixTTL);
            assertTrue(preferences.edit().clear().commit());
        }
    }

    @Test public void externalUdpUsesProxyAndTcpKeepsExistingRouteSelection() {
        try (Fixture f = new Fixture()) {
            Allowed udp = f.packet(17, 443); assertNotNull(udp); assertTrue(udp.udpProxy);
            Allowed tcp = f.packet(6, 443); assertNotNull(tcp); assertFalse(tcp.udpProxy);
            assertNull(f.packet(1, 0));
        }
    }
    @Test public void torUdpRemainsBlockedEvenWithExternalProxyEnabled() {
        try (Fixture f = new Fixture()) {
            f.modules.setTorState(ModuleState.RUNNING); f.service.tor = true;
            assertNull(f.packet(17, 443));
            assertNotNull(f.packet(6, 443));
        }
    }
    @Test public void bypassedAppsStayDirectAndDirectUdpExceptionIsPreserved() {
        try (Fixture f = new Fixture()) {
            f.service.proxy = false;
            Allowed direct = f.packet(17, 443); assertNotNull(direct); assertFalse(direct.udpProxy);
            f.service.proxy = true; f.service.tor = true; f.modules.setTorState(ModuleState.RUNNING);
            f.service.vpnPreferences.getSetDirectUdpApps().add("12345");
            Allowed external = f.packet(17, 443); assertNotNull(external); assertTrue(external.udpProxy);
        }
    }
    @Test public void localDnsForwardingTakesPrecedenceOverExternalUdpProxy() {
        try (Fixture f = new Fixture()) {
            Forward forward = new Forward(); forward.raddr = "127.0.0.1"; forward.rport = 5354;
            f.rules.mapForwardPort.put(53, forward);
            Allowed dns = f.packet(17, 53); assertNotNull(dns); assertFalse(dns.udpProxy);
            assertEquals("127.0.0.1", dns.raddr); assertEquals(5354, dns.rport);
        }
    }
    @Test public void appOwnedTrafficCannotLoopThroughItsOwnProxy() {
        try (Fixture f = new Fixture()) {
            int uid = f.service.vpnPreferences.getOwnUID();
            Allowed udp = f.packet(17, 443, uid); assertNotNull(udp); assertFalse(udp.udpProxy);
            Allowed dns = f.packet(17, 53, uid); assertNotNull(dns); assertFalse(dns.udpProxy);
        }
    }
    @Test public void disabledProxyLeavesUdpDirect() {
        try (Fixture f = new Fixture()) {
            assertTrue(f.preferences.edit().putBoolean(USE_PROXY_NON_TOR, false).commit());
            f.service.vpnPreferences = new VpnPreferenceHolder(f.preferences,
                    f.repository,
                    App.getInstance().getDaggerComponent().getPathVars().get());
            Allowed udp = f.packet(17, 443); assertNotNull(udp); assertFalse(udp.udpProxy);
        }
    }
}
