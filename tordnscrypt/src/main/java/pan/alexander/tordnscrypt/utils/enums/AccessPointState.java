package pan.alexander.tordnscrypt.utils.enums;

import androidx.annotation.IntDef;

@IntDef({
        AccessPointState.STATE_ON,
        AccessPointState.STATE_OFF,
        AccessPointState.STATE_UNKNOWN
})

public @interface AccessPointState {
    int STATE_ON = 100;
    int STATE_OFF = 200;
    int STATE_UNKNOWN = 300;
}
