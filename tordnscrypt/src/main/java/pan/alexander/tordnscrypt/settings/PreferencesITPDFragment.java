package pan.alexander.tordnscrypt.settings;
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


import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Objects;

import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.utils.NoRootService;
import pan.alexander.tordnscrypt.utils.PrefManager;
import pan.alexander.tordnscrypt.utils.RootCommands;
import pan.alexander.tordnscrypt.utils.RootExecService;

public class PreferencesITPDFragment extends PreferenceFragment implements Preference.OnPreferenceChangeListener {

    static ArrayList<String> key_itpd;
    static ArrayList<String> val_itpd;
    static ArrayList<String> key_itpd_orig;
    static ArrayList<String> val_itpd_orig;
    String appDataDir;
    String itpdPath;
    String busyboxPath;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);

        addPreferencesFromResource(R.xml.preferences_i2pd);

        findPreference("Allow incoming connections").setOnPreferenceChangeListener(this);
        findPreference("incoming port").setOnPreferenceChangeListener(this);
        findPreference("incoming host").setOnPreferenceChangeListener(this);
        findPreference("ipv4").setOnPreferenceChangeListener(this);
        findPreference("ipv6").setOnPreferenceChangeListener(this);
        findPreference("notransit").setOnPreferenceChangeListener(this);
        findPreference("floodfill").setOnPreferenceChangeListener(this);
        findPreference("bandwidth").setOnPreferenceChangeListener(this);
        findPreference("share").setOnPreferenceChangeListener(this);
        findPreference("ssu").setOnPreferenceChangeListener(this);
        findPreference("ntcp").setOnPreferenceChangeListener(this);
        findPreference("Enable ntcpproxy").setOnPreferenceChangeListener(this);
        findPreference("ntcpproxy").setOnPreferenceChangeListener(this);
        findPreference("HTTP proxy").setOnPreferenceChangeListener(this);
        findPreference("HTTP proxy port").setOnPreferenceChangeListener(this);
        findPreference("Socks proxy").setOnPreferenceChangeListener(this);
        findPreference("Socks proxy port").setOnPreferenceChangeListener(this);
        findPreference("SAM interface").setOnPreferenceChangeListener(this);
        findPreference("SAM interface port").setOnPreferenceChangeListener(this);
        findPreference("elgamal").setOnPreferenceChangeListener(this);
        findPreference("UPNP").setOnPreferenceChangeListener(this);
        findPreference("ntcp2 enabled").setOnPreferenceChangeListener(this);
        findPreference("verify").setOnPreferenceChangeListener(this);
        findPreference("transittunnels").setOnPreferenceChangeListener(this);
        findPreference("openfiles").setOnPreferenceChangeListener(this);
        findPreference("coresize").setOnPreferenceChangeListener(this);
        findPreference("ntcpsoft").setOnPreferenceChangeListener(this);
        findPreference("ntcphard").setOnPreferenceChangeListener(this);
        findPreference("defaulturl").setOnPreferenceChangeListener(this);

        key_itpd = getArguments().getStringArrayList("key_itpd");
        val_itpd = getArguments().getStringArrayList("val_itpd");
        key_itpd_orig = new ArrayList<>(key_itpd);
        val_itpd_orig = new ArrayList<>(val_itpd);
    }

    @Override
    public void onResume() {
        super.onResume();
        getActivity().setTitle(R.string.drawer_menu_I2PDSettings);

        PathVars pathVars = new PathVars(getActivity());
        appDataDir = pathVars.appDataDir;
        itpdPath = pathVars.itpdPath;
        busyboxPath = pathVars.busyboxPath;
    }

    public void onDestroy() {
        super.onDestroy();

        StringBuilder itpd_conf = new StringBuilder();
        boolean isChanged = false;
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity());

        if (key_itpd.indexOf("subscriptions")>=0)
            val_itpd.set(key_itpd.indexOf("subscriptions"),sp.getString("subscriptions",""));

        for (int i=0;i<key_itpd.size();i++){
            if(!(key_itpd_orig.get(i).equals(key_itpd.get(i))&&val_itpd_orig.get(i).equals(val_itpd.get(i)))&&!isChanged){
                //Toast.makeText(getActivity(),key_itpd.get(i)+" "+key_itpd.get(i-1),Toast.LENGTH_LONG).show();
                isChanged = true;
            }

            switch (key_itpd.get(i)) {
                case "incoming host":
                    key_itpd.set(i, "host");
                    break;
                case "incoming port":
                    key_itpd.set(i, "port");
                    break;
                case "ntcp2 enabled":
                    key_itpd.set(i, "enabled");
                    break;
                case "http enabled":
                    key_itpd.set(i, "enabled");
                    break;
                case "HTTP proxy":
                    key_itpd.set(i, "enabled");
                    break;
                case "HTTP proxy port":
                    key_itpd.set(i, "port");
                    break;
                case "Socks proxy":
                    key_itpd.set(i, "enabled");
                    break;
                case "Socks proxy port":
                    key_itpd.set(i, "port");
                    break;
                case "SAM interface":
                    key_itpd.set(i, "enabled");
                    break;
                case "SAM interface port":
                    key_itpd.set(i, "port");
                    break;
                case "UPNP":
                    key_itpd.set(i, "enabled");
                    break;
            }

            if(val_itpd.get(i).isEmpty()){
                itpd_conf.append(key_itpd.get(i)).append((char)10);
            } else {
                String val = val_itpd.get(i).replace("\"","\\\"");
                itpd_conf.append(key_itpd.get(i)).append(" = ").append(val).append((char)10);
            }

        }

        if(!isChanged) return;

        SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(getActivity());
        boolean rnI2PDWithRoot = shPref.getBoolean("swUseModulesRoot",false);
        boolean itpdRunning = new PrefManager(getActivity()).getBoolPref("I2PD Running");
        String[] commandsEcho;
        if (rnI2PDWithRoot) {
            commandsEcho = new String[] { busyboxPath+ "echo 'overwrite itpd.conf'",
                    busyboxPath+ "echo \"" + itpd_conf.toString()+"\" > "+appDataDir+"/app_data/i2pd/i2pd.conf",
                    busyboxPath+ "chmod 644 "+appDataDir+"/app_data/i2pd/i2pd.conf",
                    busyboxPath+ "sleep 1",
                    busyboxPath+ "killall i2pd; if [[ $? -eq 0 ]] ; " +
                            "then "+itpdPath+" --conf "+appDataDir+"/app_data/i2pd/i2pd.conf --datadir /data/media/0/i2pd & fi"};
        } else {
            commandsEcho = new String[] { busyboxPath+ "echo 'overwrite itpd.conf'",
                    busyboxPath+ "echo \"" + itpd_conf.toString()+"\" > "+appDataDir+"/app_data/i2pd/i2pd.conf",
                    busyboxPath+ "chmod 644 "+appDataDir+"/app_data/i2pd/i2pd.conf",
                    busyboxPath+ "sleep 1",
                    busyboxPath+ "killall i2pd"};
            if (itpdRunning)
                runITPDNoRoot();
        }


        RootCommands rootCommands  = new RootCommands(commandsEcho);
        Intent intent = new Intent(getActivity(), RootExecService.class);
        intent.setAction(RootExecService.RUN_COMMAND);
        intent.putExtra("Commands",rootCommands);
        intent.putExtra("Mark", RootExecService.SettingsActivityMark);
        RootExecService.performAction(getActivity(),intent);
        Toast.makeText(getActivity(),getText(R.string.toastSettings_saved),Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        try {
            if(Objects.equals(preference.getKey(), "Allow incoming connections")){
                if (Boolean.valueOf(newValue.toString())){
                    key_itpd.set(key_itpd.indexOf("#host"),"incoming host");
                    key_itpd.set(key_itpd.indexOf("#port"),"incoming port");
                } else {
                    key_itpd.set(key_itpd.indexOf("incoming host"),"#host");
                    key_itpd.set(key_itpd.indexOf("incoming port"),"#port");
                }
                return true;
            } else if (Objects.equals(preference.getKey(), "Enable ntcpproxy")){
                if (Boolean.valueOf(newValue.toString())){
                    key_itpd.set(key_itpd.indexOf("#ntcpproxy"),"ntcpproxy");
                } else {
                    key_itpd.set(key_itpd.indexOf("ntcpproxy"),"#ntcpproxy");
                }
                return true;
            }

            if (key_itpd.contains(preference.getKey().trim())) {
                val_itpd.set(key_itpd.indexOf(preference.getKey()),newValue.toString());
                return true;
            } else {
                Toast.makeText(getActivity(),R.string.pref_itpd_not_exist,Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getActivity(),R.string.wrong,Toast.LENGTH_LONG).show();
        }


        return false;
    }

    private void runITPDNoRoot() {
        SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(getActivity());
        boolean showNotification = shPref.getBoolean("swShowNotification",true);
        Intent intent = new Intent(getActivity(), NoRootService.class);
        intent.setAction(NoRootService.actionStartITPD);
        intent.putExtra("showNotification",showNotification);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getActivity().startForegroundService(intent);
        } else {
            getActivity().startService(intent);
        }
    }
}
