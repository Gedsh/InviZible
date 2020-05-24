package pan.alexander.tordnscrypt.utils

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Point
import android.util.Log
import android.view.Display
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException

object Utils {
    fun getScreenOrientation(activity: Activity): Int {
        val getOrient: Display = activity.windowManager.defaultDisplay
        val point = Point()
        getOrient.getSize(point)
        return if (point.x == point.y) {
            Configuration.ORIENTATION_UNDEFINED
        } else {
            if (point.x < point.y) {
                Configuration.ORIENTATION_PORTRAIT
            } else {
                Configuration.ORIENTATION_LANDSCAPE
            }
        }
    }

    fun getDeviceIP(): String {
        try {
            val en = NetworkInterface.getNetworkInterfaces()
            while (en.hasMoreElements()) {
                val intf = en.nextElement()
                val enumIpAddr = intf.inetAddresses
                while (enumIpAddr.hasMoreElements()) {
                    val inetAddress = enumIpAddr.nextElement()
                    if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                        return inetAddress.getHostAddress()
                    }
                }
            }
        } catch (e: SocketException) {
            Log.e(RootExecService.LOG_TAG, "Utils SocketException " + e.message + " " + e.cause)
        }

        return ""
    }

    fun isLANInterfaceExist(): Boolean {

        var result = false

        try {
            val en = NetworkInterface.getNetworkInterfaces()
            while (en.hasMoreElements()) {
                val intf = en.nextElement()

                if (intf.isLoopback || intf.isVirtual || !intf.isUp || intf.isPointToPoint || intf.hardwareAddress == null) {
                    continue
                }

                if (intf.name.replace("\\d+".toRegex(), "").equals("eth", ignoreCase = true)) {
                    result = true
                    break
                }

            }
        } catch (e: SocketException) {
            Log.e(RootExecService.LOG_TAG, "Util SocketException " + e.message + " " + e.cause)
        }

        return result
    }

    fun getHostByIP(IP: String): String {
        var host = ""
        try {
            val addr = InetAddress.getByName(IP)
            host = addr.hostName
        } catch (e: Exception){}
        return host
    }
}
