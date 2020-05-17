package pan.alexander.tordnscrypt.utils

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Point
import android.view.Display

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
}
