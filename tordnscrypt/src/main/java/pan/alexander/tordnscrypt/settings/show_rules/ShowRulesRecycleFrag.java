package pan.alexander.tordnscrypt.settings.show_rules;
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

    Copyright 2019-2021 by Garmatin Oleksandr invizible.soft@gmail.com
*/

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.DialogFragment;
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
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.settings.SettingsActivity;
import pan.alexander.tordnscrypt.dialogs.NotificationDialogFragment;
import pan.alexander.tordnscrypt.modules.ModulesAux;
import pan.alexander.tordnscrypt.utils.filemanager.FileManager;
import pan.alexander.tordnscrypt.modules.ModulesRestarter;

import static pan.alexander.tordnscrypt.utils.root.RootExecService.LOG_TAG;


public class ShowRulesRecycleFrag extends Fragment implements View.OnClickListener {

    private RecyclerView mRecyclerView;
    private RecyclerView.Adapter<RulesAdapter.RuleViewHolder> mAdapter;
    private FloatingActionButton btnAddRule;

    private final ArrayList<String> rules_file = new ArrayList<>();
    private final ArrayList<Rules> rules_list = new ArrayList<>();
    private final ArrayList<String> others_list = new ArrayList<>();
    private final ArrayList<String> original_rules = new ArrayList<>();

    private String file_path;
    private boolean readOnly;


    public ShowRulesRecycleFrag() {
    }


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRetainInstance(true);

        if (getArguments() != null) {
            List<String> rules = getArguments().getStringArrayList("rules_file");
            rules_file.addAll(rules != null ? rules : Collections.emptyList());
            file_path = getArguments().getString("path");
        }

    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_show_rules_recycle, container, false);

        RecyclerView.LayoutManager mLayoutManager = new LinearLayoutManager(container.getContext());
        mRecyclerView = view.findViewById(R.id.rvRules);
        mRecyclerView.setLayoutManager(mLayoutManager);

        if (checkAndTrimIfRulesTooMany()) {
            readOnly = true;
            showTooManyRulesDialog();
        }

        btnAddRule = view.findViewById(R.id.floatingBtnAddRule);
        if (readOnly) {
            btnAddRule.setVisibility(View.GONE);
        } else {
            btnAddRule.setAlpha(0.8f);
            btnAddRule.setOnClickListener(this);
            btnAddRule.requestFocus();
        }

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        setTitle(activity);

        if (rules_list.isEmpty()) {
            fillRules();
        }

        mAdapter = new RulesAdapter(rules_list);
        mRecyclerView.setAdapter(mAdapter);
    }

    @Override
    public void onStop() {
        super.onStop();

        Context context = getActivity();
        if (context == null) {
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
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
            StringBuilder sb = new StringBuilder();
            String str = "";
            for (Rules rule : rules_list) {
                if (rule.subscription)
                    sb.append(rule.text).append(", ");
            }
            if (sb.length() > 2) {
                str = sb.substring(0, sb.length() - 2);
            }
            sp.edit().putString("subscriptions", str).apply();

            rules_file.clear();
            rules_file.addAll(rules_file_new);
            original_rules.clear();
            original_rules.addAll(rules_file_new);
        } else {
            rules_file.clear();
            rules_file.addAll(others_list);
            rules_file.addAll(rules_file_new);
            original_rules.clear();
            original_rules.addAll(rules_file_new);

            FileManager.writeToTextFile(context, file_path, rules_file, SettingsActivity.rules_tag);
        }

        boolean dnsCryptRunning = ModulesAux.isDnsCryptSavedStateRunning();
        boolean itpdRunning = ModulesAux.isITPDSavedStateRunning();

        if (itpdRunning && file_path.contains("subscriptions")) {
            ModulesRestarter.restartITPD(context);
        } else if (dnsCryptRunning) {
            ModulesRestarter.restartDNSCrypt(context);
        }
    }

    private boolean checkAndTrimIfRulesTooMany() {
        if (rules_file.size() > 1000) {
            rules_file.subList(1000, rules_file.size()).clear();
            rules_file.trimToSize();

            return true;
        }

        return false;
    }

    private void showTooManyRulesDialog() {
        DialogFragment dialogFragment = NotificationDialogFragment.newInstance(R.string.dnscrypt_many_rules_dialog_message);
        if (isAdded()) {
            dialogFragment.show(getParentFragmentManager(), "TooManyRules");
        }
    }

    private void setTitle(Activity activity) {
        if (file_path.endsWith("forwarding-rules.txt")) {
            activity.setTitle(R.string.title_dnscrypt_forwarding_rules);
        } else if (file_path.endsWith("cloaking-rules.txt")) {
            activity.setTitle(R.string.title_dnscrypt_cloaking_rules);
        } else if (file_path.endsWith("ip-blacklist.txt")) {
            activity.setTitle(R.string.title_dnscrypt_ip_blacklist);
        } else if (file_path.endsWith("blacklist.txt")) {
            activity.setTitle(R.string.title_dnscrypt_blacklist);
        } else if (file_path.endsWith("whitelist.txt")) {
            activity.setTitle(R.string.title_dnscrypt_whitelist);
        } else if (file_path.endsWith("subscriptions")) {
            activity.setTitle(R.string.pref_itpd_addressbook_subscriptions);
        }
    }

    private void fillRules() {
        String[] lockedItems = {".i2p", "onion"};

        for (int i = 0; i < rules_file.size(); i++) {
            boolean match = !rules_file.get(i).matches("#.*#.*") && !rules_file.get(i).isEmpty();
            boolean active = !rules_file.get(i).contains("#");
            boolean locked = false;
            boolean subscription = file_path.contains("subscriptions") && !rules_file.get(i).isEmpty();

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
    }

    @Override
    public void onClick(View v) {
        boolean subscription = file_path.contains("subscriptions");
        rules_list.add(new Rules("", true, false, subscription));
        mAdapter.notifyDataSetChanged();
        mRecyclerView.scrollToPosition(rules_list.size() - 1);
    }

    public class RulesAdapter extends RecyclerView.Adapter<RulesAdapter.RuleViewHolder> {

        ArrayList<Rules> list_rules_adapter;
        LayoutInflater lInflater = (LayoutInflater) requireActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

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

        class RuleViewHolder extends RecyclerView.ViewHolder
                implements View.OnClickListener, CompoundButton.OnCheckedChangeListener, View.OnFocusChangeListener {

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

                if (readOnly) {
                    delBtnRules.setVisibility(View.GONE);
                    swRuleActive.setVisibility(View.GONE);
                } else {
                    if (!file_path.contains("subscriptions")) {
                        swRuleActive.setOnCheckedChangeListener(this);
                        swRuleActive.setOnFocusChangeListener(this);
                    }
                    etRule.addTextChangedListener(textWatcher);
                    delBtnRules.setOnClickListener(this);
                }
            }

            void bind(int position) {
                etRule.setText(getRule(position).text, TextView.BufferType.EDITABLE);
                etRule.setEnabled(getRule(position).active);

                if (getRule(position).subscription) {
                    swRuleActive.setVisibility(View.GONE);
                } else if (!readOnly) {
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

            @Override
            public void onClick(View v) {
                if (v.getId() == R.id.delBtnRules) {
                    delRule(getAdapterPosition());
                    notifyDataSetChanged();
                }
            }

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (getRule(getAdapterPosition()).active != isChecked) {
                    getRule(getAdapterPosition()).active = isChecked;
                    notifyItemChanged(getAdapterPosition());
                }
            }

            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    mRecyclerView.smoothScrollToPosition(getAdapterPosition());
                }
            }
        }
    }

}
