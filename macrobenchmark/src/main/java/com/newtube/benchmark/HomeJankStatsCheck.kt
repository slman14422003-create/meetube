package com.newtube.benchmark

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * JankStats check. The benchmark and debug builds carry JankProbe (mobile/src/jank), which tracks a
 * screen's frames with androidx.metrics JankStats and logs one JankStatsSummary line when the screen
 * pauses. This test cold-starts the app, scrolls Home, opens and closes the Greeting overlay, leaves
 * the app, and reads that line back. It always requires frames to have been tracked; pass
 * -e maxJankyPercent N to also fail when more than N percent of Home's frames were janky.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class HomeJankStatsCheck {
    private var targetValidated = false

    @Before fun validateTarget() {
        HomeWorkload.checkTarget()
        targetValidated = true
    }
    @After fun stopApp() {
        if (targetValidated) HomeWorkload.stop()
    }

    @Test fun homeScrollAndGreetingStayWithinJankBudget() {
        val device = HomeWorkload.device
        val context = InstrumentationRegistry.getInstrumentation().context
        HomeWorkload.stop()
        device.executeShellCommand("logcat -c")
        val launch = checkNotNull(context.packageManager.getLaunchIntentForPackage(HomeWorkload.packageName)) {
            "No launcher activity for ${HomeWorkload.packageName}"
        }.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(launch)
        HomeWorkload.waitForFeed()

        HomeWorkload.scrollHome()
        repeat(3) {
            HomeWorkload.openGreeting()
            HomeWorkload.closeGreeting()
        }
        device.pressHome() // pauses Home: JankProbe writes its summary
        Thread.sleep(1_000)

        val summary = device.executeShellCommand("logcat -d -s JankStatsSummary:I")
            .lineSequence().lastOrNull { it.contains("screen=Browse") || it.contains("screen=Home") }
        checkNotNull(summary) {
            "No JankStatsSummary line: install the benchmark or debug build (release has no JankProbe)"
        }
        Log.i("HomeJankStatsCheck", summary)
        println(summary)

        val frames = field(summary, "frames")
        val janky = field(summary, "janky")
        assertTrue("JankStats tracked no frames: $summary", frames > 0)
        val maxPercent = HomeWorkload.arguments.getString("maxJankyPercent")?.toDouble()
        if (maxPercent != null) {
            val percent = 100.0 * janky / frames
            assertTrue("Home jank $percent% over the $maxPercent% budget: $summary", percent <= maxPercent)
        }
    }

    private fun field(line: String, name: String): Double =
        Regex("""\b$name=([0-9.]+)""").find(line)?.groupValues?.get(1)?.toDouble()
            ?: error("No $name in: $line")
}
