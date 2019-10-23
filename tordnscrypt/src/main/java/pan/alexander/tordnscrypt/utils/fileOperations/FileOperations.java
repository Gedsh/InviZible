package pan.alexander.tordnscrypt.utils;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.widget.ProgressBar;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.Enums.FileOperationsVariants;

import static pan.alexander.tordnscrypt.utils.Enums.FileOperationsVariants.copyBinaryFile;
import static pan.alexander.tordnscrypt.utils.Enums.FileOperationsVariants.deleteFile;
import static pan.alexander.tordnscrypt.utils.Enums.FileOperationsVariants.moveBinaryFile;
import static pan.alexander.tordnscrypt.utils.Enums.FileOperationsVariants.readTextFile;
import static pan.alexander.tordnscrypt.utils.Enums.FileOperationsVariants.writeToTextFile;
import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;

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

public class FileOperations {
    private boolean stopThread = false;
    private static Map<String,List<String>> linesListMap = new HashMap<>();
    private static OnFileOperationsCompleteListener callback;
    private static Stack<OnFileOperationsCompleteListener> stackCallbacks;
    public static String copyBinaryFileCurrentOperation = "pan.alexander.tordnscrypt.copyBinaryFile";
    public static String deleteFileCurrentOperation = "pan.alexander.tordnscrypt.deleteFile";
    public static String readTextFileCurrentOperation = "pan.alexander.tordnscrypt.readTextFile";
    public static String writeToTextFileCurrentOperation = "pan.alexander.tordnscrypt.writeToTextFile";
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
                    stopThread = false;
                    if (br!=null)
                        context.unregisterReceiver(br);
                    br = null;
                }
            }

        }
    };

    public static void moveBinaryFile(final Context context, final String inputPath, final String inputFile, final String outputPath, final String tag) {

        Runnable runnable = new Runnable() {
            @SuppressLint("SetWorldReadable")
            @Override
            public void run() {


                try(InputStream in = new FileInputStream(inputPath + "/" + inputFile);
                    OutputStream out = new FileOutputStream(outputPath + "/" + inputFile)) {

                    File dir = new File(outputPath);
                    if (!dir.isDirectory()) {
                        if (!dir.mkdirs() || !dir.setReadable(true) || !dir.setWritable(true)) {
                            throw new IllegalStateException("Unable to create dir " + dir.toString());
                        }
                    }

                    File oldFile = new File(outputPath + "/" + inputFile);
                    if (oldFile.exists()) {
                        if (deleteFileInternal(context, outputPath, inputFile)) {
                            throw new IllegalStateException("Unable to delete file " + oldFile.toString());
                        }
                    }

                    File inFile = new File(inputPath + "/" + inputFile);
                    if (!inFile.canRead()) {
                        if (!inFile.setReadable(true)) {
                            Log.w(LOG_TAG, "Unable to chmod file " + oldFile.toString());
                            FileOperations fileOperations = new FileOperations();
                            fileOperations.restoreAccess(context, inFile.getPath());
                        } else if (!inFile.canRead()) {
                            throw new IllegalStateException("Unable to chmod file " + oldFile.toString());
                        }
                    }

                    byte[] buffer = new byte[1024];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
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
                    if (deleteFileInternal(context, inputPath, inputFile)) {
                        throw new IllegalStateException("Unable to delete file " + inputFile);
                    }

                    if (callback!=null && !tag.equals("ignored"))
                        ((OnBinaryFileOperationsCompleteListener)callback).OnFileOperationComplete(moveBinaryFile, true,outputPath + "/" + inputFile, tag);

                } catch (Exception e) {
                    Log.e(LOG_TAG,"replaceBinaryFile function fault " + e.getMessage());
                    if (callback!=null && !tag.equals("ignored"))
                        ((OnBinaryFileOperationsCompleteListener)callback).OnFileOperationComplete(moveBinaryFile, false,outputPath + "/" + inputFile, tag);
                }


            }
        };

        executorService.execute(runnable);
    }

    public static void copyBinaryFile(final Context context, final String inputPath, final String inputFile, final String outputPath, final String tag) {

        Runnable runnable = new Runnable() {
            @Override
            public void run() {


                try(InputStream in = new FileInputStream(inputPath + "/" + inputFile);
                    OutputStream out = new FileOutputStream(outputPath + "/" + inputFile)) {

                    File dir = new File(outputPath);
                    if (!dir.isDirectory()) {
                        if (!dir.mkdirs() || !dir.setReadable(true) || !dir.setWritable(true)) {
                            throw new IllegalStateException("Unable to create dir " + dir.toString());
                        }
                    }

                    File oldFile = new File(outputPath + "/" + inputFile);
                    if (oldFile.exists()) {
                        if (deleteFileInternal(context, outputPath, inputFile)) {
                            throw new IllegalStateException("Unable to delete file " + oldFile.toString());
                        }
                    }

                    File inFile = new File(inputPath + "/" + inputFile);
                    if (!inFile.canRead()) {
                        if (!inFile.setReadable(true)) {
                            throw new IllegalStateException("Unable to chmod file " + oldFile.toString());
                        }
                    }

                    byte[] buffer = new byte[1024];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }

                    File newFile = new File(outputPath + "/" + inputFile);
                    if (!newFile.exists()) {
                        throw new IllegalStateException("New file not exist " + oldFile.toString());
                    }

                    if (callback!=null && !tag.equals("ignored"))
                        ((OnBinaryFileOperationsCompleteListener)callback).OnFileOperationComplete(copyBinaryFile, true, outputPath + "/" + inputFile, tag);

                } catch (Exception e) {
                    if (callback!=null && !tag.equals("ignored"))
                        ((OnBinaryFileOperationsCompleteListener)callback).OnFileOperationComplete(copyBinaryFile, false, outputPath + "/" + inputFile, tag);
                    Log.e(LOG_TAG,"copyBinaryFile function fault " + e.getMessage());
                }

            }
        };

        executorService.execute(runnable);
    }

    private static boolean deleteFileInternal(final Context context, final String inputPath, final String inputFile) {
        try {
            File usedFile = new File(inputPath + "/" + inputFile);
            if (usedFile.exists()) {
                if (!usedFile.canRead() || !usedFile.canWrite()) {
                    if (!usedFile.setReadable(true) || !usedFile.setWritable(true)) {
                        Log.w(LOG_TAG, "Unable to chmod file " + inputPath + "/" + inputFile);
                        FileOperations fileOperations = new FileOperations();
                        fileOperations.restoreAccess(context, inputPath + "/" + inputFile);
                    } else if (!usedFile.setReadable(true) || !usedFile.setWritable(true)) {
                        Log.e(LOG_TAG, "Unable to chmod file " + inputPath + "/" + inputFile);
                        return true;
                    }
                }
                if (!usedFile.delete()) {
                    Log.e(LOG_TAG, "Unable to delete file " + usedFile.toString());
                    return true;
                }
            } else {
                Log.e(LOG_TAG, "Unable to delete file. No file " + usedFile.toString());
                return true;
            }
        } catch (Exception e) {
            Log.e(LOG_TAG,"deleteFileInternal function fault " + e.getMessage());
            return true;
        }
        return false;
    }

    public static void deleteFile(final Context context, final String inputPath, final String inputFile, final String tag) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                try {
                    File usedFile = new File(inputPath + "/" + inputFile);
                    if (usedFile.exists()) {
                        if (!usedFile.canRead() || !usedFile.canWrite()) {
                            if (!usedFile.setReadable(true) || !usedFile.setWritable(true)) {
                                Log.w(LOG_TAG, "Unable to chmod file " + inputPath + "/" + inputFile);
                                FileOperations fileOperations = new FileOperations();
                                fileOperations.restoreAccess(context, inputPath + "/" + inputFile);
                            } else if (!usedFile.setReadable(true) || !usedFile.setWritable(true)) {
                                throw new IllegalStateException("Unable to chmod file " + inputPath + "/" + inputFile);
                            }
                        }
                        if (!usedFile.delete()) {
                            throw new IllegalStateException("Unable to delete file " + usedFile.toString());
                        }
                    } else {
                        throw new IllegalStateException("Unable to delete file. No file " + usedFile.toString());
                    }

                    if (callback!=null && !tag.equals("ignored"))
                        ((OnBinaryFileOperationsCompleteListener)callback).OnFileOperationComplete(deleteFile, true, inputPath + "/" + inputFile, tag);

                } catch (Exception e) {
                    Log.e(LOG_TAG,"deleteFile function fault " + e.getMessage());
                    if (callback!=null && !tag.equals("ignored"))
                        ((OnBinaryFileOperationsCompleteListener)callback).OnFileOperationComplete(deleteFile, false,inputPath + "/" + inputFile, tag);
                }
            }
        };
        executorService.execute(runnable);
    }

    @SuppressLint("SetWorldReadable")
    public static void readTextFile(final Context context, final String filePath, final String tag) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {

                linesListMap.remove(filePath);

                try(FileInputStream fstream = new FileInputStream(filePath);
                    BufferedReader br = new BufferedReader(new InputStreamReader(fstream))) {

                    File f = new File(filePath);
                    if (f.isFile()) {
                        if (f.setReadable(true,false)) {
                            Log.i(LOG_TAG, "readTextFile take " + filePath + " success");
                        } else {
                            Log.w(LOG_TAG, "readTextFile take " + filePath + " warning");
                            FileOperations fileOperations = new FileOperations();
                            fileOperations.restoreAccess(context,filePath);
                            if (f.setReadable(true,false)) {
                                Log.i(LOG_TAG, "readTextFile take " + filePath + " success");
                            } else {
                                throw new IllegalStateException("readTextFile take " + filePath + " error");
                            }
                        }
                    } else {
                        throw new IllegalStateException("readTextFile no file " + filePath);
                    }

                    List<String> linesList = new LinkedList<>();

                    for(String tmp; (tmp = br.readLine()) != null;) {
                        linesList.add(tmp.trim());
                    }

                    linesListMap.put(filePath,linesList);

                    if (callback != null)
                        ((OnTextFileOperationsCompleteListener)callback).OnFileOperationComplete(readTextFile, true, filePath, tag, linesListMap.get(filePath));

                } catch (IOException e) {
                    Log.e(LOG_TAG,"readTextFile Exception " + e.getMessage() + e.getCause());
                    if (callback != null)
                        ((OnTextFileOperationsCompleteListener)callback).OnFileOperationComplete(readTextFile, false, filePath, tag, null);
                }
            }
        };

        executorService.execute(runnable);
    }

    @SuppressLint("SetWorldReadable")
    public static void writeToTextFile(final Context context, final String filePath, final List<String> lines, final String tag) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {

                try(PrintWriter writer = new PrintWriter(filePath)) {

                    File f = new File(filePath);

                    if (f.isFile()) {
                        if (f.setReadable(true,false) && f.setWritable(true)) {
                            Log.i(LOG_TAG,"writeToTextFile writeTo " + filePath + " success");
                        } else {
                            Log.w(LOG_TAG, "writeToTextFile writeTo " + filePath + " warning");
                            FileOperations fileOperations = new FileOperations();
                            fileOperations.restoreAccess(context,filePath);
                            if (f.setReadable(true,false) && f.setWritable(true)) {
                                Log.i(LOG_TAG,"writeToTextFile writeTo " + filePath + " success");
                            } else {
                                throw new IllegalStateException("writeToTextFile writeTo " + filePath + " error");
                            }
                        }
                    }

                    for (String line:lines) {
                        writer.println(line);
                    }

                    linesListMap.remove(filePath);

                    if (callback != null && !tag.equals("ignored"))
                        ((OnBinaryFileOperationsCompleteListener)callback).OnFileOperationComplete(writeToTextFile, true, filePath, tag);

                } catch (IOException e) {
                    Log.e(LOG_TAG,"writeToTextFile Exception " + e.getMessage() + e.getCause());
                    if (callback != null && !tag.equals("ignored"))
                        ((OnBinaryFileOperationsCompleteListener)callback).OnFileOperationComplete(writeToTextFile, false, filePath, tag);
                }
            }
        };
        executorService.execute(runnable);
    }

    private void restoreAccess(Context context, String filePath) {
        if(context!=null){
            IntentFilter intentFilterBckgIntSer = new IntentFilter(RootExecService.COMMAND_RESULT);
            context.registerReceiver(br,intentFilterBckgIntSer);

            String appUID = new PrefManager(context).getStrPref("appUID");
            PathVars pathVars = new PathVars(context);
            String[] commands = {pathVars.busyboxPath + "chown -R " + appUID + "." + appUID + " " + filePath,
                    "restorecon " + filePath};
            RootCommands rootCommands = new RootCommands(commands);
            Intent intent = new Intent(context, RootExecService.class);
            intent.setAction(RootExecService.RUN_COMMAND);
            intent.putExtra("Commands",rootCommands);
            intent.putExtra("Mark", RootExecService.FileOperationsMark);
            RootExecService.performAction(context,intent);

            try {
                stopThread = true;
                int count = 0;
                while (stopThread) {
                    Thread.sleep(100);
                    count++;
                    if (count>100) {
                        Log.e(LOG_TAG,"FileOperations root delay finished");
                        break;
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public static DialogInterface fileOperationProgressDialog(Context context) {
        final android.support.v7.app.AlertDialog.Builder builder = new android.support.v7.app.AlertDialog.Builder(context,R.style.CustomDialogTheme);
        builder.setTitle(R.string.please_wait);
        builder.setCancelable(false);
        builder.setPositiveButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
            }
        });

        ProgressBar progressBar = new ProgressBar(context,null,android.R.attr.progressBarStyleHorizontal);
        progressBar.setBackgroundResource(R.drawable.background_10dp_padding);
        progressBar.setIndeterminate(true);
        builder.setView(progressBar);
        builder.setCancelable(false);
        AlertDialog view  = builder.show();
        Objects.requireNonNull(view.getWindow()).getDecorView().setBackgroundColor(Color.TRANSPARENT);
        return view;
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

        executorService.shutdown();
    }

    public interface OnFileOperationsCompleteListener {
    }

    public interface OnBinaryFileOperationsCompleteListener extends OnFileOperationsCompleteListener {
        void OnFileOperationComplete(FileOperationsVariants currentFileOperation, boolean fileOperationResult, String path, String tag);
    }

    public interface OnTextFileOperationsCompleteListener extends OnFileOperationsCompleteListener {
        void OnFileOperationComplete(FileOperationsVariants currentFileOperation, boolean fileOperationResult, String path, String tag, List<String> lines);
    }
}
