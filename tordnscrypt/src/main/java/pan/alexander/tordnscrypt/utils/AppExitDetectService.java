package pan.alexander.tordnscrypt.utils;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class AppExitDetectService extends Service {
    public AppExitDetectService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
