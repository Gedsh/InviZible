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

package pan.alexander.tordnscrypt.installer;

import android.annotation.SuppressLint;
import java.io.File;

public class ChmodCommand {

    @SuppressLint("SetWorldReadable")
    public static void dirChmod(String path, boolean executableDir) {
        File dir = new File(path);

        if (!dir.isDirectory()) {
            throw new IllegalStateException("dirChmod dir not exist or not dir " + path);
        }

        if (!dir.setReadable(true, false)
                || !dir.setWritable(true)
                || !dir.setExecutable(true, false)) {
            throw new IllegalStateException("DirChmod chmod dir fault " + path);
        }

        File[] files = dir.listFiles();

        if (files == null) {
            return;
        }

        for (File file: files) {

            if (file.isDirectory()) {

                dirChmod(file.getAbsolutePath(), executableDir);

            } else if (file.isFile()) {

                if (executableDir) {
                    executableFileChmod(file.getAbsolutePath());
                } else {
                    regularFileChmod(file.getAbsolutePath());
                }
            }

        }


    }

    @SuppressLint("SetWorldReadable")
    private static void executableFileChmod(String path) {
        File executable = new File(path);

        if (!executable.isFile()) {
            throw new IllegalStateException("executableFileChmod file not exist or not file " + path);
        }

        if (!executable.setReadable(true, false)
                || !executable.setWritable(true)
                || !executable.setExecutable(true, false)) {
            throw new IllegalStateException("executableFileChmod chmod file fault " + path);
        }
    }

    @SuppressLint("SetWorldReadable")
    private static void regularFileChmod(String path) {
        File file = new File(path);

        if (!file.isFile()) {
            throw new IllegalStateException("regularFileChmod file not exist or not file " + path);
        }

        if (!file.setReadable(true, false)
                || !file.setWritable(true)) {
            throw new IllegalStateException("regularFileChmod chmod file fault " + path);
        }
    }
}
