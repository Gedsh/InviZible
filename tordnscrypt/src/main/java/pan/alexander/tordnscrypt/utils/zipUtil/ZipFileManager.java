package pan.alexander.tordnscrypt.utils.zipUtil;

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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class ZipFileManager {
    private String zipFile;

    public ZipFileManager(String zipFile) {
        this.zipFile = zipFile;
    }

    public ZipFileManager() {
    }

    public void extractZipFromInputStream(InputStream inputStream, String outputPathDir) throws Exception {
        File outputFile = new File(removeEndSlash(outputPathDir));

        if (!outputFile.isDirectory()) {
            if (!outputFile.mkdir()) {
                throw new IllegalStateException("ZipFileManager cannot create output dir " + outputPathDir);
            }
        }

        try (ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {

            ZipEntry zipEntry = zipInputStream.getNextEntry();

            while (zipEntry != null) {

                if (zipEntry.isDirectory()) {
                    String fileName = zipEntry.getName();
                    File fileFullName = new File(outputPathDir + "/" + removeEndSlash(fileName));

                    if (!fileFullName.isDirectory()) {
                        if (!fileFullName.mkdirs()) {
                            throw new IllegalStateException("ZipFileManager cannot create output dirs structure: dir " + fileFullName.getAbsolutePath());
                        }
                    }
                } else {
                    String fileName = zipEntry.getName();
                    File fileFullName = new File(outputPathDir + "/" + removeEndSlash(fileName));
                    File fileParent = new File(removeEndSlash(fileFullName.getParent()));

                    if (!fileParent.isDirectory()) {
                        if (!fileParent.mkdirs()) {
                            throw new IllegalStateException("ZipFileManager cannot create output dirs structure: dir " + fileParent.getAbsolutePath());
                        }
                    }

                    try (OutputStream outputStream = new FileOutputStream(fileFullName)) {
                        copyData(zipInputStream, outputStream);
                    }
                }

                zipEntry = zipInputStream.getNextEntry();
            }
        }
    }

    public void extractZip(String outputPathDir) throws Exception {
        File inputFile = new File(zipFile);

        if (!inputFile.exists()) {
            throw new FileNotFoundException("ZipFileManager input file missing " + zipFile);
        }

        try (InputStream inputStream = new FileInputStream(inputFile)) {
            extractZipFromInputStream(inputStream, outputPathDir);
        }

    }

    public void createZip(String ... inputSource) throws Exception {
        List<File> inputSources = new ArrayList<>();
        for (String source: inputSource) {
            inputSources.add(new File(source));
        }

        File outputFile = new File(zipFile);
        File outputFileDir = new File(removeEndSlash(outputFile.getParent()));

        if (!outputFileDir.isDirectory()) {
            if (outputFileDir.mkdirs()) {
                throw new IllegalStateException("ZipFileManager cannot create output dir " + outputFileDir.getAbsolutePath());
            }
        }

        try (ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(zipFile))) {
            for (File inputFile: inputSources) {
                addZipEntry(zipOutputStream, removeEndSlash(inputFile.getParent()), inputFile.getName());
            }
        }
    }

    private void addZipEntry(ZipOutputStream zipOutputStream, String inputPath, String fileName) throws Exception {

        String fullPath = inputPath + "/" + fileName;

        File inputFile = new File(fullPath);
        if (inputFile.isDirectory()) {

            File[] files = inputFile.listFiles();



            for (File file: files) {
                String nextFileName = file.getAbsolutePath().replace(inputPath + "/", "");
                addZipEntry(zipOutputStream, inputPath, nextFileName);
            }
        } else if (inputFile.isFile()) {
            try (InputStream inputStream = new FileInputStream(fullPath)) {
                ZipEntry zipEntry = new ZipEntry(fileName);
                zipOutputStream.putNextEntry(zipEntry);
                copyData(inputStream, zipOutputStream);
                zipOutputStream.closeEntry();
            }
        } else {
            throw new IllegalStateException("createZip input fault: input no file and no dir " + fullPath);
        }




    }

    private void copyData(InputStream in, OutputStream out) throws Exception {
        byte[] buffer = new byte[8 * 1024];
        int len;
        while ((len = in.read(buffer)) > 0) {
            out.write(buffer, 0, len);
        }
    }

    private String removeEndSlash(String path) {
        if (path.trim().endsWith("/")) {
            path = path.substring(0, path.lastIndexOf("/"));
        }
        return path;
    }
}
