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

    Copyright 2019-2020 by Garmatin Oleksandr invizible.soft@gmail.com
*/

import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.preference.PreferenceFragmentCompat;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.SettingsActivity;
import pan.alexander.tordnscrypt.utils.file_operations.FileOperations;
import pan.alexander.tordnscrypt.modules.ModulesRestarter;
import pan.alexander.tordnscrypt.utils.PrefManager;

import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;


public class PreferencesTorFragment extends PreferenceFragmentCompat implements Preference.OnPreferenceChangeListener {

    private static ArrayList<String> key_tor;
    private static ArrayList<String> val_tor;
    private ArrayList<String> key_tor_orig;
    private ArrayList<String> val_tor_orig;
    private SharedPreferences sp;
    private String appDataDir;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRetainInstance(true);

        addPreferencesFromResource(R.xml.preferences_tor);

        ArrayList<Preference> preferences = new ArrayList<>();

        preferences.add(findPreference("VirtualAddrNetworkIPv4"));
        preferences.add(findPreference("AvoidDiskWrites"));
        preferences.add(findPreference("ConnectionPadding"));
        preferences.add(findPreference("ReducedConnectionPadding"));
        preferences.add(findPreference("ExcludeExitNodes"));
        preferences.add(findPreference("ExitNodes"));
        preferences.add(findPreference("ExcludeNodes"));
        preferences.add(findPreference("EntryNodes"));
        preferences.add(findPreference("StrictNodes"));
        preferences.add(findPreference("FascistFirewall"));
        preferences.add(findPreference("NewCircuitPeriod"));
        preferences.add(findPreference("MaxCircuitDirtiness"));
        preferences.add(findPreference("EnforceDistinctSubnets"));
        preferences.add(findPreference("Enable SOCKS proxy"));
        preferences.add(findPreference("SOCKSPort"));
        preferences.add(findPreference("Enable HTTPTunnel"));
        preferences.add(findPreference("HTTPTunnelPort"));
        preferences.add(findPreference("Enable Transparent proxy"));
        preferences.add(findPreference("TransPort"));
        preferences.add(findPreference("Enable DNS"));
        preferences.add(findPreference("DNSPort"));
        preferences.add(findPreference("ClientUseIPv4"));
        preferences.add(findPreference("ClientUseIPv6"));

        for (Preference preference: preferences) {
            if (preference != null) {
                preference.setOnPreferenceChangeListener(this);
            } else {
                Log.e(LOG_TAG, "PreferencesTorFragment preference is null exception");
            }
        }


        if (getArguments() != null) {
            key_tor = getArguments().getStringArrayList("key_tor");
            val_tor = getArguments().getStringArrayList("val_tor");
            key_tor_orig = new ArrayList<>(key_tor);
            val_tor_orig = new ArrayList<>(val_tor);
            SettingsActivity.key_tor = key_tor;
            SettingsActivity.val_tor = val_tor;
        }
    }

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {

    }

    @Override
    public void onResume() {
        super.onResume();

        if (getActivity() == null) {
            return;
        }

        getActivity().setTitle(R.string.drawer_menu_TorSettings);

        PathVars pathVars = PathVars.getInstance(getActivity());
        appDataDir = pathVars.getAppDataDir();

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

        if(!isChanged || getActivity() == null) return;

        FileOperations.writeToTextFile(getActivity(),appDataDir+"/app_data/tor/tor.conf",tor_conf,SettingsActivity.tor_conf_tag);

        boolean torRunning = new PrefManager(getActivity()).getBoolPref("Tor Running");

        if (torRunning) {
            ModulesRestarter.restartTor(getActivity());
        }

    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {

        try {
            if(Objects.equals(preference.getKey(), "ExcludeExitNodes")){
                if (Boolean.valueOf(newValue.toString())){
                    key_tor.set(key_tor.indexOf("#ExcludeExitNodes"),"ExcludeExitNodes");
                    FragmentTransaction fTrans;
                    if (getFragmentManager() != null) {
                        fTrans = getFragmentManager().beginTransaction();
                        Fragment frg = new CountrySelectFragment();
                        Bundle bndl = new Bundle();
                        bndl.putInt("nodes_type",CountrySelectFragment.excludeExitNodes);
                        bndl.putString("countries",val_tor.get(key_tor.indexOf("ExcludeExitNodes")));
                        frg.setArguments(bndl);
                        fTrans.replace(android.R.id.content, frg,"CountrySelectFragment");
                        fTrans.commit();
                    }
                } else {
                    key_tor.set(key_tor.indexOf("ExcludeExitNodes"),"#ExcludeExitNodes");
                }
                return true;
            }
            if(Objects.equals(preference.getKey(), "ExitNodes")){
                if (Boolean.valueOf(newValue.toString())){
                    key_tor.set(key_tor.indexOf("#ExitNodes"),"ExitNodes");
                    FragmentTransaction fTrans;
                    if (getFragmentManager() != null) {
                        fTrans = getFragmentManager().beginTransaction();
                        Fragment frg = new CountrySelectFragment();
                        Bundle bndl = new Bundle();
                        bndl.putInt("nodes_type",CountrySelectFragment.exitNodes);
                        bndl.putString("countries",val_tor.get(key_tor.indexOf("ExitNodes")));
                        frg.setArguments(bndl);
                        fTrans.replace(android.R.id.content, frg,"CountrySelectFragment");
                        fTrans.commit();
                    }
                } else {
                    key_tor.set(key_tor.indexOf("ExitNodes"),"#ExitNodes");
                }
                return true;
            }
            if(Objects.equals(preference.getKey(), "ExcludeNodes")){
                if (Boolean.valueOf(newValue.toString())){
                    key_tor.set(key_tor.indexOf("#ExcludeNodes"),"ExcludeNodes");
                    FragmentTransaction fTrans;
                    if (getFragmentManager() != null) {
                        fTrans = getFragmentManager().beginTransaction();
                        Fragment frg = new CountrySelectFragment();
                        Bundle bndl = new Bundle();
                        bndl.putInt("nodes_type",CountrySelectFragment.excludeNodes);
                        bndl.putString("countries",val_tor.get(key_tor.indexOf("ExcludeNodes")));
                        frg.setArguments(bndl);
                        fTrans.replace(android.R.id.content, frg,"CountrySelectFragment");
                        fTrans.commit();
                    }
                } else {
                    key_tor.set(key_tor.indexOf("ExcludeNodes"),"#ExcludeNodes");
                }
                return true;
            }
            if(Objects.equals(preference.getKey(), "EntryNodes")){
                if (Boolean.valueOf(newValue.toString())){
                    key_tor.set(key_tor.indexOf("#EntryNodes"),"EntryNodes");
                    FragmentTransaction fTrans;
                    if (getFragmentManager() != null) {
                        fTrans = getFragmentManager().beginTransaction();
                        Fragment frg = new CountrySelectFragment();
                        Bundle bndl = new Bundle();
                        bndl.putInt("nodes_type",CountrySelectFragment.entryNodes);
                        bndl.putString("countries",val_tor.get(key_tor.indexOf("EntryNodes")));
                        frg.setArguments(bndl);
                        fTrans.replace(android.R.id.content, frg,"CountrySelectFragment");
                        fTrans.commit();
                    }
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
}
