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
import android.content.SharedPreferences;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.Objects;

import pan.alexander.tordnscrypt.R;

public class DNSServerItem implements Comparable<DNSServerItem> {
    private boolean checked = false;
    private boolean dnssec = false;
    private boolean nolog = false;
    private boolean nofilter = false;
    private boolean protoDoH = false;
    private boolean protoDNSCrypt = false;
    private boolean ipv6 = false;
    private boolean visibility = true;
    private String name;
    private final String description;
    private final String sdns;
    private boolean ownServer = false;
    private final ArrayList<String> routes = new ArrayList<>();

    public DNSServerItem(Context context, String name, String description, String sdns) throws Exception {
        this.name = name;
        this.description = description;
        this.sdns = sdns;

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);

        boolean require_dnssec = sp.getBoolean("require_dnssec", false);
        boolean require_nofilter = sp.getBoolean("require_nofilter", false);
        boolean require_nolog = sp.getBoolean("require_nolog", false);
        boolean use_dns_servers = sp.getBoolean("dnscrypt_servers", true);
        boolean use_doh_servers = sp.getBoolean("doh_servers", true);
        boolean use_ipv4_servers = sp.getBoolean("ipv4_servers", true);
        boolean use_ipv6_servers = sp.getBoolean("ipv6_servers", false);

        if (context.getText(R.string.package_name).toString().contains(".gp")) {
            require_nofilter = true;
        }

        byte[] bin = Base64.decode(sdns.substring(0, 7).getBytes(), 16);
        if (bin[0] == 0x01) {
            protoDNSCrypt = true;
        } else if (bin[0] == 0x02) {
            protoDoH = true;
        } else {
            throw new Exception("Wrong sever type");
        }

        if (((bin[1]) & 1) == 1) {
            this.dnssec = true;
        }
        if (((bin[1] >> 1) & 1) == 1) {
            this.nolog = true;
        }
        if (((bin[1] >> 2) & 1) == 1) {
            this.nofilter = true;
        }

        if (name.contains("v6") || name.contains("ip6")) {
            ipv6 = true;
        }

        if (require_dnssec)
            this.visibility = this.dnssec;

        if (require_nofilter)
            this.visibility = this.visibility && this.nofilter;

        if (require_nolog)
            this.visibility = this.visibility && this.nolog;

        if (!use_dns_servers)
            this.visibility = this.visibility && !this.protoDNSCrypt;

        if (!use_doh_servers)
            this.visibility = this.visibility && !this.protoDoH;

        if (!use_ipv4_servers)
            this.visibility = this.visibility && ipv6;

        if (!use_ipv6_servers)
            this.visibility = this.visibility && !ipv6;
    }

    public boolean isChecked() {
        return checked;
    }

    public void setChecked(boolean checked) {
        this.checked = checked;
    }

    boolean isDnssec() {
        return dnssec;
    }

    boolean isNolog() {
        return nolog;
    }

    boolean isNofilter() {
        return nofilter;
    }

    boolean isProtoDoH() {
        return protoDoH;
    }

    boolean isProtoDNSCrypt() {
        return protoDNSCrypt;
    }

    boolean isVisibility() {
        return visibility;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    String getDescription() {
        return description;
    }

    public void setOwnServer(boolean ownServer) {
        this.ownServer = ownServer;
    }

    boolean getOwnServer() {
        return ownServer;
    }

    String getSDNS() {
        return sdns;
    }

    ArrayList<String> getRoutes() {
        return routes;
    }

    public boolean isIpv6() {
        return ipv6;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DNSServerItem that = (DNSServerItem) o;
        return dnssec == that.dnssec &&
                nolog == that.nolog &&
                nofilter == that.nofilter &&
                protoDoH == that.protoDoH &&
                protoDNSCrypt == that.protoDNSCrypt &&
                name.equals(that.name) &&
                description.equals(that.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dnssec, nolog, nofilter, protoDoH, protoDNSCrypt, name, description);
    }

    @NonNull
    @Override
    public String toString() {
        return "DNSServerItem{" +
                "checked=" + checked +
                ", dnssec=" + dnssec +
                ", nolog=" + nolog +
                ", nofilter=" + nofilter +
                ", protoDoH=" + protoDoH +
                ", protoDNSCrypt=" + protoDNSCrypt +
                ", visibility=" + visibility +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", routes=" + routes +
                '}';
    }

    @Override
    public int compareTo(DNSServerItem dnsServerItem) {
        if (!this.checked && dnsServerItem.checked) {
            return 1;
        } else if (this.checked && !dnsServerItem.checked) {
            return -1;
        } else {
            return 0;
        }
    }
}
