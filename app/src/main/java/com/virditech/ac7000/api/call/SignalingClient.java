package com.virditech.ac7000.api.call;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class SignalingClient {
    private static final String TAG = SignalingClient.class.getSimpleName();
    private static final long MAX_RECONNECT_DELAY_MS = 30_000;

    private final OkHttpClient client = new OkHttpClient();
    private final Handler reconnectHandler = new Handler(Looper.getMainLooper());
    private final Runnable reconnectRunnable = this::connectSocket;
    private WebSocket webSocket;
    private String serverUrl;
    private String peerId;
    private int reconnectAttempt;
    private boolean shouldReconnect;

    public synchronized void connect(String serverUrl, String peerId) {
        this.serverUrl = serverUrl;
        this.peerId = peerId;
        reconnectAttempt = 0;
        shouldReconnect = true;
        connectSocket();
    }

    private synchronized void connectSocket() {
        if (!shouldReconnect || webSocket != null) {
            return;
        }

        Request request = new Request.Builder()
                .url(serverUrl)
                .build();

        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket socket, Response response) {
                JsonObject registerMessage = new JsonObject();
                registerMessage.addProperty("type", "register");
                registerMessage.addProperty("peerId", peerId);
                registerMessage.addProperty("peerType", "device");
                socket.send(registerMessage.toString());
                Log.i(TAG, "WebSocket connected; registration requested");
            }

            @Override
            public void onMessage(WebSocket socket, String text) {
                try {
                    JsonObject message = JsonParser.parseString(text).getAsJsonObject();
                    String type = message.has("type") ? message.get("type").getAsString() : "";
                    if ("registered".equals(type)) {
                        resetReconnectAttempt();
                        Log.i(TAG, "Device registered: " + peerId);
                    } else if ("error".equals(type)) {
                        Log.e(TAG, "Signaling server error: " + text);
                    } else {
                        Log.d(TAG, "Signaling message received: " + type);
                    }
                } catch (RuntimeException e) {
                    Log.e(TAG, "Invalid signaling message", e);
                }
            }

            @Override
            public void onClosed(WebSocket socket, int code, String reason) {
                handleDisconnect(socket);
                Log.i(TAG, "WebSocket closed: " + code + ", " + reason);
            }

            @Override
            public void onFailure(WebSocket socket, Throwable throwable, Response response) {
                handleDisconnect(socket);
                Log.e(TAG, "WebSocket failed", throwable);
            }
        });
    }

    public synchronized void disconnect() {
        shouldReconnect = false;
        reconnectHandler.removeCallbacks(reconnectRunnable);
        if (webSocket == null) {
            return;
        }

        WebSocket socket = webSocket;
        webSocket = null;
        socket.close(1000, "app closed");
    }

    private synchronized void handleDisconnect(WebSocket socket) {
        if (webSocket != socket) {
            return;
        }

        webSocket = null;
        if (shouldReconnect) {
            long delayMs = Math.min(
                    1_000L << Math.min(reconnectAttempt, 5),
                    MAX_RECONNECT_DELAY_MS
            );
            reconnectAttempt++;
            reconnectHandler.removeCallbacks(reconnectRunnable);
            reconnectHandler.postDelayed(reconnectRunnable, delayMs);
            Log.i(TAG, "Reconnect scheduled in " + delayMs + " ms");
        }
    }

    private synchronized void resetReconnectAttempt() {
        reconnectAttempt = 0;
    }
}
