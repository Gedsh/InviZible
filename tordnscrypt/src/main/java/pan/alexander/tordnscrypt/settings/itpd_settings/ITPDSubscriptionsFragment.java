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

    Copyright 2019-2024 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.settings.itpd_settings;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.modules.ModulesAux;
import pan.alexander.tordnscrypt.modules.ModulesRestarter;

import static pan.alexander.tordnscrypt.utils.logger.Logger.loge;


public class ITPDSubscriptionsFragment extends Fragment implements View.OnClickListener {

    private RecyclerView mRecyclerView;
    private RecyclerView.Adapter<RulesAdapter.RuleViewHolder> mAdapter;

    private final ArrayList<String> rules_file = new ArrayList<>();
    private final ArrayList<ITPDSubscription> ITPDSubscription_list = new ArrayList<>();
    private final ArrayList<String> original_rules = new ArrayList<>();

    private String file_path;


    public ITPDSubscriptionsFragment() {
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
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view;
        try {
            view = inflater.inflate(R.layout.fragment_itpd_subscription, container, false);
        } catch (Exception e) {
            loge("ShowRulesRecycleFrag onCreateView", e);
            throw e;
        }

        RecyclerView.LayoutManager mLayoutManager = new LinearLayoutManager(container.getContext());
        mRecyclerView = view.findViewById(R.id.rvRules);
        mRecyclerView.setLayoutManager(mLayoutManager);

        FloatingActionButton btnAddRule = view.findViewById(R.id.floatingBtnAddRule);
        btnAddRule.setAlpha(0.8f);
        btnAddRule.setOnClickListener(this);
        btnAddRule.requestFocus();

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

        if (ITPDSubscription_list.isEmpty()) {
            fillRules();
        }

        mAdapter = new RulesAdapter(ITPDSubscription_list);
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

        for (ITPDSubscription rule : ITPDSubscription_list) {
            if (rule.active) {
                rules_file_new.add(rule.text);
            } else {
                rules_file_new.add("#" + rule.text);
            }
            rules_file_new.add("");
        }

        if (rules_file_new.equals(original_rules)) return;

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        StringBuilder sb = new StringBuilder();
        String str = "";
        for (ITPDSubscription rule : ITPDSubscription_list) {
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

        boolean itpdRunning = ModulesAux.isITPDSavedStateRunning();

        if (itpdRunning) {
            ModulesRestarter.restartITPD(context);
        }
    }

    private void setTitle(Activity activity) {
        activity.setTitle(R.string.pref_itpd_addressbook_subscriptions);
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
                ITPDSubscription_list.add(new ITPDSubscription(rules_file.get(i).replace("#", ""), active, locked, subscription));
                original_rules.add(rules_file.get(i));
                original_rules.add("");
            }
        }
    }

    @Override
    public void onClick(View v) {
        boolean subscription = file_path.contains("subscriptions");
        ITPDSubscription_list.add(new ITPDSubscription("", true, false, subscription));
        mAdapter.notifyItemInserted(ITPDSubscription_list.size() - 1);
        mRecyclerView.scrollToPosition(ITPDSubscription_list.size() - 1);
    }

    class RulesAdapter extends RecyclerView.Adapter<RulesAdapter.RuleViewHolder> {

        ArrayList<ITPDSubscription> list_ITPDSubscription_adapter;
        LayoutInflater lInflater = (LayoutInflater) requireActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        RulesAdapter(ArrayList<ITPDSubscription> ITPDSubscription_list) {
            list_ITPDSubscription_adapter = ITPDSubscription_list;
        }


        @NonNull
        @Override
        public RuleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            try {
                view = lInflater.inflate(R.layout.item_rules, parent, false);
            } catch (Exception e) {
                loge("ShowRulesRecycleFrag onCreateViewHolder", e);
                throw e;
            }
            return new RuleViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RulesAdapter.RuleViewHolder holder, int position) {
            holder.bind(position);
        }

        @Override
        public int getItemCount() {
            return list_ITPDSubscription_adapter.size();
        }

        ITPDSubscription getRule(int position) {
            return list_ITPDSubscription_adapter.get(position);
        }

        void delRule(int position) {

            try {
                list_ITPDSubscription_adapter.remove(position);
            } catch (Exception e) {
                loge("ShowRulesRecycleFrag getItemCount", e);
            }


        }

        class RuleViewHolder extends RecyclerView.ViewHolder
                implements View.OnClickListener, View.OnFocusChangeListener {

            EditText etRule;
            ImageButton delBtnRules;
            SwitchCompat swRuleActive;

            RuleViewHolder(View itemView) {
                super(itemView);

                etRule = itemView.findViewById(R.id.etRule);
                delBtnRules = itemView.findViewById(R.id.delBtnRules);
                swRuleActive = itemView.findViewById(R.id.swRuleActive);
                etRule.addTextChangedListener(textWatcher);
                delBtnRules.setOnClickListener(this);
            }

            void bind(int position) {
                etRule.setText(getRule(position).text, TextView.BufferType.EDITABLE);
                etRule.setEnabled(getRule(position).active);

                if (getRule(position).subscription) {
                    swRuleActive.setVisibility(View.GONE);
                }

                delBtnRules.setEnabled(true);

                if (getRule(position).locked) {
                    delBtnRules.setEnabled(false);
                }
            }

            TextWatcher textWatcher = new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    int position = getBindingAdapterPosition();
                    if (!getRule(position).locked)
                        getRule(position).text = s.toString();
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
            };

            @Override
            public void onClick(View v) {
                if (v.getId() == R.id.delBtnRules) {
                    int position = getBindingAdapterPosition();
                    delRule(position);
                    notifyItemRemoved(position);
                }
            }

            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    mRecyclerView.smoothScrollToPosition(getBindingAdapterPosition());
                }
            }
        }
    }

}
