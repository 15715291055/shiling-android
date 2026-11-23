package com.fuguang.localdiary

import android.Manifest
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fuguang.localdiary.location.LocationHelper
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in hardware probe: -e probeLocation true -e class com.fuguang.localdiary.LocationProbeTest */
@RunWith(AndroidJUnit4::class)
class LocationProbeTest {
    @Test fun foregroundLocationProbe() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("probeLocation") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.ACCESS_COARSE_LOCATION)
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.ACCESS_FINE_LOCATION)
        ActivityScenario.launch(MainActivity::class.java).use {
            val location = runBlocking { LocationHelper.currentLocation(context) }
            assertTrue(location.latitude.isFinite() && location.latitude in -90.0..90.0)
            assertTrue(location.longitude.isFinite() && location.longitude in -180.0..180.0)
            println("Real location acquired; provider=${location.provider}; accuracyMeters=${location.accuracy}")
        }
    }
}
