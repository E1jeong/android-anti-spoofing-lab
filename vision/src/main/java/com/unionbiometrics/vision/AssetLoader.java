package com.unionbiometrics.vision;

import android.content.Context;
import android.content.res.AssetFileDescriptor;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;

final class AssetLoader {
    private AssetLoader() {}

    static String path(String relativeName) {
        if (relativeName == null || relativeName.isEmpty()) {
            throw new IllegalArgumentException("Asset name is empty");
        }
        String normalized = relativeName.replace('\\', '/');
        if (normalized.startsWith("/")) {
            throw new IllegalArgumentException("Asset name must be relative: " + relativeName);
        }
        if (normalized.startsWith(VisionConstants.DIRECTORY + "/")) return normalized;
        return VisionConstants.DIRECTORY + "/" + normalized;
    }

    static String readUtf8(Context context, String relativeName) throws IOException {
        try (InputStream input = context.getAssets().open(path(relativeName));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int n;
            while ((n = input.read(buffer)) != -1) {
                output.write(buffer, 0, n);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    static MappedByteBuffer mapModel(Context context, String relativeName) throws IOException {
        try (AssetFileDescriptor descriptor = context.getAssets().openFd(path(relativeName));
             FileInputStream input = new FileInputStream(descriptor.getFileDescriptor())) {
            return input.getChannel().map(
                    FileChannel.MapMode.READ_ONLY,
                    descriptor.getStartOffset(),
                    descriptor.getDeclaredLength());
        }
    }
}
