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

    Copyright 2019-2025 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.settings.dnscrypt_relays;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static androidx.recyclerview.widget.RecyclerView.NO_POSITION;

import static pan.alexander.tordnscrypt.utils.logger.Logger.loge;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import pan.alexander.tordnscrypt.R;

class DnsRelaysAdapter extends RecyclerView.Adapter<DnsRelaysAdapter.DNSRelaysViewHolder> {

    private static final int MIN_INTERVAL_PING_CHECK_SEC = 1;

    private final List<DnsRelayItem> dnsRelayItems = new ArrayList<>();

    private final int colorFirst;
    private final int colorSecond;
    private final int pingGoodColor;
    private final int pingAverageColor;
    private final int pingBadColor;
    private OnRelayPingMeasure pingMeasurer;

    DnsRelaysAdapter(Context context) {
        colorFirst = context.getResources().getColor(R.color.colorFirst);
        colorSecond = context.getResources().getColor(R.color.colorSecond);
        pingGoodColor = ContextCompat.getColor(context, R.color.torBridgePingGood);
        pingAverageColor = ContextCompat.getColor(context, R.color.torBridgePingAverage);
        pingBadColor = ContextCompat.getColor(context, R.color.torBridgePingBad);
    }

    void addItems(List<DnsRelayItem> items) {
        dnsRelayItems.clear();
        dnsRelayItems.addAll(items);
        Collections.sort(dnsRelayItems);
        notifyItemRangeInserted(0, items.size());
    }

    List<DnsRelayItem> getItems() {
        return new ArrayList<>(dnsRelayItems);
    }

    int updateRelayPing(String relayName, int ping) {
        for (int i = 0; i < dnsRelayItems.size(); i++) {
            DnsRelayItem relay = dnsRelayItems.get(i);
            if (relay.getName().equals(relayName)) {
                relay.setPing(ping);
                return i;
            }
        }
        return NO_POSITION;
    }

    void setOnRelayPingMeasurer(OnRelayPingMeasure measurer) {
        pingMeasurer = measurer;
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).hashCode();
    }

    @NonNull
    @Override
    public DNSRelaysViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;
        try {
            view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_dns_relay, parent, false);
        } catch (Exception e) {
            loge("DnsRelaysAdapter DNSRelaysViewHolder", e);
            throw e;
        }
        return new DNSRelaysViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DNSRelaysViewHolder holder, int position) {
        holder.bind(position);
    }

    @Override
    public int getItemCount() {
        return dnsRelayItems.size();
    }

    private DnsRelayItem getItem(int position) {
        return dnsRelayItems.get(position);
    }

    private void setItem(int position, DnsRelayItem dnsRelayItem) {
        dnsRelayItems.set(position, dnsRelayItem);
    }

    class DNSRelaysViewHolder extends RecyclerView.ViewHolder
            implements View.OnClickListener, View.OnFocusChangeListener {
        private final TextView tvDNSRelayName;
        private final TextView tvDNSRelayDescription;
        private final TextView tvDNSRelayPing;
        private final CheckBox chbDNSRelay;

        private Long checkPingTime = 0L;

        DNSRelaysViewHolder(@NonNull View itemView) {
            super(itemView);

            MaterialCardView cardDNSRelay = itemView.findViewById(R.id.cardDNSRelay);
            cardDNSRelay.setCardBackgroundColor(colorFirst);
            cardDNSRelay.setFocusable(true);
            cardDNSRelay.setOnClickListener(this);
            cardDNSRelay.setOnFocusChangeListener(this);

            tvDNSRelayName = itemView.findViewById(R.id.tvDNSRelayName);
            tvDNSRelayDescription = itemView.findViewById(R.id.tvDNSRelayDescription);
            tvDNSRelayPing = itemView.findViewById(R.id.tvDNSRelayPing);
            chbDNSRelay = itemView.findViewById(R.id.chbDNSRelay);
            chbDNSRelay.setOnClickListener(this);
        }

        private void bind(int position) {
            DnsRelayItem dnsRelayItem = getItem(position);

            Long time = System.currentTimeMillis();
            if (pingMeasurer != null
                    && (dnsRelayItem.getPing() == 0 || time - checkPingTime > MIN_INTERVAL_PING_CHECK_SEC * 1000)) {
                checkPingTime = time;
                pingMeasurer.measureRelayPing(dnsRelayItem.getName(), dnsRelayItem.getSdns());
            }

            tvDNSRelayName.setText(dnsRelayItem.getName());
            tvDNSRelayDescription.setText(dnsRelayItem.getDescription());
            chbDNSRelay.setChecked(dnsRelayItem.isChecked());

            int ping = dnsRelayItem.getPing();
            if (ping > 0) {
                tvDNSRelayPing.setText(String.format(Locale.ROOT, "%d ms", ping));
                tvDNSRelayPing.setTextColor(getPingColor(ping));
                tvDNSRelayPing.setVisibility(VISIBLE);
            } else if (ping < 0) {
                tvDNSRelayPing.setText(">1 sec");
                tvDNSRelayPing.setTextColor(pingBadColor);
                tvDNSRelayPing.setVisibility(VISIBLE);
            } else {
                tvDNSRelayPing.setVisibility(GONE);
            }
        }

        @Override
        public void onClick(View view) {
            int position = getBindingAdapterPosition();
            if (position == NO_POSITION) {
                return;
            }

            int id = view.getId();
            if (id == R.id.cardDNSRelay || id == R.id.chbDNSRelay) {
                DnsRelayItem dnsRelayItem = getItem(position);
                dnsRelayItem.setChecked(!dnsRelayItem.isChecked());
                setItem(position, dnsRelayItem);
                notifyItemChanged(position, new Object());
            }
        }

        @Override
        public void onFocusChange(View view, boolean newValue) {
            if (newValue) {
                ((CardView) view).setCardBackgroundColor(colorSecond);
            } else {
                ((CardView) view).setCardBackgroundColor(colorFirst);
            }
        }
    }

    private int getPingColor(int ping) {
        if (ping <= 100) {
            return pingGoodColor;
        } else {
            return pingAverageColor;
        }
    }

    interface OnRelayPingMeasure {
        void measureRelayPing(String name, String sdns);
    }
}
