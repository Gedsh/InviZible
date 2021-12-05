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

    String IPS_TO_UNLOCK = "ipsToUnlock";
    String IPS_FOR_CLEARNET = "ipsForClearNet";
    String IPS_TO_UNLOCK_TETHER = "ipsToUnlockTether";
    String IPS_FOR_CLEARNET_TETHER = "ipsForClearNetTether";

    String TILES_LIMIT_DIALOG_NOT_SHOW = "tilesLimitDialogNotShow";

    String ARP_SPOOFING_NOT_SUPPORTED = "arpSpoofingNotSupported";

    //VPN
    String VPN_SERVICE_ENABLED = "VPNServiceEnabled";

    //Fast Settings
    String SITES_IPS_REFRESH_INTERVAL = "pref_fast_site_refresh_interval";

    //Common Settings
    String ARP_SPOOFING_DETECTION = "pref_common_arp_spoofing_detection";
    String ARP_SPOOFING_BLOCK_INTERNET = "pref_common_arp_block_internet";
    String ALWAYS_SHOW_HELP_MESSAGES = "pref_common_show_help";
    String RUN_MODULES_WITH_ROOT = "swUseModulesRoot";
    String FIX_TTL = "pref_common_fix_ttl";
}
