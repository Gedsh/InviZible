package pan.alexander.tordnscrypt.utils.multidex

import android.content.Context
import androidx.multidex.MultiDex

object MultidexActivator {
    fun activate(context: Context) {
        MultiDex.install(context)
    }
}
