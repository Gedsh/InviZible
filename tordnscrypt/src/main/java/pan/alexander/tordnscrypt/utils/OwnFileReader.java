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

    Copyright 2019-2020 by Garmatin Oleksandr invizible.soft@gmail.com
*/

import android.content.Context;
import android.text.Html;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import pan.alexander.tordnscrypt.utils.file_operations.FileOperations;

import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;

public class OwnFileReader {

    private static final ReentrantLock reentrantLock = new ReentrantLock();

    private final Context context;
    private final String filePath;
    private BufferedReader br = null;
    private FileInputStream fstream = null;
    private List<String> lines;

    public OwnFileReader(Context context, String filePath) {
        this.context = context;
        this.filePath = filePath;
    }

    public String readLastLines() {

        String result = "";

        try {

            reentrantLock.lockInterruptibly();

            File file = new File(filePath);

            if (!file.exists()) {
                return "";
            }

            if (!file.canRead()) {
                if (!file.setReadable(true)) {
                    Log.w(LOG_TAG, "Impossible to read file " + filePath + " Try restore access");

                    FileOperations fileOperations = new FileOperations();
                    fileOperations.restoreAccess(context, filePath);
                }

                if (file.canRead()) {
                    Log.i(LOG_TAG, "Access to " + filePath + " restored");
                } else {
                    Log.e(LOG_TAG, "Impossible to read file " + filePath);
                }
            }

            lines = new LinkedList<>();
            StringBuilder sb = new StringBuilder();

            shortenToToLongFile();


            fstream = new FileInputStream(filePath);
            br = new BufferedReader(new InputStreamReader(fstream));

            for (String tmp; (tmp = br.readLine()) != null; )
                if (lines.add(tmp) && lines.size() > 100) {
                    lines.remove(0);
                }

            for (String s : lines) {
                s = Html.escapeHtml(s);
                if (s.toLowerCase().contains("[notice]") || s.toLowerCase().contains("/info")) {
                    s = "<font color=#808080>" + s.replace("[notice]", "").replace("[NOTICE]", "") + "</font>";
                } else if (s.toLowerCase().contains("[warn]") || s.toLowerCase().contains("/warn")) {
                    s = "<font color=#ffa500>" + s + "</font>";
                } else if (s.toLowerCase().contains("[warning]")) {
                    s = "<font color=#ffa500>" + s + "</font>";
                } else if (s.toLowerCase().contains("[error]") || s.toLowerCase().contains("/error")) {
                    s = "<font color=#f08080>" + s + "</font>";
                } else if (s.toLowerCase().contains("[critical]")) {
                    s = "<font color=#990000>" + s + "</font>";
                } else if (s.toLowerCase().contains("[fatal]")) {
                    s = "<font color=#990000>" + s + "</font>";
                } else if (!s.isEmpty()) {
                    s = "<font color=#6897bb>" + s + "</font>";
                }

                if (!s.isEmpty()) {
                    sb.append(s);
                    sb.append("<br />");
                }

            }

            shortenToLongFile();

            result = sb.toString();
            int lastBrIndex = result.lastIndexOf("<br />");
            if (lastBrIndex > 0) {
                result = result.substring(0, lastBrIndex);
            }

        } catch (Exception e) {
            Log.e(LOG_TAG, "Impossible to read file " + filePath + " " + e.getMessage() + " " + e.getCause());
        } finally {
            try {
                if (fstream != null) fstream.close();
                if (br != null) br.close();
            } catch (IOException ex) {
                Log.e(LOG_TAG, "Error when close file " + filePath + " " + ex.getMessage());
            }

            reentrantLock.unlock();
        }

        return result;
    }

    private void shortenToLongFile() {
        File file = new File(filePath);
        if (!file.exists())
            return;

        if (file.length() / 1024 > 100) {
            try {
                PrintWriter writer = new PrintWriter(file, "UTF-8");
                if (lines != null && lines.size() != 0) {
                    StringBuilder buffer = new StringBuilder();
                    for (String line : lines) {
                        buffer.append(line).append(System.lineSeparator());
                    }
                    writer.println(buffer);
                }
                writer.close();
            } catch (IOException e) {
                Log.e(LOG_TAG, "Unable to rewrite i2pd log file " + e.getMessage());
            }
        }
    }

    public void shortenToToLongFile() {
        File file = new File(filePath);
        if (!file.exists())
            return;

        if (file.length() / 1024 > 500) {
            try {
                PrintWriter writer = new PrintWriter(file, "UTF-8");
                writer.println("");
                writer.close();
            } catch (IOException e) {
                Log.e(LOG_TAG, "Unable to rewrite +" + filePath + " log file " + e.getMessage());
            }
        }
    }

}
