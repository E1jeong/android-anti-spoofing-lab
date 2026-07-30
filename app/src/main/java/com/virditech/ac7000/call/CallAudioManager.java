package com.virditech.ac7000.call;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;

final class CallAudioManager {
    interface Listener {
        void onAudioActiveChanged(boolean active);
    }

    private final AudioManager audioManager;
    private final Listener listener;
    private AudioFocusRequest focusRequest;
    private AudioDeviceInfo previousCommunicationDevice;
    private int previousMode;
    private boolean previousSpeakerphoneOn;
    private boolean started;

    CallAudioManager(Context context, Listener listener) {
        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        this.listener = listener;
    }

    void start() {
        if (started) return;
        previousMode = audioManager.getMode();
        previousSpeakerphoneOn = audioManager.isSpeakerphoneOn();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            previousCommunicationDevice = audioManager.getCommunicationDevice();
        }

        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        routeToSpeaker();

        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();
        focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(this::onAudioFocusChange)
                .build();
        int result = audioManager.requestAudioFocus(focusRequest);
        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            restoreAudioState();
            throw new IllegalStateException("Audio focus unavailable");
        }
        started = true;
        listener.onAudioActiveChanged(true);
    }

    void stop() {
        if (!started) return;
        listener.onAudioActiveChanged(false);
        audioManager.abandonAudioFocusRequest(focusRequest);
        restoreAudioState();
        started = false;
    }

    private void onAudioFocusChange(int focusChange) {
        listener.onAudioActiveChanged(focusChange == AudioManager.AUDIOFOCUS_GAIN);
    }

    private void routeToSpeaker() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            for (AudioDeviceInfo device : audioManager.getAvailableCommunicationDevices()) {
                if (device.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                        && audioManager.setCommunicationDevice(device)) {
                    return;
                }
            }
        }
        audioManager.setSpeakerphoneOn(true);
    }

    private void restoreAudioState() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (previousCommunicationDevice == null) {
                audioManager.clearCommunicationDevice();
            } else {
                audioManager.setCommunicationDevice(previousCommunicationDevice);
            }
        } else {
            audioManager.setSpeakerphoneOn(previousSpeakerphoneOn);
        }
        audioManager.setMode(previousMode);
    }
}
