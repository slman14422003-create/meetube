package com.newtube.mobile;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.os.MessageQueue;
import android.os.Process;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * NEWTUBE(startup): one place for launch work the first frame does not need.
 *
 * <p>Application level: {@link #background} runs a task on one low-priority worker once the
 * process's first frame has been drawn (see {@link LaunchMilestones#runAfterFirstFrame}), and
 * {@link #mainIdle} runs one on the main thread in a gap of its queue after that frame.
 * Activity level: {@link #afterFirstDraw} runs a task on the main thread when this Activity's own
 * first frame has been drawn, in an idle gap; it is skipped if the Activity is gone by then.
 *
 * <p>Every task has a fallback timer, so a busy queue or a process that never draws (a service or
 * broadcast start) still runs it. Each task runs at most once. A task that throws is logged and
 * dropped: deferred work is an optimisation, never the reason a launch fails.</p>
 */
public final class StartupDeferral {
    private static final String TAG = "StartupDeferral";
    /** The longest deferred work waits for a first frame that never comes. */
    static final long FIRST_FRAME_FALLBACK_MS = 4_000;
    /** The longest a task waits for the main queue to go idle. */
    static final long IDLE_FALLBACK_MS = 1_500;

    private static final Handler sMain = new Handler(Looper.getMainLooper());
    private static volatile Executor sWorker;

    private StartupDeferral() {
    }

    /** Off the main thread, after the process's first frame. */
    public static void background(@NonNull String name, @NonNull Runnable task) {
        LaunchMilestones.runAfterFirstFrame(FIRST_FRAME_FALLBACK_MS,
                () -> mainIdleNow(() -> worker().execute(guarded(name, task, true))));
    }

    /** On the main thread, in an idle gap after the process's first frame. */
    public static void mainIdle(@NonNull String name, @NonNull Runnable task) {
        LaunchMilestones.runAfterFirstFrame(FIRST_FRAME_FALLBACK_MS,
                () -> mainIdleNow(guarded(name, task, false)));
    }

    /**
     * On the main thread, in an idle gap after {@code activity} has drawn its first frame. Skipped
     * when the Activity is finishing or destroyed by then. Call from onCreate, after setContentView.
     */
    public static void afterFirstDraw(@NonNull Activity activity, @NonNull String name,
                                      @NonNull Runnable task) {
        View decor = activity.getWindow().getDecorView();
        AtomicBoolean fired = new AtomicBoolean();
        Runnable once = () -> {
            if (!fired.compareAndSet(false, true)) {
                return;
            }
            if (activity.isFinishing() || activity.isDestroyed()) {
                return;
            }
            mainIdleNow(guarded(name, task, false));
        };
        decor.getViewTreeObserver().addOnDrawListener(new ViewTreeObserver.OnDrawListener() {
            private boolean mSeen;

            @Override
            public void onDraw() {
                if (mSeen) {
                    return;
                }
                mSeen = true;
                ViewTreeObserver.OnDrawListener self = this;
                // A listener cannot be removed from inside its own onDraw; post once the frame
                // has been handed to the renderer.
                decor.post(() -> {
                    ViewTreeObserver live = decor.getViewTreeObserver();
                    if (live.isAlive()) {
                        live.removeOnDrawListener(self);
                    }
                    once.run();
                });
            }
        });
        sMain.postDelayed(once, FIRST_FRAME_FALLBACK_MS);
    }

    /** Runs {@code task} on the main thread when its queue is idle, or after the idle fallback. */
    private static void mainIdleNow(@NonNull Runnable task) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            sMain.post(() -> mainIdleNow(task));
            return;
        }
        AtomicBoolean ran = new AtomicBoolean();
        Runnable once = () -> {
            if (ran.compareAndSet(false, true)) {
                task.run();
            }
        };
        Looper.myQueue().addIdleHandler(new MessageQueue.IdleHandler() {
            @Override
            public boolean queueIdle() {
                once.run();
                return false;
            }
        });
        sMain.postDelayed(once, IDLE_FALLBACK_MS);
    }

    private static Runnable guarded(String name, Runnable task, boolean lowPriority) {
        return () -> {
            if (lowPriority) {
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            }
            try {
                task.run();
            } catch (Throwable e) {
                Log.w(TAG, "deferred " + name + " failed: " + e);
            }
        };
    }

    private static Executor worker() {
        Executor worker = sWorker;
        if (worker == null) {
            synchronized (StartupDeferral.class) {
                worker = sWorker;
                if (worker == null) {
                    // Core size 0: the thread exists only while there is work, then goes away.
                    ThreadPoolExecutor pool = new ThreadPoolExecutor(0, 1, 10, TimeUnit.SECONDS,
                            new LinkedBlockingQueue<>(), runnable -> {
                        Thread thread = new Thread(runnable, "StartupDeferred");
                        thread.setDaemon(true);
                        return thread;
                    });
                    sWorker = worker = pool;
                }
            }
        }
        return worker;
    }

    @VisibleForTesting
    static void setWorkerForTest(@Nullable Executor worker) {
        sWorker = worker;
    }
}
