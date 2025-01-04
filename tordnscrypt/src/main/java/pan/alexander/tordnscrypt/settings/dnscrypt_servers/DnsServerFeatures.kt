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

package pan.alexander.tordnscrypt.settings.dnscrypt_servers

import android.content.Context
import android.content.SharedPreferences
import pan.alexander.tordnscrypt.R
import pan.alexander.tordnscrypt.assistance.AccelerateDevelop.accelerated

data class DnsServerFeatures(
    val requireDnssec: Boolean,
    val requireNofilter: Boolean,
    var requireNolog: Boolean,
    val useDnsServers: Boolean,
    val useDohServers: Boolean,
    val useOdohServers: Boolean,
    val useIPv4Servers: Boolean,
    val useIPv6Servers: Boolean
) {
    constructor(context: Context, defaultPreferences: SharedPreferences) : this(
        requireDnssec = defaultPreferences.getBoolean("require_dnssec", false),
        requireNofilter = defaultPreferences.getBoolean("require_nofilter", false)
                || context.getText(R.string.package_name).contains(".gp") && !accelerated,
        requireNolog = defaultPreferences.getBoolean("require_nolog", false),
        useDnsServers = defaultPreferences.getBoolean("dnscrypt_servers", true),
        useDohServers = defaultPreferences.getBoolean("doh_servers", true),
        useOdohServers = defaultPreferences.getBoolean("odoh_servers", true),
        useIPv4Servers = defaultPreferences.getBoolean("ipv4_servers", true),
        useIPv6Servers = defaultPreferences.getBoolean("ipv6_servers", true)
    )
}
