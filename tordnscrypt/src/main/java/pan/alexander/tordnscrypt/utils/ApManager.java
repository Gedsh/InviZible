package pan.alexander.tordnscrypt.utils;
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

    Copyright 2019 by Garmatin Oleksandr invizible.soft@gmail.com
*/

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.ResultReceiver;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import pan.alexander.tordnscrypt.R;

import static android.content.Context.CONNECTIVITY_SERVICE;
import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;

@SuppressLint("PrivateApi")
public class ApManager {
    private Context context;
    //private WifiManager wifiManager;
    public static int apStateON = 100;
    public static int apStateOFF = 200;
    private static WifiManager.LocalOnlyHotspotReservation mReservation;

    @SuppressLint("WifiManagerPotentialLeak")
    public ApManager(Context context) {
        this.context = context;

    }

    //check whether wifi hotspot on or off
    public int isApOn() {

        try {
            WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            Method method;
            if (wifiManager != null) {
                method = wifiManager.getClass().getDeclaredMethod("isWifiApEnabled");
                method.setAccessible(true);
                if ((Boolean) method.invoke(wifiManager)) {
                    return apStateON;
                } else {
                    return apStateOFF;
                }

            }
        }
        catch (Throwable ignored) {}
        return 300;
    }

    // toggle wifi hotspot on or off
    @SuppressWarnings("JavaReflectionMemberAccess")
    public boolean configApState() {
        try {
            WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            // if WiFi is on, turn it off
            if (isApOn() == apStateON) {
                if (wifiManager != null) {
                    wifiManager.setWifiEnabled(false);
                }
            }

            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) {
                try {
                    if (wifiManager != null) {
                        Method wifiApConfigurationMethod = wifiManager.getClass().getMethod("getWifiApConfiguration");
                        WifiConfiguration netConfig = (WifiConfiguration)wifiApConfigurationMethod.invoke(wifiManager);
                        Method method = wifiManager.getClass().getMethod("setWifiApEnabled", WifiConfiguration.class, boolean.class);
                        int apState = isApOn();
                        if (apState==apStateON) {
                            method.invoke(wifiManager, netConfig, false);
                        } else if (apState == apStateOFF) {
                            method.invoke(wifiManager, netConfig, true);
                        }
                        return true;
                    }
                } catch (Exception e) {
                    Log.e(LOG_TAG, "ApManager configApState M Exception " + e.getMessage() + System.lineSeparator() + e.getCause());
                }

            } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                try {
                    Class<ConnectivityManager> connectivityClass = ConnectivityManager.class;
                    ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService
                            (CONNECTIVITY_SERVICE);

                    int apState = isApOn();
                    if (apState==apStateOFF) {
                        Field internalConnectivityManagerField = ConnectivityManager.class.getDeclaredField("mService");
                        internalConnectivityManagerField.setAccessible(true);

                        callStartTethering(internalConnectivityManagerField.get(connectivityManager));

                    } else if (apState == apStateON) {
                        Method stopTetheringMethod = connectivityClass.getDeclaredMethod("stopTethering", int.class);
                        stopTetheringMethod.invoke(connectivityManager, 0);
                    }
                    return true;

                } catch (Exception e) {
                    Log.e(LOG_TAG, "ApManager configApState N Exception " + e.getMessage() + System.lineSeparator() + e.getCause());
                }
            } else {
                try {
                    int apState = isApOn();
                    if (apState==apStateOFF) {
                        WifiManager manager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);

                        if (manager != null) {
                            manager.startLocalOnlyHotspot(new WifiManager.LocalOnlyHotspotCallback() {

                                @Override
                                public void onStarted(WifiManager.LocalOnlyHotspotReservation reservation) {
                                    super.onStarted(reservation);
                                    Log.d(LOG_TAG, "Wifi Hotspot is on now");
                                    mReservation = reservation;
                                }

                                @Override
                                public void onStopped() {
                                    super.onStopped();
                                    Log.d(LOG_TAG, "Wifi Hotspot onStopped: ");
                                }

                                @Override
                                public void onFailed(int reason) {
                                    super.onFailed(reason);
                                    Log.d(LOG_TAG, "Wifi Hotspot onFailed: ");
                                }
                            }, new Handler());
                        }
                    } else if (apState == apStateON) {
                        if (mReservation != null) {
                            mReservation.close();
                            mReservation = null;
                        }
                    }
                    return true;

                } catch (Exception e) {
                    Log.e(LOG_TAG, "ApManager configApState O Exception " + e.getMessage() + System.lineSeparator() + e.getCause());
                }
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "ApManager configApState Exception " + e.getMessage() + System.lineSeparator() + e.getCause());
        }
        return false;
    }

    private void callStartTethering(Object internalConnectivityManager) throws ReflectiveOperationException {
        Class internalConnectivityManagerClass = Class.forName("android.net.IConnectivityManager");

        ResultReceiver dummyResultReceiver = new ResultReceiver(null);

        try {
            Method startTetheringMethod = internalConnectivityManagerClass.getDeclaredMethod("startTethering",
                    int.class,
                    ResultReceiver.class,
                    boolean.class);

            startTetheringMethod.invoke(internalConnectivityManager,
                    0,
                    dummyResultReceiver,
                    false);
        } catch (NoSuchMethodException e) {
            // Newer devices have "callingPkg" String argument at the end of this method.
            @SuppressLint("SoonBlockedPrivateApi") Method startTetheringMethod = internalConnectivityManagerClass.getDeclaredMethod("startTethering",
                    int.class,
                    ResultReceiver.class,
                    boolean.class,
                    String.class);

            startTetheringMethod.invoke(internalConnectivityManager,
                    0,
                    dummyResultReceiver,
                    false,
                    context.getString(R.string.package_name));
        }
    }

    /*

    public void launchHotspotSettings(){
        try {
            final Intent intent = new Intent(Intent.ACTION_MAIN, null);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            final ComponentName cn = new ComponentName("com.android.settings", "com.android.settings.Settings$TetherSettingsActivity");
            intent.setComponent(cn);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity( intent);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }


    public String getHotspotName() {
        WifiConfiguration netConfig;
        if (wifiManager != null) {
            try {

                wifiManager.getClass().getMethod("getWifiApConfiguration");
                Method wifiApConfigurationMethod;
                wifiApConfigurationMethod = wifiManager.getClass().getMethod("getWifiApConfiguration");
                try {
                    netConfig = (WifiConfiguration)wifiApConfigurationMethod.invoke(wifiManager);
                    //return netConfig.SSID;
                    return netConfig.SSID;
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } catch (InvocationTargetException e) {
                    e.printStackTrace();
                }
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            }
        }
        return null;
    }


    public String getHotspotPassword() {
        WifiConfiguration netConfig;
        if (wifiManager != null) {
            try {
                Method wifiApConfigurationMethod;
                wifiApConfigurationMethod = wifiManager.getClass().getMethod("getWifiApConfiguration");
                try {
                    netConfig = (WifiConfiguration)wifiApConfigurationMethod.invoke(wifiManager);
                    return netConfig.preSharedKey;
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } catch (InvocationTargetException e) {
                    e.printStackTrace();
                }
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public String getCurrentChanel() {
        WifiConfiguration netConfig;
        if (wifiManager != null) {
            try {
                Method wifiApConfigurationMethod;
                wifiApConfigurationMethod = wifiManager.getClass().getMethod("getWifiApConfiguration");
                netConfig = (WifiConfiguration)wifiApConfigurationMethod.invoke(wifiManager);
                Field wcFreq = WifiConfiguration.class.getField("apChannel");
                int val = wcFreq.getInt(netConfig);
                return String.valueOf(val);
                StringBuilder text= new StringBuilder("NO");
                Field[] wcefFields = WifiConfiguration.class.getFields();
                for (Field field:wcefFields) {
                    text.append(field.getName()).append(" ");
                }
                return text.toString();
            } catch (IllegalAccessException | NoSuchFieldException | NoSuchMethodException | InvocationTargetException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public void setChannel() {
        WifiConfiguration netConfig;
        if (wifiManager != null) {
            try {
                Method wifiApConfigurationMethod;
                wifiApConfigurationMethod = wifiManager.getClass().getMethod("getWifiApConfiguration");
                netConfig = (WifiConfiguration)wifiApConfigurationMethod.invoke(wifiManager);

                Field wcBand = WifiConfiguration.class.getField("apBand");
                wcBand.setInt(netConfig, 2); // 2Ghz

                Field wcFreq = WifiConfiguration.class.getField("apChannel");
                wcFreq.setInt(netConfig,11); // channel 11
                //wifiManager.saveConfiguration();
            } catch (IllegalAccessException | NoSuchFieldException | NoSuchMethodException | InvocationTargetException e) {
                e.printStackTrace();
            }
        }
    }*/
}
