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
    public interface Listener {
        void onCallInvite(String fromPeerId, String callId);

        default void onWebRtcOffer(String fromPeerId, String callId, String sdp) {}

        default void onWebRtcAnswer(String fromPeerId, String callId, String sdp) {}

        default void onWebRtcIce(
                String fromPeerId,
                String callId,
                String candidate,
                String sdpMid,
                int sdpMLineIndex
        ) {}

        default void onCallHangup(String fromPeerId, String callId) {}
    }

    private static final String TAG = SignalingClient.class.getSimpleName();
    private static final long MAX_RECONNECT_DELAY_MS = 30_000;
    private static final SignalingClient INSTANCE = new SignalingClient();

    private final OkHttpClient client = new OkHttpClient();
    private final Handler reconnectHandler = new Handler(Looper.getMainLooper());
    private final Runnable reconnectRunnable = this::connectSocket;
    private WebSocket webSocket;
    private String serverUrl;
    private String peerId;
    private int reconnectAttempt;
    private boolean shouldReconnect;
    private volatile Listener listener;

    public static SignalingClient getInstance() {
        return INSTANCE;
    }

    private SignalingClient() {}

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public synchronized void clearListener(Listener expectedListener) {
        if (listener == expectedListener) listener = null;
    }

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
                    } else if ("call.invite".equals(type)) {
                        CallSignaling.Invite invite = CallSignaling.parseInvite(message);
                        if (invite == null) {
                            Log.e(TAG, "Invalid call.invite message");
                            return;
                        }
                        reconnectHandler.post(() -> {
                            Listener currentListener = listener;
                            if (currentListener != null) {
                                currentListener.onCallInvite(invite.from, invite.callId);
                            }
                        });
                    } else if ("webrtc.offer".equals(type) || "webrtc.answer".equals(type)) {
                        CallSignaling.SessionDescriptionMessage description =
                                CallSignaling.parseSessionDescription(message);
                        if (description == null) {
                            Log.e(TAG, "Invalid " + type + " message");
                            return;
                        }
                        reconnectHandler.post(() -> {
                            Listener currentListener = listener;
                            if (currentListener == null) return;
                            if ("webrtc.offer".equals(description.type)) {
                                currentListener.onWebRtcOffer(
                                        description.from,
                                        description.callId,
                                        description.sdp
                                );
                            } else {
                                currentListener.onWebRtcAnswer(
                                        description.from,
                                        description.callId,
                                        description.sdp
                                );
                            }
                        });
                    } else if ("webrtc.ice".equals(type)) {
                        CallSignaling.IceMessage ice = CallSignaling.parseIce(message);
                        if (ice == null) {
                            Log.e(TAG, "Invalid webrtc.ice message");
                            return;
                        }
                        reconnectHandler.post(() -> {
                            Listener currentListener = listener;
                            if (currentListener != null) {
                                currentListener.onWebRtcIce(
                                        ice.from,
                                        ice.callId,
                                        ice.candidate,
                                        ice.sdpMid,
                                        ice.sdpMLineIndex
                                );
                            }
                        });
                    } else if ("call.hangup".equals(type)) {
                        CallSignaling.Hangup hangup = CallSignaling.parseHangup(message);
                        if (hangup == null) {
                            Log.e(TAG, "Invalid call.hangup message");
                            return;
                        }
                        reconnectHandler.post(() -> {
                            Listener currentListener = listener;
                            if (currentListener != null) {
                                currentListener.onCallHangup(hangup.from, hangup.callId);
                            }
                        });
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

    public void acceptCall(String toPeerId, String callId) {
        sendCallMessage("call.accept", toPeerId, callId);
    }

    public void rejectCall(String toPeerId, String callId) {
        sendCallMessage("call.reject", toPeerId, callId);
    }

    public void hangUpCall(String toPeerId, String callId) {
        sendCallMessage("call.hangup", toPeerId, callId);
    }

    public void sendAnswer(String toPeerId, String callId, String sdp) {
        sendMessage(CallSignaling.createSessionDescription(
                "webrtc.answer",
                toPeerId,
                callId,
                sdp
        ));
    }

    public void sendIce(
            String toPeerId,
            String callId,
            String candidate,
            String sdpMid,
            int sdpMLineIndex
    ) {
        sendMessage(CallSignaling.createIce(
                toPeerId,
                callId,
                candidate,
                sdpMid,
                sdpMLineIndex
        ));
    }

    private synchronized void sendCallMessage(String type, String toPeerId, String callId) {
        sendMessage(CallSignaling.createRelay(type, toPeerId, callId));
    }

    private synchronized void sendMessage(JsonObject message) {
        if (webSocket == null) {
            Log.w(TAG, "Unable to send " + message.get("type").getAsString()
                    + "; WebSocket is disconnected");
            return;
        }
        webSocket.send(message.toString());
        Log.i(TAG, "Signaling message sent: " + message.get("type").getAsString());
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
