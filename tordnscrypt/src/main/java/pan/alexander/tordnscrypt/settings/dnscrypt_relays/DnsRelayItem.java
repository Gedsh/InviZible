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

import androidx.annotation.NonNull;

import java.util.Objects;

class DnsRelayItem implements Comparable<DnsRelayItem> {
    private final String name;
    private final String description;
    private final String sdns;
    private int ping;
    private boolean checked;

    DnsRelayItem(String name, String description, String sdns) {
        this.name = name;
        this.description = description;
        this.sdns = sdns;
    }

    String getName() {
        return name;
    }

    String getDescription() {
        return description;
    }

    public String getSdns() {
        return sdns;
    }

    public int getPing() {
        return ping;
    }

    public void setPing(int ping) {
        this.ping = ping;
    }

    boolean isChecked() {
        return checked;
    }

    void setChecked(boolean checked) {
        this.checked = checked;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DnsRelayItem that = (DnsRelayItem) o;
        return name.equals(that.name) &&
                description.equals(that.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, description);
    }

    @NonNull
    @Override
    public String toString() {
        return "DNSRelayItem{" +
                "name='" + name + '\'' +
                ", checked=" + checked +
                '}';
    }

    @Override
    public int compareTo(DnsRelayItem o) {
        if (this.checked && !o.checked) {
            return -1;
        } else if (!this.checked && o.checked) {
            return 1;
        }
        return 0;
    }
}
