package pan.alexander.tordnscrypt.utils;

import androidx.annotation.Keep;

@Keep
public interface Constants {
    String LOOPBACK_ADDRESS = "127.0.0.1";
    String META_ADDRESS = "0.0.0.0";

    String STANDARD_WIFI_INTERFACE_NAME = "wlan0";
    String STANDARD_ETHERNET_INTERFACE_NAME = "eth0";
    String STANDARD_VPN_INTERFACE_NAME = "tun0";
    String STANDARD_USB_MODEM_INTERFACE_NAME = "rndis0";
    String STANDARD_AP_INTERFACE_RANGE = "192.168.43.";
    String EXTENDED_AP_INTERFACE_RANGE = "192.168.";
    String STANDARD_USB_MODEM_INTERFACE_RANGE = "192.168.42.";
    String STANDARD_VPN_ADDRESS = "10.1.10.1";
    String STANDARD_ADDRESS_LOCAL_PC = "192.168.0.100";

    String[] STANDARD_ETHERNET_INTERFACE_NAMES = {"eth+"};

    String[] STANDARD_WIFI_INTERFACE_NAMES = {"wlan+", "swlan+", "tiwlan+", "ra+", "bnep+"};

    String[] STANDARD_3G_INTERFACE_NAMES = {"rmnet+", "pdp+", "uwbr+", "wimax+", "vsnet+",
            "rmnet_sdio+", "ccmni+", "qmi+", "svnet0+", "ccemni+",
            "wwan+", "cdma_rmnet+", "clat4+", "cc2mni+", "bond1+", "rmnet_smux+", "ccinet+",
            "v4-rmnet+", "seth_w+", "v4-rmnet_data+", "rmnet_ipa+", "rmnet_data+", "r_rmnet_data+"};

    String[] STANDARD_USB_INTERFACE_TETHER_NAMES = {"bt-pan", "usb+", "rndis+", "rmnet_usb+"};

    int HTTP_PORT = 80;

    int DNS_OVER_TLS_PORT = 853;

    int PLAINTEXT_DNS_PORT = 53;

    int ROOT_DEFAULT_UID = 0;
    int DNS_DEFAULT_UID = 1051;
    int NETWORK_STACK_DEFAULT_UID = 1073;

    String VPN_DNS_2 = "89.233.43.71";//blog.uncensoreddns.org

    String G_DNG_41 = "8.8.8.8";
    String G_DNS_42 = "8.8.4.4";
    String G_DNS_61 = "2001:4860:4860::8888";
    String G_DNS_62 = "2001:4860:4860::8844";
    String DNS_GOOGLE = "https://dns.google";
    String DNS_QUAD9 = "https://dns9.quad9.net";
    String DNS_MOZILLA = "https://mozilla.cloudflare-dns.com";

    String QUAD_DNS_41 = "9.9.9.9";
    String QUAD_DNS_42 = "149.112.112.112";
    String QUAD_DNS_61 = "2620:fe::fe";
    String QUAD_DNS_62 = "2620:fe::9";

    String QUAD_DOH_SERVER = "https://dns.quad9.net/dns-query";

    String TOR_BROWSER_USER_AGENT = "Mozilla/5.0 (Windows NT 6.1; rv:60.0) Gecko/20100101 Firefox/60.0";
    String TOR_SITE_ADDRESS = "https://www.torproject.org/";

    String ONIONOO_SITE_ADDRESS = "https://onionoo.torproject.org/";

    String TOR_BRIDGES_ADDRESS = "https://bridges.torproject.org/";

    int SITES_IPS_REFRESH_JOB_ID = 1;
    int DEFAULT_SITES_IPS_REFRESH_INTERVAL = 12;

    String IPv4_REGEX = "^[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}$";

    String IPv6_REGEX = "^(([0-9a-fA-F]{1,4}:){7,7}[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,7}:|([0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,5}(:[0-9a-fA-F]{1,4}){1,2}|([0-9a-fA-F]{1,4}:){1,4}(:[0-9a-fA-F]{1,4}){1,3}|([0-9a-fA-F]{1,4}:){1,3}(:[0-9a-fA-F]{1,4}){1,4}|([0-9a-fA-F]{1,4}:){1,2}(:[0-9a-fA-F]{1,4}){1,5}|[0-9a-fA-F]{1,4}:((:[0-9a-fA-F]{1,4}){1,6})|:((:[0-9a-fA-F]{1,4}){1,7}|:)|fe80:(:[0-9a-fA-F]{0,4}){0,4}%[0-9a-zA-Z]{1,}|::(ffff(:0{1,4}){0,1}:){0,1}((25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])\\.){3,3}(25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])|([0-9a-fA-F]{1,4}:){1,4}:((25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])\\.){3,3}(25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9]))$";

    String NUMBER_REGEX = "\\d+";
}
