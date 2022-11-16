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

package pan.alexander.tordnscrypt.settings.tor_ips

import androidx.recyclerview.widget.DiffUtil

class DomainIpDiffUtilItemCallback: DiffUtil.ItemCallback<DomainIpEntity>() {

    override fun areItemsTheSame(oldItem: DomainIpEntity, newItem: DomainIpEntity): Boolean {

        if (oldItem is DomainEntity && newItem is DomainEntity) {
            return oldItem.domain == newItem.domain
        }

        if (oldItem is IpEntity && newItem is IpEntity) {
            return oldItem.ip == newItem.ip
        }

        return false
    }

    override fun areContentsTheSame(oldItem: DomainIpEntity, newItem: DomainIpEntity): Boolean {

        if (oldItem is DomainEntity && newItem is DomainEntity) {
            return oldItem.ips == newItem.ips
        }

        if (oldItem is IpEntity && newItem is IpEntity) {
            return oldItem.domain == newItem.domain
        }

        return false
    }
}
