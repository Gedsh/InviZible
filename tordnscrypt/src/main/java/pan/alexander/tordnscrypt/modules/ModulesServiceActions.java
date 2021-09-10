package pan.alexander.tordnscrypt.modules;
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

    Copyright 2019-2021 by Garmatin Oleksandr invizible.soft@gmail.com
*/

public class ModulesServiceActions {
    public static final String actionDismissNotification = "pan.alexander.tordnscrypt.action.DISMISS_NOTIFICATION";
    public static final String actionStopService = "pan.alexander.tordnscrypt.action.STOP_SERVICE";
    public static final String actionStopServiceForeground = "pan.alexander.tordnscrypt.action.STOP_SERVICE_FOREGROUND";

    static final String actionStartDnsCrypt = "pan.alexander.tordnscrypt.action.START_DNSCRYPT";
    static final String actionStartTor = "pan.alexander.tordnscrypt.action.START_TOR";
    static final String actionStartITPD = "pan.alexander.tordnscrypt.action.START_ITPD";
    static final String actionStopDnsCrypt = "pan.alexander.tordnscrypt.action.STOP_DNSCRYPT";
    static final String actionStopTor = "pan.alexander.tordnscrypt.action.STOP_TOR";
    static final String actionStopITPD = "pan.alexander.tordnscrypt.action.STOP_ITPD";
    static final String actionRestartDnsCrypt = "pan.alexander.tordnscrypt.action.RESTART_DNSCRYPT";
    static final String actionRestartTor = "pan.alexander.tordnscrypt.action.RESTART_TOR";
    static final String actionRestartTorFull = "pan.alexander.tordnscrypt.action.RESTART_TOR_FULL";
    static final String actionRestartITPD = "pan.alexander.tordnscrypt.action.RESTART_ITPD";
    static final String actionUpdateModulesStatus = "pan.alexander.tordnscrypt.action.UPDATE_MODULES_STATUS";
    static final String actionRecoverService = "pan.alexander.tordnscrypt.action.RECOVER_SERVICE";
    static final String speedupLoop = "pan.alexander.tordnscrypt.action.SPEEDUP_LOOP";
    static final String slowdownLoop = "pan.alexander.tordnscrypt.action.SLOWDOWN_LOOP";
    static final String extraLoop = "pan.alexander.tordnscrypt.action.MAKE_EXTRA_LOOP";
    static final String startArpScanner = "pan.alexander.tordnscrypt.action.START_ARP_SCANNER";
    static final String stopArpScanner = "pan.alexander.tordnscrypt.action.STOP_ARP_SCANNER";
    static final String clearIptablesCommandsHash = "pan.alexander.tordnscrypt.action.CLEAR_IPTABLES_COMMANDS_HASH";
}
