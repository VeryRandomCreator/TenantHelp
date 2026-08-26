package com.veryrandomcreator.renthelp;

import android.content.Context;



import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;

public class FileStorageManager {
    public static final String FILE_PDF = "document.pdf";
    public static final String FILE_SIG = "signature.sig";
    public static final String FILE_CERT = "certificates.pem";
    public static final String FILE_OTS = "document.ots";

    public static void saveFile(Context context, String inspectionId, String filename, byte[] data) throws IOException {
        File dir = new File(context.getFilesDir(), inspectionId);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Could not create directory for bundle.");
        }
        File file = new File(dir, filename);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(data);
        }
    }

    public static byte[] loadFile(Context context, String inspectionId, String filename) {
        File dir = new File(context.getFilesDir(), inspectionId);
        File file = new File(dir, filename);
        if (!file.exists()) return null;

        try {
            return Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void deleteBundle(Context context, String inspectionId) {
        File dir = new File(context.getFilesDir(), inspectionId);
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File child : files) {
                    child.delete();
                }
            }
            dir.delete();
        }
    }
}
