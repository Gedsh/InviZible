package pan.alexander.tordnscrypt.settings.tor_bridges;

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

import java.util.List;
import java.util.Set;

import pan.alexander.tordnscrypt.utils.enums.BridgeType;
import pan.alexander.tordnscrypt.utils.enums.BridgesSelector;

public interface PreferencesBridges {
    BridgeType getCurrentBridgesType();
    void  setCurrentBridgesType(BridgeType type);
    BridgesSelector getSavedBridgesSelector();
    void setSavedBridgesSelector(BridgesSelector selector);
    Set<String> getBridgesInUse();
    List<ObfsBridge> getBridgesToDisplay();
    BridgeAdapter getBridgeAdapter();
    List<String> getBridgesInappropriateType();
    String getBridgesFilePath();
    boolean areDefaultVanillaBridgesSelected();
    boolean areRelayBridgesWereRequested();
    void saveRelayBridgesWereRequested(boolean requested);
}
