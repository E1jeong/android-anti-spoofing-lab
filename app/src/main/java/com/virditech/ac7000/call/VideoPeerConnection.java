package com.virditech.ac7000.call;

import android.content.Context;

import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.CapturerObserver;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.MediaStreamTrack;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpTransceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoFrame;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class VideoPeerConnection {
    private static final int DEVICE_VIDEO_ROTATION_DEGREES = 270;

    interface Listener {
        void onLocalAnswer(String sdp);
        void onLocalIce(IceCandidate candidate);
        void onStateChanged(String state);
        void onError(String message);
    }

    private static final Object INITIALIZATION_LOCK = new Object();
    private static boolean initialized;

    private final Context context;
    private final SurfaceViewRenderer localRenderer;
    private final SurfaceViewRenderer remoteRenderer;
    private final Listener listener;
    private final Object remoteIceLock = new Object();
    private final ArrayList<IceCandidate> pendingRemoteIce = new ArrayList<>();

    private EglBase eglBase;
    private PeerConnectionFactory factory;
    private PeerConnection peerConnection;
    private VideoCapturer videoCapturer;
    private SurfaceTextureHelper surfaceTextureHelper;
    private VideoSource videoSource;
    private VideoTrack localVideoTrack;
    private VideoTrack remoteVideoTrack;
    private boolean remoteDescriptionSet;
    private boolean closed;

    VideoPeerConnection(
            Context context,
            SurfaceViewRenderer localRenderer,
            SurfaceViewRenderer remoteRenderer,
            Listener listener
    ) {
        this.context = context.getApplicationContext();
        this.localRenderer = localRenderer;
        this.remoteRenderer = remoteRenderer;
        this.listener = listener;
    }

    void start() {
        initializeFactory();
        eglBase = EglBase.create();
        localRenderer.init(eglBase.getEglBaseContext(), null);
        localRenderer.setMirror(false);
        localRenderer.setZOrderMediaOverlay(true);
        remoteRenderer.init(eglBase.getEglBaseContext(), null);

        factory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(new DefaultVideoEncoderFactory(
                        eglBase.getEglBaseContext(),
                        true,
                        true
                ))
                .setVideoDecoderFactory(new DefaultVideoDecoderFactory(
                        eglBase.getEglBaseContext()
                ))
                .createPeerConnectionFactory();

        PeerConnection.RTCConfiguration configuration =
                new PeerConnection.RTCConfiguration(Collections.emptyList());
        configuration.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
        peerConnection = factory.createPeerConnection(configuration, createPeerObserver());
        if (peerConnection == null) throw new IllegalStateException("PeerConnection creation failed");

        videoCapturer = createFrontCameraCapturer();
        surfaceTextureHelper = SurfaceTextureHelper.create(
                "webrtc-capture",
                eglBase.getEglBaseContext()
        );
        videoSource = factory.createVideoSource(false);
        CapturerObserver sourceObserver = videoSource.getCapturerObserver();
        videoCapturer.initialize(
                surfaceTextureHelper,
                context,
                new RotatingCapturerObserver(sourceObserver)
        );
        videoCapturer.startCapture(768, 432, 15);
        localVideoTrack = factory.createVideoTrack("device-video", videoSource);
        localVideoTrack.addSink(localRenderer);
        peerConnection.addTrack(localVideoTrack, Collections.singletonList("device-stream"));
        listener.onStateChanged("Waiting for video offer");
    }

    void handleOffer(String sdp) {
        if (closed || peerConnection == null) return;
        listener.onStateChanged("Applying video offer");
        peerConnection.setRemoteDescription(new BaseSdpObserver() {
            @Override public void onSetSuccess() {
                markRemoteDescriptionSet();
                createAnswer();
            }

            @Override public void onSetFailure(String error) {
                listener.onError("Remote offer failed (" + sdp.length() + " chars): " + error);
            }
        }, new SessionDescription(SessionDescription.Type.OFFER, sdp));
    }

    void addRemoteIce(String candidate, String sdpMid, int sdpMLineIndex) {
        if (closed || peerConnection == null) return;
        IceCandidate iceCandidate = new IceCandidate(sdpMid, sdpMLineIndex, candidate);
        synchronized (remoteIceLock) {
            if (!remoteDescriptionSet) {
                pendingRemoteIce.add(iceCandidate);
                return;
            }
        }
        peerConnection.addIceCandidate(iceCandidate);
    }

    void close() {
        if (closed) return;
        closed = true;

        if (localVideoTrack != null) {
            localVideoTrack.removeSink(localRenderer);
            localVideoTrack.dispose();
            localVideoTrack = null;
        }
        if (remoteVideoTrack != null) {
            remoteVideoTrack.removeSink(remoteRenderer);
            remoteVideoTrack = null;
        }
        if (videoCapturer != null) {
            try {
                videoCapturer.stopCapture();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            videoCapturer.dispose();
            videoCapturer = null;
        }
        if (videoSource != null) {
            videoSource.dispose();
            videoSource = null;
        }
        if (surfaceTextureHelper != null) {
            surfaceTextureHelper.dispose();
            surfaceTextureHelper = null;
        }
        if (peerConnection != null) {
            peerConnection.close();
            peerConnection.dispose();
            peerConnection = null;
        }
        if (factory != null) {
            factory.dispose();
            factory = null;
        }
        localRenderer.release();
        remoteRenderer.release();
        if (eglBase != null) {
            eglBase.release();
            eglBase = null;
        }
        synchronized (remoteIceLock) {
            pendingRemoteIce.clear();
        }
    }

    private void initializeFactory() {
        synchronized (INITIALIZATION_LOCK) {
            if (initialized) return;
            PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions.builder(context)
                            .createInitializationOptions()
            );
            initialized = true;
        }
    }

    private VideoCapturer createFrontCameraCapturer() {
        CameraEnumerator enumerator = new Camera2Enumerator(context);
        for (String deviceName : enumerator.getDeviceNames()) {
            if (!enumerator.isFrontFacing(deviceName)) continue;
            CameraVideoCapturer capturer = enumerator.createCapturer(deviceName, null);
            if (capturer != null) return capturer;
        }
        throw new IllegalStateException("Front RGB camera not found");
    }

    private PeerConnection.Observer createPeerObserver() {
        return new PeerConnection.Observer() {
            @Override public void onSignalingChange(PeerConnection.SignalingState state) {}

            @Override public void onIceConnectionChange(PeerConnection.IceConnectionState state) {
                listener.onStateChanged("ICE " + state.name());
            }

            @Override public void onIceConnectionReceivingChange(boolean receiving) {}

            @Override public void onIceGatheringChange(PeerConnection.IceGatheringState state) {}

            @Override public void onIceCandidate(IceCandidate candidate) {
                listener.onLocalIce(candidate);
            }

            @Override public void onIceCandidatesRemoved(IceCandidate[] candidates) {}

            @Override public void onAddStream(MediaStream stream) {}

            @Override public void onRemoveStream(MediaStream stream) {}

            @Override public void onDataChannel(DataChannel dataChannel) {}

            @Override public void onRenegotiationNeeded() {}

            @Override public void onAddTrack(RtpReceiver receiver, MediaStream[] mediaStreams) {
                attachRemoteVideo(receiver.track());
            }

            @Override public void onTrack(RtpTransceiver transceiver) {
                attachRemoteVideo(transceiver.getReceiver().track());
            }
        };
    }

    private void attachRemoteVideo(MediaStreamTrack track) {
        if (!(track instanceof VideoTrack)) return;
        VideoTrack videoTrack = (VideoTrack) track;
        if (remoteVideoTrack == videoTrack) return;
        if (remoteVideoTrack != null) remoteVideoTrack.removeSink(remoteRenderer);
        remoteVideoTrack = videoTrack;
        remoteVideoTrack.addSink(remoteRenderer);
        listener.onStateChanged("Remote video received");
    }

    private void createAnswer() {
        if (closed || peerConnection == null) return;
        peerConnection.createAnswer(new BaseSdpObserver() {
            @Override public void onCreateSuccess(SessionDescription description) {
                setLocalAnswer(description);
            }

            @Override public void onCreateFailure(String error) {
                listener.onError("Answer creation failed: " + error);
            }
        }, new MediaConstraints());
    }

    private void setLocalAnswer(SessionDescription description) {
        if (closed || peerConnection == null) return;
        peerConnection.setLocalDescription(new BaseSdpObserver() {
            @Override public void onSetSuccess() {
                listener.onLocalAnswer(description.description);
                listener.onStateChanged("Video answer sent");
            }

            @Override public void onSetFailure(String error) {
                listener.onError("Local answer failed: " + error);
            }
        }, description);
    }

    private void markRemoteDescriptionSet() {
        List<IceCandidate> candidates;
        synchronized (remoteIceLock) {
            remoteDescriptionSet = true;
            candidates = new ArrayList<>(pendingRemoteIce);
            pendingRemoteIce.clear();
        }
        if (peerConnection == null) return;
        for (IceCandidate candidate : candidates) {
            peerConnection.addIceCandidate(candidate);
        }
    }

    private abstract static class BaseSdpObserver implements SdpObserver {
        @Override public void onCreateSuccess(SessionDescription description) {}
        @Override public void onSetSuccess() {}
        @Override public void onCreateFailure(String error) {}
        @Override public void onSetFailure(String error) {}
    }

    private static final class RotatingCapturerObserver implements CapturerObserver {
        private final CapturerObserver delegate;

        RotatingCapturerObserver(CapturerObserver delegate) {
            this.delegate = delegate;
        }

        @Override public void onCapturerStarted(boolean success) {
            delegate.onCapturerStarted(success);
        }

        @Override public void onCapturerStopped() {
            delegate.onCapturerStopped();
        }

        @Override public void onFrameCaptured(VideoFrame frame) {
            frame.getBuffer().retain();
            VideoFrame rotatedFrame = new VideoFrame(
                    frame.getBuffer(),
                    (frame.getRotation() + DEVICE_VIDEO_ROTATION_DEGREES) % 360,
                    frame.getTimestampNs()
            );
            try {
                delegate.onFrameCaptured(rotatedFrame);
            } finally {
                rotatedFrame.release();
            }
        }
    }
}
