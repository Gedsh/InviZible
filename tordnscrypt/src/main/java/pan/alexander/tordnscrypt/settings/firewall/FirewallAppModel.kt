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

    Copyright 2019-2023 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.settings.firewall

import pan.alexander.tordnscrypt.settings.tor_apps.ApplicationData

data class FirewallAppModel(val applicationData: ApplicationData,
                            var allowLan: Boolean,
                            var allowWifi: Boolean,
                            var allowGsm: Boolean,
                            var allowRoaming: Boolean,
                            var allowVPN: Boolean): Comparable<FirewallAppModel> {

    override fun compareTo(other: FirewallAppModel): Int {
        return applicationData.uid.compareTo(other.applicationData.uid)
    }

    override fun equals(other: Any?): Boolean {
        if (javaClass != other?.javaClass) return false

        other as FirewallAppModel

        if (applicationData.uid != other.applicationData.uid) return false

        return true
    }

    override fun hashCode(): Int {
        return applicationData.uid.hashCode()
    }
}
