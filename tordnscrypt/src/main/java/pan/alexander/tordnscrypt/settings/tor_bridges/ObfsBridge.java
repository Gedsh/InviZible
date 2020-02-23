package pan.alexander.tordnscrypt.settings.tor_bridges;

import pan.alexander.tordnscrypt.utils.enums.BridgeType;

class ObfsBridge {
    String bridge;
    BridgeType obfsType;
    boolean active;

    ObfsBridge(String bridge, BridgeType obfsType, boolean active) {
        this.bridge = bridge;
        this.obfsType = obfsType;
        this.active = active;
    }
}
