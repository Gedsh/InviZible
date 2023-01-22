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

package pan.alexander.tordnscrypt.settings.tor_bridges;

import java.util.Objects;

import pan.alexander.tordnscrypt.utils.enums.BridgeType;

public class ObfsBridge {
    String bridge;
    BridgeType obfsType;
    int ping;
    String country = "";
    boolean active;

    ObfsBridge(String bridge, BridgeType obfsType, boolean active) {
        this.bridge = bridge;
        this.obfsType = obfsType;
        this.active = active;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ObfsBridge bridge1 = (ObfsBridge) o;
        return bridge.equals(bridge1.bridge) && obfsType == bridge1.obfsType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(bridge, obfsType);
    }
}
