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

package pan.alexander.tordnscrypt.iptables;

interface IptablesConstants {
    String FILTER_OUTPUT_CORE = "tordnscrypt";
    String FILTER_OUTPUT_FIREWALL = "ipro_fwl_output";
    String FILTER_FIREWALL_LAN = "ipro_fwl_lan";
    String NAT_OUTPUT_CORE = "tordnscrypt_nat_output";
    String MANGLE_FIREWALL_ALLOW = "ipro_mangle_fwl";
    String FILTER_FORWARD_CORE = "tordnscrypt_forward";
    String FILTER_FORWARD_FIREWALL = "ipro_fwl_forward";
    String NAT_PREROUTING_CORE = "tordnscrypt_prerouting";
    String FILTER_OUTPUT_BLOCKING = "ipro_blocking";
}
