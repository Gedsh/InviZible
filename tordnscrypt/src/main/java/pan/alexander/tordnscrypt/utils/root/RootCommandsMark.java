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

package pan.alexander.tordnscrypt.utils.root;

import androidx.annotation.IntDef;

@IntDef({
        RootCommandsMark.DNSCRYPT_RUN_FRAGMENT_MARK,
        RootCommandsMark.TOR_RUN_FRAGMENT_MARK,
        RootCommandsMark.I2PD_RUN_FRAGMENT_MARK,
        RootCommandsMark.HELP_ACTIVITY_MARK,
        RootCommandsMark.BOOT_BROADCAST_MARK,
        RootCommandsMark.NULL_MARK,
        RootCommandsMark.FILE_OPERATIONS_MARK,
        RootCommandsMark.INSTALLER_MARK,
        RootCommandsMark.TOP_FRAGMENT_MARK,
        RootCommandsMark.IPTABLES_MARK
})

public @interface RootCommandsMark {
    int DNSCRYPT_RUN_FRAGMENT_MARK = 100;
    int TOR_RUN_FRAGMENT_MARK = 200;
    int I2PD_RUN_FRAGMENT_MARK = 300;
    int HELP_ACTIVITY_MARK = 400;
    int BOOT_BROADCAST_MARK = 500;
    int NULL_MARK = 600;
    int FILE_OPERATIONS_MARK = 700;
    int INSTALLER_MARK = 800;
    int TOP_FRAGMENT_MARK = 900;
    int IPTABLES_MARK = 1000;
}
