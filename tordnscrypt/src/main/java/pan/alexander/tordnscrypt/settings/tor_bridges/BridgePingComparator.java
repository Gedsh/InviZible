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

package pan.alexander.tordnscrypt.settings.tor_bridges;

import java.util.Comparator;

public class BridgePingComparator implements Comparator<ObfsBridge> {

    @Override
    public int compare(ObfsBridge bridge1, ObfsBridge bridge2) {
        if (!bridge1.active && bridge2.active) {
            return 1;
        } else if (bridge1.active && !bridge2.active) {
            return -1;
        } else if (bridge1.ping <= 0 && bridge2.ping > 0) {
            return 1;
        } else if (bridge1.ping > 0 && bridge2.ping <= 0) {
            return -1;
        } else if (bridge1.ping <= 0) {
            return 0;
        } else {
            return bridge1.ping - bridge2.ping;
        }
    }
}
