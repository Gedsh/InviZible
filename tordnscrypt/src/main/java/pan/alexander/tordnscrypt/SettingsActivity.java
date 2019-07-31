package pan.alexander.tordnscrypt;
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
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;

import java.util.ArrayList;
import java.util.Objects;

import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.settings.PreferencesCommonFragment;
import pan.alexander.tordnscrypt.settings.PreferencesDNSCryptServersRv;
import pan.alexander.tordnscrypt.settings.PreferencesFastFragment;
import pan.alexander.tordnscrypt.settings.PreferencesTorBridges;
import pan.alexander.tordnscrypt.settings.PreferencesTorFragment;
import pan.alexander.tordnscrypt.settings.ShowRulesRecycleFrag;
import pan.alexander.tordnscrypt.settings.UnlockTorAppsFragment;
import pan.alexander.tordnscrypt.settings.UnlockTorIpsFrag;
import pan.alexander.tordnscrypt.utils.LangAppCompatActivity;
import pan.alexander.tordnscrypt.utils.RootCommands;
import pan.alexander.tordnscrypt.utils.RootExecService;

import static pan.alexander.tordnscrypt.TopFragment.LOG_TAG;


public class SettingsActivity extends LangAppCompatActivity
        implements PreferencesDNSCryptServersRv.OnServersChangeListener {

    SettingsReceiver br = null;
    IntentFilter intentFilter = null;
    public static ArrayList<String> key_tor;
    public static ArrayList<String> val_tor;

    private static PreferencesFastFragment preferencesFastFragment;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);

        PathVars pathVars = new PathVars(this);
        String appDataDir = pathVars.appDataDir;
        String busyboxPath = pathVars.busyboxPath;

        br = new SettingsReceiver();

        if (savedInstanceState!=null) return;

        FragmentTransaction fTrans = getFragmentManager().beginTransaction();
        Intent intent = getIntent();
        Log.d(LOG_TAG,"SettingsActivity getAction " + intent.getAction());

        if(Objects.equals(intent.getAction(), "DNS_Pref")){
            findViewById(R.id.pbSettings).setVisibility(View.VISIBLE);
            ((ProgressBar)findViewById(R.id.pbSettings)).setIndeterminate(true);
            findViewById(R.id.tvSettings).setVisibility(View.VISIBLE);
            String[] commandsCat = {busyboxPath+ "echo 'cat dnscrypt-proxy.toml'",
                    busyboxPath+ "cat "+appDataDir+"/app_data/dnscrypt-proxy/dnscrypt-proxy.toml"};
            RootCommands rootCommands  = new RootCommands(commandsCat);
            intent = new Intent(this, RootExecService.class);
            intent.setAction(RootExecService.RUN_COMMAND);
            intent.putExtra("Commands",rootCommands);
            intent.putExtra("Mark", RootExecService.SettingsActivityMark);
            RootExecService.performAction(this,intent);
        } else if (Objects.equals(intent.getAction(), "Tor_Pref")){
            findViewById(R.id.pbSettings).setVisibility(View.VISIBLE);
            ((ProgressBar)findViewById(R.id.pbSettings)).setIndeterminate(true);
            findViewById(R.id.tvSettings).setVisibility(View.VISIBLE);
            String[] commandsCat = {busyboxPath+ "echo 'cat tor.conf'",
                    busyboxPath+ "cat "+appDataDir+"/app_data/tor/tor.conf"};
            RootCommands rootCommands  = new RootCommands(commandsCat);
            intent = new Intent(this, RootExecService.class);
            intent.setAction(RootExecService.RUN_COMMAND);
            intent.putExtra("Commands",rootCommands);
            intent.putExtra("Mark", RootExecService.SettingsActivityMark);
            RootExecService.performAction(this,intent);
        } else if (Objects.equals(intent.getAction(), "I2PD_Pref")){
            findViewById(R.id.pbSettings).setVisibility(View.VISIBLE);
            ((ProgressBar)findViewById(R.id.pbSettings)).setIndeterminate(true);
            findViewById(R.id.tvSettings).setVisibility(View.VISIBLE);
            String[] commandsCat = {busyboxPath+ "echo 'cat i2pd.conf'",
                    busyboxPath+ "cat "+appDataDir+"/app_data/i2pd/i2pd.conf"};
            RootCommands rootCommands  = new RootCommands(commandsCat);
            intent = new Intent(this, RootExecService.class);
            intent.setAction(RootExecService.RUN_COMMAND);
            intent.putExtra("Commands",rootCommands);
            intent.putExtra("Mark", RootExecService.SettingsActivityMark);
            RootExecService.performAction(this,intent);
        } else if (Objects.equals(intent.getAction(), "fast_Pref")){
            preferencesFastFragment = new PreferencesFastFragment();
            fTrans.replace(android.R.id.content, preferencesFastFragment,"fastSettingsFragment");
            fTrans.commit();
        } else if (Objects.equals(intent.getAction(), "common_Pref")){
            fTrans.replace(android.R.id.content, new PreferencesCommonFragment());
            fTrans.commit();
        } else if (Objects.equals(intent.getAction(), "DNS_servers_Pref")){
            findViewById(R.id.pbSettings).setVisibility(View.VISIBLE);
            ((ProgressBar)findViewById(R.id.pbSettings)).setIndeterminate(true);
            findViewById(R.id.tvSettings).setVisibility(View.VISIBLE);
            String[] commandsCat = { busyboxPath+ "echo 'cat public-resolvers.md'",
                    busyboxPath+ "cat "+appDataDir+"/app_data/dnscrypt-proxy/public-resolvers.md",
                    busyboxPath+ "echo 'cat dnscrypt-proxy.toml'",
                    busyboxPath+ "cat "+appDataDir+"/app_data/dnscrypt-proxy/dnscrypt-proxy.toml"};
            RootCommands rootCommands  = new RootCommands(commandsCat);
            intent = new Intent(this, RootExecService.class);
            intent.setAction(RootExecService.RUN_COMMAND);
            intent.putExtra("Commands",rootCommands);
            intent.putExtra("Mark", RootExecService.SettingsActivityMark);
            RootExecService.performAction(this,intent);
        } else if (Objects.equals(intent.getAction(), "open_qery_log")){
            findViewById(R.id.pbSettings).setVisibility(View.VISIBLE);
            ((ProgressBar)findViewById(R.id.pbSettings)).setIndeterminate(true);
            findViewById(R.id.tvSettings).setVisibility(View.VISIBLE);
            String[] commandsCat = { busyboxPath+ "echo 'cat dnscrypt_proxy_qery.log'",
                    busyboxPath+ "cat "+appDataDir+"/cache/query.log"};
            RootCommands rootCommands  = new RootCommands(commandsCat);
            intent = new Intent(this, RootExecService.class);
            intent.setAction(RootExecService.RUN_COMMAND);
            intent.putExtra("Commands",rootCommands);
            intent.putExtra("Mark", RootExecService.SettingsActivityMark);
            RootExecService.performAction(this,intent);
        }  else if (Objects.equals(intent.getAction(), "open_nx_log")){
            findViewById(R.id.pbSettings).setVisibility(View.VISIBLE);
            ((ProgressBar)findViewById(R.id.pbSettings)).setIndeterminate(true);
            findViewById(R.id.tvSettings).setVisibility(View.VISIBLE);
            String[] commandsCat = { busyboxPath+ "echo 'cat dnscrypt_proxy_nx.log'",
                    busyboxPath+ "cat "+appDataDir+"/cache/nx.log"};
            RootCommands rootCommands  = new RootCommands(commandsCat);
            intent = new Intent(this, RootExecService.class);
            intent.setAction(RootExecService.RUN_COMMAND);
            intent.putExtra("Commands",rootCommands);
            intent.putExtra("Mark", RootExecService.SettingsActivityMark);
            RootExecService.performAction(this,intent);
        } else if (Objects.equals(intent.getAction(), "forwarding_rules_Pref")){
            findViewById(R.id.pbSettings).setVisibility(View.VISIBLE);
            ((ProgressBar)findViewById(R.id.pbSettings)).setIndeterminate(true);
            findViewById(R.id.tvSettings).setVisibility(View.VISIBLE);
            String[] commandsCat = { busyboxPath+ "echo 'cat forwarding_rules.txt'",
                    busyboxPath+ "cat "+appDataDir+"/app_data/dnscrypt-proxy/forwarding-rules.txt"};
            RootCommands rootCommands  = new RootCommands(commandsCat);
            intent = new Intent(this, RootExecService.class);
            intent.setAction(RootExecService.RUN_COMMAND);
            intent.putExtra("Commands",rootCommands);
            intent.putExtra("Mark", RootExecService.SettingsActivityMark);
            RootExecService.performAction(this,intent);
        } else if (Objects.equals(intent.getAction(), "cloaking_rules_Pref")){
            findViewById(R.id.pbSettings).setVisibility(View.VISIBLE);
            ((ProgressBar)findViewById(R.id.pbSettings)).setIndeterminate(true);
            findViewById(R.id.tvSettings).setVisibility(View.VISIBLE);
            String[] commandsCat = { busyboxPath+ "echo 'cat cloaking_rules.txt'",
                    busyboxPath+ "cat "+appDataDir+"/app_data/dnscrypt-proxy/cloaking-rules.txt"};
            RootCommands rootCommands  = new RootCommands(commandsCat);
            intent = new Intent(this, RootExecService.class);
            intent.setAction(RootExecService.RUN_COMMAND);
            intent.putExtra("Commands",rootCommands);
            intent.putExtra("Mark", RootExecService.SettingsActivityMark);
            RootExecService.performAction(this,intent);
        } else if (Objects.equals(intent.getAction(), "blacklist_Pref")){
            findViewById(R.id.pbSettings).setVisibility(View.VISIBLE);
            ((ProgressBar)findViewById(R.id.pbSettings)).setIndeterminate(true);
            findViewById(R.id.tvSettings).setVisibility(View.VISIBLE);
            String[] commandsCat = { busyboxPath+ "echo 'cat blacklist.txt'",
                    busyboxPath+ "cat "+appDataDir+"/app_data/dnscrypt-proxy/blacklist.txt"};
            RootCommands rootCommands  = new RootCommands(commandsCat);
            intent = new Intent(this, RootExecService.class);
            intent.setAction(RootExecService.RUN_COMMAND);
            intent.putExtra("Commands",rootCommands);
            intent.putExtra("Mark", RootExecService.SettingsActivityMark);
            RootExecService.performAction(this,intent);
        } else if (Objects.equals(intent.getAction(), "ipblacklist_Pref")){
            findViewById(R.id.pbSettings).setVisibility(View.VISIBLE);
            ((ProgressBar)findViewById(R.id.pbSettings)).setIndeterminate(true);
            findViewById(R.id.tvSettings).setVisibility(View.VISIBLE);
            String[] commandsCat = { busyboxPath+ "echo 'cat ip-blacklist.txt'",
                    busyboxPath+ "cat "+appDataDir+"/app_data/dnscrypt-proxy/ip-blacklist.txt"};
            RootCommands rootCommands  = new RootCommands(commandsCat);
            intent = new Intent(this, RootExecService.class);
            intent.setAction(RootExecService.RUN_COMMAND);
            intent.putExtra("Commands",rootCommands);
            intent.putExtra("Mark", RootExecService.SettingsActivityMark);
            RootExecService.performAction(this,intent);
        } else if (Objects.equals(intent.getAction(), "whitelist_Pref")){
            findViewById(R.id.pbSettings).setVisibility(View.VISIBLE);
            ((ProgressBar)findViewById(R.id.pbSettings)).setIndeterminate(true);
            findViewById(R.id.tvSettings).setVisibility(View.VISIBLE);
            String[] commandsCat = { busyboxPath+ "echo 'cat whitelist.txt'",
                    busyboxPath+ "cat "+appDataDir+"/app_data/dnscrypt-proxy/whitelist.txt"};
            RootCommands rootCommands  = new RootCommands(commandsCat);
            intent = new Intent(this, RootExecService.class);
            intent.setAction(RootExecService.RUN_COMMAND);
            intent.putExtra("Commands",rootCommands);
            intent.putExtra("Mark", RootExecService.SettingsActivityMark);
            RootExecService.performAction(this,intent);
        } else if (Objects.equals(intent.getAction(), "pref_itpd_addressbook_subscriptions")){
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
            ArrayList<String> rules_file = new ArrayList<>();
            String[] arr = sp.getString("subscriptions", "").split(",");
            rules_file.add("subscriptions");
            for (String str:arr){
                rules_file.add(str.trim());
            }
            fTrans = getFragmentManager().beginTransaction();
            Bundle bundle = new Bundle();
            bundle.putStringArrayList("rules_file",rules_file);
            ShowRulesRecycleFrag frag = new ShowRulesRecycleFrag();
            frag.setArguments(bundle);
            fTrans.replace(android.R.id.content, frag);
            fTrans.commit();
        } else if (Objects.equals(intent.getAction(), "tor_sites_unlock")){
            Bundle bundle = new Bundle();
            bundle.putString("deviceOrTether","device");
            UnlockTorIpsFrag unlockTorIpsFrag = new UnlockTorIpsFrag();
            unlockTorIpsFrag.setArguments(bundle);
            fTrans.replace(android.R.id.content, unlockTorIpsFrag);
            fTrans.commit();
        } else if (Objects.equals(intent.getAction(), "tor_sites_unlock_tether")){
            Bundle bundle = new Bundle();
            bundle.putString("deviceOrTether","tether");
            UnlockTorIpsFrag unlockTorIpsFrag = new UnlockTorIpsFrag();
            unlockTorIpsFrag.setArguments(bundle);
            fTrans.replace(android.R.id.content, unlockTorIpsFrag);
            fTrans.commit();
        } else if (Objects.equals(intent.getAction(), "tor_apps_unlock")){
            fTrans.replace(android.R.id.content, new UnlockTorAppsFragment());
            fTrans.commit();
        } else if (Objects.equals(intent.getAction(), "tor_bridges")){
            fTrans.replace(android.R.id.content, new PreferencesTorBridges(),"PreferencesTorBridges");
            fTrans.commit();
        }

    }

    @Override
    public void onResume() {
        super.onResume();
        intentFilter = new IntentFilter(RootExecService.COMMAND_RESULT);
        this.registerReceiver(br,intentFilter);
    }

    @Override
    public void onAttachFragment(android.app.Fragment fragment) {
        super.onAttachFragment(fragment);

        if (fragment instanceof PreferencesDNSCryptServersRv) {
            PreferencesDNSCryptServersRv preferencesDNSCryptServersRv = (PreferencesDNSCryptServersRv)fragment;
            preferencesDNSCryptServersRv.setOnServersChangeListener(this);
        }
    }

    @Override
    public void onServersChange() {
        //Toast.makeText(this,"dfgdgg",Toast.LENGTH_LONG).show();
        if (preferencesFastFragment!=null)
            preferencesFastFragment.setDnsCryptServersSumm();
    }

    @Override
    public void onPause() {
        super.onPause();
        try{
            this.unregisterReceiver(br);
        } catch (IllegalArgumentException e){
            e.printStackTrace();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {// API 5+ solution
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        Fragment frgCountry = getFragmentManager().findFragmentByTag("CountrySelectFragment");
        if (frgCountry!=null && frgCountry.isVisible() && key_tor!=null && val_tor!=null){
            Bundle bundle = new Bundle();
            bundle.putStringArrayList("key_tor",key_tor);
            bundle.putStringArrayList("val_tor",val_tor);
            PreferencesTorFragment frag = new PreferencesTorFragment();
            frag.setArguments(bundle);
            FragmentTransaction fTrans = getFragmentManager().beginTransaction();
            fTrans.replace(android.R.id.content, frag);
            fTrans.commit();
            return;
        }
        super.onBackPressed();
    }
}
