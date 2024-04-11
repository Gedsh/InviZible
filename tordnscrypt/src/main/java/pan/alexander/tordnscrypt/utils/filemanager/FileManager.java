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

    Copyright 2019-2024 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.utils.filemanager;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import dagger.Lazy;
import pan.alexander.tordnscrypt.App;
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.root.RootCommands;
import pan.alexander.tordnscrypt.utils.root.RootExecService;

import static pan.alexander.tordnscrypt.utils.enums.FileOperationsVariants.copyBinaryFile;
import static pan.alexander.tordnscrypt.utils.enums.FileOperationsVariants.deleteFile;
import static pan.alexander.tordnscrypt.utils.enums.FileOperationsVariants.moveBinaryFile;
import static pan.alexander.tordnscrypt.utils.enums.FileOperationsVariants.readTextFile;
import static pan.alexander.tordnscrypt.utils.enums.FileOperationsVariants.writeToTextFile;
import static pan.alexander.tordnscrypt.utils.logger.Logger.loge;
import static pan.alexander.tordnscrypt.utils.logger.Logger.logi;
import static pan.alexander.tordnscrypt.utils.logger.Logger.logw;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.ROOT_IS_AVAILABLE;
import static pan.alexander.tordnscrypt.utils.root.RootCommandsMark.FILE_OPERATIONS_MARK;

import javax.inject.Inject;

public class FileManager {

    @Inject
    public Lazy<PreferenceRepository> preferenceRepository;

    private CountDownLatch latch;
    private static final Map<String, List<String>> linesListMap = new HashMap<>();
    private static final ReentrantLock reentrantLock = new ReentrantLock();
    private static OnFileOperationsCompleteListener callback;
    private static CopyOnWriteArrayList<OnFileOperationsCompleteListener> stackCallbacks;
    private static ExecutorService executorService = Executors.newSingleThreadExecutor();

    public FileManager() {
        App.getInstance().getDaggerComponent().inject(this);
    }

    private BroadcastReceiver br = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null) {
                final String action = intent.getAction();
                if (action == null
                        || action.equals("")
                        || ((intent.getIntExtra("Mark", 0) != FILE_OPERATIONS_MARK))) return;

                logi("FileOperations onReceive");

                if (action.equals(RootExecService.COMMAND_RESULT)) {
                    continueFileOperations();
                    if (br != null)
                        LocalBroadcastManager.getInstance(context).unregisterReceiver(br);
                    br = null;
                }
            }

        }
    };

    public static void moveBinaryFile(final Context context, final String inputPath, final String inputFile, final String outputPath, final String tag) {

        @SuppressLint("SetWorldReadable") Runnable runnable = () -> {

            reentrantLock.lock();

            try {
                File dir = new File(outputPath);
                if (!dir.isDirectory()) {
                    if (!dir.mkdirs()) {
                        throw new IllegalStateException("Unable to create dir " + dir);
                    }

                    if (!dir.canRead() || !dir.canWrite()) {
                        if (!dir.setReadable(true) || !dir.setWritable(true)) {
                            logw("Unable to chmod dir " + dir);
                        }
                    }
                }

                File oldFile = new File(outputPath + "/" + inputFile);
                if (oldFile.exists()) {
                    if (deleteFileSynchronous(context, outputPath, inputFile)) {
                        throw new IllegalStateException("Unable to delete file " + oldFile);
                    }
                }

                File inFile = null;

                try {
                    inFile = new File(inputPath + "/" + inputFile);
                } catch (Exception e) {
                    logw("File is no accessible " + e.getMessage() + " " + e.getCause() + " .Try to restore access.");
                    FileManager fileManager = new FileManager();
                    fileManager.restoreAccess(context, inputPath + "/" + inputFile);
                }

                if (inFile == null) {
                    throw new IllegalStateException("File is no accessible " + inputPath + "/" + inputFile);
                }

                if (!inFile.canRead()) {
                    if (!inFile.setReadable(true)) {
                        logw("Unable to chmod file " + inFile);
                        FileManager fileManager = new FileManager();
                        fileManager.restoreAccess(context, inFile.getPath());
                    } else if (!inFile.canRead()) {
                        throw new IllegalStateException("Unable to chmod file " + inFile);
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
                    throw new IllegalStateException("New file not exist " + oldFile);
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
                loge("moveBinaryFile function fault", e);
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

            reentrantLock.lock();

            try {
                File dir = new File(outputPath);
                if (!dir.isDirectory()) {
                    if (!dir.mkdirs()) {
                        throw new IllegalStateException("Unable to create dir " + dir);
                    }

                    if (!dir.canRead() || !dir.canWrite()) {
                        if (!dir.setReadable(true) || !dir.setWritable(true)) {
                            logw("Unable to chmod dir " + dir);
                        }
                    }
                }

                File oldFile = new File(outputPath + "/" + inputFile);
                if (oldFile.exists()) {
                    if (deleteFileSynchronous(context, outputPath, inputFile)) {
                        throw new IllegalStateException("Unable to delete file " + oldFile);
                    }
                }

                File inFile = null;

                try {
                    inFile = new File(inputPath + "/" + inputFile);
                } catch (Exception e) {
                    logw("File is no accessible " + e.getMessage() + " " + e.getCause() + " .Try to restore access.");
                    FileManager fileManager = new FileManager();
                    fileManager.restoreAccess(context, inputPath + "/" + inputFile);
                }

                if (inFile == null) {
                    throw new IllegalStateException("File is no accessible " + inputPath + "/" + inputFile);
                }

                if (!inFile.canRead()) {
                    if (!inFile.setReadable(true)) {
                        logw("Unable to chmod file " + inFile);
                        FileManager fileManager = new FileManager();
                        fileManager.restoreAccess(context, inFile.getPath());
                    } else if (!inFile.canRead()) {
                        throw new IllegalStateException("Unable to chmod file " + inFile);
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
                    throw new IllegalStateException("New file not exist " + oldFile);
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
                loge("copyBinaryFile function fault", e);
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

        reentrantLock.lock();

        try {
            File dir = new File(outputPath);
            if (!dir.isDirectory()) {
                if (!dir.mkdirs()) {
                    throw new IllegalStateException("Unable to create dir " + dir);
                }

                if (!dir.canRead() || !dir.canWrite()) {
                    if (!dir.setReadable(true) || !dir.setWritable(true)) {
                        logw("Unable to chmod dir " + dir);
                    }
                }
            }

            File oldFile = new File(outputPath + "/" + inputFile);
            if (oldFile.exists()) {
                if (deleteFileSynchronous(context, outputPath, inputFile)) {
                    throw new IllegalStateException("Unable to delete file " + oldFile);
                }
            }

            File inFile = null;

            try {
                inFile = new File(inputPath + "/" + inputFile);
            } catch (Exception e) {
                logw("File is no accessible " + e.getMessage() + " " + e.getCause() + " .Try to restore access.");
                FileManager fileManager = new FileManager();
                fileManager.restoreAccess(context, inputPath + "/" + inputFile);
            }

            if (inFile == null) {
                throw new IllegalStateException("File is no accessible " + inputPath + "/" + inputFile);
            }

            if (!inFile.canRead()) {
                if (!inFile.setReadable(true)) {
                    logw("Unable to chmod file " + inFile);
                    FileManager fileManager = new FileManager();
                    fileManager.restoreAccess(context, inFile.getPath());
                } else if (!inFile.canRead()) {
                    throw new IllegalStateException("Unable to chmod file " + inFile);
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
                throw new IllegalStateException("New file not exist " + oldFile);
            }

        } catch (Exception e) {
            loge("copyBinaryFileSynchronous function fault", e);
        } finally {
            reentrantLock.unlock();
        }

    }

    public static void copyFolderSynchronous(final Context context, final String inputPath, final String outputPath) {

        reentrantLock.lock();

        try {
            File inDir = null;

            try {
                inDir = new File(inputPath);
            } catch (Exception e) {
                logw("Dir is no accessible " + e.getMessage() + " " + e.getCause() + " .Try to restore access.");
                FileManager fileManager = new FileManager();
                fileManager.restoreAccess(context, inputPath);
            }

            if (inDir == null) {
                throw new IllegalStateException("File is no accessible " + inputPath);
            }

            if (!inDir.canRead()) {
                if (!inDir.setReadable(true)) {
                    logw("Unable to chmod dir " + inDir);
                    FileManager fileManager = new FileManager();
                    fileManager.restoreAccess(context, inDir.getPath());
                } else if (!inDir.canRead()) {
                    throw new IllegalStateException("Unable to chmod dir " + inDir);
                }
            }

            File outDir = new File(outputPath+ "/" + inDir.getName());
            if (!outDir.isDirectory()) {
                if (!outDir.mkdirs()) {
                    throw new IllegalStateException("Unable to create dir " + outDir);
                }
            }

            if (!outDir.setReadable(true) || !outDir.setWritable(true) || !outDir.setExecutable(true)) {
                logw("Unable to chmod dir " + outDir);
            }

            for (File file: Objects.requireNonNull(inDir.listFiles())) {

                if (file.isFile()) {
                    copyBinaryFileSynchronous(context, inputPath, file.getName(), outDir.getCanonicalPath());
                } else if (file.isDirectory()){
                    copyFolderSynchronous(context, file.getCanonicalPath(), outDir.getCanonicalPath());
                } else {
                    throw new IllegalStateException("copyFolderSynchronous cannot copy "
                            + inDir + " because this is no file and no dir");
                }

            }

        } catch (Exception e) {
            loge("copyFolderSynchronous function fault", e);
        } finally {
            reentrantLock.unlock();
        }
    }

    public static boolean deleteFileSynchronous(final Context context, final String inputPath, final String inputFile) {

        reentrantLock.lock();

        try {
            File usedFile = null;

            try {
                usedFile = new File(inputPath + "/" + inputFile);
            } catch (Exception e) {
                logw("File is no accessible " + e.getMessage() + " " + e.getCause() + " .Try to restore access.");
                FileManager fileManager = new FileManager();
                fileManager.restoreAccess(context, inputPath + "/" + inputFile);
            }

            if (usedFile == null) {
                throw new IllegalStateException("File is no accessible " + inputPath + "/" + inputFile);
            }

            if (usedFile.exists()) {
                if (!usedFile.canRead() || !usedFile.canWrite()) {
                    if (!usedFile.setReadable(true) || !usedFile.setWritable(true)) {
                        logw("Unable to chmod file " + inputPath + "/" + inputFile);
                        FileManager fileManager = new FileManager();
                        fileManager.restoreAccess(context, inputPath + "/" + inputFile);
                    } else if (!usedFile.setReadable(true) || !usedFile.setWritable(true)) {
                        loge("Unable to chmod file " + inputPath + "/" + inputFile);
                        return true;
                    }
                }
                if (!usedFile.delete()) {
                    logw("Unable to delete file " + usedFile + " Try restore access!");

                    FileManager fileManager = new FileManager();
                    fileManager.restoreAccess(context, inputPath + "/" + inputFile);

                    if (!usedFile.delete()) {
                        loge("Unable to delete file " + usedFile);
                    }

                    return true;
                }
            } else {
                logw("Unable to delete file internal function. No file " + usedFile);
                return false;
            }
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("Permission denied")) {
                FileManager fileManager = new FileManager();
                fileManager.restoreAccess(context, inputPath + "/" + inputFile);
            }

            loge("deleteFileSynchronous function fault", e);
            return true;
        } finally {
            reentrantLock.unlock();
        }

        return false;
    }

    public static void deleteFile(final Context context, final String inputPath, final String inputFile, final String tag) {
        Runnable runnable = () -> {
            reentrantLock.lock();

            try {
                File usedFile = null;

                try {
                    usedFile = new File(inputPath + "/" + inputFile);
                } catch (Exception e) {
                    logw("File is no accessible " + e.getMessage() + " " + e.getCause() + " .Try to restore access.");
                    FileManager fileManager = new FileManager();
                    fileManager.restoreAccess(context, inputPath + "/" + inputFile);
                }

                if (usedFile == null) {
                    throw new IllegalStateException("File is no accessible " + inputPath + "/" + inputFile);
                }

                if (usedFile.exists()) {
                    if (!usedFile.canRead() || !usedFile.canWrite()) {
                        if (!usedFile.setReadable(true) || !usedFile.setWritable(true)) {
                            logw("Unable to chmod file " + inputPath + "/" + inputFile);
                            FileManager fileManager = new FileManager();
                            fileManager.restoreAccess(context, inputPath + "/" + inputFile);
                        } else if (!usedFile.setReadable(true) || !usedFile.setWritable(true)) {
                            loge("Unable to chmod file " + inputPath + "/" + inputFile);
                        }
                    }
                    if (!usedFile.delete()) {
                        logw("Unable to delete file " + usedFile + " Try restore access!");

                        FileManager fileManager = new FileManager();
                        fileManager.restoreAccess(context, inputPath + "/" + inputFile);

                        if (!usedFile.delete()) {
                            throw new IllegalStateException("Unable to delete file " + usedFile);
                        }
                    }
                } else {
                    logw("Unable to delete file. No file " + usedFile);
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
                loge("deleteFile function fault", e);
                if (callback != null && !tag.contains("ignored")) {
                    if (callback instanceof OnBinaryFileOperationsCompleteListener) {
                        ((OnBinaryFileOperationsCompleteListener) callback).OnFileOperationComplete(
                                deleteFile, false, inputPath + "/" + inputFile, tag);
                    } else {
                        throw new ClassCastException("Wrong File operations type. Choose binary type.");
                    }
                }

                if (e.getMessage() != null && e.getMessage().contains("Permission denied")) {
                    FileManager fileManager = new FileManager();
                    fileManager.restoreAccess(context, inputPath + "/" + inputFile);
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
        File usedDir = null;

        try{

            try {
                usedDir = new File(inputPath);
            } catch (Exception e) {
                logw("Dir is no accessible " + e.getMessage() + " " + e.getCause() + " .Try to restore access.");
                FileManager fileManager = new FileManager();
                fileManager.restoreAccess(context, inputPath);
            }

            if (usedDir == null) {
                throw new IllegalStateException("Dir is no accessible " + inputPath);
            }

            if (usedDir.isDirectory()) {
                if (!usedDir.canRead() || !usedDir.canWrite()) {
                    if (!usedDir.setReadable(true) || !usedDir.setWritable(true)) {
                        logw("Unable to chmod dir " + inputPath);
                        FileManager fileManager = new FileManager();
                        fileManager.restoreAccess(context, inputPath);
                    } else if (!usedDir.setReadable(true) || !usedDir.setWritable(true)) {
                        loge("Unable to chmod dir " + inputPath);
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
                logw("Unable to delete dir " + inputPath + " Try to restore access!");

                FileManager fileManager = new FileManager();
                fileManager.restoreAccess(context, inputPath);

                if (!usedDir.delete()) {
                    throw new IllegalStateException("Impossible to delete empty dir " + inputPath);
                }
            }

            result = true;
        } catch (Exception e) {
            loge("delete Dir function fault", e);

            if (e.getMessage() != null && e.getMessage().contains("Permission denied")) {
                FileManager fileManager = new FileManager();
                fileManager.restoreAccess(context, inputPath);
            }
        } finally {
            reentrantLock.unlock();
        }

        return result;
    }

    @SuppressLint("SetWorldReadable")
    public static void readTextFile(final Context context, final String filePath, final String tag) {
        Runnable runnable = () -> {

            reentrantLock.lock();

            try {

                linesListMap.remove(filePath);

                File f = null;

                try {
                    f = new File(filePath);
                } catch (Exception e) {
                    logw("File is no accessible " + e.getMessage() + " " + e.getCause() + " .Try to restore access.");
                    FileManager fileManager = new FileManager();
                    fileManager.restoreAccess(context, filePath);
                }

                if (f == null) {
                    throw new IllegalStateException("File is no accessible " + filePath);
                }

                if (f.isFile()) {
                    if (f.canRead() || f.setReadable(true, false)) {
                        logi("readTextFile take " + filePath + " success");
                    } else {
                        logw("readTextFile take " + filePath + " warning");
                        FileManager fileManager = new FileManager();
                        fileManager.restoreAccess(context, filePath);
                        if (f.setReadable(true, false)) {
                            logi("readTextFile take " + filePath + " success");
                        } else {
                            throw new IllegalStateException("readTextFile take " + filePath + " error");
                        }
                    }
                } else {
                    throw new IllegalStateException("readTextFile no file " + filePath);
                }

                List<String> linesList = new ArrayList<>();

                try (FileInputStream fstream = new FileInputStream(filePath);
                     BufferedReader br = new BufferedReader(new InputStreamReader(fstream))) {

                    for (String tmp; (tmp = br.readLine()) != null; ) {
                        linesList.add(tmp.trim());
                    }
                } catch (Exception ex) {
                    if (ex.getMessage() != null && ex.getMessage().contains("Permission denied")) {
                        FileManager fileManager = new FileManager();
                        fileManager.restoreAccess(context, filePath);

                        try (FileInputStream fstream = new FileInputStream(filePath);
                             BufferedReader br = new BufferedReader(new InputStreamReader(fstream))) {

                            for (String tmp; (tmp = br.readLine()) != null; ) {
                                linesList.add(tmp.trim());
                            }
                        }

                    } else {
                        throw new IllegalStateException("readTextFile input stream exception " + ex.getMessage() + " " + ex.getCause());
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
                loge("readTextFile Exception", e);
                if (callback != null) {
                    if (callback instanceof OnTextFileOperationsCompleteListener) {
                        ((OnTextFileOperationsCompleteListener) callback).OnFileOperationComplete(
                                readTextFile, false, filePath, tag, null);
                    } else {
                        throw new ClassCastException("Wrong File operations type. Choose text type.");
                    }
                }

                if (e.getMessage() != null && e.getMessage().contains("Permission denied")) {
                    FileManager fileManager = new FileManager();
                    fileManager.restoreAccess(context, filePath);
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

            reentrantLock.lock();

            try {

                File f = null;

                try {
                    f = new File(filePath);
                } catch (Exception e) {
                    logw("File is no accessible " + e.getMessage() + " " + e.getCause() + " .Try to restore access.");
                    FileManager fileManager = new FileManager();
                    fileManager.restoreAccess(context, filePath);
                }

                if (f == null) {
                    throw new IllegalStateException("File is no accessible " + filePath);
                }

                if (f.isFile()) {
                    if (f.canRead() && f.canWrite() || f.setReadable(true, false) && f.setWritable(true)) {
                        logi("writeToTextFile writeTo " + filePath + " success");
                    } else {
                        logw("writeToTextFile writeTo " + filePath + " warning");
                        FileManager fileManager = new FileManager();
                        fileManager.restoreAccess(context, filePath);
                        if (f.setReadable(true, false) && f.setWritable(true)) {
                            logi("writeToTextFile writeTo " + filePath + " success");
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
                loge("writeToTextFile", e);
                if (callback != null && !tag.contains("ignored")) {
                    if (callback instanceof OnTextFileOperationsCompleteListener) {
                        ((OnTextFileOperationsCompleteListener) callback).OnFileOperationComplete(
                                writeToTextFile, false, filePath, tag, null);
                    } else {
                        throw new ClassCastException("Wrong File operations type. Choose text type.");
                    }
                }

                if (e.getMessage() != null && e.getMessage().contains("Permission denied")) {
                    FileManager fileManager = new FileManager();
                    fileManager.restoreAccess(context, filePath);
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
    @NotNull
    public static List<String> readTextFileSynchronous(final Context context, final String filePath) {

        reentrantLock.lock();

        List<String> lines = new ArrayList<>();

        try {

            File f = null;

            try {
                f = new File(filePath);
            } catch (Exception e) {
                logw("File is no accessible " + e.getMessage() + " " + e.getCause() + " .Try to restore access.");
                FileManager fileManager = new FileManager();
                fileManager.restoreAccess(context, filePath);
            }

            if (f == null) {
                throw new IllegalStateException("File is no accessible " + filePath);
            }

            if (f.isFile()) {
                if (f.canRead() || f.setReadable(true, false)) {
                    logi("readTextFileSynchronous take " + filePath + " success");
                } else {
                    logw("readTextFileSynchronous take " + filePath + " warning");
                    FileManager fileManager = new FileManager();
                    fileManager.restoreAccess(context, filePath);
                    if (f.setReadable(true, false)) {
                        logi("readTextFileSynchronous take " + filePath + " success");
                    } else {
                        throw new IllegalStateException("readTextFileSynchronous take " + filePath + " error");
                    }
                }
            } else {
                throw new IllegalStateException("readTextFileSynchronous no file " + filePath);
            }

            try (FileInputStream fstream = new FileInputStream(filePath);
                 BufferedReader br = new BufferedReader(new InputStreamReader(fstream))) {

                for (String tmp; (tmp = br.readLine()) != null && !Thread.currentThread().isInterrupted(); ) {
                    lines.add(tmp.trim());
                }
            } catch (Exception ex) {
                if (ex.getMessage() != null && ex.getMessage().contains("Permission denied")) {
                    FileManager fileManager = new FileManager();
                    fileManager.restoreAccess(context, filePath);

                    try (FileInputStream fstream = new FileInputStream(filePath);
                         BufferedReader br = new BufferedReader(new InputStreamReader(fstream))) {

                        for (String tmp; (tmp = br.readLine()) != null && !Thread.currentThread().isInterrupted(); ) {
                            lines.add(tmp.trim());
                        }
                    }

                } else {
                    throw new IllegalStateException("readTextFile synchronous input stream exception " + ex.getMessage() + " " + ex.getCause());
                }
            }

        } catch (Exception e) {
            loge("readTextFileSynchronous", e);

            if (e.getMessage() != null && e.getMessage().contains("Permission denied")) {
                FileManager fileManager = new FileManager();
                fileManager.restoreAccess(context, filePath);
            }
        } finally {
            reentrantLock.unlock();
        }

        return lines;
    }

    @SuppressLint("SetWorldReadable")
    public static boolean writeTextFileSynchronous(final Context context, final String filePath, final List<String> lines) {
        
        reentrantLock.lock();

        boolean result = true;
        try {

            File f = null;

            try {
                f = new File(filePath);
            } catch (Exception e) {
                logw("File is no accessible " + e.getMessage() + " " + e.getCause() + " .Try to restore access.");
                FileManager fileManager = new FileManager();
                fileManager.restoreAccess(context, filePath);
            }

            if (f == null) {
                throw new IllegalStateException("File is no accessible " + filePath);
            }

            if (f.isFile()) {
                if (f.canRead() && f.canWrite() || f.setReadable(true, false) && f.setWritable(true)) {
                    logi("writeTextFileSynchronous writeTo " + filePath + " success");
                } else {
                    logw("writeTextFileSynchronous writeTo " + filePath + " warning");
                    FileManager fileManager = new FileManager();
                    fileManager.restoreAccess(context, filePath);
                    if (f.setReadable(true, false) && f.setWritable(true)) {
                        logi("writeTextFileSynchronous writeTo " + filePath + " success");
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
            loge("writeTextFileSynchronous", e);
            result = false;

            if (e.getMessage() != null && e.getMessage().contains("Permission denied")) {
                FileManager fileManager = new FileManager();
                fileManager.restoreAccess(context, filePath);
            }
        } finally {
            reentrantLock.unlock();
        }

        return result;
    }

    public void restoreAccess(Context context, String filePath) {
        if (context != null) {
            boolean rootIsAvailable = preferenceRepository.get().getBoolPreference(ROOT_IS_AVAILABLE);

            if (!rootIsAvailable) {
                return;
            }

            IntentFilter intentFilterBckgIntSer = new IntentFilter(RootExecService.COMMAND_RESULT);
            LocalBroadcastManager.getInstance(context).registerReceiver(br, intentFilterBckgIntSer);

            PathVars pathVars = App.getInstance().getDaggerComponent().getPathVars().get();
            String appUID = pathVars.getAppUidStr();
            List<String> commands = new ArrayList<>(Arrays.asList(
                    pathVars.getBusyboxPath()+ "chown -R " + appUID + "." + appUID + " " + filePath + " 2> /dev/null",
                    "restorecon " + filePath + " 2> /dev/null",
                    pathVars.getBusyboxPath() + "sleep 1 2> /dev/null"
            ));

            RootCommands.execute(context, commands, FILE_OPERATIONS_MARK);

            waitRestoreAccessWithRoot();
        }
    }

    public static void setOnFileOperationCompleteListener(OnFileOperationsCompleteListener callback) {
        if (stackCallbacks == null)
            stackCallbacks = new CopyOnWriteArrayList<>();

        if (FileManager.callback != null)
            stackCallbacks.add(FileManager.callback);

        if (callback != null)
            FileManager.callback = callback;
    }

    public static void deleteOnFileOperationCompleteListener(OnFileOperationsCompleteListener callback) {
        if (stackCallbacks != null) {
            int lastIndexOfCallback = stackCallbacks.lastIndexOf(callback);

            if (stackCallbacks.isEmpty()) {
                FileManager.callback = null;
            } else if (callback == FileManager.callback) {
                FileManager.callback = stackCallbacks.remove(stackCallbacks.size() - 1);
            } else if (lastIndexOfCallback >= 0) {
                stackCallbacks.remove(lastIndexOfCallback);
            }
        }
    }

    public static void removeAllOnFileOperationsListeners() {
        if (callback != null)
            callback = null;
        if (stackCallbacks != null && !stackCallbacks.isEmpty())
            FileManager.stackCallbacks.clear();

        new Thread(() -> {
            if (executorService != null && !executorService.isShutdown()) {
                executorService.shutdown();
                try {
                    executorService.awaitTermination(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    executorService.shutdownNow();
                    logw("FileOperations executorService awaitTermination has interrupted", e);
                }

            }
        }).start();
    }

    private void waitRestoreAccessWithRoot() {
        latch = new CountDownLatch(1);
        try {
            latch.await(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            logw("FileOperations latch interrupted", e);
        }
    }

    private void continueFileOperations() {
        latch.countDown();
    }
}
