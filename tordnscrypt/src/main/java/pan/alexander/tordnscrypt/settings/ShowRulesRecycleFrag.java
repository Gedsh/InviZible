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
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;

import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.utils.RootCommands;
import pan.alexander.tordnscrypt.utils.RootExecService;


/**
 * A simple {@link Fragment} subclass.
 */
public class ShowRulesRecycleFrag extends Fragment {

    ArrayList<String> rules_file;
    ArrayList<Rules> rules_list;
    StringBuilder others_list;
    StringBuilder original_rules;
    String[] commandsEdit={"echo 'Something went wrong'"};
    String appDataDir;
    String busyboxPath;


    public ShowRulesRecycleFrag() {
        // Required empty public constructor
    }


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);

        rules_file = getArguments().getStringArrayList("rules_file");
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_show_rules_recycle, container, false);

        PathVars pathVars = new PathVars(getActivity());
        appDataDir = pathVars.appDataDir;
        busyboxPath = pathVars.busyboxPath;


        if (rules_file==null) return view;


        rules_list = new ArrayList<>();
        others_list = new StringBuilder();
        original_rules = new StringBuilder();
        String[] lockedItems = {"stun.",".in-addr.arpa","in-adr.arpa",".i2p",".lib",".onion"};
        boolean match;
        boolean active;
        boolean locked;
        boolean subscription;

        for (int i = 1; i < rules_file.size(); i++) {
            match = !rules_file.get(i).matches("#.*#.*")&&!rules_file.get(i).isEmpty();
            active = !rules_file.get(i).contains("#");
            locked = false;
            subscription = rules_file.get(0).contains("subscriptions")&&!rules_file.get(i).isEmpty();

            for (String str:lockedItems){
                if (rules_file.get(i).matches(".?"+ str +".*")){
                   locked = true;
                   break;
                }
            }
            if(match){
                rules_list.add(new Rules(rules_file.get(i).replace("#",""),active,locked,subscription));
                original_rules.append(rules_file.get(i)).append((char)10).append((char)10);
            } else if(!rules_file.get(i).isEmpty()) {
                others_list.append(rules_file.get(i)).append((char)10).append((char)10);
            }
        }

        /*StringBuilder sb = new StringBuilder();
        for(int i=0;i<rules_list.size();i++){
            sb.append(rules_list.get(i).text).append((char)10);
        }
        TopFragment.NotificationDialogFragment commandResult = TopFragment.NotificationDialogFragment.newInstance(sb.toString());
        commandResult.show(getFragmentManager(),TopFragment.NotificationDialogFragment.TAG_NOT_FRAG);*/


        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        final RecyclerView mRecyclerView = getActivity().findViewById(R.id.rvRules);

        RecyclerView.LayoutManager mLayoutManager = new LinearLayoutManager(getActivity());
        mRecyclerView.setLayoutManager(mLayoutManager);

        final RecyclerView.Adapter mAdapter = new RulesAdapter(rules_list);
        mRecyclerView.setAdapter(mAdapter);

        switch (rules_file.get(0)) {
            case "cat forwarding_rules.txt":
                getActivity().setTitle(R.string.title_dnscrypt_forwarding_rules);
                break;
            case "cat cloaking_rules.txt":
                getActivity().setTitle(R.string.title_dnscrypt_cloaking_rules);
                break;
            case "cat blacklist.txt":
                getActivity().setTitle(R.string.title_dnscrypt_blacklist);
                break;
            case "cat ip-blacklist.txt":
                getActivity().setTitle(R.string.title_dnscrypt_ip_blacklist);
                break;
            case "cat whitelist.txt":
                getActivity().setTitle(R.string.title_dnscrypt_whitelist);
                break;
            case "subscriptions":
                getActivity().setTitle(R.string.pref_itpd_addressbook_subscriptions);
                break;
        }




        ImageButton btnAddRule = getActivity().findViewById(R.id.btnAddRule);
        btnAddRule.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean subscription = rules_file.get(0).contains("subscriptions");
                rules_list.add(new Rules("",true,false,subscription));
                mAdapter.notifyItemInserted(rules_list.size()-1);
                mRecyclerView.scrollToPosition(rules_list.size() - 1);
            }
        });

    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        StringBuilder rules_file_new = new StringBuilder();

        for (Rules rule:rules_list){
            if(rule.active){
                rules_file_new.append(rule.text).append((char)10).append((char)10);
            } else {
                rules_file_new.append("#").append(rule.text).append((char)10).append((char)10);
            }
        }

        if (rules_file_new.toString().equals(original_rules.toString())) return;

        PreferencesDNSFragment.isChanged = true;

        //Toast.makeText(getActivity(),rules_file_new.toString(),Toast.LENGTH_LONG).show();


        switch (rules_file.get(0)) {
            case "cat forwarding_rules.txt":
                commandsEdit = new String[]{"echo 'restart dnscrypt-proxy'",
                        busyboxPath+ "echo \"" + others_list.toString() + rules_file_new.toString() + "\" > "+appDataDir+"/app_data/dnscrypt-proxy/forwarding-rules.txt",
                        busyboxPath+ "chmod 644 "+appDataDir+"/app_data/dnscrypt-proxy/forwarding-rules.txt"};
                break;
            case "cat cloaking_rules.txt":
                commandsEdit = new String[]{"echo 'restart dnscrypt-proxy'",
                        busyboxPath+ "echo \"" + others_list.toString() + rules_file_new.toString() + "\" > "+appDataDir+"/app_data/dnscrypt-proxy/cloaking-rules.txt",
                        busyboxPath+ "chmod 644 "+appDataDir+"/app_data/dnscrypt-proxy/cloaking-rules.txt"};
                break;
            case "cat blacklist.txt":
                commandsEdit = new String[]{"echo 'restart dnscrypt-proxy'",
                        busyboxPath+ "echo \"" + others_list.toString() + rules_file_new.toString() + "\" > "+appDataDir+"/app_data/dnscrypt-proxy/blacklist.txt",
                        busyboxPath+ "chmod 644 "+appDataDir+"/app_data/dnscrypt-proxy/blacklist.txt"};
                break;
            case "cat ip-blacklist.txt":
                commandsEdit = new String[]{"echo 'restart dnscrypt-proxy'",
                        busyboxPath+ "echo \"" + others_list.toString() + rules_file_new.toString() + "\" > "+appDataDir+"/app_data/dnscrypt-proxy/ip-blacklist.txt",
                        busyboxPath+ "chmod 644 "+appDataDir+"/app_data/dnscrypt-proxy/ip-blacklist.txt"};
                break;
            case "cat whitelist.txt":
                commandsEdit = new String[]{"echo 'restart dnscrypt-proxy'",
                        busyboxPath+ "echo \"" + others_list.toString() + rules_file_new.toString() + "\" > "+appDataDir+"/app_data/dnscrypt-proxy/whitelist.txt",
                        busyboxPath+ "chmod 644 "+appDataDir+"/app_data/dnscrypt-proxy/whitelist.txt"};
                break;
            case "subscriptions":
                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity());
                StringBuilder sb = new StringBuilder();
                String str;
                for (Rules rule:rules_list){
                    if(rule.subscription && !rule.text.contains("subscriptions"))
                        sb.append(rule.text).append(", ");
                }
                str = sb.toString().substring(0,sb.length()-2);
                sp.edit().putString("subscriptions",str).apply();
                return;
        }

        RootCommands rootCommands  = new RootCommands(commandsEdit);
        Intent intent = new Intent(getActivity(), RootExecService.class);
        intent.setAction(RootExecService.RUN_COMMAND);
        intent.putExtra("Commands",rootCommands);
        intent.putExtra("Mark", RootExecService.SettingsActivityMark);
        RootExecService.performAction(getActivity(),intent);
    }

    public class Rules {

        String text;
        boolean active;
        boolean locked;
        boolean subscription;


        Rules(String text, boolean active, boolean locked, boolean subscription) {
            this.text = text;
            this.active = active;
            this.locked = locked;
            this.subscription = subscription;
        }
    }

    public class RulesAdapter extends RecyclerView.Adapter<RulesAdapter.RuleViewHolder> {

        ArrayList<Rules> list_rules_adapter;
        LayoutInflater lInflater = (LayoutInflater)getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        RulesAdapter(ArrayList<Rules> rules_list){
            list_rules_adapter = rules_list;
        }




        @NonNull
        @Override
        public RuleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = lInflater.inflate(R.layout.item_rules, parent, false);
            return new RuleViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RulesAdapter.RuleViewHolder holder, int position) {
            holder.bind(position);
        }

        @Override
        public int getItemCount() {
            return list_rules_adapter.size();
        }

        Rules getRule(int position){
            return list_rules_adapter.get(position);
        }

        void delRule(int position){

            try {
                list_rules_adapter.remove(position);
            } catch (Exception e){
                e.printStackTrace();
            }



        }

        class RuleViewHolder extends RecyclerView.ViewHolder {

            EditText etRule;
            ImageButton delBtnRules;
            Switch swRuleActive;
            //LinearLayout llRules;

            RuleViewHolder(View itemView) {
                super(itemView);

                etRule = itemView.findViewById(R.id.etRule);
                delBtnRules = itemView.findViewById(R.id.delBtnRules);
                swRuleActive = itemView.findViewById(R.id.swRuleActive);
                swRuleActive.setOnCheckedChangeListener(activeListener);
                etRule.addTextChangedListener(textWatcher);
                delBtnRules.setOnClickListener(onClickListener);
            }

            void bind(int position){
                etRule.setText(getRule(position).text ,TextView.BufferType.EDITABLE);
                etRule.setEnabled(getRule(position).active);

                if (getRule(position).subscription){
                    swRuleActive.setVisibility(View.GONE);
                } else {
                    swRuleActive.setVisibility(View.VISIBLE);
                }
                swRuleActive.setChecked(getRule(position).active);

                delBtnRules.setEnabled(true);

                if (getRule(position).locked){
                    delBtnRules.setEnabled(false);
                }
            }

            CompoundButton.OnCheckedChangeListener activeListener = new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

                    if (getRule(getAdapterPosition()).active != isChecked){
                        getRule(getAdapterPosition()).active = isChecked;
                        notifyItemChanged(getAdapterPosition());
                    }
                }
            };

            View.OnClickListener onClickListener = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (v.getId()==R.id.delBtnRules){
                        delRule(getAdapterPosition());
                        notifyItemRemoved(getAdapterPosition());
                        //notifyItemRangeChanged(getAdapterPosition(),getItemCount());
                    }
                }
            };

            TextWatcher textWatcher = new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if(!getRule(getAdapterPosition()).locked)
                        getRule(getAdapterPosition()).text = s.toString();
                }

                @Override
                public void afterTextChanged(Editable s) {

                }
            };
        }
    }

}
