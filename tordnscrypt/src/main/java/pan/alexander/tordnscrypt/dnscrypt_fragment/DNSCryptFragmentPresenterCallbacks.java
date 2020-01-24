package pan.alexander.tordnscrypt.dnscrypt;

import android.content.Context;

public interface DNSCryptFragmentPresenterCallbacks {
    boolean isDNSCryptInstalled(Context context);
    boolean isSavedDNSStatusRunning(Context context);
    void saveDNSStatusRunning(Context context, boolean running);
    void displayLog(int period);
    void stopDisplayLog();
    void refreshDNSCryptState(Context context);
}
