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

package pan.alexander.tordnscrypt.settings.dnscrypt_servers;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static androidx.recyclerview.widget.RecyclerView.NO_POSITION;

import static pan.alexander.tordnscrypt.utils.logger.Logger.loge;

import android.content.Context;
import android.content.res.Configuration;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.appcompat.widget.SearchView;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.List;
import java.util.Locale;

import pan.alexander.tordnscrypt.R;

class DnsServersAdapter extends RecyclerView.Adapter<DnsServersAdapter.DNSServersViewHolder> {

    private static final int MIN_INTERVAL_PING_CHECK_SEC = 1;

    private final PreferencesDNSCryptServers preferencesDNSCryptServers;
    private final List<DnsServerItem> dnsServerItems;
    private final List<DnsServerItem> dnsServerItemsSaved;
    private final boolean relaysMdExist;
    private final SearchView searchDNSServer;

    private final String colorDNSCryptServer;
    private final String colorDohServer;
    private final String colorODohServer;
    private final String colorNonFilteringServer;
    private final String colorFilteringServer;
    private final String colorNonLoggingServer;
    private final String colorKeepLogsServer;
    private final String colorDNSSECServer;

    private final int colorFirst;
    private final int colorSecond;

    private final String pressToAdd;
    private final String longPressToAdd;
    private final String pressToEdit;
    private final String longPressToEdit;
    private final String anonymizeRelays;
    private final String relaysNotUsed;
    private final String dnscryptServer;
    private final String dohServer;
    private final String odohServer;
    private final String nonFilteringServer;
    private final String filteringServer;
    private final String nonLoggingServer;
    private final String keepLogsServer;
    private final String dnssecServer;
    private final int pingGoodColor;
    private final int pingAverageColor;
    private final int pingBadColor;

    private final boolean orientation;

    DnsServersAdapter(PreferencesDNSCryptServers preferencesDNSCryptServers) {

        Context context = preferencesDNSCryptServers.requireContext();

        this.searchDNSServer = preferencesDNSCryptServers.searchView;
        this.preferencesDNSCryptServers = preferencesDNSCryptServers;
        this.dnsServerItems = preferencesDNSCryptServers.dnsServerItems;
        this.dnsServerItemsSaved = preferencesDNSCryptServers.dnsServerItemsSaved;
        this.relaysMdExist = preferencesDNSCryptServers.isRelaysMdExist();

        colorDNSCryptServer = getHexFromColors(context, R.color.colorDNSCryptServer);
        colorDohServer = getHexFromColors(context, R.color.colorDohServer);
        colorODohServer = getHexFromColors(context, R.color.colorODohServer);
        colorNonFilteringServer = getHexFromColors(context, R.color.colorNonFilteringServer);
        colorFilteringServer = getHexFromColors(context, R.color.colorFilteringServer);
        colorNonLoggingServer = getHexFromColors(context, R.color.colorNonLoggingServer);
        colorKeepLogsServer = getHexFromColors(context, R.color.colorKeepLogsServer);
        colorDNSSECServer = getHexFromColors(context, R.color.colorDNSSECServer);
        pingGoodColor = ContextCompat.getColor(context, R.color.torBridgePingGood);
        pingAverageColor = ContextCompat.getColor(context, R.color.torBridgePingAverage);
        pingBadColor = ContextCompat.getColor(context, R.color.torBridgePingBad);

        colorFirst = context.getResources().getColor(R.color.colorFirst);
        colorSecond = context.getResources().getColor(R.color.colorSecond);

        pressToAdd = ContextCompat.getString(context, R.string.press_to_add);
        longPressToAdd = ContextCompat.getString(context, R.string.long_press_to_add);
        pressToEdit = ContextCompat.getString(context, R.string.press_to_edit);
        longPressToEdit = ContextCompat.getString(context, R.string.long_press_to_edit);
        anonymizeRelays = ContextCompat.getString(context, R.string.anonymize_relays);
        relaysNotUsed = ContextCompat.getString(context, R.string.anonymize_relays_not_used);
        dnscryptServer = ContextCompat.getString(context, R.string.pref_dnscrypt_dnscrypt_server);
        dohServer = ContextCompat.getString(context, R.string.pref_dnscrypt_doh_server);
        odohServer = ContextCompat.getString(context, R.string.pref_dnscrypt_odoh_server);
        nonFilteringServer = ContextCompat.getString(context, R.string.pref_dnscrypt_non_filtering_server);
        filteringServer = ContextCompat.getString(context, R.string.pref_dnscrypt_filtering_server);
        nonLoggingServer = ContextCompat.getString(context, R.string.pref_dnscrypt_non_logging_server);
        keepLogsServer = ContextCompat.getString(context, R.string.pref_dnscrypt_keep_logs_server);
        dnssecServer = ContextCompat.getString(context, R.string.pref_dnscrypt_dnssec_server);

        orientation = context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }


    @NonNull
    @Override
    public DnsServersAdapter.DNSServersViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        View view;
        try {
            view = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.item_dns_server, viewGroup, false);
        } catch (Exception e) {
            loge("DnsServersAdapter onCreateViewHolder", e);
            throw e;
        }
        return new DNSServersViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DnsServersAdapter.DNSServersViewHolder dnsServersViewHolder, int position) {
        dnsServersViewHolder.bind(position);
    }

    @Override
    public int getItemCount() {
        return dnsServerItems.size();
    }

    private DnsServerItem getItem(int position) {
        return dnsServerItems.get(position);
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).hashCode();
    }

    private void setItem(int position, DnsServerItem dnsServer) {
        int positionInSaved = dnsServerItemsSaved.indexOf(getItem(position));
        if (positionInSaved >= 0) {
            dnsServerItemsSaved.set(positionInSaved, dnsServer);
        }
        dnsServerItems.set(position, dnsServer);
    }

    private void removeItem(int position) {
        if (getItem(position) != null) {
            dnsServerItemsSaved.remove(getItem(position));
        }
        dnsServerItems.remove(position);
    }


    class DNSServersViewHolder extends RecyclerView.ViewHolder
            implements View.OnClickListener,
            View.OnLongClickListener,
            View.OnFocusChangeListener {

        private final MaterialCardView cardDNSServer;
        private final TextView tvDNSServerName;
        private final TextView tvDNSServerPing;
        private final CheckBox chbDNSServer;
        private final TextView tvDNSServerDescription;
        private final TextView tvDNSServerFlags;
        private final Button btnDNSServerRelay;
        private final ImageButton delBtnDNSServer;
        private final LinearLayoutCompat llDNSServer;

        private Long checkPingTime = 0L;

        DNSServersViewHolder(@NonNull View itemView) {
            super(itemView);

            cardDNSServer = itemView.findViewById(R.id.cardDNSServer);
            cardDNSServer.setFocusable(true);

            cardDNSServer.setOnClickListener(this);
            cardDNSServer.setOnLongClickListener(this);
            cardDNSServer.setCardBackgroundColor(colorFirst);
            cardDNSServer.setOnFocusChangeListener(this);

            tvDNSServerName = itemView.findViewById(R.id.tvDNSServerName);
            tvDNSServerPing = itemView.findViewById(R.id.tvDNSServerPing);
            chbDNSServer = itemView.findViewById(R.id.chbDNSServer);
            chbDNSServer.setFocusable(false);

            chbDNSServer.setOnClickListener(this);
            tvDNSServerDescription = itemView.findViewById(R.id.tvDNSServerDescription);
            tvDNSServerFlags = itemView.findViewById(R.id.tvDNSServerFlags);
            btnDNSServerRelay = itemView.findViewById(R.id.btnDNSServerRelay);
            btnDNSServerRelay.setOnClickListener(this);
            btnDNSServerRelay.setOnLongClickListener(this);

            delBtnDNSServer = itemView.findViewById(R.id.delBtnDNSServer);
            delBtnDNSServer.setOnClickListener(this);

            llDNSServer = itemView.findViewById(R.id.llDNSServer);
        }

        private void bind(int position) {

            if (position == 0 && searchDNSServer != null) {
                llDNSServer.setPadding(0, searchDNSServer.getHeight() + cardDNSServer.getContentPaddingBottom(), 0, 0);
            } else {
                llDNSServer.setPadding(0, 0, 0, 0);
            }

            DnsServerItem dnsServer = dnsServerItems.get(position);

            Long time = System.currentTimeMillis();
            if (dnsServer.getPing() == 0 || time - checkPingTime > MIN_INTERVAL_PING_CHECK_SEC * 1000) {
                checkPingTime = time;
                preferencesDNSCryptServers.checkServerPing(dnsServer.getName(), dnsServer.getAddress());
            }

            tvDNSServerName.setText(dnsServer.getName());
            tvDNSServerDescription.setText(dnsServer.getDescription());

            int ping = dnsServer.getPing();
            if (ping > 0) {
                tvDNSServerPing.setText(String.format(Locale.ROOT, "%d ms", ping));
                tvDNSServerPing.setTextColor(getPingColor(ping));
                tvDNSServerPing.setVisibility(VISIBLE);
            } else if (dnsServer.getPing() < 0) {
                tvDNSServerPing.setText(">1 sec");
                tvDNSServerPing.setTextColor(pingBadColor);
                tvDNSServerPing.setVisibility(VISIBLE);
            } else {
                tvDNSServerPing.setVisibility(GONE);
            }

            StringBuilder sb = new StringBuilder();
            if (dnsServer.isProtoDNSCrypt()) {
                sb.append("<font color='").append(colorDNSCryptServer).append("'>").append(dnscryptServer).append(" </font>");

                if (dnsServer.isChecked() && relaysMdExist) {
                    String routes = getRoutes(dnsServer).toString();
                    btnDNSServerRelay.setVisibility(VISIBLE);
                    btnDNSServerRelay.setText(routes);
                } else {
                    btnDNSServerRelay.setVisibility(GONE);
                    btnDNSServerRelay.setText("");
                }

            } else if (dnsServer.isProtoDoH()) {
                sb.append("<font color='").append(colorDohServer).append("'>").append(dohServer).append(" </font>");
                btnDNSServerRelay.setVisibility(GONE);
            } else if (dnsServer.isProtoODoH()) {
                sb.append("<font color='").append(colorODohServer).append("'>").append(odohServer).append(" </font>");
                if (dnsServer.isChecked()) {
                    String routes = getRoutes(dnsServer).toString();
                    btnDNSServerRelay.setVisibility(VISIBLE);
                    btnDNSServerRelay.setText(routes);
                } else {
                    btnDNSServerRelay.setVisibility(GONE);
                    btnDNSServerRelay.setText("");
                }
            }
            if (dnsServer.isNofilter()) {
                sb.append("<font color='").append(colorNonFilteringServer).append("'>").append(nonFilteringServer).append(" </font>");
            } else {
                sb.append("<font color='").append(colorFilteringServer).append("'>").append(filteringServer).append(" </font>");
            }
            if (dnsServer.isNolog()) {
                sb.append("<font color='").append(colorNonLoggingServer).append("'>").append(nonLoggingServer).append(" </font>");
            } else {
                sb.append("<font color='").append(colorKeepLogsServer).append("'>").append(keepLogsServer).append(" </font>");
            }

            if (dnsServer.isDnssec()) {
                sb.append("<font color='").append(colorDNSSECServer).append("'>").append(dnssecServer).append("</font>");
            }

            tvDNSServerFlags.setText(Html.fromHtml(sb.toString()));

            chbDNSServer.setChecked(dnsServer.isChecked());

            if (dnsServer.getOwnServer()) {
                delBtnDNSServer.setVisibility(VISIBLE);
            } else {
                delBtnDNSServer.setVisibility(GONE);
            }

        }

        private int getPingColor(int ping) {
            if (ping <= 100) {
                return pingGoodColor;
            } else {
                return pingAverageColor;
            }
        }

        @Override
        public void onClick(View view) {
            int position = getBindingAdapterPosition();
            if (position == NO_POSITION) {
                return;
            }

            int id = view.getId();
            if (id == R.id.cardDNSServer || id == R.id.chbDNSServer) {
                DnsServerItem dnsServer = getItem(position);
                dnsServer.setChecked(!dnsServer.isChecked());
                setItem(position, dnsServer);
                DnsServersAdapter.this.notifyItemChanged(position, new Object());
                if (dnsServer.isChecked() && dnsServer.isProtoODoH() && dnsServer.getRoutes().isEmpty()) {
                    preferencesDNSCryptServers.openDNSRelaysPref(dnsServer);
                }
            } else if (id == R.id.btnDNSServerRelay) {
                preferencesDNSCryptServers.openDNSRelaysPref(getItem(position));
            } else if (id == R.id.delBtnDNSServer) {
                removeItem(position);
                DnsServersAdapter.this.notifyItemRemoved(position);
            }
        }

        @Override
        public void onFocusChange(View view, boolean hasFocus) {
            if (hasFocus) {
                ((CardView) view).setCardBackgroundColor(colorSecond);
                view.findViewById(R.id.btnDNSServerRelay).setBackgroundColor(colorSecond);
            } else {
                ((CardView) view).setCardBackgroundColor(colorFirst);
                view.findViewById(R.id.btnDNSServerRelay).setBackgroundColor(colorFirst);
            }
        }

        @Override
        public boolean onLongClick(View view) {
            int position = getBindingAdapterPosition();
            if (position == NO_POSITION) {
                return true;
            }

            if (view.getId() == R.id.cardDNSServer
                    || view.getId() == R.id.btnDNSServerRelay) {
                preferencesDNSCryptServers.openDNSRelaysPref(getItem(position));
            }
            return true;
        }
    }

    @NonNull
    private StringBuilder getRoutes(DnsServerItem dnsServer) {
        StringBuilder routes = new StringBuilder();


        if (!dnsServer.getRoutes().isEmpty()) {
            routes.append(anonymizeRelays).append(": ");

            for (String route : dnsServer.getRoutes()) {
                routes.append(route).append(", ");
            }

            routes.delete(routes.lastIndexOf(","), routes.length());


            if (orientation) {
                routes.append(".\n").append(longPressToEdit);
            } else {
                routes.append(".\n").append(pressToEdit);
            }


        } else {
            if (orientation) {
                routes.append(relaysNotUsed).append("\n").append(longPressToAdd);
            } else {
                routes.append(relaysNotUsed).append("\n").append(pressToAdd);
            }

        }
        return routes;
    }

    private String getHexFromColors(Context context, @ColorRes int colorRes) {
        return String.format("#%06X", (0xFFFFFF & ContextCompat.getColor(context, colorRes)));
    }
}
