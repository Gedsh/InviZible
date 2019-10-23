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
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
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
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.SettingsActivity;
import pan.alexander.tordnscrypt.utils.fileOperations.FileOperations;
import pan.alexander.tordnscrypt.utils.NoRootService;
import pan.alexander.tordnscrypt.utils.PrefManager;
import pan.alexander.tordnscrypt.utils.RootCommands;
import pan.alexander.tordnscrypt.utils.RootExecService;


/**
 * A simple {@link Fragment} subclass.
 */
public class ShowRulesRecycleFrag extends Fragment {

    RecyclerView mRecyclerView;
    ArrayList<String> rules_file;
    String file_path;
    ArrayList<Rules> rules_list;
    ArrayList<String> others_list;
    ArrayList<String> original_rules;
    String appDataDir;
    String busyboxPath;
    FloatingActionButton btnAddRule;


    public ShowRulesRecycleFrag() {
        // Required empty public constructor
    }


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);

        rules_file = getArguments().getStringArrayList("rules_file");
        file_path = getArguments().getString("path");
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_show_rules_recycle, container, false);

        PathVars pathVars = new PathVars(getActivity());
        appDataDir = pathVars.appDataDir;
        busyboxPath = pathVars.busyboxPath;


        if (rules_file == null) return view;


        rules_list = new ArrayList<>();
        others_list = new ArrayList<>();
        original_rules = new ArrayList<>();
        String[] lockedItems = {"stun.", ".in-addr.arpa", "in-adr.arpa", ".i2p", ".lib", ".onion"};
        boolean match;
        boolean active;
        boolean locked;
        boolean subscription;

        for (int i = 0; i < rules_file.size(); i++) {
            match = !rules_file.get(i).matches("#.*#.*") && !rules_file.get(i).isEmpty();
            active = !rules_file.get(i).contains("#");
            locked = false;
            subscription = file_path.contains("subscriptions") && !rules_file.get(i).isEmpty();

            for (String str : lockedItems) {
                if (rules_file.get(i).matches(".?" + str + ".*")) {
                    locked = true;
                    break;
                }
            }
            if (match) {
                rules_list.add(new Rules(rules_file.get(i).replace("#", ""), active, locked, subscription));
                original_rules.add(rules_file.get(i));
                original_rules.add("");
            } else if (!rules_file.get(i).isEmpty()) {
                others_list.add(rules_file.get(i));
                others_list.add("");
            }
        }

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        mRecyclerView = getActivity().findViewById(R.id.rvRules);

        RecyclerView.LayoutManager mLayoutManager = new LinearLayoutManager(getActivity());
        mRecyclerView.setLayoutManager(mLayoutManager);

        final RecyclerView.Adapter mAdapter = new RulesAdapter(rules_list);
        mRecyclerView.setAdapter(mAdapter);

        if (file_path.contains("forwarding_rules.txt")) {
            getActivity().setTitle(R.string.title_dnscrypt_forwarding_rules);
        } else if (file_path.contains("cloaking_rules.txt")) {
            getActivity().setTitle(R.string.title_dnscrypt_cloaking_rules);
        } else if (file_path.contains("blacklist.txt")) {
            getActivity().setTitle(R.string.title_dnscrypt_blacklist);
        } else if (file_path.contains("ip-blacklist.txt")) {
            getActivity().setTitle(R.string.title_dnscrypt_ip_blacklist);
        } else if (file_path.contains("whitelist.txt")) {
            getActivity().setTitle(R.string.title_dnscrypt_whitelist);
        } else if (file_path.contains("subscriptions")) {
            getActivity().setTitle(R.string.pref_itpd_addressbook_subscriptions);
        }


        btnAddRule = getActivity().findViewById(R.id.floatingBtnAddRule);
        btnAddRule.setAlpha(0.8f);
        btnAddRule.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean subscription = file_path.contains("subscriptions");
                rules_list.add(new Rules("", true, false, subscription));
                mAdapter.notifyDataSetChanged();
                mRecyclerView.scrollToPosition(rules_list.size() - 1);
            }
        });
        btnAddRule.requestFocus();

    }

    @Override
    public void onStop() {
        super.onStop();

        List<String> rules_file_new = new LinkedList<>();

        for (Rules rule : rules_list) {
            if (rule.active) {
                rules_file_new.add(rule.text);
            } else {
                rules_file_new.add("#" + rule.text);
            }
            rules_file_new.add("");
        }

        if (rules_file_new.equals(original_rules)) return;

        if (file_path.contains("subscriptions")) {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity());
            StringBuilder sb = new StringBuilder();
            String str;
            for (Rules rule : rules_list) {
                if (rule.subscription)
                    sb.append(rule.text).append(", ");
            }
            str = sb.toString().substring(0, sb.length() - 2);
            sp.edit().putString("subscriptions", str).apply();
        } else {
            others_list.addAll(rules_file_new);
            FileOperations.writeToTextFile(getActivity(), file_path, others_list, SettingsActivity.rules_tag);
        }

        PathVars pathVars = new PathVars(getActivity());
        String dnscryptPath = pathVars.dnscryptPath;
        String itpdPath = pathVars.itpdPath;

        SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(getActivity());
        boolean runDNSCryptWithRoot = shPref.getBoolean("swUseModulesRoot", false);
        boolean dnsCryptRunning = new PrefManager(getActivity()).getBoolPref("DNSCrypt Running");
        boolean itpdRunning = new PrefManager(getActivity()).getBoolPref("I2PD Running");
        String[] commandsEcho;
        if (runDNSCryptWithRoot) {
            if (file_path.contains("subscriptions")) {
                commandsEcho = new String[]{
                        busyboxPath + "killall i2pd; if [[ $? -eq 0 ]] ; " +
                                "then " + itpdPath + " --conf " + appDataDir + "/app_data/i2pd/i2pd.conf --datadir /data/media/0/i2pd & fi"
                };
            } else {
                commandsEcho = new String[]{
                        busyboxPath + "killall dnscrypt-proxy; if [[ $? -eq 0 ]] ; then " + busyboxPath +
                                "nohup " + dnscryptPath + " --config " + appDataDir + "/app_data/dnscrypt-proxy/dnscrypt-proxy.toml >/dev/null 2>&1 & fi"
                };
            }
        } else {
            if (file_path.contains("subscriptions")) {
                commandsEcho = new String[]{
                        busyboxPath + "killall i2pd"
                };
            } else {
                commandsEcho = new String[]{
                        busyboxPath + "killall dnscrypt-proxy"
                };
            }

            if (itpdRunning && file_path.contains("subscriptions")) {
                runITPDNoRoot();
            } else if (dnsCryptRunning) {
                runDNSCryptNoRoot();
            }
        }


        RootCommands rootCommands = new RootCommands(commandsEcho);
        Intent intent = new Intent(getActivity(), RootExecService.class);
        intent.setAction(RootExecService.RUN_COMMAND);
        intent.putExtra("Commands", rootCommands);
        intent.putExtra("Mark", RootExecService.SettingsActivityMark);
        RootExecService.performAction(getActivity(), intent);
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
        LayoutInflater lInflater = (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        RulesAdapter(ArrayList<Rules> rules_list) {
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

        Rules getRule(int position) {
            return list_rules_adapter.get(position);
        }

        void delRule(int position) {

            try {
                list_rules_adapter.remove(position);
            } catch (Exception e) {
                e.printStackTrace();
            }


        }

        class RuleViewHolder extends RecyclerView.ViewHolder {

            EditText etRule;
            ImageButton delBtnRules;
            Switch swRuleActive;
            LinearLayout llRules;

            RuleViewHolder(View itemView) {
                super(itemView);

                etRule = itemView.findViewById(R.id.etRule);
                delBtnRules = itemView.findViewById(R.id.delBtnRules);
                llRules = itemView.findViewById(R.id.llRules);
                swRuleActive = itemView.findViewById(R.id.swRuleActive);
                if (!file_path.contains("subscriptions")) {
                    swRuleActive.setOnCheckedChangeListener(activeListener);
                    swRuleActive.setOnFocusChangeListener(onFocusChangeListener);
                }
                etRule.addTextChangedListener(textWatcher);
                delBtnRules.setOnClickListener(onClickListener);
            }

            void bind(int position) {
                etRule.setText(getRule(position).text, TextView.BufferType.EDITABLE);
                etRule.setEnabled(getRule(position).active);

                if (getRule(position).subscription) {
                    swRuleActive.setVisibility(View.GONE);
                } else {
                    swRuleActive.setVisibility(View.VISIBLE);
                    swRuleActive.setChecked(getRule(position).active);
                }

                delBtnRules.setEnabled(true);

                if (getRule(position).locked) {
                    delBtnRules.setEnabled(false);
                }

                if (position == list_rules_adapter.size() - 1) {
                    llRules.setPadding(0, 0, 0, btnAddRule.getHeight());
                } else {
                    llRules.setPadding(0, 0, 0, 0);
                }
            }

            CompoundButton.OnCheckedChangeListener activeListener = new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

                    if (getRule(getAdapterPosition()).active != isChecked) {
                        getRule(getAdapterPosition()).active = isChecked;
                        notifyItemChanged(getAdapterPosition());
                    }
                }
            };

            View.OnClickListener onClickListener = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (v.getId() == R.id.delBtnRules) {
                        delRule(getAdapterPosition());
                        notifyDataSetChanged();
                    }
                }
            };

            TextWatcher textWatcher = new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (!getRule(getAdapterPosition()).locked)
                        getRule(getAdapterPosition()).text = s.toString();
                }

                @Override
                public void afterTextChanged(Editable s) {

                }
            };

            View.OnFocusChangeListener onFocusChangeListener = new View.OnFocusChangeListener() {

                @Override
                public void onFocusChange(View view, boolean inFocus) {
                    if (inFocus) {
                        mRecyclerView.smoothScrollToPosition(getAdapterPosition());
                    }
                }
            };
        }
    }

    private void runDNSCryptNoRoot() {
        SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(getActivity());
        boolean showNotification = shPref.getBoolean("swShowNotification", true);
        Intent intent = new Intent(getActivity(), NoRootService.class);
        intent.setAction(NoRootService.actionStartDnsCrypt);
        intent.putExtra("showNotification", showNotification);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getActivity().startForegroundService(intent);
        } else {
            getActivity().startService(intent);
        }
    }

    private void runITPDNoRoot() {
        SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(getActivity());
        boolean showNotification = shPref.getBoolean("swShowNotification", true);
        Intent intent = new Intent(getActivity(), NoRootService.class);
        intent.setAction(NoRootService.actionStartITPD);
        intent.putExtra("showNotification", showNotification);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getActivity().startForegroundService(intent);
        } else {
            getActivity().startService(intent);
        }
    }

}
