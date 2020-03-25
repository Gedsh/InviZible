package pan.alexander.tordnscrypt.settings.dnscrypt_servers;

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
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.appcompat.widget.SearchView;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Objects;

import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.settings.dnscrypt_relays.DNSServerRelays;
import pan.alexander.tordnscrypt.settings.dnscrypt_relays.PreferencesDNSCryptRelays;

class DNSServersAdapter extends RecyclerView.Adapter<DNSServersAdapter.DNSServersViewHolder> {
    private Context context;
    private FragmentManager fragmentManager;
    private PreferencesDNSCryptServers preferencesDNSCryptServers;
    private ArrayList<DNSServerItem> list_dns_servers;
    private ArrayList<DNSServerItem> list_dns_servers_saved;
    private ArrayList<DNSServerRelays> routes_current;
    private LayoutInflater lInflater;
    private boolean relaysMdExist;
    private SearchView searchDNSServer;

    DNSServersAdapter(Context context, SearchView searchDNSServer,
                      PreferencesDNSCryptServers preferencesDNSCryptServers,
                      FragmentManager fragmentManager,
                      ArrayList<DNSServerItem> list_dns_servers,
                      ArrayList<DNSServerItem> list_dns_servers_saved,
                      ArrayList<DNSServerRelays> routes_current, boolean relaysMdExist) {
        this.context = context;
        this.searchDNSServer = searchDNSServer;
        this.preferencesDNSCryptServers = preferencesDNSCryptServers;
        this.fragmentManager = fragmentManager;
        this.list_dns_servers = list_dns_servers;
        this.list_dns_servers_saved = list_dns_servers_saved;
        this.routes_current = routes_current;
        this.relaysMdExist = relaysMdExist;

        lInflater = (LayoutInflater) Objects.requireNonNull(context).getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }


    @NonNull
    @Override
    public DNSServersAdapter.DNSServersViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        View view = lInflater.inflate(R.layout.item_dns_server, viewGroup, false);
        return new DNSServersViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DNSServersAdapter.DNSServersViewHolder dnsServersViewHolder, int position) {
        dnsServersViewHolder.bind(position);
    }

    @Override
    public int getItemCount() {
        return list_dns_servers.size();
    }

    private DNSServerItem getItem(int position) {
        return list_dns_servers.get(position);
    }

    private void setItem(int position, DNSServerItem dnsServer) {
        int positionInSaved = list_dns_servers_saved.indexOf(getItem(position));
        if (positionInSaved > 0) {
            list_dns_servers_saved.set(positionInSaved, dnsServer);
        }
        list_dns_servers.set(position, dnsServer);
    }

    private void removeItem(int position) {
        if (getItem(position) != null) {
            list_dns_servers_saved.remove(getItem(position));
        }
        list_dns_servers.remove(position);
    }


    class DNSServersViewHolder extends RecyclerView.ViewHolder
            implements View.OnClickListener,
            View.OnLongClickListener,
            CompoundButton.OnCheckedChangeListener,
            View.OnFocusChangeListener {

        private CardView cardDNSServer;
        private TextView tvDNSServerName;
        private CheckBox chbDNSServer;
        private TextView tvDNSServerDescription;
        private TextView tvDNSServerFlags;
        private Button btnDNSServerRelay;
        private ImageButton delBtnDNSServer;
        private LinearLayoutCompat llDNSServer;

        DNSServersViewHolder(@NonNull View itemView) {
            super(itemView);

            cardDNSServer = itemView.findViewById(R.id.cardDNSServer);
            cardDNSServer.setFocusable(true);

            cardDNSServer.setOnClickListener(this);
            cardDNSServer.setOnLongClickListener(this);
            cardDNSServer.setCardBackgroundColor(context.getResources().getColor(R.color.colorFirst));
            cardDNSServer.setOnFocusChangeListener(this);

            tvDNSServerName = itemView.findViewById(R.id.tvDNSServerName);
            chbDNSServer = itemView.findViewById(R.id.chbDNSServer);
            chbDNSServer.setFocusable(false);

            chbDNSServer.setOnCheckedChangeListener(this);
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

            DNSServerItem dnsServer = list_dns_servers.get(position);

            tvDNSServerName.setText(dnsServer.getName());
            tvDNSServerDescription.setText(dnsServer.getDescription());

            StringBuilder sb = new StringBuilder();
            if (dnsServer.isProtoDNSCrypt()) {
                sb.append("<font color='#7F4E52'>DNSCrypt Server </font>");

                if (dnsServer.isChecked() && relaysMdExist) {
                    StringBuilder routes = new StringBuilder();

                    boolean orientation = context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;

                    if (dnsServer.getRoutes().size() > 0) {
                        routes.append("Anonymize relays: ");

                        for (String route : dnsServer.getRoutes()) {
                            routes.append(route).append(", ");
                        }

                        routes.delete(routes.lastIndexOf(","), routes.length());


                        if (orientation) {
                            routes.append(".\nLong Press to edit.");
                        } else {
                            routes.append(".\nPress to edit.");
                        }


                    } else {
                        if (orientation) {
                            routes.append("Anonymize relays are not used.\nLong Press to add.");
                        } else {
                            routes.append("Anonymize relays are not used.\nPress to add.");
                        }

                    }

                    btnDNSServerRelay.setVisibility(View.VISIBLE);
                    btnDNSServerRelay.setText(routes.toString());
                } else {
                    btnDNSServerRelay.setVisibility(View.GONE);
                    btnDNSServerRelay.setText("");
                }

            } else if (dnsServer.isProtoDoH()) {
                sb.append("<font color='#614051'>DoH Server </font>");
                btnDNSServerRelay.setVisibility(View.GONE);
            }
            if (dnsServer.isNofilter()) {
                sb.append("<font color='#728FCE'>Non-Filtering </font>");
            } else {
                sb.append("<font color='#4C787E'>AD-Filtering </font>");
            }
            if (dnsServer.isNolog()) {
                sb.append("<font color='#4863A0'>Non-Logging </font>");
            } else {
                sb.append("<font color='#800517'>Keep Logs </font>");
            }

            if (dnsServer.isDnssec()) {
                sb.append("<font color='#4E387E'>DNSSEC</font>");
            }

            tvDNSServerFlags.setText(Html.fromHtml(sb.toString()));

            chbDNSServer.setChecked(dnsServer.isChecked());

            if (dnsServer.getOwnServer()) {
                delBtnDNSServer.setVisibility(View.VISIBLE);
            } else {
                delBtnDNSServer.setVisibility(View.GONE);
            }

        }

        @Override
        public void onClick(View view) {
            switch (view.getId()) {
                case R.id.cardDNSServer:
                    int position = getAdapterPosition();
                    DNSServerItem dnsServer = getItem(position);
                    dnsServer.setChecked(!dnsServer.isChecked());
                    setItem(position, dnsServer);
                    DNSServersAdapter.this.notifyItemChanged(position);
                    break;
                case R.id.btnDNSServerRelay:
                    position = getAdapterPosition();
                    openDNSRelaysPref(position);
                    break;
                case R.id.delBtnDNSServer:
                    position = getAdapterPosition();
                    removeItem(position);
                    DNSServersAdapter.this.notifyItemRemoved(position);
                    break;
            }
        }

        @Override
        public void onFocusChange(View view, boolean b) {
            if (b) {
                ((CardView) view).setCardBackgroundColor(context.getResources().getColor(R.color.colorSecond));
                view.findViewById(R.id.btnDNSServerRelay).setBackgroundColor(context.getResources().getColor(R.color.colorSecond));
            } else {
                ((CardView) view).setCardBackgroundColor(context.getResources().getColor(R.color.colorFirst));
                view.findViewById(R.id.btnDNSServerRelay).setBackgroundColor(context.getResources().getColor(R.color.colorFirst));
            }
        }

        @Override
        public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
            int position = getAdapterPosition();
            DNSServerItem dnsServer = getItem(position);
            if (dnsServer.isChecked() != checked) {
                dnsServer.setChecked(checked);
                setItem(position, dnsServer);
                DNSServersAdapter.this.notifyItemChanged(position);
            }
        }

        @Override
        public boolean onLongClick(View view) {
            if (view.getId() == R.id.cardDNSServer
                    || view.getId() == R.id.btnDNSServerRelay) {
                int position = getAdapterPosition();
                openDNSRelaysPref(position);
            }
            return true;
        }
    }

    private void openDNSRelaysPref(int position) {
        DNSServerItem dnsServer = getItem(position);
        Bundle bundle = new Bundle();
        bundle.putString("dnsServerName", dnsServer.getName());
        bundle.putSerializable("routesCurrent", routes_current);
        PreferencesDNSCryptRelays preferencesDNSCryptRelays = new PreferencesDNSCryptRelays();
        preferencesDNSCryptRelays.setOnRoutesChangeListener(preferencesDNSCryptServers);
        preferencesDNSCryptRelays.setArguments(bundle);
        if (fragmentManager != null) {
            FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
            fragmentTransaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
            fragmentTransaction.replace(android.R.id.content, preferencesDNSCryptRelays);
            fragmentTransaction.addToBackStack("preferencesDNSCryptRelaysTag");
            fragmentTransaction.commit();
        }
    }
}
