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

    private final static String PTR_SUFFIX_IPV4 = ".in-addr.arpa";
    private final static String PTR_SUFFIX_IPV6 = ".ip6.arpa";

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
        if (isIPv6Address(ip)) {
            String ipDecompressed = decompressIPv6Address(ip);
            List<String> list = Arrays.asList(
                    ipDecompressed.replace(":", "").split("")
            );
            Collections.reverse(list);
            return TextUtils.join(".", list) +
                    PTR_SUFFIX_IPV6;
        } else {
            List<String> list = Arrays.asList(ip.split("\\."));
            Collections.reverse(list);
            return TextUtils.join(".", list) +
                    PTR_SUFFIX_IPV4;
        }
    }

    private boolean isIPv6Address(String ip) {
        return ip.contains(":");
    }

    private String decompressIPv6Address(String ip) {

        StringBuilder address = new StringBuilder(ip);

        // Store the location where you need add zeroes that were removed during decompression
        int tempCompressLocation = address.indexOf("::");

        //if address was compressed and zeroes were removed, remove that marker i.e "::"
        if (tempCompressLocation != -1) {
            address.replace(tempCompressLocation, tempCompressLocation + 2, ":");
        }

        //extract rest of the components by splitting them using ":"
        String[] addressComponents = address.toString().split(":");

        for (int i = 0; i < addressComponents.length; i++) {
            StringBuilder decompressedComponent = new StringBuilder();
            for (int j = 0; j < 4 - addressComponents[i].length(); j++) {
                //add a padding of the ignored zeroes during compression if required
                decompressedComponent.append("0");
            }
            decompressedComponent.append(addressComponents[i]);

            //replace the compressed component with the uncompressed one
            addressComponents[i] = decompressedComponent.toString();
        }


        //Iterate over the uncompressed address components to add the ignored "0000" components depending on position of "::"
        ArrayList<String> decompressedAddressComponents = new ArrayList<>();

        for (int i = 0; i < addressComponents.length; i++) {
            if (i == tempCompressLocation / 4) {
                for (int j = 0; j < 8 - addressComponents.length; j++) {
                    decompressedAddressComponents.add("0000");
                }
            }
            decompressedAddressComponents.add(addressComponents[i]);

        }

        //iterate over the decompressed components to append and produce a full address
        StringBuilder decompressedAddress = new StringBuilder();
        for (String decompressedAddressComponent : decompressedAddressComponents) {
            decompressedAddress.append(decompressedAddressComponent);
            decompressedAddress.append(":");
        }
        decompressedAddress.replace(decompressedAddress.length() - 1, decompressedAddress.length(), "");
        return decompressedAddress.toString();
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
