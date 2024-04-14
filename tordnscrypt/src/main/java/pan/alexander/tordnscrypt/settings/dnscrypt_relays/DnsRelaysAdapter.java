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

package pan.alexander.tordnscrypt.settings.dnscrypt_relays;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

import pan.alexander.tordnscrypt.R;

class DnsRelaysAdapter extends RecyclerView.Adapter<DnsRelaysAdapter.DNSRelaysViewHolder> {

    private final Context context;
    private final ArrayList<DnsRelayItem> dnsRelayItems;
    private final LayoutInflater lInflater;


    DnsRelaysAdapter(Context context, ArrayList<DnsRelayItem> dnsRelayItems) {
        this.context = context;
        this.dnsRelayItems = dnsRelayItems;
        this.lInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @NonNull
    @Override
    public DNSRelaysViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = lInflater.inflate(R.layout.item_dns_relay, parent, false);
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



    class DNSRelaysViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, CompoundButton.OnCheckedChangeListener, View.OnFocusChangeListener {
        private final TextView tvDNSRelayName;
        private final TextView tvDNSRelayDescription;
        private final CheckBox chbDNSRelay;

        DNSRelaysViewHolder(@NonNull View itemView) {
            super(itemView);

            CardView cardDNSRelay = itemView.findViewById(R.id.cardDNSRelay);
            cardDNSRelay.setCardBackgroundColor(context.getResources().getColor(R.color.colorFirst));
            cardDNSRelay.setFocusable(true);
            cardDNSRelay.setOnClickListener(this);
            cardDNSRelay.setOnFocusChangeListener(this);

            tvDNSRelayName = itemView.findViewById(R.id.tvDNSRelayName);
            tvDNSRelayDescription = itemView.findViewById(R.id.tvDNSRelayDescription);
            chbDNSRelay = itemView.findViewById(R.id.chbDNSRelay);
            chbDNSRelay.setOnCheckedChangeListener(this);
        }

        private void bind(int position) {
            DnsRelayItem dnsRelayItem = getItem(position);

            tvDNSRelayName.setText(dnsRelayItem.getName());
            tvDNSRelayDescription.setText(dnsRelayItem.getDescription());
            chbDNSRelay.setChecked(dnsRelayItem.isChecked());
        }

        @Override
        public void onClick(View view) {
            if (view.getId() == R.id.cardDNSRelay) {
                int position = getAdapterPosition();
                DnsRelayItem dnsRelayItem = getItem(position);
                dnsRelayItem.setChecked(!dnsRelayItem.isChecked());
                setItem(position, dnsRelayItem);
                notifyItemChanged(position);
            }
        }

        @Override
        public void onCheckedChanged(CompoundButton compoundButton, boolean newValue) {
            int position = getAdapterPosition();
            DnsRelayItem dnsRelayItem = getItem(position);
            if (dnsRelayItem.isChecked() != newValue) {
                dnsRelayItem.setChecked(newValue);
                setItem(position, dnsRelayItem);
            }
        }

        @Override
        public void onFocusChange(View view, boolean newValue) {
            if (newValue) {
                ((CardView) view).setCardBackgroundColor(context.getResources().getColor(R.color.colorSecond));
            } else {
                ((CardView) view).setCardBackgroundColor(context.getResources().getColor(R.color.colorFirst));
            }
        }
    }
}
