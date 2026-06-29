package com.virditech.ac7000.device;

import android.os.Process;
import android.util.Log;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class AppWatchdog implements AutoCloseable {
    private static final String TAG = "AppWatchdog";
    private static final int TIMEOUT_SECONDS = 60;
    private static final AppWatchdog INSTANCE = new AppWatchdog();
    private final int pid = Process.myPid();
    private final UbimDaemonClient daemon = new UbimDaemonClient();
    private ScheduledExecutorService scheduler;

    private AppWatchdog() {
    }

    public static AppWatchdog getInstance() {
        return INSTANCE;
    }

    public synchronized void start() {
        if (scheduler != null) return;
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            boolean success = daemon.command("appalive set " + TIMEOUT_SECONDS + " " + pid + " reboot");
            if (!success) Log.e(TAG, "Unable to refresh app watchdog");
        }, 0, 5, TimeUnit.SECONDS);
    }

    @Override public synchronized void close() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        if (!daemon.command("appalive clear " + pid)) {
            Log.e(TAG, "Unable to clear app watchdog");
        }
    }
}
