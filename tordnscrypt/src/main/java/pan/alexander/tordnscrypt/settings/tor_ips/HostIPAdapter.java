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
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Set;

import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.utils.PrefManager;

public class HostIPAdapter extends RecyclerView.Adapter<HostIPAdapter.HostIPViewHolder> {

    private final UnlockTorIpsFrag unlockTorIpsFrag;
    private final Context context;
    private final ArrayList<HostIP> unlockHostIP;
    private final String unlockIPsStr;
    private final String unlockHostsStr;


    HostIPAdapter(UnlockTorIpsFrag unlockTorIpsFrag) {
        this.unlockTorIpsFrag = unlockTorIpsFrag;
        this.context = unlockTorIpsFrag.getContext();
        this.unlockHostIP = unlockTorIpsFrag.unlockHostIP;
        this.unlockIPsStr = unlockTorIpsFrag.unlockIPsStr;
        this.unlockHostsStr = unlockTorIpsFrag.unlockHostsStr;
    }

    @NonNull
    @Override
    public HostIPAdapter.HostIPViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = unlockTorIpsFrag.getLayoutInflater().inflate(R.layout.item_tor_ips, parent, false);
        return new HostIPViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull HostIPAdapter.HostIPViewHolder holder, int position) {
        holder.bind(position);
    }

    @Override
    public int getItemCount() {
        return unlockHostIP.size();
    }

    HostIP getItem(int position) {
        return unlockHostIP.get(position);
    }

    private void delItem(int position) {

        Activity activity = unlockTorIpsFrag.getActivity();
        if (activity == null || activity.isFinishing()
                || unlockTorIpsFrag.unlockHostsStr == null || unlockIPsStr == null || unlockHostIP == null) {
            return;
        }

        HostIP hostIP = getItem(position);
        if (hostIP.inputIP) {
            Set<String> ipSet;
            ipSet = new PrefManager(activity).getSetStrPref(unlockIPsStr);

            if (hostIP.active) {
                ipSet.remove(hostIP.IP);
            } else {
                ipSet.remove("#" + hostIP.IP);
            }
            new PrefManager(activity).setSetStrPref(unlockIPsStr, ipSet);

        } else if (hostIP.inputHost) {
            Set<String> hostSet;
            hostSet = new PrefManager(activity).getSetStrPref(unlockHostsStr);

            if (hostIP.active) {
                hostSet.remove(hostIP.host);
            } else {
                hostSet.remove("#" + hostIP.host);
            }
            new PrefManager(activity).setSetStrPref(unlockHostsStr, hostSet);

        }
        unlockHostIP.remove(position);
        notifyDataSetChanged();
    }

    private void setActive(int position, boolean active) {
        HostIP hip = unlockHostIP.get(position);
        hip.active = active;
    }

    class HostIPViewHolder extends RecyclerView.ViewHolder
            implements CompoundButton.OnCheckedChangeListener, View.OnClickListener, View.OnFocusChangeListener {

        TextView tvTorItemHost;
        TextView tvTorItemIP;
        SwitchCompat swTorItem;
        ImageButton imbtnTorItem;
        LinearLayoutCompat llHostIP;
        LinearLayoutCompat llHostIPRoot;

        HostIPViewHolder(View itemView) {
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

            HostIP hostIP = getItem(position);

            if (!hostIP.host.isEmpty()) {
                tvTorItemHost.setText(hostIP.host);
                tvTorItemHost.setVisibility(View.VISIBLE);
            } else {
                tvTorItemHost.setVisibility(View.GONE);
            }

            if (hostIP.active) {
                //TODO check code
                //tvTorItemIP.setText(getItem(getAdapterPosition()).IP);
                tvTorItemIP.setText(hostIP.IP);
            } else {
                tvTorItemIP.setText(context.getText(R.string.pref_tor_unlock_disabled));
            }
            swTorItem.setChecked(hostIP.active);
            llHostIP.setEnabled(hostIP.active);

            if (position == getItemCount() - 1) {
                llHostIPRoot.setPadding(0, 0, 0, unlockTorIpsFrag.floatingBtnAddTorIPs.getHeight());
            } else {
                llHostIPRoot.setPadding(0, 0, 0, 0);
            }
        }

        void editHostIPDialog(final int position) {
            DialogEditHostIP dialogEditHostIP = new DialogEditHostIP(context,
                    unlockTorIpsFrag, R.style.CustomAlertDialogTheme, position);
            dialogEditHostIP.show();
        }



        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            Context context = unlockTorIpsFrag.getContext();
            if (context == null) {
                return;
            }

            int position = getAdapterPosition();

            if (position < 0) {
                return;
            }

            HostIP hostIP = getItem(position);

            setActive(position, isChecked);

            llHostIP.setEnabled(isChecked);

            if (isChecked) {
                tvTorItemIP.setText(hostIP.IP);
            } else {
                tvTorItemIP.setText(context.getText(R.string.pref_tor_unlock_disabled));
            }

            if (hostIP.inputIP) {
                saveActiveIP(hostIP.IP, isChecked);
            } else if (hostIP.inputHost) {
                saveActiveHost(hostIP.host, isChecked);
            }
        }

        @Override
        public void onClick(View v) {
            int position = getAdapterPosition();

            if (position < 0) {
                return;
            }

            int id = v.getId();
            if (id == R.id.imbtnTorItem) {
                delItem(position);
            } else if (id == R.id.llHostIP) {
                editHostIPDialog(position);
            }
        }

        @Override
        public void onFocusChange(View v, boolean hasFocus) {
            int position = getAdapterPosition();

            if (position < 0) {
                return;
            }

            if (v.getId() == R.id.llHostIP) {
                if (hasFocus) {
                    v.setBackgroundColor(unlockTorIpsFrag.getResources().getColor(R.color.colorSecond));
                    unlockTorIpsFrag.rvListHostIP.smoothScrollToPosition(position);
                } else {
                    v.setBackgroundColor(unlockTorIpsFrag.getResources().getColor(R.color.colorFirst));
                }
            } else {
                if (hasFocus) {
                    unlockTorIpsFrag.rvListHostIP.smoothScrollToPosition(position);
                }

            }
        }
    }

    private void saveActiveHost(String oldHost, boolean active) {
        Set<String> hostsSet = new PrefManager(context).getSetStrPref(unlockHostsStr);
        if (active) {
            hostsSet.remove("#" + oldHost);
            hostsSet.add(oldHost.replace("#", ""));
        } else {
            hostsSet.remove(oldHost);
            hostsSet.add("#" + oldHost);
        }
        new PrefManager(context).setSetStrPref(unlockHostsStr, hostsSet);
    }

    private void saveActiveIP(String oldIP, boolean active) {
        Set<String> ipsSet = new PrefManager(context).getSetStrPref(unlockIPsStr);
        if (active) {
            ipsSet.remove("#" + oldIP);
            ipsSet.add(oldIP.replace("#", ""));
        } else {
            ipsSet.remove(oldIP);
            ipsSet.add("#" + oldIP);
        }
        new PrefManager(context).setSetStrPref(unlockIPsStr, ipsSet);
    }
}
