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

    Copyright 2019-2024 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.vpn;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.annotation.Keep;

import java.io.File;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import pan.alexander.tordnscrypt.App;
import pan.alexander.tordnscrypt.vpn.service.ServiceVPN;

import static pan.alexander.tordnscrypt.utils.Constants.LOOPBACK_ADDRESS_IPv6;
import static pan.alexander.tordnscrypt.utils.Constants.META_ADDRESS;
import static pan.alexander.tordnscrypt.utils.Constants.META_ADDRESS_IPv6;
import static pan.alexander.tordnscrypt.utils.logger.Logger.loge;
import static pan.alexander.tordnscrypt.utils.logger.Logger.logi;
import static pan.alexander.tordnscrypt.utils.logger.Logger.logw;

public class VpnUtils {

    public static final ArrayList<String> nonTorList = new ArrayList<>(Arrays.asList(
            /*LAN destinations that shouldn't be routed through Tor*/
            "127.0.0.0/8", //Loopback RFC1122
            "10.0.0.0/8", //Private-Use RFC1918
            "172.16.0.0/12", //Private-Use RFC1918
            "192.168.0.0/16", //Private-Use RFC1918
            /*Other IANA reserved blocks (These are not processed by tor)*/
            META_ADDRESS,
            "100.64.0.0/10", //Shared Address Space(CGNAT) RFC6598
            "169.254.0.0/16", //Link local RFC3927
            "192.0.0.0/24", //IETF Protocol Assignments RFC6890
            "192.0.2.0/24", //Documentation(TEST-NET-1) RFC5737
            "192.88.99.0/24", //6to4 Relay Anycast RFC3068
            "198.18.0.0/15", //Benchmarking RFC2544
            "198.51.100.0/24", //Documentation(TEST-NET-2) RFC5737
            "203.0.113.0/24", //Documentation(TEST-NET-3) RFC5737
            "224.0.0.0/4", //Multicast RFC 3171
            "240.0.0.0/4", //Class E address reserved RFC1112
            "255.255.255.255/32" //	Limited Broadcast RFC0919
    ));

    public static final ArrayList<String> nonTorIPv6 = new ArrayList<>(Arrays.asList(
            /*LAN destinations that shouldn't be routed through Tor*/
            //https://www.rfc-editor.org/rfc/rfc3513.html
            LOOPBACK_ADDRESS_IPv6, //Loopback Address RFC4291
            META_ADDRESS_IPv6, //Unspecified Address RFC4291
            "FEC0::/10", //Site-local unicast, equivalent to 10.0.0.0/8 RFC3513
            "FE80::/10", //Link-local unicast, equivalent to 169.254.0.0/16 RFC4291
            "FD00::/8" //Unique local address RFC4193
    ));

    public static final ArrayList<String> multicastIPv6 = new ArrayList<>(Arrays.asList(
            //https://www.rfc-editor.org/rfc/rfc3513.html
            //"FF00::/8" Multicast
            "FF01::1", //All Nodes Addresses interface-local
            "FF02::1", // All Nodes Addresses link-local
            "FF01::2", // All Routers Addresses interface-local
            "FF02::2", // All Routers Addresses link-local, SLAAC
            "FF05::2", // All Routers Addresses site-local
            "FF02::1:FF00:0/104", //Neighbor discovery
            //https://source.android.com/docs/core/ota/modular-system/dns-resolver
            "FF02::FB", //mDNS .local resolution A12
            //https://datatracker.ietf.org/doc/html/rfc8415
            "FF02::1:2", //All_DHCP_Relay_Agents_and_Servers
            "FF05::1:3" //All_DHCP_Servers
    ));

    public static final ArrayList<String> dnsRebindList = new ArrayList<>(Arrays.asList(
            "10.0.0.0/8",
            "172.16.0.0/12",
            "192.168.0.0/16",
            "100.64.0.0/10"
    ));

    @Keep
    private static native String jni_getprop(String name);

    @Keep
    private static native boolean is_numeric_address(String ip);

    public static String getSelfVersionName(Context context) {
        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return pInfo.versionName;
        } catch (PackageManager.NameNotFoundException ex) {
            return ex.toString();
        }
    }

    public static int getSelfVersionCode(Context context) {
        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return pInfo.versionCode;
        } catch (PackageManager.NameNotFoundException ex) {
            return -1;
        }
    }

    public static List<String> getDefaultDNS(Context context) {
        List<String> listDns = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network an = null;
            if (cm != null) {
                an = cm.getActiveNetwork();
            }
            if (an != null) {
                LinkProperties lp = cm.getLinkProperties(an);
                if (lp != null) {
                    List<InetAddress> dns = lp.getDnsServers();
                    for (InetAddress d : dns) {
                        String host = d.getHostAddress();
                        if (host != null) {
                            logi("DNS from LP: " + host);
                            listDns.add(host.split("%")[0]);
                        }
                    }
                }
            }
        } else {
            String dns1 = jni_getprop("net.dns1");
            String dns2 = jni_getprop("net.dns2");
            if (dns1 != null)
                listDns.add(dns1.split("%")[0]);
            if (dns2 != null)
                listDns.add(dns2.split("%")[0]);
        }

        return listDns;
    }

    static boolean isNumericAddress(String ip) {
        return is_numeric_address(ip);
    }

    static boolean isSystem(String packageName, Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo info = pm.getPackageInfo(packageName, 0);
            return ((info.applicationInfo.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0);
        } catch (PackageManager.NameNotFoundException ignore) {
            return false;
        }
    }

    static boolean hasInternet(String packageName, Context context) {
        PackageManager pm = context.getPackageManager();
        return (pm.checkPermission("android.permission.INTERNET", packageName) == PackageManager.PERMISSION_GRANTED);
    }

    static boolean isEnabled(PackageInfo info, Context context) {
        int setting;
        try {
            PackageManager pm = context.getPackageManager();
            setting = pm.getApplicationEnabledSetting(info.packageName);
        } catch (IllegalArgumentException ex) {
            setting = PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
            logw("VpnUtils isEnabled", ex);
        }
        if (setting == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
            return info.applicationInfo.enabled;
        else
            return (setting == PackageManager.COMPONENT_ENABLED_STATE_ENABLED);
    }

    public static void canFilterAsynchronous(ServiceVPN serviceVPN) {

        App.getInstance().getDaggerComponent()
                .getCoroutineExecutor().submit("VpnUtils canFilterAsynchronous", () -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && serviceVPN != null) {
                serviceVPN.canFilter = true;
                return null;
            }

            // https://android-review.googlesource.com/#/c/206710/1/untrusted_app.te
            File tcp = new File("/proc/net/tcp");
            File tcp6 = new File("/proc/net/tcp6");

            try {
                if (tcp.exists() && tcp.canRead() && serviceVPN != null)
                    serviceVPN.canFilter = true;
                return null;
            } catch (SecurityException ignored) {
            }

            try {
                if (tcp6.exists() && tcp6.canRead() && serviceVPN != null) {
                    serviceVPN.canFilter = true;
                }
            } catch (SecurityException ignored) {
                if (serviceVPN != null) {
                    serviceVPN.canFilter = false;
                }
            }
            return null;
        });
    }

    public static boolean canFilter() {
        // https://android-review.googlesource.com/#/c/206710/1/untrusted_app.te
        File tcp = new File("/proc/net/tcp");
        File tcp6 = new File("/proc/net/tcp6");
        try {
            if (tcp.exists() && tcp.canRead())
                return true;
        } catch (SecurityException ignored) {
        }
        try {
            return (tcp6.exists() && tcp6.canRead());
        } catch (SecurityException ignored) {
            return false;
        }
    }

    public static final int PRIVATE_DNS_MODE_OFF = 1;
    public static final int PRIVATE_DNS_MODE_OPPORTUNISTIC = 2;
    public static final int PRIVATE_DNS_MODE_PROVIDER_HOSTNAME = 3;
    public static final String PRIVATE_DNS_DEFAULT_MODE = "private_dns_default_mode";
    public static final String PRIVATE_DNS_MODE = "private_dns_mode";

    public static int getPrivateDnsMode(Context context) {
        try {
            final ContentResolver cr = context.getContentResolver();
            String mode = Settings.Global.getString(cr, PRIVATE_DNS_MODE);
            if (TextUtils.isEmpty(mode)) mode = Settings.Global.getString(cr, PRIVATE_DNS_DEFAULT_MODE);
            return getPrivateDnsModeAsInt(mode);
        } catch (Exception e) {
            loge("VpnUtils getPrivateDnsMode", e);
        }
        return PRIVATE_DNS_MODE_OFF;
    }
    private static int getPrivateDnsModeAsInt(String mode) {
        if (TextUtils.isEmpty(mode))
            return PRIVATE_DNS_MODE_OFF;
        return switch (mode) {
            case "hostname" -> PRIVATE_DNS_MODE_PROVIDER_HOSTNAME;
            case "opportunistic" -> PRIVATE_DNS_MODE_OPPORTUNISTIC;
            default -> PRIVATE_DNS_MODE_OFF;
        };
    }

    public static boolean isIpInSubnetOld(final String ip, final String network) {
        boolean result = false;

        try {
            String net = network;
            int prefix = 0;
            if (network.contains("/")) {
                net = network.substring(0, network.indexOf("/"));
                prefix = Integer.parseInt(network.substring(network.indexOf("/") + 1));
            }

            final byte[] ipBin = java.net.InetAddress.getByName(ip).getAddress();
            final byte[] netBin = java.net.InetAddress.getByName(net).getAddress();
            if (ipBin.length != netBin.length) return false;
            int p = prefix;
            int i = 0;
            while (p >= 8) {
                if (ipBin[i] != netBin[i]) return false;
                ++i;
                p -= 8;
            }
            final int m = (65280 >> p) & 255;
            result = (ipBin[i] & m) == (netBin[i] & m);
        } catch (Exception e) {
            loge("VpnUtils isIpInSubnet", e);
        }

        return result;
    }

    public static boolean isIpInSubnet(final String ip, final String network) {
        try {
            String net = network;
            int prefix = -1;
            if (network.contains("/")) {
                net = network.substring(0, network.indexOf("/"));
                prefix = Integer.parseInt(network.substring(network.indexOf("/") + 1));
            }

            if (prefix < 0) {
                return ip.toLowerCase(Locale.ROOT).equals(net.toLowerCase(Locale.ROOT));
            }

            final byte[] ipBin = java.net.InetAddress.getByName(ip).getAddress();
            final byte[] netBin = java.net.InetAddress.getByName(net).getAddress();
            if (netBin.length * 8 < prefix) {
                loge(String.format(
                        Locale.ROOT,
                        "IP address %s is too short for bitmask of length %d",
                        network,
                        prefix)
                );
                return false;
            }

            if (ipBin.length != netBin.length) return false;

            int nMaskFullBytes = prefix / 8;
            byte finalByte = (byte) (0xFF00 >> (prefix & 0x07));

            for (int i = 0; i < nMaskFullBytes; i++) {
                if (ipBin[i] != netBin[i]) {
                    return false;
                }
            }

            if (finalByte != 0) {
                return (ipBin[nMaskFullBytes] & finalByte) == (netBin[nMaskFullBytes] & finalByte);
            }

            return true;

        } catch (Exception e) {
            loge("VpnUtils isIpInSubnet", e);
        }

        return false;
    }

    public static boolean isIpInLanRange(String destAddress) {

        if (destAddress.isEmpty()) {
            return false;
        }

        for (String address : nonTorList) {
            if (isIpInSubnet(destAddress, address)) {
                return true;
            }
        }
        for (String address : nonTorIPv6) {
            if (isIpInSubnet(destAddress, address)) {
                return true;
            }
        }

        for (String address : multicastIPv6) {
            if (isIpInSubnet(destAddress, address)) {
                return true;
            }
        }
        return false;
    }
}
