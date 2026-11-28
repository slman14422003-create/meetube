package com.newtube.benchmark

import android.content.pm.ApplicationInfo
import android.os.Build
import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMacrobenchmarkApi
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

/** The real launcher journey: Home feed, the Greeting overlay (brand long press). */
internal object HomeWorkload {
    private const val FEED_TIMEOUT_MS = 30_000L
    private const val UI_TIMEOUT_MS = 5_000L

    val arguments get() = InstrumentationRegistry.getArguments()
    val packageName: String get() = FixtureWorkload.packageName
    val device: UiDevice get() = FixtureWorkload.device

    /** A view by id name, whatever the application id suffix of the installed target is. */
    fun id(name: String): BySelector =
        By.pkg(packageName).res(Pattern.compile(".*:id/$name"))

    fun iterations(default: Int = 5): Int {
        val iterations = (arguments.getString("iterations") ?: default.toString()).toInt()
        require(iterations in 1..30)
        return iterations
    }

    @OptIn(ExperimentalMacrobenchmarkApi::class) // CompilationMode.Ignore preserves existing ART state.
    fun compilationMode(): CompilationMode = when (arguments.getString("compilation", "keep")) {
        "keep" -> CompilationMode.Ignore()
        "none" -> CompilationMode.None()
        "baseline" -> CompilationMode.Partial(BaselineProfileMode.Require)
        else -> error("Unknown compilation mode")
    }

    /** Like the fixture's check, minus the offline switch: Home needs its real feed. */
    fun checkTarget() {
        check(Build.VERSION.SDK_INT >= 34) {
            "API 34+ is required so compilation resets cannot reinstall the user's app"
        }
        check(packageName == "io.github.aleixrodriala.arc"
            || packageName == "io.github.aleixrodriala.arc.debug") { "Unexpected target $packageName" }
        val info = InstrumentationRegistry.getInstrumentation().context.packageManager
            .getApplicationInfo(packageName, 0)
        check(info.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) {
            "Install the non-debuggable benchmark variant first"
        }
        check(info.isProfileableByShell) { "The target must be the profileable benchmark variant" }
        check(device.isScreenOn) { "Unlock and wake the test phone before benchmarking" }
        check(!device.executeShellCommand("dumpsys window").contains("mDreamingLockscreen=true")) {
            "The test phone is locked"
        }
        // Home asks for this on a cold start; the system dialog would cover the feed being measured.
        device.executeShellCommand("pm grant $packageName android.permission.POST_NOTIFICATIONS")
    }

    /** Launch from the launcher and wait until Home shows cards (needs a network or a feed snapshot). */
    fun startHome(scope: MacrobenchmarkScope) {
        scope.startActivityAndWait()
        waitForFeed()
    }

    fun waitForFeed() {
        check(device.wait(Until.hasObject(id("mobile_content_grid")), FEED_TIMEOUT_MS)) {
            "Home did not show its grid"
        }
        val deadline = System.currentTimeMillis() + FEED_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val grid = device.findObject(id("mobile_content_grid"))
            if (grid != null && grid.childCount >= 3) {
                device.waitForIdle()
                return
            }
            Thread.sleep(250)
        }
        error("Home showed no cards in ${FEED_TIMEOUT_MS / 1000} s: check the network or sign-in")
    }

    /** Scroll the Home grid down and back up; the object is found again per fling (rows are recycled). */
    fun scrollHome(flings: Int = 6) {
        repeat(flings) { fling(Direction.DOWN) }
        repeat(flings / 2) { fling(Direction.UP) }
    }

    private fun fling(direction: Direction) {
        val grid = device.findObject(id("mobile_content_grid")) ?: return
        grid.setGestureMargin(device.displayWidth / 5) // stay off the system gesture edges
        grid.fling(direction)
        device.waitForIdle()
    }

    /** Hold the brand pill: the Greeting overlay frosts the screen in. */
    fun openGreeting() {
        val brand = device.wait(Until.findObject(id("mobile_brand_glass")), UI_TIMEOUT_MS)
            ?: error("The brand pill is not on screen")
        brand.longClick()
        check(device.wait(Until.hasObject(id("mobile_greet_content")), UI_TIMEOUT_MS)) {
            "The Greeting overlay did not open"
        }
        device.waitForIdle()
    }

    fun closeGreeting() {
        device.pressBack()
        device.wait(Until.gone(id("mobile_greet_content")), UI_TIMEOUT_MS)
        device.waitForIdle()
    }

    fun stop() {
        device.executeShellCommand("am force-stop $packageName")
    }
}
