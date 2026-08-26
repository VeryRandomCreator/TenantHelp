package com.veryrandomcreator.renthelp;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.security.crypto.EncryptedSharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class InspectionStorageManager {

    private static final String PREFS_FILENAME = "secure_inspections_prefs";
    private static final String KEY_INSPECTIONS_JSON = "inspections_json";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface SaveCallback {
        void onSuccess(String id);
        void onError(Exception e);
    }

    public static void saveInspectionData(Context context, Inspection inspection, SaveCallback callback) {
        executor.execute(() -> {
            try {
                EncryptedSharedPreferences sharedPreferences = SecurityManager.getSharedPreferences(context, PREFS_FILENAME);

                String existingJson = sharedPreferences.getString(KEY_INSPECTIONS_JSON, "[]");
                JSONArray jsonArray = new JSONArray(existingJson);

                boolean found = false;
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject obj = jsonArray.getJSONObject(i);
                    if (obj.getString("id").equals(inspection.getId())) {
                        obj.put("label", inspection.getLabel());
                        obj.put("description", inspection.getDescription());
                        obj.put("isReadOnly", inspection.isReadOnly());
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    JSONObject newItem = new JSONObject();
                    newItem.put("id", inspection.getId());
                    newItem.put("label", inspection.getLabel());
                    newItem.put("description", inspection.getDescription());
                    newItem.put("isReadOnly", inspection.isReadOnly());
                    jsonArray.put(newItem);
                }

                sharedPreferences.edit().putString(KEY_INSPECTIONS_JSON, jsonArray.toString()).apply();

                mainHandler.post(() -> {
                    if (callback != null) callback.onSuccess(inspection.getId());
                });

            } catch (Exception e) {
                e.printStackTrace();
                mainHandler.post(() -> {
                    if (callback != null) callback.onError(e);
                });
            }
        });
    }

    public static void deleteInspectionData(Context context, String id, SaveCallback callback) {
        executor.execute(() -> {
            try {
                EncryptedSharedPreferences sharedPreferences = SecurityManager.getSharedPreferences(context, PREFS_FILENAME);

                String existingJson = sharedPreferences.getString(KEY_INSPECTIONS_JSON, "[]");
                JSONArray jsonArray = new JSONArray(existingJson);
                JSONArray newArray = new JSONArray();

                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject obj = jsonArray.getJSONObject(i);
                    if (!obj.getString("id").equals(id)) {
                        newArray.put(obj);
                    }
                }

                sharedPreferences.edit().putString(KEY_INSPECTIONS_JSON, newArray.toString()).apply();

                // Also we should ideally delete all photos associated with this inspection
                // But for now we just delete the inspection metadata

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

    public static List<Inspection> loadInspections(Context context) throws GeneralSecurityException, IOException, JSONException {
        EncryptedSharedPreferences sharedPreferences = SecurityManager.getSharedPreferences(context, PREFS_FILENAME);

        String jsonString = sharedPreferences.getString(KEY_INSPECTIONS_JSON, "[]");
        JSONArray jsonArray = new JSONArray(jsonString);

        List<Inspection> items = new ArrayList<>();
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject obj = jsonArray.getJSONObject(i);
            String id = obj.getString("id");
            String label = obj.optString("label", "");
            String description = obj.optString("description", "");
            boolean isReadOnly = obj.optBoolean("isReadOnly", false);
            items.add(new Inspection(id, label, description, isReadOnly));
        }

        return items;
    }
    
    public static Inspection loadInspection(Context context, String id) throws GeneralSecurityException, IOException, JSONException {
        List<Inspection> all = loadInspections(context);
        for (Inspection p : all) {
            if (p.getId().equals(id)) {
                return p;
            }
        }
        return null;
    }
}
