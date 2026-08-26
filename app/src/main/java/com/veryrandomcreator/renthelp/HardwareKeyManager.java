package com.veryrandomcreator.renthelp;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Used Google Gemini for most of this
public class HardwareKeyManager {
    public static void generateAttestedKey(String alias, byte[] challenge) throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore");

        KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setAttestationChallenge(challenge)

                // Optional: Force StrongBox (dedicated security chip) if available on the device
                // .setIsStrongBoxBacked(true)

                .setUserAuthenticationRequired(false);
        keyPairGenerator.initialize(builder.build());
        keyPairGenerator.generateKeyPair();
    }

    public static void startSession(String inspectionId, SessionCallback callback) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                if (HardwareKeyManager.doesKeyExist(inspectionId)) {
                    throw new Exception("Cryptographic anomaly: Key alias already exists for a brand new UUID.");
                }

                BlockchainHashManager.HashResult result = BlockchainHashManager.getLatestHash();

                if (result.hash == null) { // If this is a problem in the future, use scheduler to update the hash every 5 minutes while the app is open
                    throw result.exception != null ? result.exception : new Exception("Failed to fetch hash.");
                } else {
                    System.out.println(result.hash);
                    System.out.println(Arrays.toString(result.responseStatuses.toArray()));
                }

                byte[] challenge = BlockchainHashManager.getLatestHash().hash.getBytes();

                HardwareKeyManager.generateAttestedKey(inspectionId, challenge);

                if (callback != null) {
                    callback.onSuccess();
                }
            } catch (Exception e) {
                e.printStackTrace();

                if (callback != null) {
                    callback.onError(e);
                }
            }
        });
    }

    public static boolean doesKeyExist(String alias) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        return keyStore.containsAlias(alias);
    }

    private static Certificate[] getCertificateChain(String alias) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);

        Certificate[] certChain = keyStore.getCertificateChain(alias);

        if (certChain == null || certChain.length == 0) {
            throw new Exception("Certificate chain not found. Key generation may have failed.");
        }

        return certChain;
    }

    // save as .pem
    public static byte[] getCertificateChainBytes(String alias) throws Exception {
        StringBuilder pemBuilder = new StringBuilder();
        Certificate[] certificateChain = getCertificateChain(alias);

        for (Certificate cert : certificateChain) {
            String base64Cert = Base64.encodeToString(cert.getEncoded(), Base64.NO_WRAP);

            pemBuilder.append("-----BEGIN CERTIFICATE-----\n");
            pemBuilder.append(base64Cert).append("\n");
            pemBuilder.append("-----END CERTIFICATE-----\n");
        }

        return pemBuilder.toString().getBytes();
    }

    public static byte[] signPdfDocument(String alias, byte[] pdfBytes) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);

        KeyStore.Entry entry = keyStore.getEntry(alias, null);
        if (!(entry instanceof KeyStore.PrivateKeyEntry)) {
            throw new Exception("Private key not found. Has the key been generated?");
        }
        PrivateKey hardwarePrivateKey = ((KeyStore.PrivateKeyEntry) entry).getPrivateKey();

        Signature ecdsaSignature = Signature.getInstance("SHA256withECDSA");
        ecdsaSignature.initSign(hardwarePrivateKey);

        ecdsaSignature.update(pdfBytes);

        return ecdsaSignature.sign();
    }

    public interface SessionCallback {
        void onSuccess();
        void onError(Exception e);
    }
}
