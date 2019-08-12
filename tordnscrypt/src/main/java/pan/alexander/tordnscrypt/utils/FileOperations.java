package pan.alexander.tordnscrypt.utils;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;
import android.widget.ProgressBar;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
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
import java.util.Stack;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.settings.PathVars;

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
    public static boolean fileOperationResult = false;
    public static Map<String,List<String>> linesListMap = new HashMap<>();
    private static OnFileOperationsCompleteListener callback;
    public static Stack<OnFileOperationsCompleteListener> stackCallbacks;
    public static String moveBinaryFileCurrentOperation = "pan.alexander.tordnscrypt.moveBinaryFile";
    public static String copyBinaryFileCurrentOperation = "pan.alexander.tordnscrypt.copyBinaryFile";
    public static String deleteFileCurrentOperation = "pan.alexander.tordnscrypt.deleteFile";
    public static String readTextFileCurrentOperation = "pan.alexander.tordnscrypt.readTextFile";
    public static String writeToTextFileCurrentOperation = "pan.alexander.tordnscrypt.writeToTextFile";
    private static ExecutorService executorService = Executors.newFixedThreadPool(1);

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
            @Override
            public void run() {
                fileOperationResult = false;
                InputStream in;
                OutputStream out;
                try {
                    File dir = new File(outputPath);
                    if (!dir.isDirectory()) {
                        if (!dir.mkdirs() || !dir.setReadable(true) || !dir.setWritable(true)) {
                            Log.e(LOG_TAG,"Unable to create dir " + dir.toString());
                            if (callback!=null && !tag.equals("ignored"))
                                callback.OnFileOperationComplete(moveBinaryFileCurrentOperation, outputPath, tag);
                            return;
                        }
                    }

                    File oldFile = new File(outputPath + "/" + inputFile);
                    if (oldFile.exists()) {
                        if (!deleteFileInternal(context,outputPath,inputFile)) {
                            Log.e(LOG_TAG, "Unable to delete file " + oldFile.toString());
                            return;
                        }
                    }

                    File inFile = new File(inputPath + "/" + inputFile);
                    if (!inFile.canRead()) {
                        if (!inFile.setReadable(true)) {
                            Log.w(LOG_TAG, "Unable to chmod file " + oldFile.toString());
                            FileOperations fileOperations = new FileOperations();
                            fileOperations.restoreAccess(context, inFile.getPath());
                        } else if (!inFile.canRead()) {
                            Log.e(LOG_TAG, "Unable to chmod file " + oldFile.toString());
                            if (callback!=null && !tag.equals("ignored"))
                                callback.OnFileOperationComplete(moveBinaryFileCurrentOperation, inputPath + "/" + inputFile, tag);
                            return;
                        }
                    }

                    in = new FileInputStream(inputPath + "/" + inputFile);
                    out = new FileOutputStream(outputPath + "/" + inputFile);

                    byte[] buffer = new byte[1024];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                    in.close();

                    // write the output file
                    out.flush();
                    out.close();

                    File newFile = new File(outputPath + "/" + inputFile);
                    if (!newFile.exists()) {
                        Log.e(LOG_TAG,"New file not exist " + oldFile.toString());
                        if (callback!=null && !tag.equals("ignored"))
                            callback.OnFileOperationComplete(moveBinaryFileCurrentOperation, outputPath + "/" + inputFile, tag);
                        return;
                    }

                    // delete the unwanted file
                    if (!deleteFileInternal(context,inputPath,inputFile)) {
                        Log.e(LOG_TAG, "Unable to delete file " + inputFile);
                        return;
                    }
                }

                catch (FileNotFoundException fnfe1) {
                    Log.e(LOG_TAG,"File not found " + fnfe1.getMessage());
                    if (callback!=null && !tag.equals("ignored"))
                        callback.OnFileOperationComplete(moveBinaryFileCurrentOperation, inputPath + "/" + inputFile, tag);
                    return;
                }
                catch (Exception e) {
                    Log.e(LOG_TAG,"replaceBinaryFile function fault " + e.getMessage());
                    if (callback!=null && !tag.equals("ignored"))
                        callback.OnFileOperationComplete(moveBinaryFileCurrentOperation, inputPath + "/" + inputFile, tag);
                    return;
                }

                fileOperationResult = true;
                if (callback!=null && !tag.equals("ignored"))
                    callback.OnFileOperationComplete(moveBinaryFileCurrentOperation, outputPath + "/" + inputFile, tag);
            }
        };

        executorService.execute(runnable);
    }

    public static void copyBinaryFile(final Context context, final String inputPath, final String inputFile, final String outputPath, final String tag) {

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                fileOperationResult = false;
                InputStream in;
                OutputStream out;
                try {
                    File dir = new File(outputPath);
                    if (!dir.isDirectory()) {
                        if (!dir.mkdirs() || !dir.setReadable(true) || !dir.setWritable(true)) {
                            Log.e(LOG_TAG,"Unable to create dir " + dir.toString());
                            if (callback!=null && !tag.equals("ignored"))
                                callback.OnFileOperationComplete(copyBinaryFileCurrentOperation, outputPath, tag);
                            return;
                        }
                    }

                    File oldFile = new File(outputPath + "/" + inputFile);
                    if (oldFile.exists()) {
                        if (!deleteFileInternal(context,outputPath,inputFile)) {
                            Log.e(LOG_TAG, "Unable to delete file " + oldFile.toString());
                            return;
                        }
                    }

                    File inFile = new File(inputPath + "/" + inputFile);
                    if (!inFile.canRead()) {
                        if (!inFile.setReadable(true)) {
                            if (callback!=null && !tag.equals("ignored"))
                                callback.OnFileOperationComplete(copyBinaryFileCurrentOperation, inputPath + "/" + inputFile, tag);
                            Log.e(LOG_TAG, "Unable to chmod file " + oldFile.toString());
                            return;
                        }
                    }

                    in = new FileInputStream(inputPath + "/" + inputFile);
                    out = new FileOutputStream(outputPath + "/" + inputFile);

                    byte[] buffer = new byte[1024];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                    in.close();

                    // write the output file
                    out.flush();
                    out.close();

                    File newFile = new File(outputPath + "/" + inputFile);
                    if (!newFile.exists()) {
                        if (callback!=null && !tag.equals("ignored"))
                            callback.OnFileOperationComplete(copyBinaryFileCurrentOperation, outputPath + "/" + inputFile, tag);
                        Log.e(LOG_TAG,"New file not exist " + oldFile.toString());
                        return;
                    }
                }

                catch (FileNotFoundException fnfe1) {
                    Log.e(LOG_TAG,"File not found " + fnfe1.getMessage());
                    if (callback!=null && !tag.equals("ignored"))
                        callback.OnFileOperationComplete(copyBinaryFileCurrentOperation, outputPath + "/" + inputFile, tag);
                    return;
                }
                catch (Exception e) {
                    if (callback!=null && !tag.equals("ignored"))
                        callback.OnFileOperationComplete(copyBinaryFileCurrentOperation, outputPath + "/" + inputFile, tag);
                    Log.e(LOG_TAG,"copyBinaryFile function fault " + e.getMessage());
                    return;
                }
                fileOperationResult = true;
                if (callback!=null && !tag.equals("ignored"))
                    callback.OnFileOperationComplete(copyBinaryFileCurrentOperation, outputPath + "/" + inputFile, tag);
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
                        return false;
                    }
                }
                if (!usedFile.delete()) {
                    Log.e(LOG_TAG, "Unable to delete file " + usedFile.toString());
                    return false;
                }
            } else {
                Log.e(LOG_TAG, "Unable to delete file. No file " + usedFile.toString());
                return false;
            }
        } catch (Exception e) {
            Log.e(LOG_TAG,"deleteFileInternal function fault " + e.getMessage());
            return false;
        }
        return true;
    }

    public static void deleteFile(final Context context, final String inputPath, final String inputFile, final String tag) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                fileOperationResult = false;
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
                                if (callback!=null && !tag.equals("ignored"))
                                    callback.OnFileOperationComplete(deleteFileCurrentOperation, inputPath + "/" + inputFile, tag);
                                return;
                            }
                        }
                        if (!usedFile.delete()) {
                            Log.e(LOG_TAG, "Unable to delete file " + usedFile.toString());
                            if (callback!=null && !tag.equals("ignored"))
                                callback.OnFileOperationComplete(deleteFileCurrentOperation, inputPath + "/" + inputFile, tag);
                            return;
                        }
                    } else {
                        Log.e(LOG_TAG, "Unable to delete file. No file " + usedFile.toString());
                        if (callback!=null && !tag.equals("ignored"))
                            callback.OnFileOperationComplete(deleteFileCurrentOperation, inputPath + "/" + inputFile, tag);
                        return;
                    }
                } catch (Exception e) {
                    Log.e(LOG_TAG,"deleteFile function fault " + e.getMessage());
                    if (callback!=null && !tag.equals("ignored"))
                        callback.OnFileOperationComplete(deleteFileCurrentOperation, inputPath + "/" + inputFile, tag);
                    return;
                }
                fileOperationResult = true;
                if (callback!=null && !tag.equals("ignored"))
                    callback.OnFileOperationComplete(deleteFileCurrentOperation, inputPath + "/" + inputFile, tag);
            }
        };
        executorService.execute(runnable);
    }

    @SuppressLint("SetWorldReadable")
    public static void readTextFile(final Context context, final String filePath, final String tag) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                fileOperationResult = false;
                linesListMap.remove(filePath);

                BufferedReader br = null;
                FileInputStream fstream = null;
                try {
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
                                Log.e(LOG_TAG, "readTextFile take " + filePath + " error");
                                if (callback!=null)
                                    callback.OnFileOperationComplete(readTextFileCurrentOperation, filePath, tag);
                                return;
                            }
                        }
                    } else {
                        Log.e(LOG_TAG, "readTextFile no file " + filePath);
                        if (callback!=null)
                            callback.OnFileOperationComplete(readTextFileCurrentOperation, filePath, tag);
                        return;
                    }


                    fstream = new FileInputStream(filePath);
                    br = new BufferedReader(new InputStreamReader(fstream));
                    List<String> linesList = new LinkedList<>();

                    for(String tmp; (tmp = br.readLine()) != null;) {
                        linesList.add(tmp.trim());
                    }
                    fstream.close();
                    fstream = null;
                    br.close();
                    br = null;
                    linesListMap.put(filePath,linesList);
                    fileOperationResult = true;
                } catch (IOException e) {
                    Log.e(LOG_TAG,"readTextFile Exception " + e.getMessage() + e.getCause());
                } finally {
                    if (callback!=null)
                        callback.OnFileOperationComplete(readTextFileCurrentOperation, filePath, tag);
                    try {
                        if (fstream!= null)fstream.close();
                        if (br != null)br.close();
                    } catch (IOException ex) {
                        Log.e(LOG_TAG,"readTextFile Error when close file" + ex.getMessage() + ex.getCause());
                    }
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
                fileOperationResult = false;
                PrintWriter writer = null;
                try {
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
                                Log.e(LOG_TAG, "writeToTextFile writeTo " + filePath + " error");
                                if (callback!=null && !tag.equals("ignored"))
                                    callback.OnFileOperationComplete(writeToTextFileCurrentOperation, filePath, tag);
                                return;
                            }
                        }
                    }

                    writer = new PrintWriter(filePath);
                    for (String line:lines) {
                        writer.println(line);
                    }
                    writer.close();
                    writer = null;
                    fileOperationResult = true;
                    linesListMap.remove(filePath);
                } catch (IOException e) {
                    Log.e(LOG_TAG,"writeToTextFile Exception " + e.getMessage() + e.getCause());
                } finally {
                    if (writer != null)writer.close();
                    if (callback!=null && !tag.equals("ignored"))
                        callback.OnFileOperationComplete(writeToTextFileCurrentOperation, filePath, tag);
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
        final android.support.v7.app.AlertDialog.Builder builder = new android.support.v7.app.AlertDialog.Builder(context,R.style.Theme_AppCompat_DayNight_Dialog_Alert);
        builder.setTitle(R.string.please_wait);
        builder.setCancelable(false);
        builder.setPositiveButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
            }
        });

        ProgressBar progressBar = new ProgressBar(context,null,android.R.attr.progressBarStyleHorizontal);
        progressBar.setIndeterminate(true);
        builder.setView(progressBar);
        builder.setCancelable(false);
        return builder.show();
    }

    public static void setOnFileOperationCompleteListener(OnFileOperationsCompleteListener callback) {
        if (stackCallbacks == null)
            stackCallbacks = new Stack<>();

        if (FileOperations.callback != null)
            stackCallbacks.push(FileOperations.callback);

        FileOperations.callback = callback;
    }

    public static void deleteOnFileOperationCompleteListener() {
        if (stackCallbacks.empty()) {
            FileOperations.callback = null;
        } else {
            FileOperations.callback = stackCallbacks.pop();
        }

    }

    public interface OnFileOperationsCompleteListener {
        void OnFileOperationComplete(String currentFileOperation, String path, String tag);
    }
}
