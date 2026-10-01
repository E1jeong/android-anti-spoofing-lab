package com.virditech.ac7000;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.widget.ScrollView;
import android.widget.TextView;

import com.unionbiometrics.vision.AntiSpoofingEngine;
import com.unionbiometrics.vision.AntiSpoofingResult;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

/** Debug-only fixed-image probe for the public SDK session and callback contract. */
public final class SdkProbeActivity extends Activity {
    private static final String TAG = "SdkProbe";
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private TextView output;
    private volatile AntiSpoofingEngine engine;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        output = new TextView(this);
        output.setTextSize(17f);
        output.setGravity(Gravity.START);
        output.setPadding(24, 24, 24, 24);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(output);
        setContentView(scroll);
        worker.execute(this::runProbe);
    }

    private void runProbe() {
        int slot = getIntent().getIntExtra("slot", 0);
        Bitmap image = BitmapFactory.decodeResource(getResources(), R.drawable.test_image);
        if (image == null) {
            line("FAIL: bundled test image unavailable");
            return;
        }
        line("Device " + Build.MODEL + " / API " + Build.VERSION.SDK_INT
                + " / slot " + slot);
        try {
            AntiSpoofingEngine.Options options =
                    AntiSpoofingEngine.Options.defaults().withSlotIndex(slot);
            runSync(image, options);
            runLive(image, options);
            try {
                AntiSpoofingEngine invalid = AntiSpoofingEngine.create(
                        getApplicationContext(), options.withSlotIndex(9999));
                invalid.close();
                throw new IllegalStateException("Invalid slot was accepted");
            } catch (AntiSpoofingEngine.AntiSpoofingException expected) {
                line("PASS: invalid slot rejected");
            }
            line("PASS: SDK probe complete");
        } catch (Exception e) {
            Log.e(TAG, "SDK probe failed", e);
            line("FAIL: " + e.getMessage());
        } finally {
            AntiSpoofingEngine active = engine;
            if (active != null) active.close();
            engine = null;
            image.recycle();
        }
    }

    private void runSync(Bitmap image, AntiSpoofingEngine.Options options) throws Exception {
        engine = AntiSpoofingEngine.create(getApplicationContext(), options);
        line("SYNC: " + engine.label() + " / " + engine.backend());
        if (!"NNAPI".equals(engine.backend())) {
            throw new IllegalStateException("Selected slot did not use NNAPI");
        }
        Rect face = new Rect(0, 0, image.getWidth(), image.getHeight());
        for (int i = 1; i <= 13; i++) {
            AntiSpoofingResult result = engine.process(frame(image, face, engine.requiresRgb()));
            checkSessionResult(i, result);
            line("SYNC " + i + ": " + result.status() + " / samples "
                    + result.acceptedSamples());
        }
        engine.reset();
        AntiSpoofingResult restarted = engine.process(frame(image, face, engine.requiresRgb()));
        if (restarted.status() != AntiSpoofingResult.Status.PENDING
                || restarted.settleRemaining() != 9) {
            throw new IllegalStateException("SYNC reset did not restart settling");
        }
        line("PASS: SYNC reset");
        engine.close();
        engine = null;
    }

    private void runLive(Bitmap image, AntiSpoofingEngine.Options options) throws Exception {
        engine = AntiSpoofingEngine.create(getApplicationContext(), options.live(null));
        line("LIVE: " + engine.label() + " / " + engine.backend());
        Rect face = new Rect(0, 0, image.getWidth(), image.getHeight());
        for (int i = 1; i <= 13; i++) {
            Bitmap input = image.copy(Bitmap.Config.ARGB_8888, false);
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<AntiSpoofingResult> received = new AtomicReference<>();
            AtomicReference<String> callbackError = new AtomicReference<>();
            final int frameNumber = i;
            try {
                engine.submit(frame(input, face, engine.requiresRgb()), result -> {
                    if (Looper.myLooper() != Looper.getMainLooper()) {
                        callbackError.set("Callback was not on main looper");
                    }
                    received.set(result);
                    line("LIVE " + frameNumber + ": " + result.status() + " / samples "
                            + result.acceptedSamples());
                    latch.countDown();
                });
            } finally {
                input.recycle();
            }
            if (!latch.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("LIVE callback timed out at frame " + i);
            }
            if (callbackError.get() != null) throw new IllegalStateException(callbackError.get());
            checkSessionResult(i, received.get());
        }
        line("PASS: LIVE crop snapshot and main-looper callbacks");
        engine.reset();
        CountDownLatch lastCallback = new CountDownLatch(1);
        AtomicInteger callbackCount = new AtomicInteger();
        for (int i = 0; i < 30; i++) {
            Bitmap input = image.copy(Bitmap.Config.ARGB_8888, false);
            final boolean last = i == 29;
            try {
                engine.submit(frame(input, face, engine.requiresRgb()), result -> {
                    callbackCount.incrementAndGet();
                    if (last) lastCallback.countDown();
                });
            } finally {
                input.recycle();
            }
        }
        if (!lastCallback.await(30, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Latest LIVE frame did not receive a callback");
        }
        line("LIVE burst: " + callbackCount.get() + " callbacks from 30 submissions");
        engine.close();
        engine = null;
    }

    private static AntiSpoofingEngine.Frame frame(Bitmap image, Rect face, boolean dual) {
        return dual
                ? AntiSpoofingEngine.Frame.dual(image, face, image, face, 1L, 1L)
                : AntiSpoofingEngine.Frame.ir(image, face);
    }

    private static void checkSessionResult(int frame, AntiSpoofingResult result) {
        if (result == null || result.status() == AntiSpoofingResult.Status.ERROR) {
            throw new IllegalStateException("Frame " + frame + " failed: "
                    + (result == null ? "null result" : result.errorMessage()));
        }
        int samples = Math.max(0, frame - 10);
        if (result.acceptedSamples() != samples
                || result.settleRemaining() != Math.max(0, 10 - frame)) {
            throw new IllegalStateException("Unexpected session counts at frame " + frame);
        }
        if (frame <= 12 && result.status() != AntiSpoofingResult.Status.PENDING) {
            throw new IllegalStateException("Early decision at frame " + frame);
        }
        if (frame == 13 && result.status() != AntiSpoofingResult.Status.LIVE
                && result.status() != AntiSpoofingResult.Status.SPOOF) {
            throw new IllegalStateException("Missing terminal decision");
        }
        if (frame > 10 && (result.probabilities() == null
                || result.probabilities().length != 12)) {
            throw new IllegalStateException("Output is not [1,12]");
        }
    }

    private void line(String message) {
        Log.i(TAG, message);
        runOnUiThread(() -> output.append(message + "\n"));
    }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        AntiSpoofingEngine active = engine;
        if (active != null) active.close();
        super.onDestroy();
    }
}
