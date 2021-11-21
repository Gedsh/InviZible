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

package pan.alexander.tordnscrypt.utils.dns;

import static pan.alexander.tordnscrypt.utils.Constants.IPv4_REGEX;
import static pan.alexander.tordnscrypt.utils.Constants.IPv6_REGEX;

import android.text.TextUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public abstract class DnsResolver implements Resolver {

    private final static String PTR_SUFFIX = ".in-addr.arpa";

    private final int recordType;
    private final String server;

    protected final int timeout;

    public DnsResolver(String server, int recordType, int timeout) {
        this.recordType = recordType;
        this.timeout = timeout > 0 ? timeout : DNS_DEFAULT_TIMEOUT_SEC;
        this.server = server;
    }

    @Override
    public Record[] resolve(Domain domain) throws IOException {
        DnsResponse response = lookupHost(domain.domain);
        if (response == null) {
            throw new IOException("response is null");
        }

        List<Record> answers = response.getAnswerArray();
        if (answers == null || answers.size() == 0) {
            return null;
        }

        List<Record> records = new ArrayList<>();
        for (Record record : answers) {
            if (record.isA() || record.isCname() || record.isAAAA()) {
                records.add(record);
            }
        }
        return records.toArray(new Record[0]);
    }

    @Override
    public Record[] reverseResolve(String ip) throws IOException {

        if (!ip.matches(IPv4_REGEX) && !ip.matches(IPv6_REGEX)) {
            throw new IllegalArgumentException("IP wrong format " + ip);
        }

        String ptrRequest = ipToPointerRequest(ip);

        DnsResponse response = lookupHost(ptrRequest);
        if (response == null) {
            throw new IOException("response is null");
        }

        List<Record> answers = response.getAnswerArray();
        if (answers == null || answers.size() == 0) {
            return null;
        }

        List<Record> records = new ArrayList<>();
        for (Record record : answers) {
            if (record.isPointer()) {
                records.add(record);
            }
        }

        return records.toArray(new Record[0]);
    }

    private String ipToPointerRequest(String ip) {
        List<String> list = Arrays.asList(ip.split("\\."));
        Collections.reverse(list);
        return TextUtils.join(".", list) +
                PTR_SUFFIX;
    }

    private DnsResponse lookupHost(String host) throws IOException {
        return request(host, recordType);
    }

    private DnsResponse request(final String host, final int recordType) throws IOException {
        if (server == null) {
            throw new IOException("server can not empty");
        }

        if (host == null || host.length() == 0) {
            throw new IOException("host can not empty");
        }

        return request(server, host, recordType);
    }

    abstract DnsResponse request(String server, String host, int recordType) throws IOException;
}
