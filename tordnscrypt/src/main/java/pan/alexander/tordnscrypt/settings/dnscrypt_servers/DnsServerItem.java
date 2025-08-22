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

import static pan.alexander.tordnscrypt.utils.logger.Logger.loge;

import android.util.Base64;

import androidx.annotation.NonNull;

import java.nio.charset.StandardCharsets;
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
    private String address;
    private int ping;
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

        if (sdns.length() < 15) {
            throw new IllegalArgumentException("Wrong sever type " + name);
        }

        byte[] bin = Base64.decode(sdns.getBytes(), Base64.URL_SAFE);
        if (bin[0] == 0x01) {
            protoDNSCrypt = true;
        } else if (bin[0] == 0x02) {
            protoDoH = true;
        } else if (bin[0] == 0x05) {
            protoODoH = true;
        } else {
            throw new IllegalArgumentException("Wrong sever type " + name);
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

        calculateAddress(bin);

        if (name.contains("v6") || name.contains("ip6") || name.endsWith("6")) {
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

    private void calculateAddress(byte[] bin) {
        try {
            int binLen = bin.length;
            int pos = 9;
            int addrLen = Byte.toUnsignedInt(bin[pos]);
            if (1 + addrLen >= bin.length - pos) {
                throw new IllegalArgumentException("Invalid sdns address " + name);
            }
            pos++;
            String addr = new String(bin, pos, addrLen, StandardCharsets.UTF_8);
            pos += addrLen;
            if (protoDoH && addr.isBlank()) {
                // Hashes
                while (true) {
                    int vlen = Byte.toUnsignedInt(bin[pos]);
                    int lengthHash = vlen & ~0x80;
                    if (1 + lengthHash >= binLen - pos) {
                        throw new IllegalArgumentException("Invalid sdns hash " + name);
                    }
                    pos++;
                    pos += lengthHash;
                    if ((vlen & 0x80) != 0x80) {
                        break;
                    }
                }

                // Host name
                int length = Byte.toUnsignedInt(bin[pos]);
                if (1 + length >= binLen - pos) {
                    throw new IllegalArgumentException("Invalid sdns host name " + name);
                }
                pos++;
                if (addr.isEmpty()) {
                    addr = new String(bin, pos, length, StandardCharsets.UTF_8);
                }
                pos += length;

                // Path
                length = Byte.toUnsignedInt(bin[pos]);
                if (length >= binLen - pos) {
                    throw new IllegalArgumentException("Invalid sdns path " + name);
                }
                pos++;
                String path = new String(bin, pos, length, StandardCharsets.UTF_8);
                pos += length;

                if (pos != binLen) {
                    throw new Exception("Invalid sdns (garbage after end) " + name);
                }

                if (addr.isEmpty() && path.contains("/") && path.indexOf("/") > 0) {
                    addr = path.substring(0, path.indexOf("/"));
                }
            }

            if (!addr.isEmpty() && (!addr.contains(":") || !addr.matches(".+:\\d{1,5}$"))) {
                addr += ":443";
            }
            address = addr;
        } catch (Exception e) {
            loge("DnsServerItem calculateAddressAndHost " + name, e);
        }
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

    public String getAddress() {
        return address;
    }

    public int getPing() {
        return ping;
    }

    public void setPing(int ping) {
        this.ping = ping;
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
                ", addr='" + address + '\'' +
                ", ping='" + ping + '\'' +
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
