package pan.alexander.tordnscrypt.settings.tor_apps

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

    Copyright 2019-2022 by Garmatin Oleksandr invizible.soft@gmail.com
*/

import android.graphics.drawable.Drawable
import java.util.*
import java.util.concurrent.ConcurrentSkipListSet
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.Comparator

data class ApplicationData(private val name: String = "",
                           val pack: String = "",
                           val uid: Int = -1000,
                           val icon: Drawable? = null,
                           val system: Boolean = false,
                           var active: Boolean = false): Comparable<ApplicationData> {

    val names = ConcurrentSkipListSet(setOf(name))

    fun addName(name: String) {
        names.add(name)
    }

    fun addAllNames(_names: ConcurrentSkipListSet<String>) {
        names.addAll(_names)
    }

    companion object {
        const val SPECIAL_UID_KERNEL = -1
        const val SPECIAL_UID_NTP = -14
        const val SPECIAL_PORT_NTP = 123
        const val SPECIAL_UID_AGPS = -15
        const val SPECIAL_PORT_AGPS1 = 7275
        const val SPECIAL_PORT_AGPS2 = 7276
        const val SPECIAL_UID_CONNECTIVITY_CHECK = -16

        fun <T> sortListBy(list: CopyOnWriteArrayList<T>?, comparator: Comparator<T>) {
            if (list != null && list.size > 1) {
                val sortedList = ArrayList(list)
                sortedList.sortWith(comparator)
                list.clear()
                list.addAll(sortedList)
            }
        }
    }

    override fun compareTo(other: ApplicationData): Int {
        return if (!active && other.active) {
            1
        } else if (active && !other.active) {
            -1
        } else {
            names.first().lowercase(Locale.getDefault()).compareTo(other.names.first()
                .lowercase(Locale.getDefault()))
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ApplicationData

        if (uid != other.uid) return false

        return true
    }

    override fun hashCode(): Int {
        return uid
    }

    override fun toString(): String {
        return names.joinToString()
    }
}