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

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.CardView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.SettingsActivity;
import pan.alexander.tordnscrypt.utils.fileOperations.FileOperations;
import pan.alexander.tordnscrypt.utils.NoRootService;
import pan.alexander.tordnscrypt.dialogs.NotificationHelper;
import pan.alexander.tordnscrypt.utils.PrefManager;
import pan.alexander.tordnscrypt.utils.RootCommands;
import pan.alexander.tordnscrypt.utils.RootExecService;
import pan.alexander.tordnscrypt.utils.Verifier;

import static pan.alexander.tordnscrypt.TopFragment.TOP_BROADCAST;
import static pan.alexander.tordnscrypt.TopFragment.wrongSign;
import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;

public class PreferencesDNSCryptServersRv extends Fragment {

    ArrayList<String> dnsServerNames;
    ArrayList<String> dnsServerDescr;
    ArrayList<String> dnsServerSDNS;
    ArrayList<String> dnscrypt_proxy_toml;
    ArrayList<String> dnscrypt_servers_current;
    ArrayList<DNSServers> list_dns_servers;
    String appDataDir;
    String dnscryptPath;
    String busyboxPath;
    boolean require_dnssec;
    boolean require_nolog;
    boolean require_nofilter;
    boolean use_doh_servers;
    boolean use_dns_servers;
    boolean use_ipv4;
    boolean use_ipv6;
    OnServersChangeListener callback;


    public PreferencesDNSCryptServersRv() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRetainInstance(true);

        if (getArguments() != null) {
            dnsServerNames = getArguments().getStringArrayList("dnsServerNames");
            dnsServerDescr = getArguments().getStringArrayList("dnsServerDescr");
            dnsServerSDNS = getArguments().getStringArrayList("dnsServerSDNS");
            dnscrypt_proxy_toml = getArguments().getStringArrayList("dnscrypt_proxy_toml");
            dnscrypt_servers_current = getArguments().getStringArrayList("dnscrypt_servers");


            assert dnscrypt_servers_current != null;
        } else {
            Log.e(LOG_TAG,"PreferencesDNSCryptServersRv getArguments() nullpointer");
        }

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Verifier verifier = new Verifier(getActivity());
                    String appSign = verifier.getApkSignatureZipModern();
                    String appSignAlt = verifier.getApkSignature();
                    if (!verifier.decryptStr(wrongSign,appSign,appSignAlt).equals(TOP_BROADCAST)) {
                        NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                                getActivity(),getText(R.string.verifier_error).toString(),"6787");
                        if (notificationHelper != null) {
                            if (getFragmentManager() != null) {
                                notificationHelper.show(getFragmentManager(),NotificationHelper.TAG_HELPER);
                            }
                        }
                    }

                } catch (Exception e) {
                    NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                            getActivity(),getText(R.string.verifier_error).toString(),"8990");
                    if (notificationHelper != null) {
                        if (getFragmentManager() != null) {
                            notificationHelper.show(getFragmentManager(),NotificationHelper.TAG_HELPER);
                        }
                    }
                    Log.e(LOG_TAG,"PreferencesDNSCryptServersRv fault "+e.getMessage() + " " + e.getCause() + System.lineSeparator() +
                            Arrays.toString(e.getStackTrace()));
                }
            }
        });
        thread.start();

    }

    @Override
    public void onResume() {
        super.onResume();

        Objects.requireNonNull(getActivity()).setTitle(R.string.pref_fast_dns_server);

        PathVars pathVars = new PathVars(getActivity());
        appDataDir = pathVars.appDataDir;
        dnscryptPath = pathVars.dnscryptPath;
        busyboxPath = pathVars.busyboxPath;

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity());

        require_dnssec = sp.getBoolean("require_dnssec",false);
        require_nofilter = sp.getBoolean("require_nofilter",false);
        require_nolog = sp.getBoolean("require_nolog",false);
        use_dns_servers = sp.getBoolean("dnscrypt_servers",true);
        use_doh_servers = sp.getBoolean("doh_servers",true);
        use_ipv4 = sp.getBoolean("ipv4_servers",true);
        use_ipv6 = sp.getBoolean("ipv6_servers",false);

        list_dns_servers = new ArrayList<>();
        for (int i = 0; i < dnsServerNames.size(); i++) {
            DNSServers dnsServer = new DNSServers(dnsServerNames.get(i),dnsServerDescr.get(i),dnsServerSDNS.get(i));
            if (dnsServer.visibility && !dnsServerNames.get(i).contains("repeat_server"))
                list_dns_servers.add(dnsServer);
        }

        final RecyclerView rvDNSServers = getActivity().findViewById(R.id.rvDNSServers);
        RecyclerView.LayoutManager mLayoutManager = new LinearLayoutManager(getActivity());
        rvDNSServers.setLayoutManager(mLayoutManager);

        RecyclerView.Adapter dNSServersAdapter = new DNSServersAdapter();
        try {
            rvDNSServers.setAdapter(dNSServersAdapter);
        } catch (IllegalStateException e) {
            Log.e(LOG_TAG, "PreferencesDNSCryptServersRv setAdapter Exception "+e.getMessage());
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_preferences_dnscrypt_servers_rv, container, false);
    }

    public void setOnServersChangeListener(OnServersChangeListener callback) {
        this.callback = callback;
    }

    public interface OnServersChangeListener {
        void onServersChange();
    }

    @Override
    public void onStop() {
        super.onStop();

        if (getActivity()==null)
            return;

        if(list_dns_servers.size()==0)
            return;

        StringBuilder dnscrypt_servers = new StringBuilder();
        dnscrypt_servers.append("[\"");
        for(int i = 0; i < list_dns_servers.size(); i++){
            if (list_dns_servers.get(i).checked) {
                dnscrypt_servers.append(list_dns_servers.get(i).name);
                dnscrypt_servers.append("\", \"");
            }
        }
        if (dnscrypt_servers.toString().equals("[\"")) {
            Toast.makeText(getActivity(),getText(R.string.pref_dnscrypt_select_server_names),Toast.LENGTH_LONG).show();
            return;
        }
        dnscrypt_servers.delete(dnscrypt_servers.length()-4,dnscrypt_servers.length()).append("\"]");

        new PrefManager(getActivity()).setStrPref("DNSCrypt Servers",dnscrypt_servers.toString());

        for (int i=0; i<dnscrypt_proxy_toml.size();i++){
            String str = dnscrypt_proxy_toml.get(i);
            if(str.contains("server_names")){
                String strTemp = str;
                str = str.replaceFirst("\\[.+]",dnscrypt_servers.toString());
                if(strTemp.equals(str))
                    return;
                dnscrypt_proxy_toml.set(i,str);
                break;
            }
        }

        FileOperations.writeToTextFile(getActivity(),appDataDir+"/app_data/dnscrypt-proxy/dnscrypt-proxy.toml",dnscrypt_proxy_toml, SettingsActivity.public_resolvers_md_tag);

        SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(getActivity());
        boolean runDNSCryptWithRoot = shPref.getBoolean("swUseModulesRoot",true);
        boolean dnsCryptRunning = new PrefManager(getActivity()).getBoolPref("DNSCrypt Running");
        String[] commandsEcho;
        if (runDNSCryptWithRoot) {
            commandsEcho = new String[] {
                    busyboxPath+ "killall dnscrypt-proxy; if [[ $? -eq 0 ]] ; then "+busyboxPath+
                            "nohup " + dnscryptPath+" --config "+appDataDir+"/app_data/dnscrypt-proxy/dnscrypt-proxy.toml >/dev/null 2>&1 & fi"
            };
        } else {
            commandsEcho = new String[] {
                    busyboxPath+ "killall dnscrypt-proxy"
            };

            if (dnsCryptRunning)
                runDNSCryptNoRoot();
        }

        RootCommands rootCommands  = new RootCommands(commandsEcho);
        Intent intent = new Intent(getActivity(), RootExecService.class);
        intent.setAction(RootExecService.RUN_COMMAND);
        intent.putExtra("Commands",rootCommands);
        intent.putExtra("Mark", RootExecService.SettingsActivityMark);
        RootExecService.performAction(getActivity(),intent);

        if (callback!=null)
            callback.onServersChange();
    }

    public class DNSServers {

        private String name;
        private String description;
        private boolean checked = false;
        private boolean dnssec = false;
        private boolean nolog = false;
        private boolean nofilter = false;
        private boolean protoDoH = false;
        private boolean protoDNSCrypt = false;
        private boolean ipv4 = true;
        private boolean ipv6 = false;
        private boolean visibility = true;

        DNSServers(String name, String description, String sdns) {
            this.name = name;
            this.description = description;

            byte[] bin = Base64.decode(sdns.substring(0,7).getBytes(),16);
            if (bin[0]==0x01) {
                protoDNSCrypt = true;
            } else if (bin[0]==0x02) {
                protoDoH = true;
            }

            if (((bin[1]) & 1)==1){
                this.dnssec = true;
            }
            if (((bin[1] >> 1) & 1)==1) {
                this.nolog = true;
            }
            if (((bin[1] >> 2) & 1)==1) {
                this.nofilter = true;
            }

            for (int i = 0; i < dnscrypt_servers_current.size(); i++) {
                if (!this.name.isEmpty() && this.name.equals(dnscrypt_servers_current.get(i).trim()))
                    this.checked = true;
            }

            if (name.contains("v6") || name.contains("ip6")) {
                this.ipv4 = false;
                this.ipv6 = true;
            }

            if (require_dnssec)
                this.visibility = this.dnssec;

            if (require_nofilter)
                this.visibility = this.visibility && this.nofilter;

            if (require_nolog)
                this.visibility = this.visibility && this.nolog;

            if (!use_dns_servers)
                this.visibility = this.visibility && !this.protoDNSCrypt;

            if (!use_doh_servers)
                this.visibility = this.visibility && !this.protoDoH;

            if (!use_ipv4)
                this.visibility = this.visibility && !this.ipv4;

            if (!use_ipv6)
                this.visibility = this.visibility && !this.ipv6;

        }
    }

    public class DNSServersAdapter extends RecyclerView.Adapter<DNSServersAdapter.DNSServersViewHolder> {

        LayoutInflater lInflater = (LayoutInflater) Objects.requireNonNull(getActivity()).getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        DNSServersAdapter() {
        }


        @NonNull
        @Override
        public DNSServersAdapter.DNSServersViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
            View view = lInflater.inflate(R.layout.item_dns_server, viewGroup, false);
            return new DNSServersViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull DNSServersAdapter.DNSServersViewHolder dnsServersViewHolder, int position) {
            dnsServersViewHolder.bind(position);
        }

        @Override
        public int getItemCount() {
            return list_dns_servers.size();
        }

        DNSServers getItem(int position) {
            return list_dns_servers.get(position);
        }

        void setItem(int position, DNSServers dnsServer) {
            list_dns_servers.set(position,dnsServer);
        }


        class DNSServersViewHolder extends RecyclerView.ViewHolder{

            CardView cardDNSServer;
            TextView tvDNSServerName;
            CheckBox chbDNSServer;
            TextView tvDNSServerDescription;
            TextView tvDNSServerFlags;

            DNSServersViewHolder(@NonNull View itemView) {
                super(itemView);

                cardDNSServer = itemView.findViewById(R.id.cardDNSServer);
                cardDNSServer.setFocusable(true);
                cardDNSServer.setOnClickListener(onClickListener);
                cardDNSServer.setCardBackgroundColor(getResources().getColor(R.color.colorFirst));
                cardDNSServer.setOnFocusChangeListener(onFocusChangeListener);
                tvDNSServerName = itemView.findViewById(R.id.tvDNSServerName);
                chbDNSServer = itemView.findViewById(R.id.chbDNSServer);
                chbDNSServer.setFocusable(false);
                chbDNSServer.setOnCheckedChangeListener(onCheckedChangeListener);
                tvDNSServerDescription = itemView.findViewById(R.id.tvDNSServerDescription);
                tvDNSServerFlags = itemView.findViewById(R.id.tvDNSServerFlags);
            }

            void bind(int position){


                DNSServers dnsServer = list_dns_servers.get(position);

                tvDNSServerName.setText(dnsServer.name);
                tvDNSServerDescription.setText(dnsServer.description);

                StringBuilder sb = new StringBuilder();
                if (dnsServer.protoDNSCrypt) {
                    sb.append("<font color='#7F4E52'>DNS Crypt Server </font>");
                } else if (dnsServer.protoDoH) {
                    sb.append("<font color='#614051'>DoH Server </font>");
                }
                if (dnsServer.nofilter) {
                    sb.append("<font color='#728FCE'>Non-Filtering </font>");
                } else {
                    sb.append("<font color='#4C787E'>AD-Filtering </font>");
                }
                if (dnsServer.nolog) {
                    sb.append("<font color='#4863A0'>Non-Logging </font>");
                } else {
                    sb.append("<font color='#800517'>Keep Logs </font>");
                }

                if (dnsServer.dnssec)
                    sb.append("<font color='#4E387E'>DNSSEC</font>");
                tvDNSServerFlags.setText(Html.fromHtml(sb.toString()));

                chbDNSServer.setChecked(dnsServer.checked);
            }

            CheckBox.OnCheckedChangeListener onCheckedChangeListener = new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                    int position = getAdapterPosition();
                    DNSServers dnsServer = getItem(position);
                    if (dnsServer.checked!=checked) {
                        dnsServer.checked = checked;
                        setItem(position,dnsServer);
                    }
                }
            };

            View.OnClickListener onClickListener = new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    int position = getAdapterPosition();
                    DNSServers dnsServer = getItem(position);
                    dnsServer.checked = !dnsServer.checked;
                    setItem(position,dnsServer);
                    notifyItemChanged(position);
                }
            };

            View.OnFocusChangeListener onFocusChangeListener = new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View view, boolean b) {
                    if (b) {
                        ((CardView)view).setCardBackgroundColor(getResources().getColor(R.color.colorSecond));
                    } else {
                        ((CardView)view).setCardBackgroundColor(getResources().getColor(R.color.colorFirst));
                    }
                }
            };

        }
    }

    private void runDNSCryptNoRoot() {
        SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(getActivity());
        boolean showNotification = shPref.getBoolean("swShowNotification",true);
        Intent intent = new Intent(getActivity(), NoRootService.class);
        intent.setAction(NoRootService.actionStartDnsCrypt);
        intent.putExtra("showNotification",showNotification);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Objects.requireNonNull(getActivity()).startForegroundService(intent);
        } else {
            Objects.requireNonNull(getActivity()).startService(intent);
        }
    }


}
