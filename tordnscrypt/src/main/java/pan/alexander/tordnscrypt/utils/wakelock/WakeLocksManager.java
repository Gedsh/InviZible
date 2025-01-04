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

package pan.alexander.tordnscrypt.utils.wakelock;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.PowerManager;

import org.jetbrains.annotations.NotNull;

import static pan.alexander.tordnscrypt.utils.logger.Logger.logi;

public class WakeLocksManager {

    private static volatile WakeLocksManager wakeLocksManager;
    private static PowerManager.WakeLock powerWakeLock;
    private static WifiManager.WifiLock wifiWakeLock;

    private WakeLocksManager() {}

    public static WakeLocksManager getInstance() {
        if (wakeLocksManager == null) {
            synchronized (WakeLocksManager.class) {
                if (wakeLocksManager == null) {
                    wakeLocksManager = new WakeLocksManager();
                }
            }
        }
        return wakeLocksManager;
    }

    @SuppressLint({"InvalidWakeLockTag", "WakelockTimeout"})
    public void managePowerWakelock(@NotNull Context context, boolean lock) {
        if (lock) {
            final String TAG = "AudioMix";
            PowerManager pm = (PowerManager) context.getApplicationContext().getSystemService(Context.POWER_SERVICE);
            if (powerWakeLock == null && pm != null) {
                powerWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
                powerWakeLock.acquire();
                logi("WakeLocksManager Power wake lock is acquired");
            }
        } else {
            stopPowerWakelock();
        }
    }

    public void manageWiFiLock(@NotNull Context context, boolean lock) {
        if (lock) {
            WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifiWakeLock == null && wm != null) {
                wifiWakeLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF , "InviZible::WifiLock");
                wifiWakeLock.acquire();
                logi("WakeLocksManager WiFi wake lock is acquired");
            }
        } else {
            stopWiFiLock();
        }
    }

    public void stopPowerWakelock() {
        if (powerWakeLock != null && powerWakeLock.isHeld()) {
            powerWakeLock.release();
            powerWakeLock = null;
            logi("WakeLocksManager Power wake lock is released");
        }
    }

    public void stopWiFiLock() {
        if (wifiWakeLock != null && wifiWakeLock.isHeld()) {
            wifiWakeLock.release();
            wifiWakeLock = null;
            logi("WakeLocksManager WiFi wake lock is released");
        }
    }

    public boolean isPowerWakeLockHeld() {
        if (powerWakeLock != null) {
            return powerWakeLock.isHeld();
        }

        return false;
    }

    public boolean isWiFiWakeLockHeld() {
        if (wifiWakeLock != null) {
            return wifiWakeLock.isHeld();
        }

        return false;
    }
}
