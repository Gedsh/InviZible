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

package pan.alexander.tordnscrypt.settings.dnscrypt_servers;

import android.util.Base64;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class DnsServerItem implements Comparable<DnsServerItem> {
    private boolean checked = false;
    private boolean dnssec = false;
    private boolean nolog = false;
    private boolean nofilter = false;
    private boolean protoDoH = false;
    private boolean protoODoH = false;
    private boolean protoDNSCrypt = false;
    private boolean ipv6 = false;
    private boolean visibility = true;
    private String name;
    private final String description;
    private final String sdns;
    private boolean ownServer;
    private final ArrayList<String> routes = new ArrayList<>();

    public DnsServerItem(
            String name,
            String description,
            String sdns,
            DnsServerFeatures features
    ) throws IllegalArgumentException {
        this.name = name;
        this.description = description;
        this.sdns = sdns;

        byte[] bin = Base64.decode(sdns.substring(0, 7).getBytes(), 16);
        if (bin[0] == 0x01) {
            protoDNSCrypt = true;
        } else if (bin[0] == 0x02) {
            protoDoH = true;
        } else if (bin[0] == 0x05) {
            protoODoH = true;
        } else {
            throw new IllegalArgumentException("Wrong sever type");
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

        if (features.getRequireDnssec())
            this.visibility = this.dnssec;

        if (features.getRequireNofilter())
            this.visibility = this.visibility && this.nofilter;

        if (features.getRequireNolog())
            this.visibility = this.visibility && this.nolog;

        if (!features.getUseDnsServers())
            this.visibility = this.visibility && !this.protoDNSCrypt;

        if (!features.getUseDohServers())
            this.visibility = this.visibility && !this.protoDoH;

        if (!features.getUseOdohServers())
            this.visibility = this.visibility && !this.protoODoH;

        if (!features.getUseIPv4Servers())
            this.visibility = this.visibility && ipv6;

        if (!features.getUseIPv6Servers())
            this.visibility = this.visibility && !ipv6;

        if (ownServer)
            this.visibility = true;
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

    boolean isProtoODoH() {
        return protoODoH;
    }

    boolean isProtoDNSCrypt() {
        return protoDNSCrypt;
    }

    boolean isVisible() {
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

    void setRoutes(List<String> routes) {
        this.routes.clear();
        this.routes.addAll(routes);
    }

    public boolean isIpv6() {
        return ipv6;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DnsServerItem that = (DnsServerItem) o;
        return dnssec == that.dnssec &&
                nolog == that.nolog &&
                nofilter == that.nofilter &&
                protoDoH == that.protoDoH &&
                protoODoH == that.protoODoH &&
                protoDNSCrypt == that.protoDNSCrypt &&
                name.equals(that.name) &&
                description.equals(that.description) &&
                sdns.equals(that.sdns);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dnssec, nolog, nofilter, protoDoH, protoODoH, protoDNSCrypt, name, description, sdns);
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
                ", protoODoH=" + protoODoH +
                ", protoDNSCrypt=" + protoDNSCrypt +
                ", visibility=" + visibility +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", routes=" + routes +
                '}';
    }

    @Override
    public int compareTo(DnsServerItem dnsServerItem) {
        if (!this.checked && dnsServerItem.checked) {
            return 1;
        } else if (this.checked && !dnsServerItem.checked) {
            return -1;
        } else {
            return this.name.compareTo(dnsServerItem.name);
        }
    }
}
