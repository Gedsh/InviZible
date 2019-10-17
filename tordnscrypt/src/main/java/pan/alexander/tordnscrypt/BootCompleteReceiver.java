package pan.alexander.tordnscrypt;
/*
    This file is part of InviZible Pro.

    InviZible Pro is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    InviZible Pro is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with InviZible Pro.  If not, see <http://www.gnu.org/licenses/>.

    Copyright 2019 by Garmatin Oleksandr invizible.soft@gmail.com
*/
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;

import java.util.Objects;

import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.settings.PreferencesFastFragment;
import pan.alexander.tordnscrypt.utils.ApManager;
import pan.alexander.tordnscrypt.utils.Arr;
import pan.alexander.tordnscrypt.utils.GetIPsJobService;
import pan.alexander.tordnscrypt.utils.NoRootService;
import pan.alexander.tordnscrypt.utils.OwnFileReader;
import pan.alexander.tordnscrypt.utils.PrefManager;
import pan.alexander.tordnscrypt.utils.RootCommands;
import pan.alexander.tordnscrypt.utils.RootExecService;
import pan.alexander.tordnscrypt.utils.Tethering;

public class BootCompleteReceiver extends BroadcastReceiver {
    Context context;
    SharedPreferences shPref;
    String appDataDir;
    String dnsCryptPort;
    String itpdHttpProxyPort;
    String torTransPort;
    String dnsCryptFallbackRes;
    String torDNSPort;
    public String torSOCKSPort;
    public String torHTTPTunnelPort;
    public String itpdSOCKSPort;
    String torVirtAdrNet;
    String dnscryptPath;
    String torPath;
    String itpdPath;
    String obfsPath;
    String busyboxPath;
    String iptablesPath;
    String rejectAddress;
    int mJobId = PreferencesFastFragment.mJobId;
    private int refreshPeriodHours = 12;

    @Override
    public void onReceive(final Context context, Intent intent) {

        String BOOT_COMPLETE = "android.intent.action.BOOT_COMPLETED";
        this.context = context.getApplicationContext();
        shPref = PreferenceManager.getDefaultSharedPreferences(this.context);
        refreshPeriodHours = Integer.parseInt(shPref.getString("pref_fast_site_refresh_interval","12"));

        PathVars pathVars = new PathVars(context.getApplicationContext());
        appDataDir = pathVars.appDataDir;
        dnsCryptPort = pathVars.dnsCryptPort;
        itpdHttpProxyPort = pathVars.itpdHttpProxyPort;
        torTransPort = pathVars.torTransPort;
        dnsCryptFallbackRes = pathVars.dnsCryptFallbackRes;
        torDNSPort = pathVars.torDNSPort;
        torVirtAdrNet = pathVars.torVirtAdrNet;
        dnscryptPath = pathVars.dnscryptPath;
        torPath = pathVars.torPath;
        itpdPath = pathVars.itpdPath;
        obfsPath = pathVars.obfsPath;
        busyboxPath = pathVars.busyboxPath;
        iptablesPath = pathVars.iptablesPath;
        rejectAddress = pathVars.rejectAddress;
        torSOCKSPort = pathVars.torSOCKSPort;
        torHTTPTunnelPort = pathVars.torHTTPTunnelPort;
        itpdSOCKSPort = pathVars.itpdSOCKSPort;
        Tethering tethering = new Tethering(context);
        boolean tethering_autostart;
        boolean routeAllThroughTor;
        boolean blockHttp;


        if(Objects.requireNonNull(intent.getAction()).equalsIgnoreCase(BOOT_COMPLETE)){

            //new PrefManager(context).setBoolPref("bootCompleteForTether",false);
            new PrefManager(context).setBoolPref("APisON",false);


            SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(context);
            tethering_autostart = shPref.getBoolean("pref_common_tethering_autostart",false);
            routeAllThroughTor = shPref.getBoolean("pref_fast_all_through_tor",true);
            blockHttp = shPref.getBoolean("pref_fast_block_http",false);

            boolean autoStartDNSCrypt = shPref.getBoolean("swAutostartDNS",true);
            boolean autoStartTor = shPref.getBoolean("swAutostartTor",false);
            boolean autoStartITPD = shPref.getBoolean("swAutostartITPD",false);

            if (autoStartITPD) {
                OwnFileReader ofr = new OwnFileReader(appDataDir+"/logs/i2pd.log");
                ofr.shortenToToLongFile();
            }

            String torSitesBypassNatTCP = "";
            String torSitesBypassFilterTCP = "";
            String torSitesBypassNatUDP = "";
            String torSitesBypassFilterUDP = "";
            String torAppsBypassNatTCP = "";
            String torAppsBypassNatUDP = "";
            String torAppsBypassFilterTCP = "";
            String torAppsBypassFilterUDP = "";
            if (routeAllThroughTor) {
                torSitesBypassNatTCP = busyboxPath+ "cat "+appDataDir+"/app_data/tor/clearnet | while read var1; do "+iptablesPath+"iptables -t nat -A tordnscrypt_nat_output -p tcp -d $var1 -j RETURN; done";
                torSitesBypassFilterTCP = busyboxPath+ "cat "+appDataDir+"/app_data/tor/clearnet | while read var1; do "+iptablesPath+"iptables -A tordnscrypt -p tcp -d $var1 -j RETURN; done";
                torSitesBypassNatUDP = busyboxPath+ "cat "+appDataDir+"/app_data/tor/clearnet | while read var1; do "+iptablesPath+"iptables -t nat -A tordnscrypt_nat_output -p udp -d $var1 -j RETURN; done";
                torSitesBypassFilterUDP = busyboxPath+ "cat "+appDataDir+"/app_data/tor/clearnet | while read var1; do "+iptablesPath+"iptables -A tordnscrypt -p udp -d $var1 -j RETURN; done";
                torAppsBypassNatTCP = busyboxPath+ "cat "+appDataDir+"/app_data/tor/clearnetApps | while read var1; do "+iptablesPath+"iptables -t nat -A tordnscrypt_nat_output -p tcp -m owner --uid-owner $var1 -j RETURN; done";
                torAppsBypassNatUDP = busyboxPath+ "cat "+appDataDir+"/app_data/tor/clearnetApps | while read var1; do "+iptablesPath+"iptables -t nat -A tordnscrypt_nat_output -p udp -m owner --uid-owner $var1 -j RETURN; done";
                torAppsBypassFilterTCP = busyboxPath+ "cat "+appDataDir+"/app_data/tor/clearnetApps | while read var1; do "+iptablesPath+"iptables -A tordnscrypt -p tcp -m owner --uid-owner $var1 -j RETURN; done";
                torAppsBypassFilterUDP = busyboxPath+ "cat "+appDataDir+"/app_data/tor/clearnetApps | while read var1; do "+iptablesPath+"iptables -A tordnscrypt -p udp -m owner --uid-owner $var1 -j RETURN; done";
            }

            String blockHttpRuleFilterAll = "";
            String blockHttpRuleNatTCP = "";
            String blockHttpRuleNatUDP = "";
            if (blockHttp) {
                blockHttpRuleFilterAll = iptablesPath + "iptables -A tordnscrypt -d +"+ rejectAddress +" -j REJECT";
                blockHttpRuleNatTCP = iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p tcp --dport 80 -j DNAT --to-destination "+rejectAddress;
                blockHttpRuleNatUDP = iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p udp --dport 80 -j DNAT --to-destination "+rejectAddress;
            }

            if (tethering_autostart) {
                new PrefManager(context).setBoolPref("APisON",true);

                try {
                    ApManager apManager = new ApManager(context);
                    if (!apManager.configApState()) {
                        Intent intent_tether = new Intent(Intent.ACTION_MAIN, null);
                        intent_tether.addCategory(Intent.CATEGORY_LAUNCHER);
                        ComponentName cn = new ComponentName("com.android.settings", "com.android.settings.TetherSettings");
                        intent_tether.setComponent(cn);
                        intent_tether.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(intent_tether);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            boolean rnDNSCryptWithRoot = shPref.getBoolean("swUseModulesRoot",false);
            boolean rnTorWithRoot = shPref.getBoolean("swUseModulesRoot",false);
            boolean rnI2PDWithRoot = shPref.getBoolean("swUseModulesRoot",false);

            String startCommandDNSCrypt = "";
            String appUIDDNS = new PrefManager(context).getStrPref("appUID");
            String restoreUIDDNS = busyboxPath+ "chown -R "+appUIDDNS+"."+appUIDDNS+" "+appDataDir+"/app_data/dnscrypt-proxy";
            String restoreSEContextDNS = "restorecon -R "+appDataDir+"/app_data/dnscrypt-proxy";
            String killallDNS = busyboxPath + "killall dnscrypt-proxy";
            if (rnDNSCryptWithRoot) {
                startCommandDNSCrypt = busyboxPath+ "nohup " + dnscryptPath+" --config "+appDataDir+"/app_data/dnscrypt-proxy/dnscrypt-proxy.toml >/dev/null 2>&1 &";
                restoreUIDDNS = busyboxPath+"chown -R 0.0 "+appDataDir+"/app_data/dnscrypt-proxy";
                restoreSEContextDNS = "";
                killallDNS = busyboxPath + "killall dnscrypt-proxy";
            }

            String startCommandTor = "";
            String appUIDTor = new PrefManager(context).getStrPref("appUID");
            String restoreUIDTor = busyboxPath+ "chown -R "+appUIDTor+"."+appUIDTor+" "+appDataDir+"/tor_data";
            String restoreSEContextTor = "restorecon -R "+appDataDir+"/tor_data";
            String killallTor = busyboxPath + "killall tor";
            if (rnTorWithRoot) {
                startCommandTor = torPath+" -f "+appDataDir+"/app_data/tor/tor.conf";
                restoreUIDTor = busyboxPath+"chown -R 0.0 "+appDataDir+"/tor_data";
                restoreSEContextTor = "";
                appUIDTor ="0";
                killallTor = busyboxPath + "killall tor";
            }

            String startCommandI2PD = "";
            String appUIDITPD = new PrefManager(context).getStrPref("appUID");
            String restoreUIDITPD = busyboxPath+ "chown -R "+appUIDITPD+"."+appUIDITPD+" "+appDataDir+"/i2pd_data";
            String restoreSEContextITPD = "restorecon -R "+appDataDir+"/i2pd_data";
            String killallITPD = busyboxPath + "killall i2pd";
            if (rnI2PDWithRoot) {
                startCommandI2PD = itpdPath+" --conf "+appDataDir+"/app_data/i2pd/i2pd.conf --datadir "+appDataDir+"/i2pd_data &";
                restoreUIDITPD = busyboxPath+"chown -R 0.0 "+appDataDir+"/i2pd_data";
                restoreSEContextITPD = "";
                killallITPD = busyboxPath + "killall i2pd";
            }

            String[] commandsStart = null;

            if(autoStartDNSCrypt && autoStartTor && autoStartITPD){

                new PrefManager(context).setBoolPref("DNSCrypt Running",true);
                new PrefManager(context).setBoolPref("Tor Running",true);
                new PrefManager(context).setBoolPref("I2PD Running",true);

                if (!routeAllThroughTor) {
                    commandsStart = new String[] {
                            killallDNS, killallTor, killallITPD,
                            "ip6tables -D OUTPUT -j DROP || true",
                            "ip6tables -I OUTPUT -j DROP",
                            iptablesPath + "iptables -t nat -F tordnscrypt_nat_output",
                            iptablesPath + "iptables -t nat -D OUTPUT -j tordnscrypt_nat_output || true",
                            iptablesPath + "iptables -F tordnscrypt",
                            iptablesPath + "iptables -D OUTPUT -j tordnscrypt || true",
                            busyboxPath + "sleep 1",

                            busyboxPath+ "echo 'Beginning of log' > "+appDataDir+"/logs/DnsCrypt.log",
                            busyboxPath+ "echo 'Beginning of log' > "+appDataDir+"/logs/Tor.log",
                            busyboxPath+ "sleep 1",
                            restoreUIDDNS,
                            restoreUIDTor,
                            restoreUIDITPD,
                            restoreSEContextDNS,
                            restoreSEContextTor,
                            restoreSEContextITPD,
                            busyboxPath+ "sleep 1",
                            startCommandDNSCrypt,
                            startCommandTor,
                            startCommandI2PD,
                            busyboxPath+ "sleep 1",
                            iptablesPath+ "iptables -t nat -N tordnscrypt_nat_output",
                            iptablesPath+ "iptables -t nat -I OUTPUT -j tordnscrypt_nat_output",
                            iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p tcp -d 127.0.0.1/32 -j RETURN",
                            iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p udp -d 127.0.0.1/32 -j RETURN",
                            iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p tcp -d 10.191.0.1 -j DNAT --to-destination 127.0.0.1:"+itpdHttpProxyPort,
                            iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p udp -d 10.191.0.1 -j DNAT --to-destination 127.0.0.1:"+itpdHttpProxyPort,
                            iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p udp -d "+dnsCryptFallbackRes+" --dport 53 -j ACCEPT",
                            iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p udp --dport 53 -j DNAT --to-destination 127.0.0.1:"+dnsCryptPort,
                            iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p tcp --dport 53 -j DNAT --to-destination 127.0.0.1:"+dnsCryptPort,
                            iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p tcp -d "+torVirtAdrNet+" -j DNAT --to-destination 127.0.0.1:"+torTransPort,
                            blockHttpRuleNatTCP,
                            blockHttpRuleNatUDP,
                            iptablesPath+ "iptables -N tordnscrypt",
                            iptablesPath+ "iptables -A tordnscrypt -m state --state ESTABLISHED,RELATED -j ACCEPT",
                            iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+dnsCryptPort+" -m owner --uid-owner 0 -j ACCEPT",
                            iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+dnsCryptPort+" -m owner --uid-owner 0 -j ACCEPT",
                            blockHttpRuleFilterAll,
                            iptablesPath+ "iptables -I OUTPUT -j tordnscrypt",
                            busyboxPath+ "cat "+appDataDir+"/app_data/tor/unlock | while read var1; do "+iptablesPath+"iptables -t nat -A tordnscrypt_nat_output -p tcp -d $var1 -j REDIRECT --to-port "+torTransPort+"; done",
                            busyboxPath+ "cat "+appDataDir+"/app_data/tor/unlockApps | while read var1; do "+iptablesPath+"iptables -t nat -A tordnscrypt_nat_output -p tcp -m owner --uid-owner $var1 -j REDIRECT --to-port "+torTransPort+"; done",
                            busyboxPath+ "sleep 10",
                            iptablesPath+ "iptables -t nat -D tordnscrypt_nat_output -p udp -d "+dnsCryptFallbackRes+" --dport 53 -j ACCEPT",
                    };

                } else {
                    commandsStart = new String[] {
                            killallDNS, killallTor, killallITPD,
                            "ip6tables -D OUTPUT -j DROP || true",
                            "ip6tables -I OUTPUT -j DROP",
                            iptablesPath + "iptables -t nat -F tordnscrypt_nat_output",
                            iptablesPath + "iptables -t nat -D OUTPUT -j tordnscrypt_nat_output || true",
                            iptablesPath + "iptables -F tordnscrypt",
                            iptablesPath + "iptables -D OUTPUT -j tordnscrypt || true",
                            busyboxPath + "sleep 1",

                            busyboxPath+ "echo 'Beginning of log' > "+appDataDir+"/logs/DnsCrypt.log",
                            busyboxPath+ "echo 'Beginning of log' > "+appDataDir+"/logs/Tor.log",
                            busyboxPath+ "sleep 1",
                            restoreUIDDNS,
                            restoreUIDTor,
                            restoreUIDITPD,
                            restoreSEContextDNS,
                            restoreSEContextTor,
                            restoreSEContextITPD,
                            busyboxPath+ "sleep 1",
                            startCommandDNSCrypt,
                            startCommandTor,
                            startCommandI2PD,
                            busyboxPath+ "sleep 1",
                            "TOR_UID="+ appUIDTor,
                            iptablesPath+ "iptables -t nat -N tordnscrypt_nat_output",
                            iptablesPath+ "iptables -t nat -I OUTPUT -j tordnscrypt_nat_output",
                            iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p tcp -d 127.0.0.1/32 -j RETURN",
                            iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p udp -d 127.0.0.1/32 -j RETURN",
                            iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p tcp -d 10.191.0.1 -j DNAT --to-destination 127.0.0.1:"+itpdHttpProxyPort,
                            iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p udp -d 10.191.0.1 -j DNAT --to-destination 127.0.0.1:"+itpdHttpProxyPort,
                            iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p udp -d "+dnsCryptFallbackRes+" --dport 53 -j ACCEPT",
                            iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p udp --dport 53 -j DNAT --to-destination 127.0.0.1:"+dnsCryptPort,
                            iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p tcp --dport 53 -j DNAT --to-destination 127.0.0.1:"+dnsCryptPort,
                            iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -m owner --uid-owner $TOR_UID -j RETURN",
                            iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p tcp -d " + torVirtAdrNet + " -j DNAT --to-destination 127.0.0.1:" + torTransPort,
                            blockHttpRuleNatTCP,
                            blockHttpRuleNatUDP,
                            torSitesBypassNatTCP,
                            torSitesBypassNatUDP,
                            torAppsBypassNatTCP,
                            torAppsBypassNatUDP,
                            iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p tcp --syn -j DNAT --to-destination 127.0.0.1:"+torTransPort,
                            iptablesPath+ "iptables -N tordnscrypt",
                            iptablesPath+ "iptables -A tordnscrypt -m state --state ESTABLISHED,RELATED -j ACCEPT",
                            iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+torSOCKSPort+" -j RETURN",
                            iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+torSOCKSPort+" -j RETURN",
                            iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+torHTTPTunnelPort+" -j RETURN",
                            iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+torHTTPTunnelPort+" -j RETURN",
                            iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+itpdSOCKSPort+" -j RETURN",
                            iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+itpdSOCKSPort+" -j RETURN",
                            iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+itpdHttpProxyPort+" -j RETURN",
                            iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+itpdHttpProxyPort+" -j RETURN",
                            iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+torTransPort+" -j RETURN",
                            iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+dnsCryptPort+" -m owner --uid-owner 0 -j ACCEPT",
                            iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+dnsCryptPort+" -m owner --uid-owner 0 -j ACCEPT",
                            iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+dnsCryptPort+" -j RETURN",
                            iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+dnsCryptPort+" -j RETURN",
                            iptablesPath+ "iptables -A tordnscrypt -m owner --uid-owner $TOR_UID -j ACCEPT",
                            blockHttpRuleFilterAll,
                            torSitesBypassFilterTCP,
                            torSitesBypassFilterUDP,
                            torAppsBypassFilterTCP,
                            torAppsBypassFilterUDP,
                            iptablesPath+ "iptables -A tordnscrypt -j REJECT",
                            iptablesPath+ "iptables -I OUTPUT -j tordnscrypt"};
                }

                if (!rnDNSCryptWithRoot) {
                    runDNSCryptNoRoot();
                }
                if (!rnTorWithRoot) {
                    runTorNoRoot();
                }
                if (!rnI2PDWithRoot) {
                    runITPDNoRoot();
                }
                startRefreshTorUnlockIPs(context);
                if (tethering_autostart){
                    String[] commandsTether = tethering.activateTethering(false);
                    if (commandsTether!=null && commandsTether.length>0)
                        commandsStart = Arr.ADD2(commandsStart, commandsTether);
                }
            } else if(autoStartDNSCrypt && autoStartTor && !autoStartITPD) {

                new PrefManager(context).setBoolPref("DNSCrypt Running",true);
                new PrefManager(context).setBoolPref("Tor Running",true);
                new PrefManager(context).setBoolPref("I2PD Running",false);

                if (!routeAllThroughTor) {
                    commandsStart = new String[] {
                            killallDNS, killallTor, killallITPD,
                            "ip6tables -D OUTPUT -j DROP || true",
                            "ip6tables -I OUTPUT -j DROP",
                            iptablesPath + "iptables -t nat -F tordnscrypt_nat_output",
                            iptablesPath + "iptables -t nat -D OUTPUT -j tordnscrypt_nat_output || true",
                            iptablesPath + "iptables -F tordnscrypt",
                            iptablesPath + "iptables -D OUTPUT -j tordnscrypt || true",
                            busyboxPath + "sleep 1",

                            busyboxPath+ "echo 'Beginning of log' > "+appDataDir+"/logs/DnsCrypt.log",
                            busyboxPath+ "echo 'Beginning of log' > "+appDataDir+"/logs/Tor.log",
                            busyboxPath+ "sleep 1",
                            restoreUIDDNS,
                            restoreUIDTor,
                            restoreSEContextDNS,
                            restoreSEContextTor,
                            busyboxPath+ "sleep 1",
                            startCommandDNSCrypt,
                            startCommandTor,
                            busyboxPath+ "sleep 1",
                            iptablesPath+ "iptables -t nat -N tordnscrypt_nat_output",
                            iptablesPath+ "iptables -t nat -I OUTPUT -j tordnscrypt_nat_output",
                            iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p tcp -d 127.0.0.1/32 -j RETURN",
                            iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p udp -d 127.0.0.1/32 -j RETURN",
                            iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p tcp -d 10.191.0.1 -j DNAT --to-destination 127.0.0.1:"+itpdHttpProxyPort,
                            iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p udp -d 10.191.0.1 -j DNAT --to-destination 127.0.0.1:"+itpdHttpProxyPort,
                            iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p udp -d "+dnsCryptFallbackRes+" --dport 53 -j ACCEPT",
                            iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p udp --dport 53 -j DNAT --to-destination 127.0.0.1:"+dnsCryptPort,
                            iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p tcp --dport 53 -j DNAT --to-destination 127.0.0.1:"+dnsCryptPort,
                            iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p tcp -d "+torVirtAdrNet+" -j DNAT --to-destination 127.0.0.1:"+torTransPort,
                            blockHttpRuleNatTCP,
                            blockHttpRuleNatUDP,
                            iptablesPath+ "iptables -N tordnscrypt",
                            iptablesPath+ "iptables -A tordnscrypt -m state --state ESTABLISHED,RELATED -j ACCEPT",
                            iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+dnsCryptPort+" -m owner --uid-owner 0 -j ACCEPT",
                            iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+dnsCryptPort+" -m owner --uid-owner 0 -j ACCEPT",
                            blockHttpRuleFilterAll,
                            iptablesPath+ "iptables -I OUTPUT -j tordnscrypt",
                            busyboxPath+ "cat "+appDataDir+"/app_data/tor/unlock | while read var1; do "+iptablesPath+"iptables -t nat -A tordnscrypt_nat_output -p tcp -d $var1 -j REDIRECT --to-port "+torTransPort+"; done",
                            busyboxPath+ "cat "+appDataDir+"/app_data/tor/unlockApps | while read var1; do "+iptablesPath+"iptables -t nat -A tordnscrypt_nat_output -p tcp -m owner --uid-owner $var1 -j REDIRECT --to-port "+torTransPort+"; done",
                            busyboxPath+ "sleep 10",
                            iptablesPath+ "iptables -t nat -D tordnscrypt_nat_output -p udp -d "+dnsCryptFallbackRes+" --dport 53 -j ACCEPT"
                    };
                } else {
                    commandsStart = new String[] {
                            killallDNS, killallTor, killallITPD,
                            "ip6tables -D OUTPUT -j DROP || true",
                            "ip6tables -I OUTPUT -j DROP",
                            iptablesPath + "iptables -t nat -F tordnscrypt_nat_output",
                            iptablesPath + "iptables -t nat -D OUTPUT -j tordnscrypt_nat_output || true",
                            iptablesPath + "iptables -F tordnscrypt",
                            iptablesPath + "iptables -D OUTPUT -j tordnscrypt || true",
                            busyboxPath + "sleep 1",

                            busyboxPath+ "echo 'Beginning of log' > "+appDataDir+"/logs/DnsCrypt.log",
                            busyboxPath+ "echo 'Beginning of log' > "+appDataDir+"/logs/Tor.log",
                            busyboxPath+ "sleep 1",
                            restoreUIDDNS,
                            restoreUIDTor,
                            restoreSEContextDNS,
                            restoreSEContextTor,
                            busyboxPath+ "sleep 1",
                            startCommandDNSCrypt,
                            startCommandTor,
                            busyboxPath+ "sleep 1",
                            "TOR_UID="+ appUIDTor,
                            iptablesPath+ "iptables -t nat -N tordnscrypt_nat_output",
                            iptablesPath+ "iptables -t nat -I OUTPUT -j tordnscrypt_nat_output",
                            iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p tcp -d 127.0.0.1/32 -j RETURN",
                            iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p udp -d 127.0.0.1/32 -j RETURN",
                            iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p tcp -d 10.191.0.1 -j DNAT --to-destination 127.0.0.1:"+itpdHttpProxyPort,
                            iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p udp -d 10.191.0.1 -j DNAT --to-destination 127.0.0.1:"+itpdHttpProxyPort,
                            iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p udp -d "+dnsCryptFallbackRes+" --dport 53 -j ACCEPT",
                            iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p udp --dport 53 -j DNAT --to-destination 127.0.0.1:"+dnsCryptPort,
                            iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p tcp --dport 53 -j DNAT --to-destination 127.0.0.1:"+dnsCryptPort,
                            iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -m owner --uid-owner $TOR_UID -j RETURN",
                            iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p tcp -d " + torVirtAdrNet + " -j DNAT --to-destination 127.0.0.1:" + torTransPort,
                            blockHttpRuleNatTCP,
                            blockHttpRuleNatUDP,
                            torSitesBypassNatTCP,
                            torSitesBypassNatUDP,
                            torAppsBypassNatTCP,
                            torAppsBypassNatUDP,
                            iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p tcp --syn -j DNAT --to-destination 127.0.0.1:"+torTransPort,
                            iptablesPath+ "iptables -N tordnscrypt",
                            iptablesPath+ "iptables -A tordnscrypt -m state --state ESTABLISHED,RELATED -j ACCEPT",
                            iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+torSOCKSPort+" -j RETURN",
                            iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+torSOCKSPort+" -j RETURN",
                            iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+torHTTPTunnelPort+" -j RETURN",
                            iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+torHTTPTunnelPort+" -j RETURN",
                            iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+itpdSOCKSPort+" -j RETURN",
                            iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+itpdSOCKSPort+" -j RETURN",
                            iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+itpdHttpProxyPort+" -j RETURN",
                            iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+itpdHttpProxyPort+" -j RETURN",
                            iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+torTransPort+" -j RETURN",
                            iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+dnsCryptPort+" -m owner --uid-owner 0 -j ACCEPT",
                            iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+dnsCryptPort+" -m owner --uid-owner 0 -j ACCEPT",
                            iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+dnsCryptPort+" -j RETURN",
                            iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+dnsCryptPort+" -j RETURN",
                            iptablesPath+ "iptables -A tordnscrypt -m owner --uid-owner $TOR_UID -j ACCEPT",
                            blockHttpRuleFilterAll,
                            torSitesBypassFilterTCP,
                            torSitesBypassFilterUDP,
                            torAppsBypassFilterTCP,
                            torAppsBypassFilterUDP,
                            iptablesPath+ "iptables -A tordnscrypt -j REJECT",
                            iptablesPath+ "iptables -I OUTPUT -j tordnscrypt",
                            busyboxPath+ "sleep 10",
                            iptablesPath+ "iptables -t nat -D tordnscrypt_nat_output -p udp -d "+dnsCryptFallbackRes+" --dport 53 -j ACCEPT"
                    };
                }


                if (!rnDNSCryptWithRoot) {
                    runDNSCryptNoRoot();
                }
                if (!rnTorWithRoot) {
                    runTorNoRoot();
                }
                startRefreshTorUnlockIPs(context);
                if (tethering_autostart){
                    String[] commandsTether = tethering.activateTethering(false);
                    if (commandsTether!=null && commandsTether.length>0)
                        commandsStart = Arr.ADD2(commandsStart, commandsTether);
                }
            } else if (autoStartDNSCrypt && !autoStartTor && !autoStartITPD){

                new PrefManager(context).setBoolPref("DNSCrypt Running",true);
                new PrefManager(context).setBoolPref("Tor Running",false);
                new PrefManager(context).setBoolPref("I2PD Running",false);

                commandsStart = new String[] {
                        killallDNS, killallTor, killallITPD,
                        "ip6tables -D OUTPUT -j DROP || true",
                        "ip6tables -I OUTPUT -j DROP",
                        iptablesPath + "iptables -t nat -F tordnscrypt_nat_output",
                        iptablesPath + "iptables -t nat -D OUTPUT -j tordnscrypt_nat_output || true",
                        iptablesPath + "iptables -F tordnscrypt",
                        iptablesPath + "iptables -D OUTPUT -j tordnscrypt || true",
                        busyboxPath + "sleep 1",

                        busyboxPath+ "echo 'Beginning of log' > "+appDataDir+"/logs/DnsCrypt.log",
                        busyboxPath+ "sleep 1",
                        restoreUIDDNS,
                        restoreSEContextDNS,
                        busyboxPath+ "sleep 1",
                        startCommandDNSCrypt,
                        busyboxPath+ "sleep 1",
                        iptablesPath+ "iptables -t nat -N tordnscrypt_nat_output",
                        iptablesPath+ "iptables -t nat -I OUTPUT -j tordnscrypt_nat_output",
                        iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p tcp -d 127.0.0.1/32 -j RETURN",
                        iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p udp -d 127.0.0.1/32 -j RETURN",
                        iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p tcp -d 10.191.0.1 -j DNAT --to-destination 127.0.0.1:"+itpdHttpProxyPort,
                        iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p udp -d 10.191.0.1 -j DNAT --to-destination 127.0.0.1:"+itpdHttpProxyPort,
                        iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p udp -d "+dnsCryptFallbackRes+" --dport 53 -j ACCEPT",
                        iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p udp --dport 53 -j DNAT --to-destination 127.0.0.1:"+dnsCryptPort,
                        iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p tcp --dport 53 -j DNAT --to-destination 127.0.0.1:"+dnsCryptPort,
                        blockHttpRuleNatTCP,
                        blockHttpRuleNatUDP,
                        iptablesPath+ "iptables -N tordnscrypt",
                        iptablesPath+ "iptables -A tordnscrypt -m state --state ESTABLISHED,RELATED -j ACCEPT",
                        iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+dnsCryptPort+" -m owner --uid-owner 0 -j ACCEPT",
                        iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+dnsCryptPort+" -m owner --uid-owner 0 -j ACCEPT",
                        blockHttpRuleFilterAll,
                        iptablesPath+ "iptables -I OUTPUT -j tordnscrypt",
                        busyboxPath+ "sleep 10",
                        iptablesPath+ "iptables -t nat -D tordnscrypt_nat_output -p udp -d "+dnsCryptFallbackRes+" --dport 53 -j ACCEPT"
                };
                if (!rnDNSCryptWithRoot) {
                    runDNSCryptNoRoot();
                }
                stopRefreshTorUnlockIPs(context);
                if (tethering_autostart){
                    String[] commandsTether = tethering.activateTethering(false);
                    if (commandsTether!=null && commandsTether.length>0)
                        commandsStart = Arr.ADD2(commandsStart, commandsTether);
                }
            } else if(!autoStartDNSCrypt && autoStartTor && !autoStartITPD){

                new PrefManager(context).setBoolPref("DNSCrypt Running",false);
                new PrefManager(context).setBoolPref("Tor Running",true);
                new PrefManager(context).setBoolPref("I2PD Running",false);

                commandsStart = new String[] {
                        killallDNS, killallTor, killallITPD,
                        "ip6tables -D OUTPUT -j DROP || true",
                        "ip6tables -I OUTPUT -j DROP",
                        iptablesPath + "iptables -t nat -F tordnscrypt_nat_output",
                        iptablesPath + "iptables -t nat -D OUTPUT -j tordnscrypt_nat_output || true",
                        iptablesPath + "iptables -F tordnscrypt",
                        iptablesPath + "iptables -D OUTPUT -j tordnscrypt || true",
                        busyboxPath + "sleep 1",

                        busyboxPath+ "echo 'Beginning of log' > "+appDataDir+"/logs/Tor.log",
                        busyboxPath+ "sleep 1",
                        restoreUIDTor,
                        restoreSEContextTor,
                        busyboxPath+ "sleep 1",
                        startCommandTor,
                        busyboxPath+ "sleep 1",
                        "TOR_UID="+ appUIDTor,
                        iptablesPath+ "iptables -t nat -N tordnscrypt_nat_output",
                        iptablesPath+ "iptables -t nat -I OUTPUT -j tordnscrypt_nat_output",
                        iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p tcp -d 127.0.0.1/32 -j RETURN",
                        iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p udp -d 127.0.0.1/32 -j RETURN",
                        iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p udp --dport 53 -j DNAT --to-destination 127.0.0.1:"+ torDNSPort,
                        iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p tcp --dport 53 -j DNAT --to-destination 127.0.0.1:"+ torDNSPort,
                        iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -m owner --uid-owner $TOR_UID -j RETURN",
                        iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p tcp -d " + torVirtAdrNet + " -j DNAT --to-destination 127.0.0.1:" + torTransPort,
                        blockHttpRuleNatTCP,
                        blockHttpRuleNatUDP,
                        torSitesBypassNatTCP,
                        torSitesBypassNatUDP,
                        torAppsBypassNatTCP,
                        torAppsBypassNatUDP,
                        iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p tcp --syn -j DNAT --to-destination 127.0.0.1:"+torTransPort,
                        iptablesPath+ "iptables -N tordnscrypt",
                        iptablesPath+ "iptables -A tordnscrypt -m state --state ESTABLISHED,RELATED -j ACCEPT",
                        iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+torSOCKSPort+" -j RETURN",
                        iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+torSOCKSPort+" -j RETURN",
                        iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+torHTTPTunnelPort+" -j RETURN",
                        iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+torHTTPTunnelPort+" -j RETURN",
                        iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+itpdSOCKSPort+" -j RETURN",
                        iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+itpdSOCKSPort+" -j RETURN",
                        iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+itpdHttpProxyPort+" -j RETURN",
                        iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+itpdHttpProxyPort+" -j RETURN",
                        iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+torTransPort+" -j RETURN",
                        iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+torDNSPort+" -m owner --uid-owner 0 -j ACCEPT",
                        iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+torDNSPort+" -m owner --uid-owner 0 -j ACCEPT",
                        iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+torDNSPort+" -j RETURN",
                        iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+torDNSPort+" -j RETURN",
                        iptablesPath+ "iptables -A tordnscrypt -m owner --uid-owner $TOR_UID -j ACCEPT",
                        blockHttpRuleFilterAll,
                        torSitesBypassFilterTCP,
                        torSitesBypassFilterUDP,
                        torAppsBypassFilterTCP,
                        torAppsBypassFilterUDP,
                        iptablesPath+ "iptables -A tordnscrypt -j REJECT",
                        iptablesPath+ "iptables -I OUTPUT -j tordnscrypt"};

                if (!rnTorWithRoot) {
                    runTorNoRoot();
                }
                startRefreshTorUnlockIPs(context);
                if (tethering_autostart){
                    String[] commandsTether = tethering.activateTethering(true);
                    if (commandsTether!=null && commandsTether.length>0)
                        commandsStart = Arr.ADD2(commandsStart, commandsTether);
                }
            } else if(!autoStartDNSCrypt && !autoStartTor && autoStartITPD){

                new PrefManager(context).setBoolPref("DNSCrypt Running",false);
                new PrefManager(context).setBoolPref("Tor Running",false);
                new PrefManager(context).setBoolPref("I2PD Running",true);

                commandsStart = new String[] {
                        "ip6tables -I OUTPUT -j DROP",
                        busyboxPath+ "sleep 1",
                        restoreUIDITPD,
                        restoreSEContextITPD,
                        startCommandI2PD};
                if (!rnI2PDWithRoot) {
                    runITPDNoRoot();
                }
                stopRefreshTorUnlockIPs(context);
                if (tethering_autostart){
                    String[] commandsTether = tethering.activateTethering(false);
                    if (commandsTether!=null && commandsTether.length>0)
                        commandsStart = Arr.ADD2(commandsStart, commandsTether);
                }
            } else if(!autoStartDNSCrypt && autoStartTor){

                new PrefManager(context).setBoolPref("DNSCrypt Running",false);
                new PrefManager(context).setBoolPref("Tor Running",true);
                new PrefManager(context).setBoolPref("I2PD Running",true);

                commandsStart = new String[] {
                        killallDNS, killallTor, killallITPD,
                        "ip6tables -D OUTPUT -j DROP || true",
                        "ip6tables -I OUTPUT -j DROP",
                        iptablesPath + "iptables -t nat -F tordnscrypt_nat_output",
                        iptablesPath + "iptables -t nat -D OUTPUT -j tordnscrypt_nat_output || true",
                        iptablesPath + "iptables -F tordnscrypt",
                        iptablesPath + "iptables -D OUTPUT -j tordnscrypt || true",
                        busyboxPath + "sleep 1",

                        busyboxPath+ "echo 'Beginning of log' > "+appDataDir+"/logs/Tor.log",
                        busyboxPath+ "sleep 1",
                        restoreUIDTor,
                        restoreUIDITPD,
                        restoreSEContextTor,
                        restoreSEContextITPD,
                        busyboxPath+ "sleep 1",
                        startCommandTor,
                        startCommandI2PD,
                        busyboxPath+ "sleep 1",
                        "TOR_UID="+ appUIDTor,
                        iptablesPath+ "iptables -t nat -N tordnscrypt_nat_output",
                        iptablesPath+ "iptables -t nat -I OUTPUT -j tordnscrypt_nat_output",
                        iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p tcp -d 127.0.0.1/32 -j RETURN",
                        iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p udp -d 127.0.0.1/32 -j RETURN",
                        iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p udp --dport 53 -j DNAT --to-destination 127.0.0.1:"+ torDNSPort,
                        iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p tcp --dport 53 -j DNAT --to-destination 127.0.0.1:"+ torDNSPort,
                        iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -m owner --uid-owner $TOR_UID -j RETURN",
                        iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p tcp -d " + torVirtAdrNet + " -j DNAT --to-destination 127.0.0.1:" + torTransPort,
                        blockHttpRuleNatTCP,
                        blockHttpRuleNatUDP,
                        torSitesBypassNatTCP,
                        torSitesBypassNatUDP,
                        torAppsBypassNatTCP,
                        torAppsBypassNatUDP,
                        iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p tcp --syn -j DNAT --to-destination 127.0.0.1:"+torTransPort,
                        iptablesPath+ "iptables -N tordnscrypt",
                        iptablesPath+ "iptables -A tordnscrypt -m state --state ESTABLISHED,RELATED -j ACCEPT",
                        iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+torSOCKSPort+" -j RETURN",
                        iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+torSOCKSPort+" -j RETURN",
                        iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+torHTTPTunnelPort+" -j RETURN",
                        iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+torHTTPTunnelPort+" -j RETURN",
                        iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+itpdSOCKSPort+" -j RETURN",
                        iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+itpdSOCKSPort+" -j RETURN",
                        iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+itpdHttpProxyPort+" -j RETURN",
                        iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+itpdHttpProxyPort+" -j RETURN",
                        iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+torTransPort+" -j RETURN",
                        iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+torDNSPort+" -m owner --uid-owner 0 -j ACCEPT",
                        iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+torDNSPort+" -m owner --uid-owner 0 -j ACCEPT",
                        iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+torDNSPort+" -j RETURN",
                        iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+torDNSPort+" -j RETURN",
                        iptablesPath+ "iptables -A tordnscrypt -m owner --uid-owner $TOR_UID -j ACCEPT",
                        blockHttpRuleFilterAll,
                        torSitesBypassFilterTCP,
                        torSitesBypassFilterUDP,
                        torAppsBypassFilterTCP,
                        torAppsBypassFilterUDP,
                        iptablesPath+ "iptables -A tordnscrypt -j REJECT",
                        iptablesPath+ "iptables -I OUTPUT -j tordnscrypt"};

                if (!rnTorWithRoot) {
                    runTorNoRoot();
                }
                if (!rnI2PDWithRoot) {
                    runITPDNoRoot();
                }
                startRefreshTorUnlockIPs(context);
                if (tethering_autostart){
                    String[] commandsTether = tethering.activateTethering(true);
                    if (commandsTether!=null && commandsTether.length>0)
                        commandsStart = Arr.ADD2(commandsStart, commandsTether);
                }
            } else if(autoStartDNSCrypt && !autoStartTor){

                new PrefManager(context).setBoolPref("DNSCrypt Running",true);
                new PrefManager(context).setBoolPref("Tor Running",false);
                new PrefManager(context).setBoolPref("I2PD Running",true);

                commandsStart = new String[] {
                        killallDNS, killallTor, killallITPD,
                        "ip6tables -D OUTPUT -j DROP || true",
                        "ip6tables -I OUTPUT -j DROP",
                        iptablesPath + "iptables -t nat -F tordnscrypt_nat_output",
                        iptablesPath + "iptables -t nat -D OUTPUT -j tordnscrypt_nat_output || true",
                        iptablesPath + "iptables -F tordnscrypt",
                        iptablesPath + "iptables -D OUTPUT -j tordnscrypt || true",
                        busyboxPath + "sleep 1",

                        busyboxPath+ "echo 'Beginning of log' > "+appDataDir+"/logs/DnsCrypt.log",
                        busyboxPath+ "sleep 1",
                        restoreUIDDNS,
                        restoreUIDITPD,
                        restoreSEContextDNS,
                        restoreSEContextITPD,
                        busyboxPath+ "sleep 1",
                        startCommandDNSCrypt,
                        startCommandI2PD,
                        busyboxPath+ "sleep 1",
                        iptablesPath+ "iptables -t nat -N tordnscrypt_nat_output",
                        iptablesPath+ "iptables -t nat -I OUTPUT -j tordnscrypt_nat_output",
                        iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p tcp -d 127.0.0.1/32 -j RETURN",
                        iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p udp -d 127.0.0.1/32 -j RETURN",
                        iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p tcp -d 10.191.0.1 -j DNAT --to-destination 127.0.0.1:"+itpdHttpProxyPort,
                        iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p udp -d 10.191.0.1 -j DNAT --to-destination 127.0.0.1:"+itpdHttpProxyPort,
                        iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p udp -d "+dnsCryptFallbackRes+" --dport 53 -j ACCEPT",
                        iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p udp --dport 53 -j DNAT --to-destination 127.0.0.1:"+dnsCryptPort,
                        iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p tcp --dport 53 -j DNAT --to-destination 127.0.0.1:"+dnsCryptPort,
                        blockHttpRuleNatTCP,
                        blockHttpRuleNatUDP,
                        iptablesPath+ "iptables -N tordnscrypt",
                        iptablesPath+ "iptables -A tordnscrypt -m state --state ESTABLISHED,RELATED -j ACCEPT",
                        iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+dnsCryptPort+" -m owner --uid-owner 0 -j ACCEPT",
                        iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+dnsCryptPort+" -m owner --uid-owner 0 -j ACCEPT",
                        blockHttpRuleFilterAll,
                        iptablesPath+ "iptables -I OUTPUT -j tordnscrypt",
                        busyboxPath+ "sleep 10",
                        iptablesPath+ "iptables -t nat -D tordnscrypt_nat_output -p udp -d "+dnsCryptFallbackRes+" --dport 53 -j ACCEPT"
                };

                if (!rnDNSCryptWithRoot) {
                    runDNSCryptNoRoot();
                }

                if (!rnI2PDWithRoot) {
                    runITPDNoRoot();
                }
                stopRefreshTorUnlockIPs(context);
                if (tethering_autostart){
                    String[] commandsTether = tethering.activateTethering(false);
                    if (commandsTether!=null && commandsTether.length>0)
                        commandsStart = Arr.ADD2(commandsStart, commandsTether);
                }
            }

            if(commandsStart!=null && new PrefManager(this.context).getBoolPref("rootOK")){
                RootCommands rootCommands = new RootCommands(commandsStart);
                Intent intentCommands = new Intent(context, RootExecService.class);
                intentCommands.setAction(RootExecService.RUN_COMMAND);
                intentCommands.putExtra("Commands",rootCommands);
                intentCommands.putExtra("Mark", RootExecService.BootBroadcastMark);
                RootExecService.performAction(context,intentCommands);
            }
        }
    }

    private void runDNSCryptNoRoot() {
        SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(context);
        boolean showNotification = shPref.getBoolean("swShowNotification",true);
        Intent intent = new Intent(context, NoRootService.class);
        intent.setAction(NoRootService.actionStartDnsCrypt);
        intent.putExtra("showNotification",showNotification);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    private void runTorNoRoot() {
        SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(context);
        boolean showNotification = shPref.getBoolean("swShowNotification",true);
        Intent intent = new Intent(context, NoRootService.class);
        intent.setAction(NoRootService.actionStartTor);
        intent.putExtra("showNotification",showNotification);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    private void runITPDNoRoot() {
        SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(context);
        boolean showNotification = shPref.getBoolean("swShowNotification",true);
        Intent intent = new Intent(context, NoRootService.class);
        intent.setAction(NoRootService.actionStartITPD);
        intent.putExtra("showNotification",showNotification);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    private void startRefreshTorUnlockIPs(Context context) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.LOLLIPOP || refreshPeriodHours==0) {
            return;
        }
        ComponentName jobService = new ComponentName(context, GetIPsJobService.class);
        JobInfo.Builder getIPsJobBuilder = new JobInfo.Builder(mJobId, jobService);
        getIPsJobBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
        getIPsJobBuilder.setPeriodic(refreshPeriodHours*60*60*1000);

        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);

        if (jobScheduler != null) {
            jobScheduler.schedule(getIPsJobBuilder.build());
        }
    }

    private void stopRefreshTorUnlockIPs(Context context) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.LOLLIPOP) {
            return;
        }
        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (jobScheduler != null) {
            jobScheduler.cancel(mJobId);
        }
    }
}
