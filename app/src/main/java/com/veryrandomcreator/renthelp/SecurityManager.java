package com.veryrandomcreator.renthelp;

import android.content.Context;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;

public class SecurityManager {
    private static MasterKey masterKeyInstance;
    private static final Map<String, EncryptedSharedPreferences> prefsCache = new HashMap<>();

    public static synchronized MasterKey getMasterKey(Context context) throws GeneralSecurityException, IOException {
        if (masterKeyInstance == null) {
            masterKeyInstance = new MasterKey.Builder(context.getApplicationContext()).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build();
        }
        return masterKeyInstance;
    }

    public static synchronized EncryptedSharedPreferences getSharedPreferences(Context context, String filename) throws GeneralSecurityException, IOException {
        if (!prefsCache.containsKey(filename)) {
            EncryptedSharedPreferences prefs = (EncryptedSharedPreferences) EncryptedSharedPreferences.create(
                    context.getApplicationContext(),
                    filename,
                    getMasterKey(context),
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
            prefsCache.put(filename, prefs);
        }
        return prefsCache.get(filename);
    }
}
