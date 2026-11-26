package com.newtube.mobile.ui.common;

import android.view.View;

/** MEETUBE(glass): wires a {@link GlassView} to the {@link GlassSource} it frosts. */
public final class Glass {
    private static final String PREFS = "meetube_app";
    private static final String KEY_LIQUID = "liquid_glass";
    private static final String KEY_PLAYER_FROST = "player_frost"; // before Player buttons: read once, as its default
    private static final String KEY_PLAYER_BUTTONS = "player_buttons";

    private static final String KEY_LIVE_BLUR = "live_blur"; // before Glass quality: false carries over as Flat
    private static final String KEY_QUALITY = "glass_quality";
    /** Lens with seven-band dispersion, live blur: the full look. */
    public static final int QUALITY_FULL = 0;
    /** Live blur and a three-tap lens on small glass only. */
    public static final int QUALITY_LIGHT = 1;
    /** No sampling: every glass is a plain tint. */
    public static final int QUALITY_FLAT = 2;
    private static volatile int sQuality = QUALITY_FULL;
    private static volatile int sEpoch;

    private Glass() {
    }

    /** Loads the Glass quality once at start-up, so drawing never reads preferences. */
    public static void init(android.content.Context context) {
        sQuality = quality(context);
        sEpoch++;
    }

    /** Changes whenever the cached quality does, so cached recordings know to rebuild. */
    static int epoch() {
        return sEpoch;
    }

    /** The saved Glass quality; untouched, Light on a low-end phone (NavLens#lowEnd) and Full otherwise. */
    public static int quality(android.content.Context context) {
        if (PrefCache.has(context, PREFS, KEY_QUALITY)) {
            return PrefCache.getInt(context, PREFS, KEY_QUALITY, QUALITY_FULL);
        }
        if (!PrefCache.getBoolean(context, PREFS, KEY_LIVE_BLUR, true)) {
            return QUALITY_FLAT;
        }
        return NavLens.lowEnd(context) ? QUALITY_LIGHT : QUALITY_FULL;
    }

    public static void setQuality(android.content.Context context, int quality) {
        sQuality = quality;
        sEpoch++;
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit().putInt(KEY_QUALITY, quality).apply();
    }

    /** The quality in force right now (cached). */
    public static int quality() {
        return sQuality;
    }

    /** True when glass may sample and blur what is under it: not Flat, and Battery saving is off. */
    public static boolean liveBlur() {
        return sQuality != QUALITY_FLAT && !BatterySaver.lite();
    }

    /** The Liquid glass setting: off, every glass is a plain frosted blur (no lens, light or grain). */
    public static boolean isLiquid(android.content.Context context) {
        return !BatterySaver.lite() // MEETUBE(issue #93)
                && PrefCache.getBoolean(context, PREFS, KEY_LIQUID, true);
    }

    public static void setLiquid(android.content.Context context, boolean on) {
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit().putBoolean(KEY_LIQUID, on).apply();
    }

    /** The Player buttons setting's three looks for the controls over a video (and a short's). */
    public static final int BUTTONS_SEE_THROUGH = 0;
    /** Every disc re-blurs the video under it each frame. */
    public static final int BUTTONS_FROSTED = 1;
    /** MEETUBE(minimal buttons): no discs, the bare icons with a soft shadow, like YouTube. */
    public static final int BUTTONS_MINIMAL = 2;

    /** See-through tinted discs by default; the old Frosted player buttons switch carries over. */
    public static int playerButtons(android.content.Context context) {
        int style = PrefCache.getInt(context, PREFS, KEY_PLAYER_BUTTONS,
                PrefCache.getBoolean(context, PREFS, KEY_PLAYER_FROST, false) ? BUTTONS_FROSTED : BUTTONS_SEE_THROUGH);
        return style == BUTTONS_FROSTED && !liveBlur() ? BUTTONS_SEE_THROUGH : style; // MEETUBE(issue #93)
    }

    public static void setPlayerButtons(android.content.Context context, int style) {
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit().putInt(KEY_PLAYER_BUTTONS, style).apply();
    }

    /** MEETUBE(glass segmented): Home's topic chips and the search filters, as separate pills or one glass segment. */
    public static final int CHIPS_PILLS = 0;
    public static final int CHIPS_SEGMENT = 1;

    public static int chipStyle(android.content.Context context) {
        // MEETUBE(youtube mode): the track, drawn as YouTube's text tabs, until the user picks a style
        // (issue #287: the pick used to snap back to Segment bubble while YouTube mode was on).
        return PrefCache.getInt(context, PREFS, "chip_style", CHIPS_SEGMENT);
    }

    public static void setChipStyle(android.content.Context context, int style) {
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit().putInt("chip_style", style).apply();
    }

    public static boolean isPlayerFrost(android.content.Context context) {
        return playerButtons(context) == BUTTONS_FROSTED;
    }

    /** A top bar's frost tint: the page colour (black, or white in the light theme) at 70%, never washed out. */
    public static int barTint(android.content.Context context) {
        return (androidx.core.content.ContextCompat.getColor(context,
                com.liskovsoft.smartyoutubetv2.tv.R.color.mobile_color_background) & 0xFFFFFF) | 0xB3000000;
    }

    public static void blur(View glass, View source) {
        if (glass instanceof GlassView && source instanceof GlassSource) {
            ((GlassView) glass).setSource((GlassSource) source);
        }
    }
}
