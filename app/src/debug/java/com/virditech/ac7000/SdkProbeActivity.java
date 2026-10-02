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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Debug-only fixed-image probe for the public SDK session contract. */
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
        boolean dual = getIntent().getBooleanExtra("dual", false);
        Bitmap image = BitmapFactory.decodeResource(getResources(), R.drawable.test_image);
        if (image == null) {
            line("FAIL: bundled test image unavailable");
            return;
        }
        line("Device " + Build.MODEL + " / API " + Build.VERSION.SDK_INT
                + " / slot " + slot);
        try {
            engine = AntiSpoofingEngine.create(getApplicationContext(), slot, 0, 1);
            if (!"NNAPI".equals(engine.backend)) {
                throw new IllegalStateException("Selected slot did not use NNAPI");
            }
            Rect face = new Rect(0, 0, image.getWidth(), image.getHeight());
            AntiSpoofingResult raw = engine.process(
                    dual ? image : null, dual ? face : null, image, face);
            if (raw.status() == AntiSpoofingResult.Status.ERROR
                    || raw.probabilities().length != 12) {
                throw new IllegalStateException("Raw frame inference failed");
            }
            line("PASS: raw frame result");
            checkDecision(inferAndWait(dual ? image : null, dual ? face : null, image, face));
            engine.reset();
            checkDecision(inferAndWait(dual ? image : null, dual ? face : null, image, face));
            line("PASS: reset");
            engine.reset();
            runBurst(dual ? image : null, dual ? face : null, image, face);
            engine.close();
            engine = null;
            try {
                AntiSpoofingEngine invalid = AntiSpoofingEngine.create(
                        getApplicationContext(), 9999, 10, 3);
                invalid.close();
                throw new IllegalStateException("Invalid slot was accepted");
            } catch (IllegalStateException expected) {
                if ("Invalid slot was accepted".equals(expected.getMessage())) throw expected;
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

    private AntiSpoofingResult inferAndWait(Bitmap rgb, Rect rgbFace,
                                           Bitmap ir, Rect irFace) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<AntiSpoofingResult> received = new AtomicReference<>();
        AtomicReference<String> callbackError = new AtomicReference<>();
        Bitmap irInput = ir.copy(Bitmap.Config.ARGB_8888, false);
        Bitmap rgbInput = rgb == null ? null : rgb == ir ? irInput
                : rgb.copy(Bitmap.Config.ARGB_8888, false);
        try {
            engine.infer(rgbInput, rgbFace, irInput, irFace, result -> {
                if (Looper.myLooper() != Looper.getMainLooper()) {
                    callbackError.set("Callback was not on main looper");
                }
                received.set(result);
                latch.countDown();
            });
        } finally {
            if (rgbInput != null && rgbInput != irInput) rgbInput.recycle();
            irInput.recycle();
        }
        if (!latch.await(30, TimeUnit.SECONDS)) {
            throw new IllegalStateException("SDK callback timed out");
        }
        if (callbackError.get() != null) throw new IllegalStateException(callbackError.get());
        return received.get();
    }

    private void runBurst(Bitmap rgb, Rect rgbFace, Bitmap ir, Rect irFace)
            throws InterruptedException {
        CountDownLatch firstCallback = new CountDownLatch(1);
        AtomicInteger callbackCount = new AtomicInteger();
        AtomicReference<AntiSpoofingResult> received = new AtomicReference<>();
        for (int i = 0; i < 30; i++) {
            Bitmap input = ir.copy(Bitmap.Config.ARGB_8888, false);
            try {
                engine.infer(rgb == null ? null : input, rgbFace, input, irFace, result -> {
                    received.set(result);
                    callbackCount.incrementAndGet();
                    firstCallback.countDown();
                });
            } finally {
                input.recycle();
            }
        }
        if (!firstCallback.await(30, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Burst decision timed out");
        }
        checkDecision(received.get());
        Thread.sleep(500);
        if (callbackCount.get() != 1) {
            throw new IllegalStateException("Expected one callback for burst, got "
                    + callbackCount.get());
        }
        line("PASS: 30-frame burst produced one terminal callback");
    }

    private static void checkDecision(AntiSpoofingResult result) {
        if (result == null || result.status() == AntiSpoofingResult.Status.ERROR) {
            throw new IllegalStateException("Session failed: "
                    + (result == null ? "null result" : result.errorMessage()));
        }
        if (result.acceptedSamples() != 1 || result.settleRemaining() != 0
                || (result.status() != AntiSpoofingResult.Status.LIVE
                && result.status() != AntiSpoofingResult.Status.SPOOF)) {
            throw new IllegalStateException("Missing terminal decision");
        }
        if (result.probabilities() == null || result.probabilities().length != 12) {
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
