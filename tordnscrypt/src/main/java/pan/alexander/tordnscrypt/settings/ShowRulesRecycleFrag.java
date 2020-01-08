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

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

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


/**
 * A simple {@link Fragment} subclass.
 */
public class ShowRulesRecycleFrag extends Fragment {

    private RecyclerView mRecyclerView;
    private ArrayList<String> rules_file;
    private String file_path;
    private ArrayList<Rules> rules_list;
    private ArrayList<String> others_list;
    private ArrayList<String> original_rules;
    private FloatingActionButton btnAddRule;


    public ShowRulesRecycleFrag() {}


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);

        if (getArguments() != null) {
            rules_file = getArguments().getStringArrayList("rules_file");
            file_path = getArguments().getString("path");
        }

    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_show_rules_recycle, container, false);

        if (getActivity() == null) {
            return view;
        }


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

        if (getActivity() == null) {
            return;
        }

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
        btnAddRule.setOnClickListener(v -> {
            boolean subscription = file_path.contains("subscriptions");
            rules_list.add(new Rules("", true, false, subscription));
            mAdapter.notifyDataSetChanged();
            mRecyclerView.scrollToPosition(rules_list.size() - 1);
        });
        btnAddRule.requestFocus();

    }

    @Override
    public void onStop() {
        super.onStop();

        if (getActivity() == null) {
            return;
        }

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

        boolean dnsCryptRunning = new PrefManager(getActivity()).getBoolPref("DNSCrypt Running");
        boolean itpdRunning = new PrefManager(getActivity()).getBoolPref("I2PD Running");

        if (itpdRunning && file_path.contains("subscriptions")) {
           ModulesRestarter.restartITPD(getActivity());
        } else if (dnsCryptRunning){
            ModulesRestarter.restartDNSCrypt(getActivity());
        }
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
        LayoutInflater lInflater = (LayoutInflater) Objects.requireNonNull(getActivity()).getSystemService(Context.LAYOUT_INFLATER_SERVICE);

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
                Log.e(LOG_TAG, "ShowRulesRecycleFrag getItemCount exception " + e.getMessage() + " " + e.getCause());
            }


        }

        class RuleViewHolder extends RecyclerView.ViewHolder {

            EditText etRule;
            ImageButton delBtnRules;
            SwitchCompat swRuleActive;
            LinearLayoutCompat llRules;

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

            CompoundButton.OnCheckedChangeListener activeListener = (buttonView, isChecked) -> {

                if (getRule(getAdapterPosition()).active != isChecked) {
                    getRule(getAdapterPosition()).active = isChecked;
                    notifyItemChanged(getAdapterPosition());
                }
            };

            View.OnClickListener onClickListener = v -> {
                if (v.getId() == R.id.delBtnRules) {
                    delRule(getAdapterPosition());
                    notifyDataSetChanged();
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

}
