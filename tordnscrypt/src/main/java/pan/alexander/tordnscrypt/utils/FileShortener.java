package pan.alexander.tordnscrypt.utils;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;

public class FileShortener {
    private final static long TOO_TOO_LONG_FILE_LENGTH = 1024 * 500;
    private final static long TOO_TOO_LONG_FILE_LENGTH_HYSTERESIS = 1024 * 100;

    public static void shortenTooTooLongFile(String filePath) {
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
