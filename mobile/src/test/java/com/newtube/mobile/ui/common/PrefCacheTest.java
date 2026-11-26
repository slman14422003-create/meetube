package com.newtube.mobile.ui.common;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class PrefCacheTest {
    private static final String FILE = "prefcache_test";
    private Context context;
    private SharedPreferences prefs;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        PrefCache.invalidate(FILE);
    }

    @Test
    public void absentKeyGivesTheCallersDefault() {
        assertTrue(PrefCache.getBoolean(context, FILE, "flag", true));
        assertEquals(7, PrefCache.getInt(context, FILE, "count", 7));
        assertEquals(9L, PrefCache.getLong(context, FILE, "big", 9L));
        assertNull(PrefCache.getString(context, FILE, "text", null));
        assertFalse(PrefCache.has(context, FILE, "flag"));
    }

    @Test
    public void aChangeIsSeenAfterItWasCached() {
        prefs.edit().putBoolean("flag", true).putInt("count", 3).putString("text", "a").putLong("big", 5L).commit();
        assertTrue(PrefCache.getBoolean(context, FILE, "flag", false));
        assertEquals(3, PrefCache.getInt(context, FILE, "count", 0));
        assertEquals("a", PrefCache.getString(context, FILE, "text", null));
        assertEquals(5L, PrefCache.getLong(context, FILE, "big", 0L));
        prefs.edit().putBoolean("flag", false).putInt("count", 4).putString("text", "b").putLong("big", 6L).commit();
        assertFalse(PrefCache.getBoolean(context, FILE, "flag", true));
        assertEquals(4, PrefCache.getInt(context, FILE, "count", 0));
        assertEquals("b", PrefCache.getString(context, FILE, "text", null));
        assertEquals(6L, PrefCache.getLong(context, FILE, "big", 0L));
    }

    @Test
    public void aRemovedKeyFallsBackToTheDefault() {
        prefs.edit().putInt("count", 3).commit();
        assertEquals(3, PrefCache.getInt(context, FILE, "count", 0));
        assertTrue(PrefCache.has(context, FILE, "count"));
        prefs.edit().remove("count").commit();
        assertEquals(11, PrefCache.getInt(context, FILE, "count", 11));
        assertFalse(PrefCache.has(context, FILE, "count"));
    }

    @Test
    public void invalidateDropsWhatAClearLeftBehind() {
        prefs.edit().putBoolean("flag", true).commit();
        assertTrue(PrefCache.getBoolean(context, FILE, "flag", false));
        prefs.edit().clear().commit(); // before API 30 a clear tells no listener
        PrefCache.invalidate(FILE);
        assertFalse(PrefCache.getBoolean(context, FILE, "flag", false));
    }

    @Test
    public void hasThenTypedReadReturnsTheStoredValue() {
        prefs.edit().putInt("count", 8).commit();
        assertTrue(PrefCache.has(context, FILE, "count"));
        assertEquals(8, PrefCache.getInt(context, FILE, "count", 0));
    }
}
