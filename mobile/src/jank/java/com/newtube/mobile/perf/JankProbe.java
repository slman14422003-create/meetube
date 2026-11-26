package com.newtube.mobile.perf;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.Trace;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.metrics.performance.FrameData;
import androidx.metrics.performance.JankStats;

import java.util.Arrays;
import java.util.WeakHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Debug and benchmark builds only (the release source set has a no-op twin): tracks the frames of
 * a screen with JankStats and, when the screen pauses, writes one summary line to logcat under the
 * tag {@code JankStatsSummary}:
 * {@code screen=Browse frames=412 janky=9 jankyPercent=2.2 uiMs p50=.. p90=.. p95=.. p99=..}.
 * Every janky frame is also a zero-length {@code JankStats.janky} trace slice, so a Perfetto trace
 * of a macrobenchmark run shows where they fell. The macrobenchmark module's
 * HomeJankStatsCheck reads that line.
 */
public final class JankProbe {
    private static final String TAG = "JankStatsSummary";
    private static final int MAX_SAMPLES = 20_000;
    private static final Executor sExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "JankProbe");
        thread.setDaemon(true);
        return thread;
    });
    private static final WeakHashMap<Activity, Probe> sProbes = new WeakHashMap<>();
    private static boolean sCallbacksInstalled;

    private JankProbe() {
    }

    /** Start tracking {@code activity}'s window. Call after setContentView; idempotent. */
    public static synchronized void attach(@NonNull Activity activity) {
        if (sProbes.containsKey(activity)) {
            return;
        }
        Probe probe = new Probe(activity.getClass().getSimpleName().replace("Mobile", "")
                .replace("Activity", ""));
        try {
            probe.stats = JankStats.createAndTrack(activity.getWindow(), sExecutor, probe);
        } catch (RuntimeException e) {
            Log.w(TAG, "JankStats unavailable: " + e);
            return;
        }
        sProbes.put(activity, probe);
        if (!sCallbacksInstalled) {
            sCallbacksInstalled = true;
            activity.getApplication().registerActivityLifecycleCallbacks(new Lifecycle());
        }
    }

    private static synchronized void onResumed(Activity activity) {
        Probe probe = sProbes.get(activity);
        if (probe != null) {
            probe.reset();
            probe.stats.setTrackingEnabled(true);
        }
    }

    private static synchronized void onPaused(Activity activity) {
        Probe probe = sProbes.get(activity);
        if (probe != null) {
            probe.stats.setTrackingEnabled(false);
            probe.summarize();
        }
    }

    private static final class Probe implements JankStats.OnFrameListener {
        private final String mScreen;
        private final long[] mUiNanos = new long[MAX_SAMPLES];
        private int mFrames;
        private int mJanky;
        JankStats stats;

        Probe(String screen) {
            mScreen = screen;
        }

        // Called on the JankProbe thread.
        @Override
        public synchronized void onFrame(@NonNull FrameData frame) {
            if (mFrames < MAX_SAMPLES) {
                mUiNanos[mFrames] = frame.getFrameDurationUiNanos();
            }
            mFrames++;
            if (frame.isJank()) {
                mJanky++;
                Trace.beginSection("JankStats.janky");
                Trace.endSection();
            }
        }

        synchronized void reset() {
            mFrames = 0;
            mJanky = 0;
        }

        synchronized void summarize() {
            if (mFrames == 0) {
                return;
            }
            int count = Math.min(mFrames, MAX_SAMPLES);
            long[] sorted = Arrays.copyOf(mUiNanos, count);
            Arrays.sort(sorted);
            Log.i(TAG, String.format(java.util.Locale.US,
                    "screen=%s frames=%d janky=%d jankyPercent=%.1f uiMs p50=%.2f p90=%.2f p95=%.2f p99=%.2f",
                    mScreen, mFrames, mJanky, 100.0 * mJanky / mFrames,
                    percentile(sorted, 50), percentile(sorted, 90), percentile(sorted, 95),
                    percentile(sorted, 99)));
        }

        private static double percentile(long[] sorted, int p) {
            int index = (int) Math.ceil(p / 100.0 * sorted.length) - 1;
            return sorted[Math.max(0, Math.min(sorted.length - 1, index))] / 1_000_000.0;
        }
    }

    private static final class Lifecycle implements Application.ActivityLifecycleCallbacks {
        @Override public void onActivityResumed(@NonNull Activity activity) {
            JankProbe.onResumed(activity);
        }

        @Override public void onActivityPaused(@NonNull Activity activity) {
            JankProbe.onPaused(activity);
        }

        @Override public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle state) { }
        @Override public void onActivityStarted(@NonNull Activity activity) { }
        @Override public void onActivityStopped(@NonNull Activity activity) { }
        @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle state) { }
        @Override public void onActivityDestroyed(@NonNull Activity activity) { }
    }
}
