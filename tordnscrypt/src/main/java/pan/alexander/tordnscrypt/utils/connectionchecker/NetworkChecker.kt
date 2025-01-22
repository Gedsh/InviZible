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

    Copyright 2019-2025 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.utils.connectionchecker

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import androidx.core.net.ConnectivityManagerCompat
import pan.alexander.tordnscrypt.modules.ModulesStatus
import pan.alexander.tordnscrypt.utils.enums.OperationMode
import pan.alexander.tordnscrypt.utils.logger.Logger.loge
import java.util.TreeMap

private const val DEFAULT_MTU = 1400
private val MTU_REGEX = Regex("\\d{4}")

@Suppress("deprecation")
object NetworkChecker {

    @JvmStatic
    fun isNetworkAvailable(context: Context): Boolean =
        try {
            val connectivityManager = context.getConnectivityManager()
            var capabilities: NetworkCapabilities? = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && connectivityManager != null) {
                capabilities = connectivityManager.getNetworkCapabilities(
                    connectivityManager.activeNetwork
                )
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && capabilities != null && !isRootMode()) {
                hasActiveTransport(capabilities)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && connectivityManager != null) {
                connectivityManager.allNetworks.let {
                    for (network in it) {
                        val networkCapabilities =
                            connectivityManager.getNetworkCapabilities(network)
                        if (networkCapabilities != null && hasActiveTransport(networkCapabilities)) {
                            return true
                        }
                    }
                    return connectivityManager.activeNetworkInfo?.isConnected ?: false
                }

            } else {
                connectivityManager?.let { it.activeNetworkInfo?.isConnected ?: false } ?: true
            }
        } catch (e: Exception) {
            loge("NetworkChecker isNetworkAvailable", e)
            false
        }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun hasActiveTransport(capabilities: NetworkCapabilities): Boolean =
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) &&
                (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                        && isCellularInternetMayBeAvailable(capabilities)
                        || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                        || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))


    @JvmStatic
    @JvmOverloads
    fun isCellularActive(context: Context, checkAllNetworks: Boolean = false): Boolean =
        try {
            val connectivityManager = context.getConnectivityManager()
            var capabilities: NetworkCapabilities? = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && connectivityManager != null
                && !checkAllNetworks
            ) {
                capabilities = connectivityManager.getNetworkCapabilities(
                    connectivityManager.activeNetwork
                )
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && capabilities != null) {
                hasCellularTransport(capabilities)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && connectivityManager != null) {
                connectivityManager.allNetworks.let {
                    for (network in it) {
                        val networkCapabilities =
                            connectivityManager.getNetworkCapabilities(network)
                        if (networkCapabilities != null && hasCellularTransport(networkCapabilities)) {
                            return true
                        }
                    }
                    return connectivityManager.activeNetworkInfo?.type == ConnectivityManager.TYPE_MOBILE
                }

            } else {
                connectivityManager?.activeNetworkInfo?.type == ConnectivityManager.TYPE_MOBILE
            }
        } catch (e: java.lang.Exception) {
            loge("NetworkChecker isCellularActive", e)
            false
        }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun hasCellularTransport(capabilities: NetworkCapabilities): Boolean =
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) &&
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                && isCellularInternetMayBeAvailable(capabilities)


    @JvmStatic
    fun isRoaming(context: Context): Boolean =
        try {
            val connectivityManager = context.getConnectivityManager()
            var capabilities: NetworkCapabilities? = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && connectivityManager != null) {
                capabilities = connectivityManager.getNetworkCapabilities(
                    connectivityManager.activeNetwork
                )
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && capabilities != null) {
                hasRoamingTransport(capabilities)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && connectivityManager != null) {
                connectivityManager.allNetworks.let {
                    for (network in it) {
                        val networkCapabilities =
                            connectivityManager.getNetworkCapabilities(network)
                        if (networkCapabilities != null && hasRoamingTransport(networkCapabilities)) {
                            return true
                        }
                    }
                    return connectivityManager.activeNetworkInfo?.let { info ->
                        info.type == ConnectivityManager.TYPE_MOBILE && info.isRoaming
                    } ?: false
                }

            } else {
                connectivityManager?.activeNetworkInfo?.let {
                    it.type == ConnectivityManager.TYPE_MOBILE && it.isRoaming
                } ?: run {
                    val telephonyManager =
                        context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                    telephonyManager?.isNetworkRoaming ?: false
                }
            }
        } catch (e: Exception) {
            loge("NetworkChecker isRoaming", e)
            false
        }


    @RequiresApi(Build.VERSION_CODES.P)
    private fun hasRoamingTransport(capabilities: NetworkCapabilities): Boolean =
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) &&
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                && isCellularInternetMayBeAvailable(capabilities)
                && !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)

    @JvmStatic
    @JvmOverloads
    fun isWifiActive(context: Context, checkAllNetworks: Boolean = false): Boolean =
        try {
            val connectivityManager = context.getConnectivityManager()
            var capabilities: NetworkCapabilities? = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && connectivityManager != null
                && !checkAllNetworks
            ) {
                capabilities = connectivityManager.getNetworkCapabilities(
                    connectivityManager.activeNetwork
                )
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && capabilities != null) {
                hasWifiTransport(capabilities)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && connectivityManager != null) {
                connectivityManager.allNetworks.let {
                    for (network in it) {
                        val networkCapabilities =
                            connectivityManager.getNetworkCapabilities(network)
                        if (networkCapabilities != null && hasWifiTransport(networkCapabilities)) {
                            return true
                        }
                    }
                    return connectivityManager.activeNetworkInfo?.type == ConnectivityManager.TYPE_WIFI
                }

            } else {
                connectivityManager?.activeNetworkInfo?.type == ConnectivityManager.TYPE_WIFI
            }
        } catch (e: Exception) {
            loge("NetworkChecker isWifiActive", e)
            false
        }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun hasWifiTransport(capabilities: NetworkCapabilities): Boolean =
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) &&
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)

    @JvmStatic
    fun isEthernetActive(context: Context): Boolean =
        try {
            val connectivityManager = context.getConnectivityManager()
            var capabilities: NetworkCapabilities? = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && connectivityManager != null) {
                capabilities = connectivityManager.getNetworkCapabilities(
                    connectivityManager.activeNetwork
                )
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && capabilities != null) {
                hasEthernetTransport(capabilities)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && connectivityManager != null) {
                connectivityManager.allNetworks.let {
                    for (network in it) {
                        val networkCapabilities =
                            connectivityManager.getNetworkCapabilities(network)
                        if (networkCapabilities != null && hasEthernetTransport(networkCapabilities)) {
                            return true
                        }
                    }
                    return connectivityManager.activeNetworkInfo?.type == ConnectivityManager.TYPE_ETHERNET
                }

            } else {
                connectivityManager?.activeNetworkInfo?.type == ConnectivityManager.TYPE_ETHERNET
            }
        } catch (e: Exception) {
            loge("NetworkChecker isEthernetActive", e)
            false
        }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun hasEthernetTransport(capabilities: NetworkCapabilities): Boolean =
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) &&
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)

    @JvmStatic
    fun isMeteredNetwork(context: Context): Boolean =
        try {
            val connectivityManager = context.getConnectivityManager()

            var capabilities: NetworkCapabilities? = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && connectivityManager != null) {
                capabilities = connectivityManager.getNetworkCapabilities(
                    connectivityManager.activeNetwork
                )
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                capabilities?.let {
                    if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) {
                        false
                    } else if (connectivityManager != null) {
                        ConnectivityManagerCompat.isActiveNetworkMetered(connectivityManager)
                    } else {
                        isCellularActive(context)
                    }
                } ?: isCellularActive(context)
            } else if (connectivityManager != null) {
                ConnectivityManagerCompat.isActiveNetworkMetered(connectivityManager)
            } else {
                true
            }
        } catch (e: Exception) {
            loge("NetworkChecker isMeteredNetwork", e)
            true
        }

    @JvmStatic
    fun isVpnActive(context: Context): Boolean =
        try {
            val connectivityManager = context.getConnectivityManager()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && connectivityManager != null) {
                connectivityManager.allNetworks.let {
                    for (network in it) {
                        val networkCapabilities =
                            connectivityManager.getNetworkCapabilities(network)
                        if (networkCapabilities != null && hasVpnTransport(networkCapabilities)) {
                            return true
                        }
                    }
                    return false
                }

            } else {
                false
            }
        } catch (e: Exception) {
            loge("NetworkChecker isVpnActive", e)
            false
        }

    private fun getVpnInterfaceName(context: Context): String = try {
        val connectivityManager = context.getConnectivityManager()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && connectivityManager != null) {
            connectivityManager.allNetworks.let {
                for (network in it) {
                    val networkCapabilities =
                        connectivityManager.getNetworkCapabilities(network)
                    if (networkCapabilities != null && hasVpnTransport(networkCapabilities)) {
                        return connectivityManager.getLinkProperties(network)?.interfaceName ?: ""
                    }
                }
                return ""
            }

        } else {
            ""
        }
    } catch (e: Exception) {
        loge("NetworkChecker getVpnInterfaceName", e)
        ""
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun hasVpnTransport(capabilities: NetworkCapabilities): Boolean =
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)

    @JvmStatic
    fun getMtu(context: Context, network: Network): Int =
        try {
            val connectivityManager = context.getConnectivityManager()
            var linkProperties: LinkProperties? = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && connectivityManager != null) {
                linkProperties = connectivityManager.getLinkProperties(network)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && linkProperties != null) {
                val mtu = linkProperties.mtu
                if (MTU_REGEX.matches(mtu.toString())) mtu else DEFAULT_MTU
            } else {
                DEFAULT_MTU
            }
        } catch (e: Exception) {
            loge("NetworkChecker isEthernetActive", e)
            DEFAULT_MTU
        }


    @JvmStatic
    @RequiresApi(Build.VERSION_CODES.M)
    fun isCaptivePortalDetected(context: Context): Boolean =
        try {
            val connectivityManager = context.getConnectivityManager()

            var capabilities: NetworkCapabilities? = null
            if (connectivityManager != null) {
                capabilities = connectivityManager.getNetworkCapabilities(
                    connectivityManager.activeNetwork
                )
            }

            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)
                ?: false
        } catch (e: Exception) {
            loge("NetworkChecker isCaptivePortalDetected", e)
            false
        }

    //Use of background networks requires CHANGE_NETWORK_STATE permission.
    @JvmStatic
    fun getAvailableNetworksSorted(context: Context): Array<Network> {

        val networks = TreeMap<Int, Network>()

        try {
            val connectivityManager = context.getConnectivityManager()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && connectivityManager != null) {
                for (network in connectivityManager.allNetworks) {
                    val capabilities = connectivityManager.getNetworkCapabilities(network)
                    if (capabilities != null
                        && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                        && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                    ) {
                        when {
                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ->
                                networks[1] = network

                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ->
                                networks[2] = network

                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                                    && isCellularInternetMayBeAvailable(capabilities) ->
                                networks[3] = network
                        }
                    } else if (capabilities != null
                        && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                    ) {
                        when {
                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ->
                                networks[4] = network

                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ->
                                networks[5] = network

                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                                    && isCellularInternetMayBeAvailable(capabilities) ->
                                networks[6] = network
                        }
                    }
                }

                if (networks.isEmpty()) {
                    connectivityManager.activeNetwork?.let {
                        networks[1] = it
                    }
                }
            }

        } catch (e: Exception) {
            loge("NetworkChecker getAvailableNetworksSorted", e)
        }

        return networks.values.toTypedArray()
    }

    //This is required because the cellular interface may only be available for ims
    private fun isCellularInternetMayBeAvailable(capabilities: NetworkCapabilities): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
        return true
    }

    @JvmStatic
    @RequiresApi(Build.VERSION_CODES.M)
    fun getCurrentActiveInterface(context: Context): String = try {
        val connectivityManager = context.getConnectivityManager()
        if (connectivityManager != null) {
            getVpnInterfaceName(context).ifEmpty {
                connectivityManager.activeNetwork?.let {
                    connectivityManager.getLinkProperties(it)?.interfaceName
                } ?: ""
            }
        } else {
            ""
        }
    } catch (e: Exception) {
        loge("NetworkChecker getCurrentActiveInterface", e)
        ""
    }

    @JvmStatic
    @RequiresApi(Build.VERSION_CODES.M)
    fun getUnderlyingVpnActiveInterface(context: Context): String = try {
        val connectivityManager = context.getConnectivityManager()
        if (connectivityManager != null) {
            connectivityManager.activeNetwork?.let {
                connectivityManager.getLinkProperties(it)?.interfaceName
            } ?: ""
        } else {
            ""
        }
    } catch (e: Exception) {
        loge("NetworkChecker getUnderlyingVpnActiveInterface", e)
        ""
    }

    private fun Context.getConnectivityManager(): ConnectivityManager? =
        getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private fun isRootMode() = ModulesStatus.getInstance().run {
        mode == OperationMode.ROOT_MODE && !isFixTTL
    }
}
