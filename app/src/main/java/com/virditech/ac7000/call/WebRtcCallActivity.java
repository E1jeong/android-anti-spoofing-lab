package com.virditech.ac7000.call;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.virditech.ac7000.api.call.SignalingClient;

import org.webrtc.IceCandidate;
import org.webrtc.SurfaceViewRenderer;

public final class WebRtcCallActivity extends Activity implements SignalingClient.Listener {
    public static final String EXTRA_REMOTE_PEER_ID = "remotePeerId";
    public static final String EXTRA_CALL_ID = "callId";
    private static final int MICROPHONE_PERMISSION_REQUEST = 20;

    private SignalingClient signalingClient;
    private VideoPeerConnection videoPeerConnection;
    private CallAudioManager callAudioManager;
    private TextView status;
    private Button muteButton;
    private String remotePeerId;
    private String callId;
    private boolean previewOnly;
    private boolean mediaStarted;
    private boolean microphoneMuted;
    private boolean remoteHangup;
    private boolean hangupSent;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestFullscreenMode();

        remotePeerId = getIntent().getStringExtra(EXTRA_REMOTE_PEER_ID);
        callId = getIntent().getStringExtra(EXTRA_CALL_ID);
        previewOnly = remotePeerId == null || callId == null;

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        SurfaceViewRenderer remoteRenderer = new SurfaceViewRenderer(this);
        if (!previewOnly) {
            root.addView(remoteRenderer, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
            ));
        }

        SurfaceViewRenderer localRenderer = new SurfaceViewRenderer(this);
        FrameLayout.LayoutParams localParams;
        if (previewOnly) {
            localParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
            );
        } else {
            localParams = new FrameLayout.LayoutParams(dp(135), dp(240));
            localParams.gravity = Gravity.TOP | Gravity.END;
            localParams.setMargins(dp(16), dp(16), dp(16), dp(16));
        }
        root.addView(localRenderer, localParams);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(dp(32), dp(32), dp(32), dp(32));
        controls.setBackgroundColor(0x66000000);

        TextView title = new TextView(this);
        title.setText("WEBRTC TEST");
        title.setTextColor(Color.WHITE);
        title.setTextSize(32f);
        title.setGravity(Gravity.CENTER);
        controls.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        status = new TextView(this);
        status.setText(previewOnly
                ? "Preparing camera preview"
                : "Preparing video call\nRemote peer: " + remotePeerId);
        status.setTextColor(Color.LTGRAY);
        status.setTextSize(20f);
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        statusParams.setMargins(0, dp(24), 0, dp(24));
        controls.addView(status, statusParams);

        if (!previewOnly) {
            muteButton = new Button(this);
            muteButton.setText("MUTE");
            muteButton.setOnClickListener(v -> toggleMicrophoneMuted());
            controls.addView(muteButton, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
        }

        Button closeButton = new Button(this);
        closeButton.setText("CLOSE");
        closeButton.setOnClickListener(v -> endCallAndFinish());
        controls.addView(closeButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        FrameLayout.LayoutParams controlsParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        controlsParams.gravity = Gravity.BOTTOM;
        root.addView(controls, controlsParams);
        setContentView(root);

        if (!previewOnly) {
            signalingClient = SignalingClient.getInstance();
            signalingClient.setListener(this);
        }
        videoPeerConnection = new VideoPeerConnection(
                this,
                localRenderer,
                remoteRenderer,
                new VideoPeerConnection.Listener() {
                    @Override public void onLocalAnswer(String sdp) {
                        if (signalingClient != null) {
                            signalingClient.sendAnswer(remotePeerId, callId, sdp);
                        }
                    }

                    @Override public void onLocalIce(IceCandidate candidate) {
                        if (signalingClient == null) return;
                        signalingClient.sendIce(
                                remotePeerId,
                                callId,
                                candidate.sdp,
                                candidate.sdpMid == null ? "0" : candidate.sdpMid,
                                candidate.sdpMLineIndex
                        );
                    }

                    @Override public void onStateChanged(String state) {
                        runOnUiThread(() -> status.setText(previewOnly
                                ? "Camera preview"
                                : state + "\nRemote peer: " + remotePeerId));
                    }

                    @Override public void onError(String message) {
                        runOnUiThread(() -> status.setText(previewOnly
                                ? "Preview error: " + message
                                : "Video error: " + message + "\nRemote peer: " + remotePeerId));
                    }
                }
        );
        if (!previewOnly
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            status.setText("Microphone permission required");
            requestPermissions(
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    MICROPHONE_PERMISSION_REQUEST
            );
            return;
        }
        startMedia();
    }

    private void startMedia() {
        if (mediaStarted || videoPeerConnection == null) return;
        try {
            if (!previewOnly) {
                callAudioManager = new CallAudioManager(
                        this,
                        active -> {
                            if (videoPeerConnection != null) {
                                videoPeerConnection.setAudioActive(active);
                            }
                        }
                );
                callAudioManager.start();
            }
            videoPeerConnection.start(!previewOnly);
            mediaStarted = true;
            if (signalingClient != null) {
                signalingClient.acceptCall(remotePeerId, callId);
            }
        } catch (RuntimeException e) {
            status.setText((previewOnly ? "Preview" : "Video")
                    + " initialization failed: " + e.getMessage());
            if (signalingClient != null) {
                signalingClient.rejectCall(remotePeerId, callId);
            }
            videoPeerConnection.close();
            videoPeerConnection = null;
            closeAudio();
        }
    }

    @Override public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != MICROPHONE_PERMISSION_REQUEST) return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startMedia();
            return;
        }
        status.setText("Microphone permission denied");
        if (signalingClient != null) {
            signalingClient.rejectCall(remotePeerId, callId);
        }
    }

    private void toggleMicrophoneMuted() {
        if (videoPeerConnection == null || !mediaStarted) return;
        microphoneMuted = !microphoneMuted;
        videoPeerConnection.setMicrophoneMuted(microphoneMuted);
        muteButton.setText(microphoneMuted ? "UNMUTE" : "MUTE");
    }

    @Override public void onCallInvite(String fromPeerId, String incomingCallId) {
        signalingClient.rejectCall(fromPeerId, incomingCallId);
    }

    @Override public void onWebRtcOffer(String fromPeerId, String incomingCallId, String sdp) {
        if (!matchesCall(fromPeerId, incomingCallId) || videoPeerConnection == null) return;
        videoPeerConnection.handleOffer(sdp);
    }

    @Override public void onWebRtcIce(
            String fromPeerId,
            String incomingCallId,
            String candidate,
            String sdpMid,
            int sdpMLineIndex
    ) {
        if (!matchesCall(fromPeerId, incomingCallId) || videoPeerConnection == null) return;
        videoPeerConnection.addRemoteIce(candidate, sdpMid, sdpMLineIndex);
    }

    @Override public void onCallHangup(String fromPeerId, String incomingCallId) {
        if (!matchesCall(fromPeerId, incomingCallId)) return;
        remoteHangup = true;
        closeVideo();
        finish();
    }

    private boolean matchesCall(String fromPeerId, String incomingCallId) {
        return remotePeerId.equals(fromPeerId) && callId.equals(incomingCallId);
    }

    private void endCallAndFinish() {
        if (signalingClient != null && !hangupSent) {
            signalingClient.hangUpCall(remotePeerId, callId);
            hangupSent = true;
        }
        closeVideo();
        finish();
    }

    @Override protected void onDestroy() {
        if (signalingClient != null && !remoteHangup && !hangupSent) {
            signalingClient.hangUpCall(remotePeerId, callId);
        }
        closeVideo();
        if (signalingClient != null) signalingClient.clearListener(this);
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        endCallAndFinish();
    }

    private void closeVideo() {
        if (videoPeerConnection != null) {
            videoPeerConnection.close();
            videoPeerConnection = null;
        }
        mediaStarted = false;
        closeAudio();
    }

    private void closeAudio() {
        if (callAudioManager == null) return;
        callAudioManager.stop();
        callAudioManager = null;
    }

    private void requestFullscreenMode() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
