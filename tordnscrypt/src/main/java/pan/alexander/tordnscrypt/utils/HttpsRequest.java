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

    Copyright 2019-2021 by Garmatin Oleksandr invizible.soft@gmail.com
*/

import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

import pan.alexander.tordnscrypt.modules.ModulesStatus;
import pan.alexander.tordnscrypt.settings.PathVars;

import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RUNNING;

public class HttpsRequest {
    private static final int READTIMEOUT = 30;
    private static final int CONNECTTIMEOUT = 30;
    public static String post(Context context, String serverUrl, String dataToSend) throws IOException {

        Proxy proxy = null;
        if (ModulesStatus.getInstance().getTorState() == RUNNING) {
            PathVars pathVars = PathVars.getInstance(context);
            proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", Integer.parseInt(pathVars.getTorHTTPTunnelPort())));
        }


        URL url = new URL(serverUrl);

        HttpsURLConnection con;
        if (proxy == null) {
            con = (HttpsURLConnection) url.openConnection();
        } else {
            con = (HttpsURLConnection) url.openConnection(proxy);
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            con.setHostnameVerifier((hostname, session) -> true);
        }

        //set timeout of 30 seconds
        con.setConnectTimeout(1000 * CONNECTTIMEOUT);
        con.setReadTimeout(1000 * READTIMEOUT);
        //method
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        con.setRequestProperty("Content-Length", String.valueOf(dataToSend.getBytes().length));
        con.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 9.0.1; " +
                "Mi Mi) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.100 Mobile Safari/537.36");

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

    public static String hashMapToUrl(HashMap<String, String> params) throws UnsupportedEncodingException {
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
