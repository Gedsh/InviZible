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

import android.app.FragmentTransaction;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;

import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.settings.PreferencesDNSCryptServersRv;
import pan.alexander.tordnscrypt.settings.PreferencesDNSFragment;
import pan.alexander.tordnscrypt.settings.PreferencesITPDFragment;
import pan.alexander.tordnscrypt.settings.PreferencesTorFragment;
import pan.alexander.tordnscrypt.settings.ShowLogFragment;
import pan.alexander.tordnscrypt.settings.ShowRulesRecycleFrag;
import pan.alexander.tordnscrypt.utils.RootCommands;
import pan.alexander.tordnscrypt.utils.RootExecService;

import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;

public class SettingsReceiver extends BroadcastReceiver {


    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(LOG_TAG,"SettingsActivity onReceive");

        if(!(context instanceof SettingsActivity))
            return;

        AppCompatActivity settingsActivity = (AppCompatActivity) context;

        PathVars pathVars = new PathVars(context);
        String appDataDir = pathVars.appDataDir;

        if (intent != null) {
            final String action = intent.getAction();
            if ((action == null) || (action.equals("") || intent.getIntExtra("Mark",0)!=
                    RootExecService.SettingsActivityMark)) return;

            if(action.equals(RootExecService.COMMAND_RESULT)){
                RootCommands comResult = (RootCommands) intent.getSerializableExtra("CommandsResult");

                if(comResult.getCommands().length == 0){
                    Toast.makeText(context,R.string.wrong,Toast.LENGTH_LONG).show();
                    return;
                }

                /////////////////////////////////////////////////////////////////////////////////
                //////////////// Select DNSCrypt  Servers /////////////////////////////////////////////
                ///////////////////////////////////////////////////////////////////////////////

                if(comResult.getCommands()[0].equalsIgnoreCase("cat public-resolvers.md")){
                    StringBuilder sb = new StringBuilder();
                    boolean lockServer = false;
                    boolean lockMD = false;
                    boolean lockTOML = false;
                    ArrayList<String> dnsServerNames = new ArrayList<>();
                    ArrayList<String> dnsServerDescr = new ArrayList<>();
                    ArrayList<String> dnsServerSDNS = new ArrayList<>();
                    ArrayList<String> dnscrypt_proxy_toml = new ArrayList<>();
                    ArrayList<String> dnscrypt_servers = new ArrayList<>();
                    String prevCom = ""; //to remove empty end of file dnscrypt-proxy.toml
                    for (String com:comResult.getCommands()){

                        if(com.contains("cat public-resolvers.md")){
                            lockMD = true;
                            lockTOML = false;
                        }
                        if(com.contains("cat dnscrypt-proxy.toml")){
                            lockMD = false;
                            lockTOML = true;
                        }

                        if((com.contains("##") || lockServer) && lockMD){
                            if(com.contains("##")){
                                lockServer = true;
                                dnsServerNames.add(com.substring(2).replaceAll("\\s+","").trim());
                            } else if (com.contains("sdns")) {
                                dnsServerSDNS.add(com.replace("sdns://","").trim());
                                lockServer = false;
                                dnsServerDescr.add(sb.toString());
                                sb.setLength(0);
                            } else if(!com.contains("##") || lockServer) {
                                sb.append(com).append((char)10);
                            }
                        }
                        if (lockTOML){
                            if(!com.contains("cat dnscrypt-proxy.toml")&&!(com.isEmpty()&&prevCom.isEmpty())){
                                dnscrypt_proxy_toml.add(com);
                                if(com.contains("server_names")){
                                    String temp = com.substring(com.indexOf("[")+1,com.indexOf("]")).trim();
                                    dnscrypt_servers = new ArrayList<>(Arrays.asList(temp.trim().split(", ?")));
                                }

                            }
                            prevCom = com;

                        }

                    }

                    for (int i=0;i<dnscrypt_servers.size();i++){
                        dnscrypt_servers.set(i,dnscrypt_servers.get(i).trim().replace("\"","").trim());
                    }


                    //Check equals server names
                    for (int i=0;i<dnsServerNames.size();i++) {
                        for (int j=0;j<dnsServerNames.size();j++){
                            String dnsServerName = dnsServerNames.get(i);
                            if (dnsServerName.equals(dnsServerNames.get(j)) && j!=i) {
                                dnsServerNames.set(j,dnsServerName+"_repeat_server"+j);
                            }

                        }

                    }

                            /*sb.setLength(0);
                            for (String com:dnscrypt_servers){
                                sb.append(com).append((char)10);
                            }
                            TopFragment.NotificationDialogFragment commandResult = TopFragment.NotificationDialogFragment.newInstance(sb.toString());
                            commandResult.show(getFragmentManager(),TopFragment.NotificationDialogFragment.TAG_NOT_FRAG);
                            */

                    settingsActivity.findViewById(R.id.pbSettings).setVisibility(View.GONE);
                    settingsActivity.findViewById(R.id.tvSettings).setVisibility(View.GONE);
                    FragmentTransaction fTrans = settingsActivity.getFragmentManager().beginTransaction();
                    Bundle bundle = new Bundle();
                    bundle.putStringArrayList("dnsServerNames",dnsServerNames);
                    bundle.putStringArrayList("dnsServerDescr",dnsServerDescr);
                    bundle.putStringArrayList("dnsServerSDNS",dnsServerSDNS);
                    bundle.putStringArrayList("dnscrypt_proxy_toml",dnscrypt_proxy_toml);
                    bundle.putStringArrayList("dnscrypt_servers",dnscrypt_servers);
                    PreferencesDNSCryptServersRv frag = new PreferencesDNSCryptServersRv();
                    frag.setArguments(bundle);
                    fTrans.replace(android.R.id.content, frag);
                    fTrans.commit();
                }

                /////////////////////////////////////////////////////////////////////////////////
                //////////////// DNS Crypt Settings /////////////////////////////////////////////
                ///////////////////////////////////////////////////////////////////////////////
                if(comResult.getCommands()[0].equalsIgnoreCase("cat dnscrypt-proxy.toml")){
                    ArrayList<String> key_toml = new ArrayList<>();
                    ArrayList<String> val_toml = new ArrayList<>();
                    String prevCom = "";//to remove empty end of file dnscrypt-proxy.toml
                    String key = "";
                    String val = "";
                    //StringBuilder sb = new StringBuilder()
                    SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
                    SharedPreferences.Editor editor = sp.edit();
                    for(String com:comResult.getCommands()){
                        if(!com.contains("cat dnscrypt-proxy.toml")&&!(com.isEmpty()&&prevCom.isEmpty())){
                            if(com.contains("=")){
                                key = com.substring(0,com.indexOf("=")).trim();
                                val = com.substring(com.indexOf("=")+1).trim();
                            } else {
                                key = com;
                                val = "";
                            }
                            key_toml.add(key);
                            val_toml.add(val);
                            //sb.append(key).append((char)10);
                        }
                        prevCom = com;

                        if (key.equals("listen_addresses")){
                            key = "listen_port";
                            val = val.substring(val.indexOf(":")+1,val.indexOf("\"",3)).trim();
                        }
                        if (key.equals("fallback_resolver")) val = val.substring(val.indexOf("\"")+1,val.indexOf(":")).trim();
                        if (key.equals("proxy")){
                            key = "proxy_port";
                            val = val.substring(val.indexOf(":",10)+1,val.indexOf("\"",10)).trim();
                        }
                        if (key.equals("cache")) key = "Enable DNS cache";
                        if (key.equals("urls")) key = "Sources";

                        String val_saved_str = "";
                        boolean val_saved_bool = false;
                        boolean isbool = false;

                        try {
                            val_saved_str = sp.getString(key,"").trim();
                        } catch (ClassCastException e){
                            isbool = true;
                            val_saved_bool = sp.getBoolean(key,false);
                        }


                        if (!val_saved_str.isEmpty()&&!val_saved_str.equals(val)&&!isbool){
                            editor.putString(key,val);
                        }
                        if (isbool&&val_saved_bool!=Boolean.valueOf(val)){
                            editor.putBoolean(key,Boolean.valueOf(val));
                        }

                        if (key.equals("#proxy")&&sp.getBoolean("Enable proxy",false)){
                            editor.putBoolean("Enable proxy",false);
                        }
                        if (key.equals("proxy_port")&&!sp.getBoolean("Enable proxy",false)){
                            editor.putBoolean("Enable proxy",true);
                        }
                        if (val.contains(appDataDir+"/cache/query.log")&&!key.contains("#")&&!sp.getBoolean("Enable Query logging",false)){
                            editor.putBoolean("Enable Query logging",true);
                        }
                        if (val.contains(appDataDir+"/cache/query.log")&&key.contains("#")&&sp.getBoolean("Enable Query logging",false)){
                            editor.putBoolean("Enable Query logging",false);
                        }
                        if (val.contains(appDataDir+"/cache/nx.log")&&!key.contains("#")&&!sp.getBoolean("Enable Query logging",false)){
                            editor.putBoolean("Enable Suspicious logging",true);
                        }
                        if (val.contains(appDataDir+"/cache/nx.log")&&key.contains("#")&&sp.getBoolean("Enable Query logging",false)){
                            editor.putBoolean("Enable Suspicious logging",false);
                        }
                    }
                    editor.apply();

                    //TopFragment.NotificationDialogFragment commandResult = TopFragment.NotificationDialogFragment.newInstance(sb.toString());
                    //commandResult.show(getFragmentManager(),TopFragment.NotificationDialogFragment.TAG_NOT_FRAG);

                    settingsActivity.findViewById(R.id.pbSettings).setVisibility(View.GONE);
                    settingsActivity.findViewById(R.id.tvSettings).setVisibility(View.GONE);
                    FragmentTransaction fTrans = settingsActivity.getFragmentManager().beginTransaction();
                    Bundle bundle = new Bundle();
                    bundle.putStringArrayList("key_toml",key_toml);
                    bundle.putStringArrayList("val_toml",val_toml);
                    PreferencesDNSFragment frag = new PreferencesDNSFragment();
                    frag.setArguments(bundle);
                    fTrans.replace(android.R.id.content, frag);
                    fTrans.commit();
                }

                /////////////////////////////////////////////////////////////////////////////////
                //////////////// cat dnscrypt_proxy_qery.log or cat dnscrypt_proxy_qery.log /////////////////////////////////////////////
                ///////////////////////////////////////////////////////////////////////////////

                if(comResult.getCommands()[0].equalsIgnoreCase("cat dnscrypt_proxy_qery.log")
                        || comResult.getCommands()[0].equalsIgnoreCase("cat dnscrypt_proxy_nx.log")){

                    ArrayList<String> log_file = new ArrayList<>(Arrays.asList(comResult.getCommands()));

                    settingsActivity.findViewById(R.id.pbSettings).setVisibility(View.GONE);
                    settingsActivity.findViewById(R.id.tvSettings).setVisibility(View.GONE);
                    FragmentTransaction fTrans = settingsActivity.getFragmentManager().beginTransaction();
                    Bundle bundle = new Bundle();
                    bundle.putStringArrayList("log_file",log_file);
                    ShowLogFragment frag = new ShowLogFragment();
                    frag.setArguments(bundle);
                    fTrans.replace(android.R.id.content, frag);
                    fTrans.commit();
                }

                /////////////////////////////////////////////////////////////////////////////////
                //////////////// cat dnscrypt_proxy rules /////////////////////////////////////////////
                ///////////////////////////////////////////////////////////////////////////////

                if(comResult.getCommands()[0].equalsIgnoreCase("cat forwarding_rules.txt")
                        || comResult.getCommands()[0].equalsIgnoreCase("cat cloaking_rules.txt")
                        || comResult.getCommands()[0].equalsIgnoreCase("cat blacklist.txt")
                        || comResult.getCommands()[0].equalsIgnoreCase("cat ip-blacklist.txt")
                        || comResult.getCommands()[0].equalsIgnoreCase("cat whitelist.txt") ){

                    ArrayList<String> rules_file = new ArrayList<>(Arrays.asList(comResult.getCommands()));

                            /*StringBuilder sb = new StringBuilder();
                            for(String key:rules_file){
                                sb.append(key).append((char)10);
                            }
                            TopFragment.NotificationDialogFragment commandResult = TopFragment.NotificationDialogFragment.newInstance(sb.toString());
                            commandResult.show(getFragmentManager(),TopFragment.NotificationDialogFragment.TAG_NOT_FRAG);
                            */

                    settingsActivity.findViewById(R.id.pbSettings).setVisibility(View.GONE);
                    settingsActivity.findViewById(R.id.tvSettings).setVisibility(View.GONE);
                    FragmentTransaction fTrans = settingsActivity.getFragmentManager().beginTransaction();
                    Bundle bundle = new Bundle();
                    bundle.putStringArrayList("rules_file",rules_file);
                    ShowRulesRecycleFrag frag = new ShowRulesRecycleFrag();
                    frag.setArguments(bundle);
                    fTrans.replace(android.R.id.content, frag);
                    fTrans.commit();
                }

                /////////////////////////////////////////////////////////////////////////////////
                //////////////// Tor Settings /////////////////////////////////////////////
                ///////////////////////////////////////////////////////////////////////////////
                if(comResult.getCommands()[0].equalsIgnoreCase("cat tor.conf")) {
                    ArrayList<String> key_tor = new ArrayList<>();
                    ArrayList<String> val_tor = new ArrayList<>();
                    String prevCom = "";//to remove empty end of file tor.conf
                    String key = "";
                    String val = "";
                    //StringBuilder sb = new StringBuilder();
                    SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
                    SharedPreferences.Editor editor = sp.edit();
                    for (String com : comResult.getCommands()) {
                        if (!com.contains("cat tor.conf") && !(com.isEmpty() && prevCom.isEmpty())) {
                            if (com.contains(" ")) {
                                key = com.substring(0, com.indexOf(" ")).trim();
                                val = com.substring(com.indexOf(" ") + 1).trim();
                            } else {
                                key = com;
                                val = "";
                            }
                            if (val.trim().equals("1")) val = "true";
                            if (val.trim().equals("0")) val = "false";

                            key_tor.add(key);
                            val_tor.add(val);
                            //sb.append(key).append((char)10);
                        }
                        prevCom = com;

                        String val_saved_str = "";
                        boolean val_saved_bool = false;
                        boolean isbool = false;

                        try {
                            val_saved_str = sp.getString(key, "").trim();
                        } catch (ClassCastException e) {
                            isbool = true;
                            val_saved_bool = sp.getBoolean(key, false);
                        }


                        if (!val_saved_str.isEmpty() && !val_saved_str.equals(val) && !isbool) {
                            editor.putString(key, val);
                        } else if (isbool && val_saved_bool != Boolean.valueOf(val)) {
                            editor.putBoolean(key, Boolean.valueOf(val));
                        }


                        switch (key) {
                            case "ExcludeNodes":
                                editor.putBoolean("ExcludeNodes", true);
                                break;
                            case "#ExcludeNodes":
                                editor.putBoolean("ExcludeNodes", false);
                                break;
                            case "EntryNodes":
                                editor.putBoolean("EntryNodes", true);
                                break;
                            case "#ExitNodes":
                                editor.putBoolean("ExitNodes", false);
                                break;
                            case "ExitNodes":
                                editor.putBoolean("ExitNodes", true);
                                break;
                            case "#ExcludeExitNodes":
                                editor.putBoolean("ExcludeExitNodes", false);
                                break;
                            case "ExcludeExitNodes":
                                editor.putBoolean("ExcludeExitNodes", true);
                                break;
                            case "#EntryNodes":
                                editor.putBoolean("EntryNodes", false);
                                break;
                            case "SOCKSPort":
                                editor.putBoolean("Enable SOCKS proxy", true);
                                break;
                            case "#SOCKSPort":
                                editor.putBoolean("Enable SOCKS proxy", false);
                                break;
                            case "TransPort":
                                editor.putBoolean("Enable Transparent proxy", true);
                                break;
                            case "#TransPort":
                                editor.putBoolean("Enable Transparent proxy", false);
                                break;
                            case "DNSPort":
                                editor.putBoolean("Enable DNS", true);
                                break;
                            case "#DNSPort":
                                editor.putBoolean("Enable DNS", false);
                                break;
                            case "HTTPTunnelPort":
                                editor.putBoolean("Enable HTTPTunnel", true);
                                break;
                            case "#HTTPTunnelPort":
                                editor.putBoolean("Enable HTTPTunnel", false);
                                break;
                        }
                    }
                    editor.apply();
                            /*
                            StringBuilder sb = new StringBuilder();
                            for (String str:key_tor){
                                sb.append(str).append((char)10);
                            }
                            TopFragment.NotificationDialogFragment commandResult = TopFragment.NotificationDialogFragment.newInstance(sb.toString());
                            commandResult.show(getFragmentManager(),TopFragment.NotificationDialogFragment.TAG_NOT_FRAG);
                            */
                    //Toast.makeText(context,Environment.getExternalStorageDirectory().getPath(),Toast.LENGTH_LONG).show();
                    settingsActivity.findViewById(R.id.pbSettings).setVisibility(View.GONE);
                    settingsActivity.findViewById(R.id.tvSettings).setVisibility(View.GONE);
                    Bundle bundle = new Bundle();
                    bundle.putStringArrayList("key_tor",key_tor);
                    bundle.putStringArrayList("val_tor",val_tor);
                    PreferencesTorFragment frag = new PreferencesTorFragment();
                    frag.setArguments(bundle);
                    FragmentTransaction fTrans = settingsActivity.getFragmentManager().beginTransaction();
                    fTrans.replace(android.R.id.content, frag);
                    fTrans.commit();

                }

                /////////////////////////////////////////////////////////////////////////////////
                //////////////// I2PD Settings /////////////////////////////////////////////
                ///////////////////////////////////////////////////////////////////////////////
                if(comResult.getCommands()[0].equalsIgnoreCase("cat i2pd.conf")) {
                    ArrayList<String> key_itpd = new ArrayList<>();
                    ArrayList<String> val_itpd = new ArrayList<>();
                    String prevCom = "";//to remove empty end of file i2pd.conf
                    String key = "";
                    String val = "";
                    //StringBuilder sb = new StringBuilder();
                    SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
                    SharedPreferences.Editor editor = sp.edit();
                    String header = "common";
                    for (String com : comResult.getCommands()) {
                        if (!com.contains("cat i2pd.conf") && !(com.isEmpty() && prevCom.isEmpty())) {
                            if (com.contains("=")) {
                                key = com.substring(0, com.indexOf("=")).trim();
                                val = com.substring(com.indexOf("=") + 1).trim();
                            } else {
                                key = com;
                                val = "";
                            }

                            //key_itpd.add(key);
                            //val_itpd.add(val);
                            //sb.append(key).append((char)10);
                        }

                        String val_saved_str = "";
                        boolean val_saved_bool = false;
                        boolean isbool = false;

                        if (key.contains("[")) header = key;

                        if (header.equals("common")&&key.equals("host")){
                            key = "incoming host";
                        } else if (header.equals("common")&&key.equals("port")){
                            key = "incoming port";
                        } else if (header.equals("[ntcp2]")&&key.equals("enabled")){
                            key = "ntcp2 enabled";
                        } else if (header.equals("[http]")&&key.equals("enabled")){
                            key = "http enabled";
                        } else if (header.equals("[httpproxy]")&&key.equals("enabled")){
                            key = "HTTP proxy";
                        } else if (header.equals("[httpproxy]")&&key.equals("port")){
                            key = "HTTP proxy port";
                        } else if (header.equals("[socksproxy]")&&key.equals("enabled")){
                            key = "Socks proxy";
                        } else if (header.equals("[socksproxy]")&&key.equals("port")){
                            key = "Socks proxy port";
                        } else if (header.equals("[sam]")&&key.equals("enabled")){
                            key = "SAM interface";
                        } else if (header.equals("[sam]")&&key.equals("port")){
                            key = "SAM interface port";
                        } else if (header.equals("[upnp]")&&key.equals("enabled")){
                            key = "UPNP";
                        } else if (header.contains("[addressbook]")&&key.equals("subscriptions")){
                            editor.putString(key, val);
                        }

                        if (!com.contains("cat i2pd.conf") && !(com.isEmpty() && prevCom.isEmpty())) {
                            key_itpd.add(key);
                            val_itpd.add(val);
                        }
                        prevCom = com;

                        try {
                            val_saved_str = sp.getString(key, "").trim();
                        } catch (ClassCastException e) {
                            isbool = true;
                            val_saved_bool = sp.getBoolean(key, false);
                        }


                        if (!val_saved_str.isEmpty() && !val_saved_str.equals(val) && !isbool) {
                            editor.putString(key, val);
                        } else if (isbool && val_saved_bool != Boolean.valueOf(val)) {
                            editor.putBoolean(key, Boolean.valueOf(val));
                        }


                        switch (key) {
                            case "incomming host":
                                editor.putBoolean("Allow incoming connections", true);
                                break;
                            case "#host":
                                editor.putBoolean("Allow incoming connections", false);
                                break;
                            case "ntcpproxy":
                                editor.putBoolean("Enable ntcpproxy", true);
                                break;
                            case "#ntcpproxy":
                                editor.putBoolean("Enable ntcpproxy", false);
                                break;
                        }
                    }
                    editor.apply();

                            /*StringBuilder sb = new StringBuilder();
                            for (String str:subscriptions){
                                sb.append(str).append((char)10);
                            }
                            TopFragment.NotificationDialogFragment commandResult = TopFragment.NotificationDialogFragment.newInstance(sb.toString());
                            commandResult.show(getFragmentManager(),TopFragment.NotificationDialogFragment.TAG_NOT_FRAG);
                            */
                    settingsActivity.findViewById(R.id.pbSettings).setVisibility(View.GONE);
                    settingsActivity.findViewById(R.id.tvSettings).setVisibility(View.GONE);
                    FragmentTransaction fTrans = settingsActivity.getFragmentManager().beginTransaction();
                    Bundle bundle = new Bundle();
                    bundle.putStringArrayList("key_itpd",key_itpd);
                    bundle.putStringArrayList("val_itpd",val_itpd);
                    PreferencesITPDFragment frag = new PreferencesITPDFragment();
                    frag.setArguments(bundle);
                    fTrans.replace(android.R.id.content, frag);
                    fTrans.commit();

                }

            }
        }
    }
}
