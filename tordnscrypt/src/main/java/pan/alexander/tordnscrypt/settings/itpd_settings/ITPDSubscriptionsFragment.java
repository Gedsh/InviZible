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

import static androidx.recyclerview.widget.RecyclerView.NO_POSITION;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import pan.alexander.tordnscrypt.App;
import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.databinding.FragmentItpdSubscriptionBinding;
import pan.alexander.tordnscrypt.databinding.ItemRulesBinding;

import static pan.alexander.tordnscrypt.utils.logger.Logger.loge;

import javax.inject.Inject;


public class ITPDSubscriptionsFragment extends Fragment implements View.OnClickListener {

    @Inject
    public ViewModelProvider.Factory viewModelFactory;

    private ItpdSubscriptionsViewModel viewModel;

    private FragmentItpdSubscriptionBinding binding;

    private SubscriptionsAdapter adapter;


    public ITPDSubscriptionsFragment() {
    }


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        App.getInstance().getDaggerComponent().inject(this);
        super.onCreate(savedInstanceState);

        viewModel = new ViewModelProvider(this, viewModelFactory).get(ItpdSubscriptionsViewModel.class);

        Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        setTitle(activity);
    }

    private void setTitle(Activity activity) {
        activity.setTitle(R.string.pref_itpd_addressbook_subscriptions);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        try {
            binding = FragmentItpdSubscriptionBinding.inflate(inflater, container, false);
        } catch (Exception e) {
            loge("ShowRulesRecycleFrag onCreateView", e);
            throw e;
        }

        initRecycler();
        initFab();

        return binding.getRoot();
    }

    private void initRecycler() {
        RecyclerView.LayoutManager mLayoutManager = new LinearLayoutManager(requireContext());
        binding.rvRules.setLayoutManager(mLayoutManager);
        adapter = new SubscriptionsAdapter();
        binding.rvRules.setAdapter(adapter);
    }

    private void initFab() {
        FloatingActionButton btnAddRule = binding.floatingBtnAddRule;
        btnAddRule.setAlpha(0.8f);
        btnAddRule.setOnClickListener(this);
        btnAddRule.requestFocus();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        observeSubscriptions();
        requestSubscriptions();
    }

    private void observeSubscriptions() {
        viewModel.getSubscriptions().observe(getViewLifecycleOwner(), subscriptions -> {
            if (subscriptions != null) {
                adapter.updateSubscriptions(subscriptions);
            }
        });
    }

    private void requestSubscriptions() {
        viewModel.requestSubscriptions();
    }

    @Override
    public void onClick(View v) {
        adapter.addSubscription();
    }

    private class SubscriptionsAdapter extends RecyclerView.Adapter<SubscriptionsAdapter.ViewHolder> {

        private final List<ItpdSubscriptionRecycleItem> subscriptions = new ArrayList<>();

        @SuppressLint("NotifyDataSetChanged")
        void updateSubscriptions(List<ItpdSubscriptionRecycleItem> subscriptions) {
            this.subscriptions.clear();
            for (ItpdSubscriptionRecycleItem item : subscriptions) {
                this.subscriptions.add(new ItpdSubscriptionRecycleItem(item.getText()));
            }
            notifyDataSetChanged();
        }

        void addSubscription() {
            int position = subscriptions.size();
            subscriptions.add(
                    position,
                    new ItpdSubscriptionRecycleItem("")
            );
            notifyItemInserted(position);
            binding.rvRules.scrollToPosition(position);
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            try {
                view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_rules, parent, false);
            } catch (Exception e) {
                loge("ShowRulesRecycleFrag onCreateViewHolder", e);
                throw e;
            }
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.bind(position);
        }

        @Override
        public int getItemCount() {
            return subscriptions.size();
        }

        ItpdSubscriptionRecycleItem getRule(int position) {
            return subscriptions.get(position);
        }

        void delRule(int position) {
            subscriptions.remove(position);
        }

        class ViewHolder extends RecyclerView.ViewHolder
                implements View.OnClickListener, View.OnFocusChangeListener {

            ItemRulesBinding itemRulesBinding;

            ViewHolder(View itemView) {
                super(itemView);

                itemRulesBinding = ItemRulesBinding.bind(itemView);

                itemRulesBinding.etRule.addTextChangedListener(textWatcher);
                itemRulesBinding.delBtnRules.setOnClickListener(this);
            }

            void bind(int position) {
                itemRulesBinding.etRule.setText(getRule(position).getText(), TextView.BufferType.EDITABLE);
                itemRulesBinding.swRuleActive.setVisibility(View.GONE);
            }

            TextWatcher textWatcher = new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    int position = getBindingAdapterPosition();
                    if (position != NO_POSITION) {
                        getRule(position).setText(s.toString());
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
            };

            @Override
            public void onClick(View v) {
                if (v.getId() == R.id.delBtnRules) {
                    int position = getBindingAdapterPosition();
                    if (position != NO_POSITION) {
                        delRule(position);
                        notifyItemRemoved(position);
                    }
                }
            }

            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    int position = getBindingAdapterPosition();
                    if (position != NO_POSITION) {
                        binding.rvRules.smoothScrollToPosition(position);
                    }
                }
            }
        }
    }

    @Override
    public void onStop() {
        super.onStop();

        viewModel.saveSubscriptions(requireContext(), adapter.subscriptions);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        binding = null;
    }
}
