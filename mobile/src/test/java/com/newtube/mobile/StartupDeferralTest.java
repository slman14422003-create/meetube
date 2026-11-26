package com.newtube.mobile;

import static org.junit.Assert.assertEquals;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.os.Looper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.LooperMode;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/** Deferred launch work waits for the first frame, runs once, and a failing task is dropped. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@LooperMode(LooperMode.Mode.PAUSED)
public class StartupDeferralTest {
    @Before
    public void reset() {
        LaunchMilestones.resetForTest();
        StartupDeferral.setWorkerForTest(Runnable::run); // the "worker" runs inline, so it is observable
    }

    @After
    public void tearDown() {
        StartupDeferral.setWorkerForTest(null);
    }

    @Test
    public void backgroundWorkWaitsForTheFirstFrame() {
        AtomicInteger runs = new AtomicInteger();
        StartupDeferral.background("test", runs::incrementAndGet);

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1_000));
        assertEquals(0, runs.get());

        LaunchMilestones.onFirstFrame("MobileBrowseActivity");
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(StartupDeferral.IDLE_FALLBACK_MS + 100));
        assertEquals(1, runs.get());

        // Neither the first-frame fallback nor the idle fallback may run it again.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(10_000));
        assertEquals(1, runs.get());
    }

    @Test
    public void backgroundWorkStillRunsWhenNothingEverDraws() {
        AtomicInteger runs = new AtomicInteger();
        StartupDeferral.background("test", runs::incrementAndGet);

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(StartupDeferral.FIRST_FRAME_FALLBACK_MS - 100));
        assertEquals(0, runs.get());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(StartupDeferral.IDLE_FALLBACK_MS + 500));
        assertEquals(1, runs.get());
    }

    @Test
    public void mainIdleWorkRunsOnTheMainThread() {
        Thread[] ranOn = new Thread[1];
        StartupDeferral.mainIdle("test", () -> ranOn[0] = Thread.currentThread());

        LaunchMilestones.onFirstFrame("MobileBrowseActivity");
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(StartupDeferral.IDLE_FALLBACK_MS + 100));
        assertEquals(Looper.getMainLooper().getThread(), ranOn[0]);
    }

    @Test
    public void aTaskThatThrowsIsDroppedAndTheNextOneStillRuns() {
        AtomicInteger runs = new AtomicInteger();
        StartupDeferral.background("boom", () -> {
            throw new IllegalStateException("expected by the test");
        });
        StartupDeferral.background("after", runs::incrementAndGet);

        LaunchMilestones.onFirstFrame("MobileBrowseActivity");
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(StartupDeferral.IDLE_FALLBACK_MS + 100));
        assertEquals(1, runs.get());
    }
}
