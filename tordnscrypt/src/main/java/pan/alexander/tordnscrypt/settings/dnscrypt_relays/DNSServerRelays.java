package pan.alexander.tordnscrypt.settings.dnscrypt_relays;

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

    Copyright 2019 by Garmatin Oleksandr invizible.soft@gmail.com
*/

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

public class DNSServerRelays implements Serializable {
    private String dnsServerName;
    private List<String> dnsServerRelays;

    public DNSServerRelays(String dnsServerName, List<String> dnsServerRelays) {
        this.dnsServerName = dnsServerName;
        this.dnsServerRelays = dnsServerRelays;
    }

    public String getDnsServerName() {
        return dnsServerName;
    }

    public List<String> getDnsServerRelays() {
        return dnsServerRelays;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DNSServerRelays that = (DNSServerRelays) o;
        return dnsServerName.equals(that.dnsServerName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dnsServerName);
    }
}
