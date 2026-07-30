package com.virditech.ac7000.call;

import android.app.Activity;
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

    private SignalingClient signalingClient;
    private VideoPeerConnection videoPeerConnection;
    private TextView status;
    private String remotePeerId;
    private String callId;
    private boolean remoteHangup;
    private boolean hangupSent;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestFullscreenMode();

        remotePeerId = getIntent().getStringExtra(EXTRA_REMOTE_PEER_ID);
        callId = getIntent().getStringExtra(EXTRA_CALL_ID);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        SurfaceViewRenderer remoteRenderer = new SurfaceViewRenderer(this);
        root.addView(remoteRenderer, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        SurfaceViewRenderer localRenderer = new SurfaceViewRenderer(this);
        FrameLayout.LayoutParams localParams = new FrameLayout.LayoutParams(dp(135), dp(240));
        localParams.gravity = Gravity.TOP | Gravity.END;
        localParams.setMargins(dp(16), dp(16), dp(16), dp(16));
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
        status.setText(remotePeerId == null
                ? "Main camera pipeline is paused.\nWebRTC is not started yet."
                : "Preparing video call\nRemote peer: " + remotePeerId);
        status.setTextColor(Color.LTGRAY);
        status.setTextSize(20f);
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        statusParams.setMargins(0, dp(24), 0, dp(24));
        controls.addView(status, statusParams);

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

        if (remotePeerId == null || callId == null) return;

        signalingClient = SignalingClient.getInstance();
        signalingClient.setListener(this);
        videoPeerConnection = new VideoPeerConnection(
                this,
                localRenderer,
                remoteRenderer,
                new VideoPeerConnection.Listener() {
                    @Override public void onLocalAnswer(String sdp) {
                        signalingClient.sendAnswer(remotePeerId, callId, sdp);
                    }

                    @Override public void onLocalIce(IceCandidate candidate) {
                        signalingClient.sendIce(
                                remotePeerId,
                                callId,
                                candidate.sdp,
                                candidate.sdpMid == null ? "0" : candidate.sdpMid,
                                candidate.sdpMLineIndex
                        );
                    }

                    @Override public void onStateChanged(String state) {
                        runOnUiThread(() -> status.setText(state + "\nRemote peer: " + remotePeerId));
                    }

                    @Override public void onError(String message) {
                        runOnUiThread(() -> status.setText(
                                "Video error: " + message + "\nRemote peer: " + remotePeerId
                        ));
                    }
                }
        );
        try {
            videoPeerConnection.start();
            signalingClient.acceptCall(remotePeerId, callId);
        } catch (RuntimeException e) {
            status.setText("Video initialization failed: " + e.getMessage());
            signalingClient.rejectCall(remotePeerId, callId);
            videoPeerConnection.close();
            videoPeerConnection = null;
        }
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
        if (videoPeerConnection == null) return;
        videoPeerConnection.close();
        videoPeerConnection = null;
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
