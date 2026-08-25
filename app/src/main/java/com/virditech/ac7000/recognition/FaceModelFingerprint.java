package com.virditech.ac7000.recognition;

import android.content.Context;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** SHA-256 identity of the exact TFLite asset used to create an embedding. */
public final class FaceModelFingerprint {
    private FaceModelFingerprint() {}

    public static String sha256(Context context, String modelAssetPath) throws IOException {
        try (InputStream input = context.getAssets().open(modelAssetPath)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
            byte[] hash = digest.digest();
            StringBuilder value = new StringBuilder(hash.length * 2);
            for (byte b : hash) value.append(String.format(java.util.Locale.US, "%02x", b & 0xff));
            return value.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
