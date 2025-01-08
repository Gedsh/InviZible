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

package pan.alexander.tordnscrypt.modules;

public class ModulesServiceActions {
    public static final String ACTION_DISMISS_NOTIFICATION = "pan.alexander.tordnscrypt.action.DISMISS_NOTIFICATION";
    public static final String ACTION_STOP_SERVICE = "pan.alexander.tordnscrypt.action.STOP_SERVICE";
    public static final String ACTION_STOP_SERVICE_FOREGROUND = "pan.alexander.tordnscrypt.action.STOP_SERVICE_FOREGROUND";

    static final String ACTION_START_DNSCRYPT = "pan.alexander.tordnscrypt.action.START_DNSCRYPT";
    static final String ACTION_START_TOR = "pan.alexander.tordnscrypt.action.START_TOR";
    static final String ACTION_START_ITPD = "pan.alexander.tordnscrypt.action.START_ITPD";
    static final String ACTION_STOP_DNSCRYPT = "pan.alexander.tordnscrypt.action.STOP_DNSCRYPT";
    static final String ACTION_STOP_TOR = "pan.alexander.tordnscrypt.action.STOP_TOR";
    static final String ACTION_STOP_ITPD = "pan.alexander.tordnscrypt.action.STOP_ITPD";
    static final String ACTION_RESTART_DNSCRYPT = "pan.alexander.tordnscrypt.action.RESTART_DNSCRYPT";
    static final String ACTION_RESTART_TOR = "pan.alexander.tordnscrypt.action.RESTART_TOR";
    static final String ACTION_RESTART_TOR_FULL = "pan.alexander.tordnscrypt.action.RESTART_TOR_FULL";
    static final String ACTION_RESTART_ITPD = "pan.alexander.tordnscrypt.action.RESTART_ITPD";
    static final String ACTION_UPDATE_MODULES_STATUS = "pan.alexander.tordnscrypt.action.UPDATE_MODULES_STATUS";
    static final String ACTION_RECOVER_SERVICE = "pan.alexander.tordnscrypt.action.RECOVER_SERVICE";
    static final String SPEEDUP_LOOP = "pan.alexander.tordnscrypt.action.SPEEDUP_LOOP";
    static final String SLOWDOWN_LOOP = "pan.alexander.tordnscrypt.action.SLOWDOWN_LOOP";
    static final String EXTRA_LOOP = "pan.alexander.tordnscrypt.action.MAKE_EXTRA_LOOP";
    static final String START_ARP_SCANNER = "pan.alexander.tordnscrypt.action.START_ARP_SCANNER";
    static final String STOP_ARP_SCANNER = "pan.alexander.tordnscrypt.action.STOP_ARP_SCANNER";
    static final String CLEAR_IPTABLES_COMMANDS_HASH = "pan.alexander.tordnscrypt.action.CLEAR_IPTABLES_COMMANDS_HASH";
}
