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


import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import pan.alexander.tordnscrypt.CountrySelectFragment;
import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.SettingsActivity;
import pan.alexander.tordnscrypt.utils.FileOperations;
import pan.alexander.tordnscrypt.utils.NoRootService;
import pan.alexander.tordnscrypt.utils.PrefManager;
import pan.alexander.tordnscrypt.utils.RootCommands;
import pan.alexander.tordnscrypt.utils.RootExecService;

import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;


public class PreferencesTorFragment extends PreferenceFragment implements Preference.OnPreferenceChangeListener {

    static ArrayList<String> key_tor;
    static ArrayList<String> val_tor;
    ArrayList<String> key_tor_orig;
    ArrayList<String> val_tor_orig;
    SharedPreferences sp;
    String appDataDir;
    String torPath;
    String busyboxPath;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRetainInstance(true);

        addPreferencesFromResource(R.xml.preferences_tor);

        findPreference("VirtualAddrNetworkIPv4").setOnPreferenceChangeListener(this);
        findPreference("AvoidDiskWrites").setOnPreferenceChangeListener(this);
        findPreference("ConnectionPadding").setOnPreferenceChangeListener(this);
        findPreference("ReducedConnectionPadding").setOnPreferenceChangeListener(this);
        findPreference("ExcludeExitNodes").setOnPreferenceChangeListener(this);
        findPreference("ExitNodes").setOnPreferenceChangeListener(this);
        findPreference("ExcludeNodes").setOnPreferenceChangeListener(this);
        findPreference("EntryNodes").setOnPreferenceChangeListener(this);
        findPreference("StrictNodes").setOnPreferenceChangeListener(this);
        findPreference("FascistFirewall").setOnPreferenceChangeListener(this);
        findPreference("NewCircuitPeriod").setOnPreferenceChangeListener(this);
        findPreference("MaxCircuitDirtiness").setOnPreferenceChangeListener(this);
        findPreference("EnforceDistinctSubnets").setOnPreferenceChangeListener(this);
        findPreference("Enable SOCKS proxy").setOnPreferenceChangeListener(this);
        findPreference("SOCKSPort").setOnPreferenceChangeListener(this);
        findPreference("Enable HTTPTunnel").setOnPreferenceChangeListener(this);
        findPreference("HTTPTunnelPort").setOnPreferenceChangeListener(this);
        findPreference("Enable Transparent proxy").setOnPreferenceChangeListener(this);
        findPreference("TransPort").setOnPreferenceChangeListener(this);
        findPreference("Enable DNS").setOnPreferenceChangeListener(this);
        findPreference("DNSPort").setOnPreferenceChangeListener(this);
        findPreference("ClientUseIPv4").setOnPreferenceChangeListener(this);
        findPreference("ClientUseIPv6").setOnPreferenceChangeListener(this);


        key_tor = getArguments().getStringArrayList("key_tor");
        val_tor = getArguments().getStringArrayList("val_tor");
        key_tor_orig = new ArrayList<>(key_tor);
        val_tor_orig = new ArrayList<>(val_tor);
        SettingsActivity.key_tor = key_tor;
        SettingsActivity.val_tor = val_tor;
    }

    @Override
    public void onResume() {
        super.onResume();
        getActivity().setTitle(R.string.drawer_menu_TorSettings);

        PathVars pathVars = new PathVars(getActivity());
        appDataDir = pathVars.appDataDir;
        torPath = pathVars.torPath;
        busyboxPath = pathVars.busyboxPath;

        //PreferenceManager.setDefaultValues(getActivity(),R.xml.preferences_tor,false);

        sp = PreferenceManager.getDefaultSharedPreferences(getActivity());
    }

    @Override
    public void onStop() {
        super.onStop();

        try {
            if (key_tor.contains("ExcludeExitNodes"))
                val_tor.set(key_tor.indexOf("ExcludeExitNodes"),sp.getString("ExcludeExitNodesCountries",""));
            if (key_tor.contains("ExitNodes"))
                val_tor.set(key_tor.indexOf("ExitNodes"),sp.getString("ExitNodesCountries",""));
            if (key_tor.contains("EntryNodes"))
                val_tor.set(key_tor.indexOf("EntryNodes"),sp.getString("EntryNodesCountries",""));
            if (key_tor.contains("ExcludeNodes"))
                val_tor.set(key_tor.indexOf("ExcludeNodes"),sp.getString("ExcludeNodesCountries",""));
        } catch (Exception e){
            Log.e(LOG_TAG,"PreferenceTorFragment onStop exception "+e.getMessage());
            Toast.makeText(getActivity(),R.string.wrong,Toast.LENGTH_LONG).show();
        }




        List<String> tor_conf = new LinkedList<>();
        boolean isChanged = false;
        for (int i=0;i<key_tor.size();i++){

            if(!(key_tor_orig.get(i).equals(key_tor.get(i))&&val_tor_orig.get(i).equals(val_tor.get(i)))&&!isChanged){
                isChanged = true;
            }

            if(val_tor.get(i).isEmpty()){
                tor_conf.add(key_tor.get(i));
            } else {
                String val = val_tor.get(i);
                if (val.equals("true")) val = "1";
                if (val.equals("false")) val = "0";
                tor_conf.add(key_tor.get(i) + " " + val);
            }

        }

        if(!isChanged) return;

        FileOperations.writeToTextFile(getActivity(),appDataDir+"/app_data/tor/tor.conf",tor_conf,SettingsActivity.tor_conf_tag);

        SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(getActivity());
        boolean rnTorWithRoot = shPref.getBoolean("swUseModulesRoot",false);
        boolean torRunning = new PrefManager(getActivity()).getBoolPref("Tor Running");
        String[] commandsEcho;
        if (rnTorWithRoot) {
            commandsEcho = new String[] {
                    busyboxPath+ "killall tor; if [[ $? -eq 0 ]] ; " +
                            "then "+torPath+" -f "+appDataDir+"/app_data/tor/tor.conf; fi"
            };
        } else {
            commandsEcho = new String[] {
                    busyboxPath+ "killall tor"
            };
            if (torRunning)
                runTorNoRoot();
        }


        RootCommands rootCommands  = new RootCommands(commandsEcho);
        Intent intent = new Intent(getActivity(), RootExecService.class);
        intent.setAction(RootExecService.RUN_COMMAND);
        intent.putExtra("Commands",rootCommands);
        intent.putExtra("Mark", RootExecService.SettingsActivityMark);
        RootExecService.performAction(getActivity(),intent);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {

        try {
            if(Objects.equals(preference.getKey(), "ExcludeExitNodes")){
                if (Boolean.valueOf(newValue.toString())){
                    key_tor.set(key_tor.indexOf("#ExcludeExitNodes"),"ExcludeExitNodes");
                    FragmentTransaction fTrans = getActivity().getFragmentManager().beginTransaction();
                    Fragment frg = new CountrySelectFragment();
                    Bundle bndl = new Bundle();
                    bndl.putInt("nodes_type",CountrySelectFragment.excludeExitNodes);
                    bndl.putString("countries",val_tor.get(key_tor.indexOf("ExcludeExitNodes")));
                    frg.setArguments(bndl);
                    fTrans.replace(android.R.id.content, frg,"CountrySelectFragment");
                    fTrans.commit();
                } else {
                    key_tor.set(key_tor.indexOf("ExcludeExitNodes"),"#ExcludeExitNodes");
                }
                return true;
            }
            if(Objects.equals(preference.getKey(), "ExitNodes")){
                if (Boolean.valueOf(newValue.toString())){
                    key_tor.set(key_tor.indexOf("#ExitNodes"),"ExitNodes");
                    FragmentTransaction fTrans = getActivity().getFragmentManager().beginTransaction();
                    Fragment frg = new CountrySelectFragment();
                    Bundle bndl = new Bundle();
                    bndl.putInt("nodes_type",CountrySelectFragment.exitNodes);
                    bndl.putString("countries",val_tor.get(key_tor.indexOf("ExitNodes")));
                    frg.setArguments(bndl);
                    fTrans.replace(android.R.id.content, frg,"CountrySelectFragment");
                    fTrans.commit();
                } else {
                    key_tor.set(key_tor.indexOf("ExitNodes"),"#ExitNodes");
                }
                return true;
            }
            if(Objects.equals(preference.getKey(), "ExcludeNodes")){
                if (Boolean.valueOf(newValue.toString())){
                    key_tor.set(key_tor.indexOf("#ExcludeNodes"),"ExcludeNodes");
                    FragmentTransaction fTrans = getActivity().getFragmentManager().beginTransaction();
                    Fragment frg = new CountrySelectFragment();
                    Bundle bndl = new Bundle();
                    bndl.putInt("nodes_type",CountrySelectFragment.excludeNodes);
                    bndl.putString("countries",val_tor.get(key_tor.indexOf("ExcludeNodes")));
                    frg.setArguments(bndl);
                    fTrans.replace(android.R.id.content, frg,"CountrySelectFragment");
                    fTrans.commit();
                } else {
                    key_tor.set(key_tor.indexOf("ExcludeNodes"),"#ExcludeNodes");
                }
                return true;
            }
            if(Objects.equals(preference.getKey(), "EntryNodes")){
                if (Boolean.valueOf(newValue.toString())){
                    key_tor.set(key_tor.indexOf("#EntryNodes"),"EntryNodes");
                    FragmentTransaction fTrans = getActivity().getFragmentManager().beginTransaction();
                    Fragment frg = new CountrySelectFragment();
                    Bundle bndl = new Bundle();
                    bndl.putInt("nodes_type",CountrySelectFragment.entryNodes);
                    bndl.putString("countries",val_tor.get(key_tor.indexOf("EntryNodes")));
                    frg.setArguments(bndl);
                    fTrans.replace(android.R.id.content, frg,"CountrySelectFragment");
                    fTrans.commit();
                } else {
                    key_tor.set(key_tor.indexOf("EntryNodes"),"#EntryNodes");
                }
                return true;
            }
            if(Objects.equals(preference.getKey(), "Enable SOCKS proxy")){
                if(Boolean.valueOf(newValue.toString())){
                    key_tor.set(key_tor.indexOf("#SOCKSPort"),"SOCKSPort");
                } else {
                    key_tor.set(key_tor.indexOf("SOCKSPort"),"#SOCKSPort");
                }
                return true;
            }
            if(Objects.equals(preference.getKey(), "Enable HTTPTunnel")){
                if(Boolean.valueOf(newValue.toString())){
                    key_tor.set(key_tor.indexOf("#HTTPTunnelPort"),"HTTPTunnelPort");
                } else {
                    key_tor.set(key_tor.indexOf("HTTPTunnelPort"),"#HTTPTunnelPort");
                }
                return true;
            }
            if(Objects.equals(preference.getKey(), "Enable Transparent proxy")){
                if(Boolean.valueOf(newValue.toString())){
                    key_tor.set(key_tor.indexOf("#TransPort"),"TransPort");
                } else {
                    key_tor.set(key_tor.indexOf("TransPort"),"#TransPort");
                }
                return true;
            }
            if(Objects.equals(preference.getKey(), "Enable DNS")){
                if(Boolean.valueOf(newValue.toString())){
                    key_tor.set(key_tor.indexOf("#DNSPort"),"DNSPort");
                } else {
                    key_tor.set(key_tor.indexOf("DNSPort"),"#DNSPort");
                }
                return true;
            }

            if (key_tor.contains(preference.getKey().trim())) {
                val_tor.set(key_tor.indexOf(preference.getKey()),newValue.toString());
                return true;
            } else {
                Toast.makeText(getActivity(),R.string.pref_tor_not_exist,Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e){
            Log.e(LOG_TAG,"PreferenceTorFragment OnPreferenceChange exception "+e.getMessage());
            Toast.makeText(getActivity(),R.string.wrong,Toast.LENGTH_LONG).show();
        }


        return false;
    }

    private void runTorNoRoot() {
        SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(getActivity());
        boolean showNotification = shPref.getBoolean("swShowNotification",true);
        Intent intent = new Intent(getActivity(), NoRootService.class);
        intent.setAction(NoRootService.actionStartTor);
        intent.putExtra("showNotification",showNotification);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getActivity().startForegroundService(intent);
        } else {
            getActivity().startService(intent);
        }
    }
}
