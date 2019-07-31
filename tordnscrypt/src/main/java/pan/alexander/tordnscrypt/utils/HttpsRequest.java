package pan.alexander.tordnscrypt.utils;
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

import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;

class HttpsRequest {
    private static final int READTIMEOUT = 30;
    private static final int CONNECTTIMEOUT = 30;
    static String post(String serverUrl, String dataToSend) throws IOException {
        URL url = new URL(serverUrl);
        HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
        //set timeout of 30 seconds
        con.setConnectTimeout(1000 * CONNECTTIMEOUT);
        con.setReadTimeout(1000 * READTIMEOUT);
        //method
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        con.setRequestProperty("Content-Length", String.valueOf(dataToSend.getBytes().length));
        con.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/73.0.3683.86 Safari/537.36");

        OutputStream os = con.getOutputStream();
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8));

        //make request
        writer.write(dataToSend);
        writer.flush();
        writer.close();
        os.close();

        //get the response
        int responseCode = con.getResponseCode();

        if (responseCode == HttpURLConnection.HTTP_OK) {
            //read the response
            StringBuilder sb = new StringBuilder();

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(con.getInputStream()));
            String line;

            //loop through the response from the server
            while ((line = reader.readLine()) != null) {
                sb.append(line).append(System.lineSeparator());
            }

            con.disconnect();

            //return the response
            return sb.toString();
        } else {
            Log.e(LOG_TAG, "Invalid response code from server " + responseCode);
            return "";
        }
    }

    static String hashMapToUrl(HashMap<String, String> params) throws UnsupportedEncodingException {
        StringBuilder result = new StringBuilder();
        boolean first = true;
        for(Map.Entry<String, String> entry : params.entrySet()){
            if (first)
                first = false;
            else
                result.append("&");

            result.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
            result.append("=");
            result.append(URLEncoder.encode(entry.getValue(), "UTF-8"));
        }

        return result.toString();
    }
}
