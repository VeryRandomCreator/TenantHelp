package com.veryrandomcreator.renthelp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.security.crypto.EncryptedFile;
import androidx.security.crypto.EncryptedSharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PhotoStorageManager {

    private static final String PREFS_FILENAME = "secure_photo_prefs";
    private static final String BASE_KEY_PHOTOS_JSON = "photos_json_";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface SaveCallback {
        void onSuccess(String id);

        void onError(Exception e);
    }

    public static void savePhotoData(Context context, String inspectionId, String label, String notes, Bitmap bitmap, SaveCallback callback) {
        executor.execute(() -> {
            try {
                // HANDLE DUPLICATE IDS, or seperate directories for each inspection
                String id = UUID.randomUUID().toString();

                File file = new File(context.getFilesDir(), id + ".jpg");
                EncryptedFile encryptedFile = new EncryptedFile.Builder(
                        context,
                        file,
                        SecurityManager.getMasterKey(context),
                        EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
                ).build();

                if (file.exists()) {
                    file.delete();
                }

                try (FileOutputStream outputStream = encryptedFile.openFileOutput()) {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream);
                }

                EncryptedSharedPreferences sharedPreferences = SecurityManager.getSharedPreferences(context, PREFS_FILENAME);

                String prefsKey = BASE_KEY_PHOTOS_JSON + inspectionId;
                JSONArray jsonArray;
                String existingJson = sharedPreferences.getString(prefsKey, "[]");
                jsonArray = new JSONArray(existingJson);

                JSONObject newItem = new JSONObject();
                newItem.put("id", id);
                newItem.put("label", label);
                newItem.put("notes", notes);

                jsonArray.put(newItem);

                sharedPreferences.edit().putString(prefsKey, jsonArray.toString()).apply();

                mainHandler.post(() -> {
                    if (callback != null) callback.onSuccess(id);
                });

            } catch (Exception e) {
                e.printStackTrace();
                mainHandler.post(() -> {
                    if (callback != null) callback.onError(e);
                });
            }
        });
    }

    // Updates an existing photo entry: overwrites the bitmap file and patches label/notes in JSON
    public static void updatePhotoData(Context context, String inspectionId, String existingId, String label, String notes, Bitmap bitmap, SaveCallback callback) {
        executor.execute(() -> {
            try {
                File file = new File(context.getFilesDir(), existingId + ".jpg");
                if (file.exists()) {
                    file.delete();
                }

                EncryptedFile encryptedFile = new EncryptedFile.Builder(
                        context,
                        file,
                        SecurityManager.getMasterKey(context),
                        EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
                ).build();

                try (FileOutputStream outputStream = encryptedFile.openFileOutput()) {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream);
                }

                EncryptedSharedPreferences sharedPreferences = SecurityManager.getSharedPreferences(context, PREFS_FILENAME);

                String prefsKey = BASE_KEY_PHOTOS_JSON + inspectionId;
                String existingJson = sharedPreferences.getString(prefsKey, "[]");
                JSONArray jsonArray = new JSONArray(existingJson);



                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject obj = jsonArray.getJSONObject(i);
                    if (obj.getString("id").equals(existingId)) {
                        obj.put("label", label);
                        obj.put("notes", notes);
                        break;
                    }
                }

                sharedPreferences.edit().putString(prefsKey, jsonArray.toString()).apply();

                mainHandler.post(() -> {
                    if (callback != null) callback.onSuccess(existingId);
                });

            } catch (Exception e) {
                e.printStackTrace();
                mainHandler.post(() -> {
                    if (callback != null) callback.onError(e);
                });
            }
        });
    }



    // Updates only the label and notes in the JSON, skipping the (expensive) file write entirely
    public static void updatePhotoMetadataOnly(Context context, String inspectionId, String existingId, String label, String notes, SaveCallback callback) {
        executor.execute(() -> {
            try {
                EncryptedSharedPreferences sharedPreferences = SecurityManager.getSharedPreferences(context, PREFS_FILENAME);

                String prefsKey = BASE_KEY_PHOTOS_JSON + inspectionId;
                String existingJson = sharedPreferences.getString(prefsKey, "[]");
                JSONArray jsonArray = new JSONArray(existingJson);

                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject obj = jsonArray.getJSONObject(i);
                    if (obj.getString("id").equals(existingId)) {
                        obj.put("label", label);
                        obj.put("notes", notes);
                        break;
                    }
                }

                sharedPreferences.edit().putString(prefsKey, jsonArray.toString()).apply();

                mainHandler.post(() -> {
                    if (callback != null) callback.onSuccess(existingId);
                });

            } catch (Exception e) {
                e.printStackTrace();
                mainHandler.post(() -> {
                    if (callback != null) callback.onError(e);
                });
            }
        });
    }

    public static void deletePhotoData(Context context, String inspectionId, String id, SaveCallback callback) {
        executor.execute(() -> {
            try {
                File file = new File(context.getFilesDir(), id + ".jpg");
                if (file.exists()) {
                    file.delete();
                }

                EncryptedSharedPreferences sharedPreferences = SecurityManager.getSharedPreferences(context, PREFS_FILENAME);

                String prefsKey = BASE_KEY_PHOTOS_JSON + inspectionId;
                String existingJson = sharedPreferences.getString(prefsKey, "[]");
                JSONArray jsonArray = new JSONArray(existingJson);
                JSONArray newArray = new JSONArray();

                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject obj = jsonArray.getJSONObject(i);
                    if (!obj.getString("id").equals(id)) {
                        newArray.put(obj);
                    }
                }

                sharedPreferences.edit().putString(prefsKey, newArray.toString()).apply();

                mainHandler.post(() -> {
                    if (callback != null) callback.onSuccess(id);
                });

            } catch (Exception e) {
                e.printStackTrace();
                mainHandler.post(() -> {
                    if (callback != null) callback.onError(e);
                });
            }
        });
    }

    public static List<InspectionImage> loadInspectionImageData(Context context, String inspectionId) throws GeneralSecurityException, IOException, JSONException {
        EncryptedSharedPreferences sharedPreferences = SecurityManager.getSharedPreferences(context, PREFS_FILENAME);

        String prefsKey = BASE_KEY_PHOTOS_JSON + inspectionId;
        String jsonString = sharedPreferences.getString(prefsKey, "[]");
        JSONArray jsonArray = new JSONArray(jsonString);

        List<InspectionImage> items = new ArrayList<>();
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject obj = jsonArray.getJSONObject(i);
            String id = obj.getString("id");
            String label = obj.getString("label");
            String notes = obj.getString("notes");
            items.add(new InspectionImage(id, label, notes));
        }

        return items;
    }

    public static Bitmap loadPhotoBitmap(Context context, String id, boolean mutable) throws GeneralSecurityException, IOException {
        File file = new File(context.getFilesDir(), id + ".jpg");
        if (!file.exists()) {
            return null;
        }

        EncryptedFile encryptedFile = new EncryptedFile.Builder(
                context,
                file,
                SecurityManager.getMasterKey(context),
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build();

        try (InputStream inputStream = encryptedFile.openFileInput()) {
            if (mutable) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inMutable = true;
                return BitmapFactory.decodeStream(inputStream, null, options);
            }

            return BitmapFactory.decodeStream(inputStream);
        }
    }
}
