package pan.alexander.tordnscrypt.utils

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Point
import android.util.Log
import android.view.Display
import pan.alexander.tordnscrypt.modules.ModulesService
import pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException
import kotlin.math.roundToInt

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

    fun dips2pixels(dips: Int, context: Context): Int {
        return (dips * context.resources.displayMetrics.density + 0.5f).roundToInt()
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
            Log.e(LOG_TAG, "Utils SocketException " + e.message + " " + e.cause)
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
            Log.e(LOG_TAG, "Util SocketException " + e.message + " " + e.cause)
        }

        return result
    }

    fun getHostByIP(IP: String): String {
        val addr = InetAddress.getByName(IP)
        return addr.hostName
    }

    fun isServiceRunning(context: Context, serviceClass: Class<ModulesService>): Boolean {
        var result = false

        try {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            for (serviceFounded in manager.getRunningServices(Int.MAX_VALUE)) {
                if (serviceClass.name == serviceFounded.service.className) {
                    result = true
                }
            }
        } catch (exception: Exception) {
            Log.e(LOG_TAG, "Utils isServiceRunning exception " + exception.message + " " + exception.cause)
        }

        return result
    }
}
