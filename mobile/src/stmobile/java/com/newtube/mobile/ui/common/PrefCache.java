package com.newtube.mobile.ui.common;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MEETUBE(perf): read-through cache over SharedPreferences, so a hot path (onDraw, onBindViewHolder,
 * onMeasure, a scroll listener, a tick runnable) costs one ConcurrentHashMap lookup instead of a
 * Context.getSharedPreferences call plus the preferences' own lock on every read.
 *
 * <p>Values are cached as stored (never with the caller's default applied), keyed by file + key.
 * Each file gets one {@link SharedPreferences.OnSharedPreferenceChangeListener}, held by a static
 * strong reference (SharedPreferences keeps its listeners weakly), that drops the changed key, or
 * every key of the file when the whole file was cleared.</p>
 *
 * <p>A read that races an invalidation is never cached: a read samples the file's generation
 * before it touches the preferences and stores its result only if the generation is unchanged,
 * checked under the same lock the listener takes to bump it.</p>
 */
public final class PrefCache {
    private static final int BOOLEAN = 0;
    private static final int INT = 1;
    private static final int LONG = 2;
    private static final int STRING = 3;
    private static final int ANY = 4;

    /** The key is not in the file. */
    private static final Object ABSENT = new Object();
    /** The key is in the file, its value not read yet (only {@link #has} saw it). */
    private static final Object PRESENT = new Object();

    private static final ConcurrentHashMap<String, Object> VALUES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, FileState> FILES = new ConcurrentHashMap<>();

    private PrefCache() {
    }

    public static boolean getBoolean(@NonNull Context context, @NonNull String file, @NonNull String key, boolean def) {
        FileState state = state(context, file);
        Object value = lookup(state, key, BOOLEAN);
        if (value == ABSENT) {
            return def;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return state.prefs.getBoolean(key, def); // another type stored: fails as a direct read would
    }

    public static int getInt(@NonNull Context context, @NonNull String file, @NonNull String key, int def) {
        FileState state = state(context, file);
        Object value = lookup(state, key, INT);
        if (value == ABSENT) {
            return def;
        }
        if (value instanceof Integer) {
            return (Integer) value;
        }
        return state.prefs.getInt(key, def);
    }

    public static long getLong(@NonNull Context context, @NonNull String file, @NonNull String key, long def) {
        FileState state = state(context, file);
        Object value = lookup(state, key, LONG);
        if (value == ABSENT) {
            return def;
        }
        if (value instanceof Long) {
            return (Long) value;
        }
        return state.prefs.getLong(key, def);
    }

    @Nullable
    public static String getString(@NonNull Context context, @NonNull String file, @NonNull String key, @Nullable String def) {
        FileState state = state(context, file);
        Object value = lookup(state, key, STRING);
        if (value == ABSENT) {
            return def;
        }
        if (value instanceof String) {
            return (String) value;
        }
        return state.prefs.getString(key, def);
    }

    public static boolean has(@NonNull Context context, @NonNull String file, @NonNull String key) {
        FileState state = state(context, file);
        return lookup(state, key, ANY) != ABSENT;
    }

    /** Drops everything cached for {@code file}, for a writer that does not notify listeners. */
    public static void invalidate(@NonNull String file) {
        FileState state = FILES.get(file);
        if (state != null) {
            state.invalidateAll();
        }
    }

    private static Object lookup(FileState state, String key, int type) {
        String cacheKey = state.prefix + key;
        Object cached = VALUES.get(cacheKey);
        if (cached != null && (cached != PRESENT || type == ANY)) {
            return cached;
        }
        int generation = state.generation;
        SharedPreferences prefs = state.prefs;
        Object read;
        if (!prefs.contains(key)) {
            read = ABSENT;
        } else {
            switch (type) {
                case BOOLEAN:
                    read = prefs.getBoolean(key, false);
                    break;
                case INT:
                    read = prefs.getInt(key, 0);
                    break;
                case LONG:
                    read = prefs.getLong(key, 0L);
                    break;
                case STRING:
                    read = prefs.getString(key, null);
                    break;
                default:
                    read = PRESENT;
                    break;
            }
            if (read == null) { // a stored null is the same as no value
                read = ABSENT;
            }
        }
        synchronized (state) {
            if (state.generation == generation && state.live) {
                VALUES.put(cacheKey, read);
            }
        }
        return read;
    }

    private static FileState state(Context context, String file) {
        Context app = context.getApplicationContext();
        if (app == null) {
            app = context;
        }
        FileState state = FILES.get(file);
        if (state != null && state.app == app) {
            return state;
        }
        return bind(app, file);
    }

    /** First use of a file, or its Application changed (a test's fresh one): a new listener. */
    private static FileState bind(Context app, String file) {
        synchronized (FILES) {
            FileState old = FILES.get(file);
            if (old != null && old.app == app) {
                return old;
            }
            if (old != null) {
                old.retire();
            }
            FileState fresh = new FileState(app, file, app.getSharedPreferences(file, Context.MODE_PRIVATE));
            fresh.invalidateAll();
            fresh.prefs.registerOnSharedPreferenceChangeListener(fresh);
            FILES.put(file, fresh);
            return fresh;
        }
    }

    private static final class FileState implements SharedPreferences.OnSharedPreferenceChangeListener {
        final Context app;
        final String prefix;
        final SharedPreferences prefs;
        /** Bumped, under this object's lock, by every change; read without it. */
        volatile int generation;
        /** False once a newer Application took the file over. */
        boolean live = true;

        FileState(Context app, String file, SharedPreferences prefs) {
            this.app = app;
            this.prefix = file + '\u0000';
            this.prefs = prefs;
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences changed, @Nullable String key) {
            synchronized (this) {
                generation++;
                if (key == null) {
                    dropAll();
                } else {
                    VALUES.remove(prefix + key);
                }
            }
        }

        void invalidateAll() {
            synchronized (this) {
                generation++;
                dropAll();
            }
        }

        void retire() {
            synchronized (this) {
                live = false;
                generation++;
                dropAll();
            }
            prefs.unregisterOnSharedPreferenceChangeListener(this);
        }

        private void dropAll() {
            for (Iterator<String> it = VALUES.keySet().iterator(); it.hasNext(); ) {
                if (it.next().startsWith(prefix)) {
                    it.remove();
                }
            }
        }
    }
}
