package pan.alexander.tordnscrypt.settings.tor_ips;

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
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.AsyncListDiffer;
import androidx.recyclerview.widget.RecyclerView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import pan.alexander.tordnscrypt.R;

public class DomainIpAdapter extends RecyclerView.Adapter<DomainIpAdapter.DomainIpViewHolder> {
    private final AsyncListDiffer<DomainIpEntity> diff = new AsyncListDiffer<>(
            this, new DomainIpDiffUtilItemCallback()
    );
    private final UnlockTorIpsFragment unlockTorIpsFragment;
    private final Context context;

    DomainIpAdapter(UnlockTorIpsFragment unlockTorIpsFragment) {
        this.unlockTorIpsFragment = unlockTorIpsFragment;
        this.context = unlockTorIpsFragment.getContext();
    }

    public void updateDomainIps(Set<DomainIpEntity> newDomainIpsSet) {
        List<DomainIpEntity> newDomainIps = new ArrayList<>(newDomainIpsSet);
        Collections.sort(newDomainIps);
        diff.submitList(newDomainIps);
    }

    @NonNull
    @Override
    public DomainIpViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = unlockTorIpsFragment.getLayoutInflater().inflate(R.layout.item_tor_ips, parent, false);
        return new DomainIpViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DomainIpViewHolder holder, int position) {
        holder.bind(position);
    }

    @Override
    public int getItemCount() {
        return diff.getCurrentList().size();
    }

    DomainIpEntity getItem(int position) {
        return diff.getCurrentList().get(position);
    }

    private void delItem(int position) {

        Activity activity = unlockTorIpsFragment.getActivity();
        if (activity == null || activity.isFinishing()) {
            return;
        }

        DomainIpEntity domainIp = getItem(position);
        unlockTorIpsFragment.viewModel.deleteDomainIpFromPreferences(domainIp);
        unlockTorIpsFragment.viewModel.removeDomainIp(domainIp);
    }

    private void setActive(int position, boolean active) {
        DomainIpEntity domainIp = getItem(position);
        domainIp.setActive(active);
    }

    class DomainIpViewHolder extends RecyclerView.ViewHolder
            implements CompoundButton.OnCheckedChangeListener, View.OnClickListener, View.OnFocusChangeListener {

        TextView tvTorItemHost;
        TextView tvTorItemIP;
        SwitchCompat swTorItem;
        ImageButton imbtnTorItem;
        LinearLayoutCompat llHostIP;
        LinearLayoutCompat llHostIPRoot;

        DomainIpViewHolder(View itemView) {
            super(itemView);

            tvTorItemHost = itemView.findViewById(R.id.tvTorItemHost);
            tvTorItemIP = itemView.findViewById(R.id.tvTorItemIP);
            swTorItem = itemView.findViewById(R.id.swTorItem);
            swTorItem.setOnCheckedChangeListener(this);
            swTorItem.setOnFocusChangeListener(this);
            imbtnTorItem = itemView.findViewById(R.id.imbtnTorItem);
            imbtnTorItem.setOnClickListener(this);
            imbtnTorItem.setOnFocusChangeListener(this);
            llHostIP = itemView.findViewById(R.id.llHostIP);
            llHostIP.setOnClickListener(this);
            llHostIP.setFocusable(true);
            llHostIP.setOnFocusChangeListener(this);
            llHostIPRoot = itemView.findViewById(R.id.llHostIPRoot);

        }

        void bind(int position) {

            if (position < 0) {
                return;
            }

            DomainIpEntity domainIp = getItem(position);

            if (domainIp instanceof IpEntity) {
                String domain = ((IpEntity) domainIp).getDomain();
                String ip = ((IpEntity) domainIp).getIp();
                if (!domain.isEmpty()) {
                    tvTorItemHost.setText(domain);
                } else {
                    tvTorItemHost.setText(ip);
                }
            } else if (domainIp instanceof DomainEntity) {
                String domain = ((DomainEntity) domainIp).getDomain();
                tvTorItemHost.setText(domain);
            }

            setItemIpText(domainIp);

            swTorItem.setChecked(domainIp.isActive());
            llHostIP.setEnabled(domainIp.isActive());
        }

        void editHostIPDialog(final DomainIpEntity domainIpEntity) {
            DialogEditDomainIp dialogEditHostIP = new DialogEditDomainIp(
                    new WeakReference<>(unlockTorIpsFragment),
                    R.style.CustomAlertDialogTheme,
                    domainIpEntity
            );
            dialogEditHostIP.show();
        }


        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            Context context = unlockTorIpsFragment.getContext();
            if (context == null) {
                return;
            }

            int position = getAbsoluteAdapterPosition();

            if (position < 0) {
                return;
            }

            DomainIpEntity domainIp = getItem(position);

            setActive(position, isChecked);

            llHostIP.setEnabled(isChecked);

            setItemIpText(domainIp);

            if (domainIp instanceof IpEntity) {
                unlockTorIpsFragment.viewModel.saveIpActiveInPreferences(
                        ((IpEntity) domainIp).getIp(),
                        isChecked
                );
            } else if (domainIp instanceof DomainEntity) {
                unlockTorIpsFragment.viewModel.saveDomainActiveInPreferences(
                        ((DomainEntity) domainIp).getDomain(),
                        isChecked
                );
            }
            unlockTorIpsFragment.viewModel.addDomainIp(domainIp);
        }

        private void setItemIpText(DomainIpEntity domainIp) {
            if (domainIp.isActive()) {
                if (domainIp instanceof DomainEntity) {
                    tvTorItemIP.setText(TextUtils.join(", ", ((DomainEntity) domainIp).getIps()));
                } else if (domainIp instanceof IpEntity) {
                    tvTorItemIP.setText(((IpEntity) domainIp).getIp());
                }
            } else {
                if (domainIp instanceof DomainEntity) {
                    tvTorItemIP.setText(context.getText(R.string.pref_tor_unlock_disabled));
                } else if (domainIp instanceof IpEntity) {
                    tvTorItemIP.setText(context.getText(R.string.pref_tor_unlock_disabled));
                }
            }
        }

        @Override
        public void onClick(View v) {
            int position = getAbsoluteAdapterPosition();

            if (position < 0) {
                return;
            }

            int id = v.getId();
            if (id == R.id.imbtnTorItem) {
                delItem(position);
            } else if (id == R.id.llHostIP) {
                editHostIPDialog(getItem(position));
            }
        }

        @Override
        public void onFocusChange(View v, boolean hasFocus) {
            int position = getAbsoluteAdapterPosition();

            if (position < 0) {
                return;
            }

            if (v.getId() == R.id.llHostIP) {
                if (hasFocus) {
                    v.setBackgroundColor(unlockTorIpsFragment.getResources().getColor(R.color.colorSecond));
                    unlockTorIpsFragment.rvListHostIP.smoothScrollToPosition(position);
                } else {
                    v.setBackgroundColor(unlockTorIpsFragment.getResources().getColor(R.color.colorFirst));
                }
            } else {
                if (hasFocus) {
                    unlockTorIpsFragment.rvListHostIP.smoothScrollToPosition(position);
                }

            }
        }
    }
}
