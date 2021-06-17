package pan.alexander.tordnscrypt.utils;

public interface Constants {
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

    String[] STANDARD_WIFI_INTERFACE_NAMES = {"eth+", "wlan+", "tiwlan+", "ra+", "bnep+"};

    String[] STANDARD_3G_INTERFACE_NAMES = {"rmnet+", "pdp+", "uwbr+", "wimax+", "vsnet+",
            "rmnet_sdio+", "ccmni+", "qmi+", "svnet0+", "ccemni+",
            "wwan+", "cdma_rmnet+", "clat4+", "cc2mni+", "bond1+", "rmnet_smux+", "ccinet+",
            "v4-rmnet+", "seth_w+", "v4-rmnet_data+", "rmnet_ipa+", "rmnet_data+", "r_rmnet_data+"};

    String[] STANDARD_USB_INTERFACE_TETHER_NAMES = {"bt-pan", "usb+", "rndis+", "rmnet_usb+"};
}
