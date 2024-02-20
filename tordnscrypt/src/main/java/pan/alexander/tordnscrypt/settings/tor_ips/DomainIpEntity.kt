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

package pan.alexander.tordnscrypt.settings.tor_ips

sealed class DomainIpEntity(
    open var isActive: Boolean
): Comparable<DomainIpEntity> {
    override fun compareTo(other: DomainIpEntity): Int {
        return when {
            this is DomainEntity && other is IpEntity -> -1
            this is IpEntity && other is DomainEntity -> 1
            this is DomainEntity && other is DomainEntity -> domain.compareTo(other.domain)
            this is IpEntity && other is IpEntity -> ip.compareTo(other.ip)
            else -> 0
        }
    }
}

data class DomainEntity(
    val domain: String,
    val ips: Set<String>,
    override var isActive: Boolean
): DomainIpEntity(isActive) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DomainEntity

        if (domain != other.domain) return false

        return true
    }

    override fun hashCode(): Int {
        return domain.hashCode()
    }
}

data class IpEntity(
    val ip: String,
    val domain: String,
    override var isActive: Boolean
): DomainIpEntity(isActive) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as IpEntity

        if (ip != other.ip) return false

        return true
    }

    override fun hashCode(): Int {
        return ip.hashCode()
    }
}
