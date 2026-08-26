package com.veryrandomcreator.renthelp;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;

public class OTSWorker extends Worker {
    public OTSWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        String inspectionId = getInputData().getString("inspectionId");
        if (inspectionId == null) return Result.failure();
        try {
            byte[] sigBytes = FileStorageManager.loadFile(getApplicationContext(), inspectionId, FileStorageManager.FILE_SIG);
            if (sigBytes == null) return Result.failure();

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(sigBytes);

            byte[] magicBytes = new byte[]{
                    (byte) 0x00, (byte) 0x4f, (byte) 0x70, (byte) 0x65, (byte) 0x6e,
                    (byte) 0x54, (byte) 0x69, (byte) 0x6d, (byte) 0x65, (byte) 0x73,
                    (byte) 0x74, (byte) 0x61, (byte) 0x6d, (byte) 0x70, (byte) 0x73,
                    (byte) 0x00, (byte) 0x00, (byte) 0x50, (byte) 0x72, (byte) 0x6f,
                    (byte) 0x6f, (byte) 0x66, (byte) 0x00, (byte) 0xbf, (byte) 0x89,
                    (byte) 0xe2, (byte) 0xe8, (byte) 0x84, (byte) 0xe8, (byte) 0x92,
                    (byte) 0x94
            };
            byte version = (byte) 0x01;
            byte sha256Opcode = (byte) 0x08;

            // alt: https://b.pool.opentimestamps.org/digest
            URL url = new URL("https://a.pool.opentimestamps.org/digest");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");

            conn.setRequestProperty("Content-Type", "application/octet-stream");
            conn.setRequestProperty("Accept", "application/octet-stream");
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(hash);
                os.flush();
            }

            if (conn.getResponseCode() == 200) {
                InputStream is = conn.getInputStream();
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();

                int nRead;
                byte[] data = new byte[16384];
                while ((nRead = is.read(data, 0, data.length)) != -1) {
                    buffer.write(data, 0, nRead);
                }
                buffer.flush();

                ByteArrayOutputStream finalOtsFile = new ByteArrayOutputStream();
                finalOtsFile.write(magicBytes);
                finalOtsFile.write(version);
                finalOtsFile.write(sha256Opcode);
                finalOtsFile.write(hash);
                finalOtsFile.write(buffer.toByteArray());
                FileStorageManager.saveFile(getApplicationContext(), inspectionId, FileStorageManager.FILE_OTS, finalOtsFile.toByteArray());

                return Result.success();
            } else {
                return Result.retry();
            }
        } catch (Exception e) {
            e.printStackTrace();
            // Returning retry tells WorkManager to try again later (e.g., if the network drops)
            return Result.retry();
        }
    }
}
