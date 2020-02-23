package pan.alexander.tordnscrypt.settings.tor_bridges;

import java.util.List;

import pan.alexander.tordnscrypt.utils.enums.BridgeType;

public interface PreferencesBridges {
    BridgeType getCurrentBridgesType();
    void  setCurrentBridgesType(BridgeType type);
    List<String> getCurrentBridges();
    List<ObfsBridge> getBridgeList();
    BridgeAdapter getBridgeAdapter();
    List<String> getAnotherBridges();
    String get_bridges_file_path();
}
