package pan.alexander.tordnscrypt.utils.file_operations;

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

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Stack;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.PrefManager;
import pan.alexander.tordnscrypt.utils.RootCommands;
import pan.alexander.tordnscrypt.utils.RootExecService;

import static pan.alexander.tordnscrypt.utils.enums.FileOperationsVariants.copyBinaryFile;
import static pan.alexander.tordnscrypt.utils.enums.FileOperationsVariants.deleteFile;
import static pan.alexander.tordnscrypt.utils.enums.FileOperationsVariants.moveBinaryFile;
import static pan.alexander.tordnscrypt.utils.enums.FileOperationsVariants.readTextFile;
import static pan.alexander.tordnscrypt.utils.enums.FileOperationsVariants.writeToTextFile;
import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;

public class FileOperations {
    private final CountDownLatch latch = new CountDownLatch(1);
    private static final Map<String, List<String>> linesListMap = new HashMap<>();
    private static final ReentrantLock reentrantLock = new ReentrantLock();
    private static OnFileOperationsCompleteListener callback;
    private static Stack<OnFileOperationsCompleteListener> stackCallbacks;
    private static ExecutorService executorService = Executors.newSingleThreadExecutor();

    private BroadcastReceiver br = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null) {
                final String action = intent.getAction();
                if (action == null || action.equals("") || ((intent.getIntExtra("Mark", 0) !=
                        RootExecService.FileOperationsMark))) return;
                Log.i(LOG_TAG, "FileOperations onReceive");

                if (action.equals(RootExecService.COMMAND_RESULT)) {
                    continueFileOperations();
                    if (br != null)
                        context.unregisterReceiver(br);
                    br = null;
                }
            }

        }
    };

    public static void moveBinaryFile(final Context context, final String inputPath, final String inputFile, final String outputPath, final String tag) {

        @SuppressLint("SetWorldReadable") Runnable runnable = () -> {

            try {

                reentrantLock.lock();

                File dir = new File(outputPath);
                if (!dir.isDirectory()) {
                    if (!dir.mkdirs()) {
                        throw new IllegalStateException("Unable to create dir " + dir.toString());
                    }

                    if (!dir.canRead() || !dir.canWrite()) {
                        if (!dir.setReadable(true) || !dir.setWritable(true)) {
                            Log.w(LOG_TAG, "Unable to chmod dir " + dir.toString());
                        }
                    }
                }

                File oldFile = new File(outputPath + "/" + inputFile);
                if (oldFile.exists()) {
                    if (deleteFileSynchronous(context, outputPath, inputFile)) {
                        throw new IllegalStateException("Unable to delete file " + oldFile.toString());
                    }
                }

                File inFile = new File(inputPath + "/" + inputFile);
                if (!inFile.canRead()) {
                    if (!inFile.setReadable(true)) {
                        Log.w(LOG_TAG, "Unable to chmod file " + inFile.toString());
                        FileOperations fileOperations = new FileOperations();
                        fileOperations.restoreAccess(context, inFile.getPath());
                    } else if (!inFile.canRead()) {
                        throw new IllegalStateException("Unable to chmod file " + inFile.toString());
                    }
                }

                byte[] buffer = new byte[1024];
                int read;

                try (FileInputStream in = new FileInputStream(inputPath + "/" + inputFile);
                     FileOutputStream out = new FileOutputStream(outputPath + "/" + inputFile)) {
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                }

                File newFile = new File(outputPath + "/" + inputFile);
                if (!newFile.exists()) {
                    throw new IllegalStateException("New file not exist " + oldFile.toString());
                }

                if (tag.contains("executable")) {
                    if (!newFile.setReadable(true, false) || !newFile.setWritable(true) || !newFile.setExecutable(true, false)) {
                        throw new IllegalStateException("Chmod exec file fault " + outputPath + "/" + inputFile);
                    }
                }

                // delete the unwanted file
                if (deleteFileSynchronous(context, inputPath, inputFile)) {
                    throw new IllegalStateException("Unable to delete file " + inputFile);
                }

                if (callback != null && !tag.contains("ignored")) {
                    if (callback instanceof OnBinaryFileOperationsCompleteListener) {
                        ((OnBinaryFileOperationsCompleteListener) callback).OnFileOperationComplete(
                                moveBinaryFile, true, outputPath + "/" + inputFile, tag);
                    } else {
                        throw new ClassCastException("Wrong File operations type. Choose binary type.");
                    }
                }

            } catch (Exception e) {
                Log.e(LOG_TAG, "moveBinaryFile function fault " + e.getMessage() + " " + e.getCause());
                if (callback != null && !tag.contains("ignored")) {
                    if (callback instanceof OnBinaryFileOperationsCompleteListener) {
                        ((OnBinaryFileOperationsCompleteListener) callback).OnFileOperationComplete(
                                moveBinaryFile, false, outputPath + "/" + inputFile, tag);
                    } else {
                        throw new ClassCastException("Wrong File operations type. Choose binary type.");
                    }
                }
            } finally {
                reentrantLock.unlock();
            }
        };

        if (executorService == null || executorService.isShutdown()) {
            executorService = Executors.newSingleThreadExecutor();
        }
        executorService.execute(runnable);
    }

    public static void copyBinaryFile(final Context context, final String inputPath, final String inputFile, final String outputPath, final String tag) {

        Runnable runnable = () -> {


            try {

                reentrantLock.lock();

                File dir = new File(outputPath);
                if (!dir.isDirectory()) {
                    if (!dir.mkdirs()) {
                        throw new IllegalStateException("Unable to create dir " + dir.toString());
                    }

                    if (!dir.canRead() || !dir.canWrite()) {
                        if (!dir.setReadable(true) || !dir.setWritable(true)) {
                            Log.w(LOG_TAG, "Unable to chmod dir " + dir.toString());
                        }
                    }
                }

                File oldFile = new File(outputPath + "/" + inputFile);
                if (oldFile.exists()) {
                    if (deleteFileSynchronous(context, outputPath, inputFile)) {
                        throw new IllegalStateException("Unable to delete file " + oldFile.toString());
                    }
                }

                File inFile = new File(inputPath + "/" + inputFile);
                if (!inFile.canRead()) {
                    if (!inFile.setReadable(true)) {
                        Log.w(LOG_TAG, "Unable to chmod file " + inFile.toString());
                        FileOperations fileOperations = new FileOperations();
                        fileOperations.restoreAccess(context, inFile.getPath());
                    } else if (!inFile.canRead()) {
                        throw new IllegalStateException("Unable to chmod file " + inFile.toString());
                    }
                }

                byte[] buffer = new byte[1024];
                int read;

                try (InputStream in = new FileInputStream(inputPath + "/" + inputFile);
                     OutputStream out = new FileOutputStream(outputPath + "/" + inputFile)) {
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                }

                File newFile = new File(outputPath + "/" + inputFile);
                if (!newFile.exists()) {
                    throw new IllegalStateException("New file not exist " + oldFile.toString());
                }

                if (callback != null && !tag.contains("ignored")) {
                    if (callback instanceof OnBinaryFileOperationsCompleteListener) {
                        ((OnBinaryFileOperationsCompleteListener) callback).OnFileOperationComplete(
                                copyBinaryFile, true, outputPath + "/" + inputFile, tag);
                    } else {
                        throw new ClassCastException("Wrong File operations type. Choose binary type.");
                    }

                }


            } catch (Exception e) {
                if (callback != null && !tag.contains("ignored")) {
                    if (callback instanceof OnBinaryFileOperationsCompleteListener) {
                        ((OnBinaryFileOperationsCompleteListener) callback).OnFileOperationComplete(
                                copyBinaryFile, false, outputPath + "/" + inputFile, tag);
                    } else {
                        throw new ClassCastException("Wrong File operations type. Choose binary type.");
                    }
                }
                Log.e(LOG_TAG, "copyBinaryFile function fault " + e.getMessage() + " " + e.getCause());
            } finally {
                reentrantLock.unlock();
            }

        };

        if (executorService == null || executorService.isShutdown()) {
            executorService = Executors.newSingleThreadExecutor();
        }
        executorService.execute(runnable);
    }

    private static void copyBinaryFileSynchronous(final Context context, final String inputPath,
                                                  final String inputFile, final String outputPath) {

        try {

            reentrantLock.lock();

            File dir = new File(outputPath);
            if (!dir.isDirectory()) {
                if (!dir.mkdirs()) {
                    throw new IllegalStateException("Unable to create dir " + dir.toString());
                }

                if (!dir.canRead() || !dir.canWrite()) {
                    if (!dir.setReadable(true) || !dir.setWritable(true)) {
                        Log.w(LOG_TAG, "Unable to chmod dir " + dir.toString());
                    }
                }
            }

            File oldFile = new File(outputPath + "/" + inputFile);
            if (oldFile.exists()) {
                if (deleteFileSynchronous(context, outputPath, inputFile)) {
                    throw new IllegalStateException("Unable to delete file " + oldFile.toString());
                }
            }

            File inFile = new File(inputPath + "/" + inputFile);
            if (!inFile.canRead()) {
                if (!inFile.setReadable(true)) {
                    Log.w(LOG_TAG, "Unable to chmod file " + inFile.toString());
                    FileOperations fileOperations = new FileOperations();
                    fileOperations.restoreAccess(context, inFile.getPath());
                } else if (!inFile.canRead()) {
                    throw new IllegalStateException("Unable to chmod file " + inFile.toString());
                }
            }

            byte[] buffer = new byte[1024];
            int read;

            try (InputStream in = new FileInputStream(inputPath + "/" + inputFile);
                 OutputStream out = new FileOutputStream(outputPath + "/" + inputFile)) {
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }

            File newFile = new File(outputPath + "/" + inputFile);
            if (!newFile.exists()) {
                throw new IllegalStateException("New file not exist " + oldFile.toString());
            }

        } catch (Exception e) {
            Log.e(LOG_TAG, "copyBinaryFileSynchronous function fault " + e.getMessage() + " " + e.getCause());
        } finally {
            reentrantLock.unlock();
        }

    }

    public static void copyFolderSynchronous(final Context context, final String inputPath, final String outputPath) {
        try {

            reentrantLock.lock();

            File inDir = new File(inputPath);
            if (!inDir.canRead()) {
                if (!inDir.setReadable(true)) {
                    Log.w(LOG_TAG, "Unable to chmod dir " + inDir.toString());
                    FileOperations fileOperations = new FileOperations();
                    fileOperations.restoreAccess(context, inDir.getPath());
                } else if (!inDir.canRead()) {
                    throw new IllegalStateException("Unable to chmod dir " + inDir.toString());
                }
            }

            File outDir = new File(outputPath+ "/" + inDir.getName());
            if (!outDir.isDirectory()) {
                if (!outDir.mkdirs()) {
                    throw new IllegalStateException("Unable to create dir " + outDir.toString());
                }
            }

            if (!outDir.setReadable(true) || !outDir.setWritable(true) || !outDir.setExecutable(true)) {
                Log.w(LOG_TAG, "Unable to chmod dir " + outDir.toString());
            }

            for (File file: Objects.requireNonNull(inDir.listFiles())) {

                if (file.isFile()) {
                    copyBinaryFileSynchronous(context, inputPath, file.getName(), outDir.getCanonicalPath());
                } else if (file.isDirectory()){
                    copyFolderSynchronous(context, file.getCanonicalPath(), outDir.getCanonicalPath());
                } else {
                    throw new IllegalStateException("copyFolderSynchronous cannot copy "
                            + inDir.toString() + " because this is no file and no dir");
                }

            }

        } catch (Exception e) {
            Log.e(LOG_TAG, "copyFolderSynchronous function fault " + e.getMessage() + " " + e.getCause());
        } finally {
            reentrantLock.unlock();
        }
    }

    public static boolean deleteFileSynchronous(final Context context, final String inputPath, final String inputFile) {
        try {
            reentrantLock.lock();

            File usedFile = new File(inputPath + "/" + inputFile);
            if (usedFile.exists()) {
                if (!usedFile.canRead() || !usedFile.canWrite()) {
                    if (!usedFile.setReadable(true) || !usedFile.setWritable(true)) {
                        Log.w(LOG_TAG, "Unable to chmod file " + inputPath + "/" + inputFile);
                        FileOperations fileOperations = new FileOperations();
                        fileOperations.restoreAccess(context, inputPath + "/" + inputFile);
                    } else if (!usedFile.setReadable(true) || !usedFile.setWritable(true)) {
                        reentrantLock.unlock();
                        Log.e(LOG_TAG, "Unable to chmod file " + inputPath + "/" + inputFile);
                        return true;
                    }
                }
                if (!usedFile.delete()) {
                    Log.w(LOG_TAG, "Unable to delete file " + usedFile.toString() + " Try restore access!");

                    FileOperations fileOperations = new FileOperations();
                    fileOperations.restoreAccess(context, inputPath + "/" + inputFile);

                    if (!usedFile.delete()) {
                        Log.e(LOG_TAG, "Unable to delete file " + usedFile.toString());
                    }

                    reentrantLock.unlock();
                    return true;
                }
            } else {
                reentrantLock.unlock();
                Log.w(LOG_TAG, "Unable to delete file internal function. No file " + usedFile.toString());
                return false;
            }
        } catch (Exception e) {
            reentrantLock.unlock();
            Log.e(LOG_TAG, "deleteFileSynchronous function fault " + e.getMessage());
            return true;
        }
        reentrantLock.unlock();
        return false;
    }

    public static void deleteFile(final Context context, final String inputPath, final String inputFile, final String tag) {
        Runnable runnable = () -> {
            try {
                reentrantLock.lock();

                File usedFile = new File(inputPath + "/" + inputFile);
                if (usedFile.exists()) {
                    if (!usedFile.canRead() || !usedFile.canWrite()) {
                        if (!usedFile.setReadable(true) || !usedFile.setWritable(true)) {
                            Log.w(LOG_TAG, "Unable to chmod file " + inputPath + "/" + inputFile);
                            FileOperations fileOperations = new FileOperations();
                            fileOperations.restoreAccess(context, inputPath + "/" + inputFile);
                        } else if (!usedFile.setReadable(true) || !usedFile.setWritable(true)) {
                            Log.e(LOG_TAG, "Unable to chmod file " + inputPath + "/" + inputFile);
                        }
                    }
                    if (!usedFile.delete()) {
                        Log.w(LOG_TAG, "Unable to delete file " + usedFile.toString() + " Try restore access!");

                        FileOperations fileOperations = new FileOperations();
                        fileOperations.restoreAccess(context, inputPath + "/" + inputFile);

                        if (!usedFile.delete()) {
                            throw new IllegalStateException("Unable to delete file " + usedFile.toString());
                        }
                    }
                } else {
                    Log.w(LOG_TAG, "Unable to delete file. No file " + usedFile.toString());
                }

                if (callback != null && !tag.contains("ignored")) {
                    if (callback instanceof OnBinaryFileOperationsCompleteListener) {
                        ((OnBinaryFileOperationsCompleteListener) callback).OnFileOperationComplete(
                                deleteFile, true, inputPath + "/" + inputFile, tag);
                    } else {
                        throw new ClassCastException("Wrong File operations type. Choose binary type.");
                    }
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "deleteFile function fault " + e.getMessage() + " " + e.getCause());
                if (callback != null && !tag.contains("ignored")) {
                    if (callback instanceof OnBinaryFileOperationsCompleteListener) {
                        ((OnBinaryFileOperationsCompleteListener) callback).OnFileOperationComplete(
                                deleteFile, false, inputPath + "/" + inputFile, tag);
                    } else {
                        throw new ClassCastException("Wrong File operations type. Choose binary type.");
                    }
                }
            } finally {
                reentrantLock.unlock();
            }
        };

        if (executorService == null || executorService.isShutdown()) {
            executorService = Executors.newSingleThreadExecutor();
        }
        executorService.execute(runnable);
    }

    public static boolean deleteDirSynchronous(final Context context, final String inputPath) {
        reentrantLock.lock();

        boolean result = false;
        try{

            File usedDir = new File(inputPath);
            if (usedDir.isDirectory()) {
                if (!usedDir.canRead() || !usedDir.canWrite()) {
                    if (!usedDir.setReadable(true) || !usedDir.setWritable(true)) {
                        Log.w(LOG_TAG, "Unable to chmod dir " + inputPath);
                        FileOperations fileOperations = new FileOperations();
                        fileOperations.restoreAccess(context, inputPath);
                    } else if (!usedDir.setReadable(true) || !usedDir.setWritable(true)) {
                        Log.e(LOG_TAG, "Unable to chmod dir " + inputPath);
                    }
                }
            } else {
                throw new IllegalStateException(inputPath + " is not Dir");
            }

            File[] files = usedDir.listFiles();

            if (files == null) {
                throw new IllegalStateException("Impossible to delete dir, listFiles is null " + inputPath);
            }

            if (files.length != 0) {
                for (File file: files) {
                    if (file.isFile()) {
                        deleteFileSynchronous(context, file.getParent(), file.getName());
                    } else if (file.isDirectory()) {
                        deleteDirSynchronous(context, file.getAbsolutePath());
                    }
                }
            }

            if (!usedDir.delete()) {
                Log.w(LOG_TAG, "Unable to delete dir " + inputPath + " Try to restore access!");

                FileOperations fileOperations = new FileOperations();
                fileOperations.restoreAccess(context, inputPath);

                if (!usedDir.delete()) {
                    throw new IllegalStateException("Impossible to delete empty dir " + inputPath);
                }
            }

            result = true;
        } catch (Exception e) {
            Log.e(LOG_TAG, "delete Dir function fault " + e.getMessage() + " " + e.getCause());
        }

        reentrantLock.unlock();

        return result;
    }

    @SuppressLint("SetWorldReadable")
    public static void readTextFile(final Context context, final String filePath, final String tag) {
        Runnable runnable = () -> {

            try {

                reentrantLock.lock();

                linesListMap.remove(filePath);

                File f = new File(filePath);
                if (f.isFile()) {
                    if (f.setReadable(true, false)) {
                        Log.i(LOG_TAG, "readTextFile take " + filePath + " success");
                    } else {
                        Log.w(LOG_TAG, "readTextFile take " + filePath + " warning");
                        FileOperations fileOperations = new FileOperations();
                        fileOperations.restoreAccess(context, filePath);
                        if (f.setReadable(true, false)) {
                            Log.i(LOG_TAG, "readTextFile take " + filePath + " success");
                        } else {
                            throw new IllegalStateException("readTextFile take " + filePath + " error");
                        }
                    }
                } else {
                    throw new IllegalStateException("readTextFile no file " + filePath);
                }

                List<String> linesList = new LinkedList<>();

                try (FileInputStream fstream = new FileInputStream(filePath);
                     BufferedReader br = new BufferedReader(new InputStreamReader(fstream))) {

                    for (String tmp; (tmp = br.readLine()) != null; ) {
                        linesList.add(tmp.trim());
                    }
                }


                linesListMap.put(filePath, linesList);

                if (callback != null) {
                    if (callback instanceof OnTextFileOperationsCompleteListener) {
                        ((OnTextFileOperationsCompleteListener) callback).OnFileOperationComplete(
                                readTextFile, true, filePath, tag, linesListMap.get(filePath));
                    } else {
                        throw new ClassCastException("Wrong File operations type. Choose text type.");
                    }
                }

            } catch (Exception e) {
                Log.e(LOG_TAG, "readTextFile Exception " + e.getMessage() + " " + e.getCause());
                if (callback != null) {
                    if (callback instanceof OnTextFileOperationsCompleteListener) {
                        ((OnTextFileOperationsCompleteListener) callback).OnFileOperationComplete(
                                readTextFile, false, filePath, tag, null);
                    } else {
                        throw new ClassCastException("Wrong File operations type. Choose text type.");
                    }
                }
            } finally {
                reentrantLock.unlock();
            }
        };

        if (executorService == null || executorService.isShutdown()) {
            executorService = Executors.newSingleThreadExecutor();
        }
        executorService.execute(runnable);
    }

    @SuppressLint("SetWorldReadable")
    public static void writeToTextFile(final Context context, final String filePath, final List<String> lines, final String tag) {
        Runnable runnable = () -> {

            try {

                reentrantLock.lock();

                File f = new File(filePath);

                if (f.isFile()) {
                    if (f.setReadable(true, false) && f.setWritable(true)) {
                        Log.i(LOG_TAG, "writeToTextFile writeTo " + filePath + " success");
                    } else {
                        Log.w(LOG_TAG, "writeToTextFile writeTo " + filePath + " warning");
                        FileOperations fileOperations = new FileOperations();
                        fileOperations.restoreAccess(context, filePath);
                        if (f.setReadable(true, false) && f.setWritable(true)) {
                            Log.i(LOG_TAG, "writeToTextFile writeTo " + filePath + " success");
                        } else {
                            throw new IllegalStateException("writeToTextFile writeTo " + filePath + " error");
                        }
                    }
                }

                try (PrintWriter writer = new PrintWriter(filePath)) {

                    for (String line : lines) {
                        writer.println(line);
                    }

                }

                linesListMap.remove(filePath);

                if (callback != null && !tag.contains("ignored")) {
                    if (callback instanceof OnTextFileOperationsCompleteListener) {
                        ((OnTextFileOperationsCompleteListener) callback).OnFileOperationComplete(
                                writeToTextFile, true, filePath, tag, null);
                    } else {
                        throw new ClassCastException("Wrong File operations type. Choose text type.");
                    }
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "writeToTextFile Exception " + e.getMessage() + " " + e.getCause());
                if (callback != null && !tag.contains("ignored")) {
                    if (callback instanceof OnTextFileOperationsCompleteListener) {
                        ((OnTextFileOperationsCompleteListener) callback).OnFileOperationComplete(
                                writeToTextFile, false, filePath, tag, null);
                    } else {
                        throw new ClassCastException("Wrong File operations type. Choose text type.");
                    }
                }
            } finally {
                reentrantLock.unlock();
            }
        };

        if (executorService == null || executorService.isShutdown()) {
            executorService = Executors.newSingleThreadExecutor();
        }
        executorService.execute(runnable);
    }

    @SuppressLint("SetWorldReadable")
    public static List<String> readTextFileSynchronous(final Context context, final String filePath) {

        reentrantLock.lock();

        List<String> lines = null;

        try {

            File f = new File(filePath);
            if (f.isFile()) {
                if (f.setReadable(true, false)) {
                    Log.i(LOG_TAG, "readTextFileSynchronous take " + filePath + " success");
                } else {
                    Log.w(LOG_TAG, "readTextFileSynchronous take " + filePath + " warning");
                    FileOperations fileOperations = new FileOperations();
                    fileOperations.restoreAccess(context, filePath);
                    if (f.setReadable(true, false)) {
                        Log.i(LOG_TAG, "readTextFileSynchronous take " + filePath + " success");
                    } else {
                        throw new IllegalStateException("readTextFileSynchronous take " + filePath + " error");
                    }
                }
            } else {
                throw new IllegalStateException("readTextFileSynchronous no file " + filePath);
            }

            lines = new LinkedList<>();

            try (FileInputStream fstream = new FileInputStream(filePath);
                 BufferedReader br = new BufferedReader(new InputStreamReader(fstream))) {

                for (String tmp; (tmp = br.readLine()) != null; ) {
                    lines.add(tmp.trim());
                }
            }

        } catch (Exception e) {
            Log.e(LOG_TAG, "readTextFileSynchronous Exception " + e.getMessage() + " " + e.getCause());
        }

        reentrantLock.unlock();

        return lines;
    }

    @SuppressLint("SetWorldReadable")
    public static boolean writeTextFileSynchronous(final Context context, final String filePath, final List<String> lines) {
        
        reentrantLock.lock();

        boolean result = true;
        try {

            File f = new File(filePath);

            if (f.isFile()) {
                if (f.setReadable(true, false) && f.setWritable(true)) {
                    Log.i(LOG_TAG, "writeTextFileSynchronous writeTo " + filePath + " success");
                } else {
                    Log.w(LOG_TAG, "writeTextFileSynchronous writeTo " + filePath + " warning");
                    FileOperations fileOperations = new FileOperations();
                    fileOperations.restoreAccess(context, filePath);
                    if (f.setReadable(true, false) && f.setWritable(true)) {
                        Log.i(LOG_TAG, "writeTextFileSynchronous writeTo " + filePath + " success");
                    } else {
                        throw new IllegalStateException("writeTextFileSynchronous writeTo " + filePath + " error");
                    }
                }
            }

            try (PrintWriter writer = new PrintWriter(filePath)) {

                for (String line : lines) {
                    writer.println(line);
                }

            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "writeTextFileSynchronous Exception " + e.getMessage() + " " + e.getCause());
            result = false;
        }

        reentrantLock.unlock();

        return result;
    }

    public void restoreAccess(Context context, String filePath) {
        if (context != null) {
            boolean rootIsAvailable = new PrefManager(context).getBoolPref("rootIsAvailable");

            if (!rootIsAvailable) {
                return;
            }

            IntentFilter intentFilterBckgIntSer = new IntentFilter(RootExecService.COMMAND_RESULT);
            context.registerReceiver(br, intentFilterBckgIntSer);

            String appUID = new PrefManager(context).getStrPref("appUID");
            PathVars pathVars = PathVars.getInstance(context);
            String[] commands = {
                    pathVars.getBusyboxPath()+ "chown -R " + appUID + "." + appUID + " " + filePath,
                    "restorecon " + filePath,
                    pathVars.getBusyboxPath() + "sleep 1"
            };
            RootCommands rootCommands = new RootCommands(commands);
            Intent intent = new Intent(context, RootExecService.class);
            intent.setAction(RootExecService.RUN_COMMAND);
            intent.putExtra("Commands", rootCommands);
            intent.putExtra("Mark", RootExecService.FileOperationsMark);
            RootExecService.performAction(context, intent);

            waitRestoreAccessWithRoot();
        }
    }

    public static void setOnFileOperationCompleteListener(OnFileOperationsCompleteListener callback) {
        if (stackCallbacks == null)
            stackCallbacks = new Stack<>();

        if (FileOperations.callback != null)
            stackCallbacks.push(FileOperations.callback);

        if (callback != null)
            FileOperations.callback = callback;
    }

    public static void deleteOnFileOperationCompleteListener() {
        if (stackCallbacks != null) {
            if (stackCallbacks.empty()) {
                callback = null;
            } else {
                callback = stackCallbacks.pop();
            }
        }
    }

    public static void removeAllOnFileOperationsListeners() {
        if (callback != null)
            callback = null;
        if (stackCallbacks != null && !stackCallbacks.empty())
            FileOperations.stackCallbacks.removeAllElements();

        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            try {
                executorService.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Log.w(LOG_TAG, "FileOperations executorService awaitTermination has interrupted " + e.getMessage());
            }

        }
    }

    private void waitRestoreAccessWithRoot() {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Log.w(LOG_TAG, "FileOperations latch interrupted " + e.getMessage() + " " + e.getCause());
        }
    }

    private void continueFileOperations() {
        latch.countDown();
    }
}
