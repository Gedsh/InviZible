package pan.alexander.tordnscrypt.utils.ap;

import static pan.alexander.tordnscrypt.utils.root.RootExecService.LOG_TAG;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.util.Log;
import android.util.Pair;

import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

import pan.alexander.tordnscrypt.App;
import pan.alexander.tordnscrypt.utils.Constants;
import pan.alexander.tordnscrypt.utils.enums.AccessPointState;

public class InternetSharingChecker {

    private static final String wifiAPAddressesRangeExtended = "192.168.0.0/16";

    private String wifiAPAddressesRange = "192.168.43.0/24";
    private String usbModemAddressesRange = "192.168.42.0/24";

    private boolean apIsOn = false;
    private boolean usbTetherOn = false;
    private boolean ethernetOn = false;

    private String vpnInterfaceName = Constants.STANDARD_VPN_INTERFACE_NAME;
    private String wifiAPInterfaceName = Constants.STANDARD_WIFI_INTERFACE_NAME;
    private String usbModemInterfaceName = Constants.STANDARD_USB_MODEM_INTERFACE_NAME;
    private String ethernetInterfaceName = Constants.STANDARD_ETHERNET_INTERFACE_NAME;

    public void updateData() {

        Pair<String, String> wifiInterfaceNameToAddressExtended = null;
        boolean gsmInternetIsUp = false;
        Pair<String, String> wifiInterfaceNameToAddressFuzzy = null;

        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
                 en.hasMoreElements(); ) {

                NetworkInterface intf = en.nextElement();

                if (intf.isLoopback()) {
                    continue;
                }
                if (intf.isVirtual()) {
                    continue;
                }
                if (!intf.isUp()) {
                    continue;
                }

                setVpnInterfaceName(intf);

                if (intf.isPointToPoint()) {
                    continue;
                }
                if (intf.getHardwareAddress() == null) {
                    continue;
                }

                if (!ethernetOn) {
                    checkEthernetAvailable(intf);
                }

                if (!apIsOn) {
                    checkWiFiAccessPointAvailableStandard(intf);
                }
                if (wifiInterfaceNameToAddressExtended == null) {
                    wifiInterfaceNameToAddressExtended = checkWiFiAccessPointAvailableExtended(intf);
                }
                if (!gsmInternetIsUp) {
                    gsmInternetIsUp = check3GIsUp(intf);
                }
                if (wifiInterfaceNameToAddressFuzzy == null) {
                    wifiInterfaceNameToAddressFuzzy = checkWiFiIsUp(intf);
                }

                if (!usbTetherOn) {
                    checkUsbModemAvailableStandard(intf);
                }
                if (!usbTetherOn) {
                    checkUsbModemAvailableExtended(intf);
                }

            }

            boolean performExtendedCheck = false;
            if (!apIsOn) {
                performExtendedCheck = checkApOnWithReflection() != AccessPointState.STATE_OFF;
            }

            if (!apIsOn && performExtendedCheck && wifiInterfaceNameToAddressExtended != null) {
                apIsOn = true;
                wifiAPInterfaceName = wifiInterfaceNameToAddressExtended.first;
                wifiAPAddressesRange = wifiInterfaceNameToAddressExtended.second;
            }

            if (!apIsOn && performExtendedCheck && gsmInternetIsUp
                    && wifiInterfaceNameToAddressFuzzy != null) {
                apIsOn = true;
                wifiAPInterfaceName = wifiInterfaceNameToAddressFuzzy.first;
                wifiAPAddressesRange = wifiInterfaceNameToAddressFuzzy.second;
            }

            String logEntry = "WiFi Access point is " + (apIsOn ? "ON" : "OFF") + "\n" +
                    "WiFi AP interface name " + wifiAPInterfaceName + "\n" +
                    "WiFi AP addresses range " + wifiAPAddressesRange + "\n" +
                    "USB modem is " + (usbTetherOn ? "ON" : "OFF") + "\n" +
                    "USB modem interface name " + usbModemInterfaceName + "\n" +
                    "USB modem addresses range " + usbModemAddressesRange;
            Log.i(LOG_TAG, logEntry);

        } catch (SocketException e) {
            Log.e(LOG_TAG, "Tethering SocketException " + e.getMessage() + " " + e.getCause());
        }
    }

    private void checkEthernetAvailable(NetworkInterface networkInterface) {
        String interfaceName = networkInterface.getName();
        for (String name : Constants.STANDARD_ETHERNET_INTERFACE_NAMES) {
            if (interfaceName.matches(name.replace("+", "\\d+"))) {
                ethernetOn = true;
                ethernetInterfaceName = networkInterface.getName();
                Log.i(LOG_TAG, "LAN interface name " + ethernetInterfaceName);
                break;
            }
        }
    }

    private void checkWiFiAccessPointAvailableStandard(NetworkInterface networkInterface) {
        for (Enumeration<InetAddress> enumIpAddr = networkInterface.getInetAddresses();
             enumIpAddr.hasMoreElements(); ) {
            InetAddress inetAddress = enumIpAddr.nextElement();
            String hostAddress = inetAddress.getHostAddress();

            if (hostAddress.contains(Constants.STANDARD_AP_INTERFACE_RANGE)) {
                apIsOn = true;
                wifiAPInterfaceName = networkInterface.getName();
                Log.i(LOG_TAG, "WiFi AP interface name " + wifiAPInterfaceName);
                return;
            }
        }
    }

    private Pair<String, String> checkWiFiAccessPointAvailableExtended(NetworkInterface networkInterface) {

        String interfaceName = networkInterface.getName();
        if (interfaceName.equals(Constants.STANDARD_WIFI_INTERFACE_NAME)
                || interfaceName.equals(Constants.STANDARD_ETHERNET_INTERFACE_NAME)) {
            return null;
        }

        for (Enumeration<InetAddress> enumIpAddr = networkInterface.getInetAddresses();
             enumIpAddr.hasMoreElements(); ) {
            InetAddress inetAddress = enumIpAddr.nextElement();
            String hostAddress = inetAddress.getHostAddress();

            if (hostAddress.contains(Constants.EXTENDED_AP_INTERFACE_RANGE)) {
                return new Pair<>(networkInterface.getName(), wifiAPAddressesRangeExtended);
            }
        }
        return null;
    }

    private boolean check3GIsUp(NetworkInterface networkInterface) {
        String interfaceName = networkInterface.getName();
        for (String interfaceName3g : Constants.STANDARD_3G_INTERFACE_NAMES) {
            if (interfaceName.matches(interfaceName3g.replace("+", "\\d+"))) {
                return true;
            }
        }
        return false;
    }

    private Pair<String, String> checkWiFiIsUp(NetworkInterface networkInterface) {
        String interfaceName = networkInterface.getName();
        for (String interfaceNameWiFi : Constants.STANDARD_WIFI_INTERFACE_NAMES) {
            if (interfaceName.matches(interfaceNameWiFi.replace("+", "\\d+"))) {
                for (Enumeration<InetAddress> enumIpAddr = networkInterface.getInetAddresses();
                     enumIpAddr.hasMoreElements(); ) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    String hostAddress = inetAddress.getHostAddress();

                    if (isNotIPv6Address(hostAddress)) {
                        return new Pair<>(interfaceName, hostAddress);
                    }
                }
                return null;
            }
        }
        return null;
    }

    private void checkUsbModemAvailableStandard(NetworkInterface networkInterface) {
        for (Enumeration<InetAddress> enumIpAddr = networkInterface.getInetAddresses();
             enumIpAddr.hasMoreElements(); ) {
            InetAddress inetAddress = enumIpAddr.nextElement();
            String hostAddress = inetAddress.getHostAddress();

            if (hostAddress.contains(Constants.STANDARD_USB_MODEM_INTERFACE_RANGE)) {
                usbTetherOn = true;
                usbModemInterfaceName = networkInterface.getName();
                Log.i(LOG_TAG, "USB Modem interface name " + usbModemInterfaceName);
                return;
            }
        }
    }

    private void checkUsbModemAvailableExtended(NetworkInterface networkInterface) {
        String interfaceName = networkInterface.getName();
        for (String name : Constants.STANDARD_USB_INTERFACE_TETHER_NAMES) {
            if (interfaceName.matches(name.replace("+", "\\d+"))) {
                usbTetherOn = true;
                usbModemInterfaceName = networkInterface.getName();
                Log.i(LOG_TAG, "USB Modem interface name " + usbModemInterfaceName);

                for (Enumeration<InetAddress> enumIpAddr = networkInterface.getInetAddresses();
                     enumIpAddr.hasMoreElements(); ) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    String hostAddress = inetAddress.getHostAddress();

                    if (isNotIPv6Address(hostAddress)) {
                        usbModemAddressesRange = hostAddress;
                        Log.i(LOG_TAG, "USB Modem addresses range " + usbModemAddressesRange);
                        return;
                    }
                }
                return;
            }
        }
    }

    private boolean isNotIPv6Address(String address) {
        return !address.contains(":");
    }

    private void setVpnInterfaceName(NetworkInterface intf) throws SocketException {

        if (!intf.isPointToPoint()) {
            return;
        }

        for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses();
             enumIpAddr.hasMoreElements(); ) {
            InetAddress inetAddress = enumIpAddr.nextElement();
            String hostAddress = inetAddress.getHostAddress();

            if (hostAddress.contains(Constants.STANDARD_VPN_ADDRESS)) {
                vpnInterfaceName = intf.getName();
                Log.i(LOG_TAG, "VPN interface name " + vpnInterfaceName);
            }
        }
    }

    public int checkApOnWithReflection() {
        Context context = App.Companion.getInstance();

        int result = AccessPointState.STATE_UNKNOWN;

        if (context == null) {
            return result;
        }

        try {
            WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            Method method = null;
            if (wifiManager != null) {
                method = wifiManager.getClass().getDeclaredMethod("isWifiApEnabled");
                method.setAccessible(true);
            }

            if (method != null) {
                Object on = method.invoke(wifiManager);
                if (on != null && (Boolean) on) {
                    result = AccessPointState.STATE_ON;
                } else {
                    result = AccessPointState.STATE_OFF;
                }
            }
        } catch (Exception e) {
            Log.w(LOG_TAG, "InternetSharingChecker checkApOn exception", e);
        }

        return result;
    }

    public boolean isApOn() {
        return apIsOn;
    }

    public boolean isUsbTetherOn() {
        return usbTetherOn;
    }

    public boolean isEthernetOn() {
        return ethernetOn;
    }

    public String getWifiAPAddressesRange() {
        return wifiAPAddressesRange;
    }

    public String getUsbModemAddressesRange() {
        return usbModemAddressesRange;
    }

    public String getVpnInterfaceName() {
        return vpnInterfaceName;
    }

    public String getWifiAPInterfaceName() {
        return wifiAPInterfaceName;
    }

    public String getUsbModemInterfaceName() {
        return usbModemInterfaceName;
    }

    public String getEthernetInterfaceName() {
        return ethernetInterfaceName;
    }

}
