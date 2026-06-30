package com.virditech.ac7000.device;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public final class UbimDaemonClient {
    private static final String TAG = "UbimDaemonClient";

    public UbimDaemonClient() {}

    public synchronized boolean command(String command) {
        byte[] payload = command.getBytes(StandardCharsets.UTF_8);
        if (payload.length == 0 || payload.length > 1024) return false;
        try (LocalSocket socket = new LocalSocket()) {
            socket.connect(new LocalSocketAddress("ubimd", LocalSocketAddress.Namespace.RESERVED));
            OutputStream output = socket.getOutputStream();
            output.write(payload.length & 0xff);
            output.write((payload.length >> 8) & 0xff);
            output.write(payload);
            output.flush();

            InputStream input = socket.getInputStream();
            int low = input.read();
            int high = input.read();
            if (low < 0 || high < 0) return false;
            int replyLength = low | (high << 8);
            if (replyLength < 1 || replyLength > 1024) return false;
            byte[] reply = new byte[replyLength];
            int offset = 0;
            while (offset < replyLength) {
                int count = input.read(reply, offset, replyLength - offset);
                if (count < 0) return false;
                offset += count;
            }
            return reply[0] == '0';
        } catch (IOException e) {
            Log.e(TAG, "ubimd command failed: " + command, e);
            return false;
        }
    }
}
