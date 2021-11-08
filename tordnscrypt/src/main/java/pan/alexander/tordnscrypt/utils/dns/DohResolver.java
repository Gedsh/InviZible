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

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;

public class DohResolver extends DnsResolver {

    @AssistedInject
    public DohResolver(@Assisted String server) {
        super(server);
    }

    @Override
    DnsResponse request(String server, String host, int recordType) throws IOException {
        double d = Math.random();
        short messageId = (short) (d * 0xFFFF);
        DnsRequest request = new DnsRequest(messageId, recordType, host);
        byte[] requestData = request.toDnsQuestionData();

        HttpsURLConnection httpConn = (HttpsURLConnection) new URL(server).openConnection();
        httpConn.setConnectTimeout(timeout * 1000);
        httpConn.setReadTimeout(timeout * 1000);
        httpConn.setDoOutput(true);
        httpConn.setRequestMethod("POST");
        httpConn.setRequestProperty("Content-Type", "application/dns-message");
        httpConn.setRequestProperty("Accept", "application/dns-message");
        httpConn.setRequestProperty("Accept-Encoding", "");

        DataOutputStream bodyStream = new DataOutputStream(httpConn.getOutputStream());
        bodyStream.write(requestData);
        bodyStream.close();

        int responseCode = httpConn.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            return null;
        }

        int length = httpConn.getContentLength();
        if (length <= 0 || length > 1024 * 1024) {
            return null;
        }
        InputStream is = httpConn.getInputStream();
        byte[] responseData = new byte[length];
        int read = is.read(responseData);
        is.close();
        if (read <= 0) {
            return null;
        }

        return new DnsResponse(server, Record.Source.Doh, request, responseData);
    }
}
