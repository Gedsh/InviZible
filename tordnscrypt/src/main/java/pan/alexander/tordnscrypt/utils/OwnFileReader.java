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
import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import pan.alexander.tordnscrypt.utils.file_operations.FileOperations;

import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;

public class OwnFileReader {
    private final static long TOO_TOO_LONG_FILE_LENGTH = 1024 * 500;
    private final static long TOO_TOO_LONG_FILE_LENGTH_HYSTERESIS = 1024 * 100;
    private final static long TOO_LONG_FILE_LENGTH = 1024 * 100;
    private final static int MAX_LINES_QUANTITY = 100;

    private static final ReentrantLock reentrantLock = new ReentrantLock();

    private final Context context;
    private final String filePath;

    public OwnFileReader(Context context, String filePath) {
        this.context = context;
        this.filePath = filePath;
    }

    public String readLastLines() {

        List<String> lines = new LinkedList<>();
        String result = "";
        boolean fileIsTooLong = false;

        try (FileInputStream fstream = new FileInputStream(filePath);
             InputStreamReader reader = new InputStreamReader(fstream);
             BufferedReader br = new BufferedReader(reader)) {

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

            shortenTooTooLongFile();

            for (String tmp; (tmp = br.readLine()) != null; )
                if (lines.add(tmp) && lines.size() > MAX_LINES_QUANTITY) {
                    lines.remove(0);
                    fileIsTooLong = true;
                }

            for (String s : lines) {

                //s = Html.escapeHtml(s);
                s = TextUtils.htmlEncode(s);

                if (s.toLowerCase().contains("[notice]") || s.toLowerCase().contains("/info")) {
                    s = "<font color=#808080>" + s.replace("[notice]", "")
                            .replace("[NOTICE]", "") + "</font>";
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

            result = sb.toString();
            int lastBrIndex = result.lastIndexOf("<br />");
            if (lastBrIndex > 0) {
                result = result.substring(0, lastBrIndex);
            }

        } catch (Exception e) {
            Log.e(LOG_TAG, "Impossible to read file " + filePath + " " + e.getMessage() + " " + e.getCause());
        } finally {
            reentrantLock.unlock();
        }

        if (fileIsTooLong) {
            shortenTooLongFile(lines);
        }

        return result;
    }

    private void shortenTooLongFile(List<String> lines) {
        File file = new File(filePath);
        if (!file.exists())
            return;

        if (file.length() > TOO_LONG_FILE_LENGTH) {
            try (PrintWriter writer = new PrintWriter(file, "UTF-8")) {
                if (lines != null && lines.size() != 0) {
                    StringBuilder buffer = new StringBuilder();
                    for (String line : lines) {
                        buffer.append(line).append("\n");
                    }
                    writer.println(buffer);
                }
            } catch (IOException e) {
                Log.e(LOG_TAG, "Unable to rewrite too long file" + filePath + e.getMessage() + " " + e.getCause());
            }
        }
    }

    public void shortenTooTooLongFile() {
        File file = new File(filePath);
        if (!file.exists())
            return;

        long fileLength = file.length();

        if (fileLength > TOO_TOO_LONG_FILE_LENGTH) {

            try (RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw");
                 ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

                randomAccessFile.seek(fileLength - (TOO_TOO_LONG_FILE_LENGTH - TOO_TOO_LONG_FILE_LENGTH_HYSTERESIS));

                byte[] buffer = new byte[1024];
                int len;
                while ((len = randomAccessFile.read(buffer)) != -1) {
                    baos.write(buffer, 0, len);
                }
                baos.flush();

                randomAccessFile.seek(0);
                randomAccessFile.write(baos.toByteArray());
                randomAccessFile.setLength(baos.size());

            } catch (IOException e) {
                Log.e(LOG_TAG, "Unable to rewrite too too long file" + filePath + e.getMessage() + " " + e.getCause());
            }
        }
    }

}
