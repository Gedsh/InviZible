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

package pan.alexander.tordnscrypt.settings.tor_apps

import android.graphics.drawable.Drawable
import pan.alexander.tordnscrypt.modules.ModulesStatus
import pan.alexander.tordnscrypt.utils.enums.OperationMode
import java.util.ArrayList
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

data class TorAppData(
    val names: Set<String>,
    val pack: String,
    val uid: Int,
    val icon: Drawable?,
    val system: Boolean,
    val hasInternetPermission: Boolean,
    var torifyApp: Boolean,
    var directUdp: Boolean,
    var excludeFromAll: Boolean
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TorAppData

        return uid == other.uid
    }

    override fun hashCode(): Int {
        return uid
    }

    companion object {

        @JvmStatic
        fun CopyOnWriteArrayList<TorAppData>.sortByName() = sortListBy(this) { o1, o2 ->
            if (!o1.excludeFromAll && o2.excludeFromAll && !isRootMode()) {
                1
            } else if (o1.excludeFromAll && !o2.excludeFromAll && !isRootMode()) {
                -1
            } else if (!o1.torifyApp && o2.torifyApp) {
                1
            } else if (o1.torifyApp && !o2.torifyApp) {
                -1
            } else if (!o1.directUdp && o2.directUdp) {
                1
            } else if (o1.directUdp && !o2.directUdp) {
                -1
            } else {
                o1.names.first().lowercase(Locale.getDefault()).compareTo(
                    o2.names.first().lowercase(Locale.getDefault())
                )
            }
        }

        @JvmStatic
        fun CopyOnWriteArrayList<TorAppData>.sortByUid() = sortListBy(this) { o1, o2 ->
            if (!o1.excludeFromAll && o2.excludeFromAll && !isRootMode()) {
                1
            } else if (o1.excludeFromAll && !o2.excludeFromAll && !isRootMode()) {
                -1
            } else if (!o1.torifyApp && o2.torifyApp) {
                1
            } else if (o1.torifyApp && !o2.torifyApp) {
                -1
            } else if (!o1.directUdp && o2.directUdp) {
                1
            } else if (o1.directUdp && !o2.directUdp) {
                -1
            } else {
                o1.uid - o2.uid
            }
        }

        @JvmStatic
        fun ApplicationData.mapToTorAppData() =
            TorAppData(
                names = names,
                pack = pack,
                uid = uid,
                icon = icon,
                system = system,
                hasInternetPermission = hasInternetPermission,
                torifyApp = active,
                directUdp = false,
                excludeFromAll = false
            )

        private fun <T> sortListBy(list: CopyOnWriteArrayList<T>?, comparator: Comparator<T>) {
            if (list != null && list.size > 1) {
                val sortedList = ArrayList(list)
                sortedList.sortWith(comparator)
                list.clear()
                list.addAll(sortedList)
            }
        }

        private fun isRootMode() = ModulesStatus.getInstance().mode == OperationMode.ROOT_MODE
    }
}
