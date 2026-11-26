package com.newtube.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Launcher-journey profile: cold start to Home, a scroll, the Greeting overlay (MeeTube), MeeTune,
 * its list and its own Greeting overlay. The checked-in mobile/src/main/baseline-prof.txt carries
 * wildcard rules for these screens until a measured profile from this generator replaces them.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class HomeBaselineProfileGenerator {
    @get:Rule val profiles = BaselineProfileRule()
    private var targetValidated = false

    @Before fun validateTarget() {
        HomeWorkload.checkTarget()
        targetValidated = true
    }
    @After fun stopApp() {
        if (targetValidated) HomeWorkload.stop()
    }

    @Test fun homeMeeTuneAndGreetingProfile() {
        profiles.collect(
            packageName = HomeWorkload.packageName,
            maxIterations = 5,
            stableIterations = 2,
            strictStability = false, // network-fed screens never replay identically
            outputFilePrefix = "newtube-home",
            includeInStartupProfile = true,
            filterPredicate = FixtureWorkload::appRule,
        ) {
            pressHome()
            HomeWorkload.startHome(this)
            HomeWorkload.scrollHome(flings = 4)
            HomeWorkload.openGreeting()
            HomeWorkload.closeGreeting()
            HomeWorkload.openMeeTune()
            HomeWorkload.scrollMeeTune()
            device.pressBack() // back to Home
            device.waitForIdle()
        }
    }
}
