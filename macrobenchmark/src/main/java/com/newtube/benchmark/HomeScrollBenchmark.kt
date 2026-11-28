package com.newtube.benchmark

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Frame timing of the Home feed scrolling and of the Greeting overlay opening, on the real launcher
 * journey. FrameTimingMetric's output JSON carries frameDurationCpuMs and frameOverrunMs with
 * P50/P90/P95/P99; tools/frame-percentiles.py prints them, and a before/after pair side by side.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class HomeScrollBenchmark {
    @get:Rule val benchmark = MacrobenchmarkRule()
    private var targetValidated = false

    @Before fun validateTarget() {
        HomeWorkload.checkTarget()
        targetValidated = true
    }
    @After fun stopApp() {
        if (targetValidated) HomeWorkload.stop()
    }

    @Test fun scrollHome() {
        benchmark.measureRepeated(
            packageName = HomeWorkload.packageName,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = HomeWorkload.compilationMode(),
            startupMode = StartupMode.COLD,
            iterations = HomeWorkload.iterations(),
            setupBlock = {
                pressHome()
                HomeWorkload.startHome(this)
            },
            measureBlock = { HomeWorkload.scrollHome() },
        )
    }

    @Test fun openGreetingOverlay() {
        benchmark.measureRepeated(
            packageName = HomeWorkload.packageName,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = HomeWorkload.compilationMode(),
            startupMode = StartupMode.COLD,
            iterations = HomeWorkload.iterations(),
            setupBlock = {
                pressHome()
                HomeWorkload.startHome(this)
            },
            measureBlock = {
                // The animation is ~300 ms: open and close it a few times per iteration so the
                // percentiles rest on enough frames.
                repeat(5) {
                    HomeWorkload.openGreeting()
                    HomeWorkload.closeGreeting()
                }
            },
        )
    }
}
