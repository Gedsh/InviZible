package pan.alexander.tordnscrypt.utils.preferences;

public interface PreferenceKeys {
    String WIFI_ACCESS_POINT_IS_ON = "APisON";
    String USB_MODEM_IS_ON = "ModemIsON";
    String IGNORE_SYSTEM_DNS = "ignore_system_dns";
    String DO_NOT_SHOW_IGNORE_BATTERY_OPTIMIZATION_DIALOG = "DoNotShowIgnoreBatteryOptimizationDialog";

    String DNSCRYPT_READY_PREF = "DNSCrypt Ready";
    String TOR_READY_PREF = "Tor Ready";
    String ITPD_READY_PREF = "ITPD Ready";

    String SAVED_DNSCRYPT_STATE_PREF = "savedDNSCryptState";
    String SAVED_TOR_STATE_PREF = "savedTorState";
    String SAVED_ITPD_STATE_PREF = "savedITPDState";

    String CHILD_LOCK_PASSWORD = "passwd";

    String ROOT_IS_AVAILABLE = "rootIsAvailable";

    String OPERATION_MODE = "OPERATION_MODE";

    String DEFAULT_BRIDGES_OBFS = "defaultBridgesObfs";
    String OWN_BRIDGES_OBFS = "ownBridgesObfs";
    String RELAY_BRIDGES_REQUESTED = "relayBridgesRequested";

    String IPS_TO_UNLOCK = "ipsToUnlock";
    String IPS_FOR_CLEARNET = "ipsForClearNet";
    String IPS_TO_UNLOCK_TETHER = "ipsToUnlockTether";
    String IPS_FOR_CLEARNET_TETHER = "ipsForClearNetTether";

    String TILES_LIMIT_DIALOG_NOT_SHOW = "tilesLimitDialogNotShow";

    String ARP_SPOOFING_NOT_SUPPORTED = "arpSpoofingNotSupported";

    String FIREWALL_WAS_STARTED = "FirewallWasStarted";
    String FIREWALL_ENABLED = "FirewallEnabled";
    String APPS_ALLOW_LAN_PREF = "appsAllowLan";
    String APPS_ALLOW_WIFI_PREF = "appsAllowWifi";
    String APPS_ALLOW_GSM_PREF = "appsAllowGsm";
    String APPS_ALLOW_ROAMING = "appsAllowRoaming";
    String APPS_ALLOW_VPN = "appsAllowVpn";
    String APPS_NEWLY_INSTALLED = "appsNewlyInstalled";

    String WIFI_ON_REQUESTED = "wifiOnRequested";
    String GSM_ON_REQUESTED = "gsmOnRequested";

    String MAIN_ACTIVITY_RECREATE = "refresh_main_activity";

    String USE_NO_BRIDGES = "useNoBridges";
    String USE_DEFAULT_BRIDGES = "useDefaultBridges";
    String USE_OWN_BRIDGES = "useOwnBridges";

    //VPN
    String VPN_SERVICE_ENABLED = "VPNServiceEnabled";

    //Fast Settings
    String SITES_IPS_REFRESH_INTERVAL = "pref_fast_site_refresh_interval";
    String BLOCK_HTTP = "pref_fast_block_http";
    String ALL_THROUGH_TOR = "pref_fast_all_through_tor";
    String BYPASS_LAN = "Allow LAN";
    String AUTO_START_DELAY = "pref_fast_autostart_delay";

    //Common Settings
    String ARP_SPOOFING_DETECTION = "pref_common_arp_spoofing_detection";
    String ARP_SPOOFING_BLOCK_INTERNET = "pref_common_arp_block_internet";
    String ALWAYS_SHOW_HELP_MESSAGES = "pref_common_show_help";
    String RUN_MODULES_WITH_ROOT = "swUseModulesRoot";
    String FIX_TTL = "pref_common_fix_ttl";
    String TOR_TETHERING = "pref_common_tor_tethering";
    String ITPD_TETHERING = "pref_common_itpd_tethering";
    String COMPATIBILITY_MODE = "swCompatibilityMode";
    String DNS_REBIND_PROTECTION = "pref_common_dns_rebind_protection";
    String USE_PROXY = "swUseProxy";
    String PROXY_ADDRESS = "ProxyServer";
    String PROXY_PORT = "ProxyPort";
    String MULTI_USER_SUPPORT = "pref_common_multi_user";
    String REFRESH_RULES = "swRefreshRules";
    String KILL_SWITCH = "swKillSwitch";
    String USE_IPTABLES = "pref_common_use_iptables";

    //DNSCrypt Settings
    String BLOCK_IPv6 = "block_ipv6";

    //Tor Settings
    String SNOWFLAKE_RENDEZVOUS = "SnowflakeRendezvous";

    //Firewall Settings
    String FIREWALL_SHOWS_ALL_APPS = "FirewallShowsAllApps";

    //Logs
    String SAVE_ROOT_LOGS = "swRootCommandsLog";
}
