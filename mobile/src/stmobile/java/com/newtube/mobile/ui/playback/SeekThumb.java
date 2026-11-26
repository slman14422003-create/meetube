package com.newtube.mobile.ui.playback;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.newtube.mobile.ui.common.LocalizedPickerActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * MEETUBE(seek thumb): the player seek bar's dot can be a picture the user picks (a GIF or an
 * animated WebP moves). The picked file is copied to the app's files dir; no file = the dot.
 * Also the invisible host for the system image picker, like {@code DownloadFolderActivity}.
 */
public final class SeekThumb extends LocalizedPickerActivity {
    private static final int REQUEST_IMAGE = 1;
    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final int DECODE_PX = 192;

    public static final int DOT = 0;
    public static final int PICTURE = 1;

    private static File file(Context context) {
        return new File(context.getFilesDir(), "seek_thumb");
    }

    public static int type(Context context) {
        return file(context).exists() ? PICTURE : DOT;
    }

    public static void setType(Context context, int type) {
        if (type == PICTURE) {
            context.startActivity(new Intent(context, SeekThumb.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } else {
            //noinspection ResultOfMethodCallIgnored
            file(context).delete();
            CACHE.evictAll();
        }
    }

    /** Receives the picture on the main thread; never called after {@link Request#cancel}. */
    interface Callback {
        void onLoaded(@Nullable Drawable drawable);
    }

    /** A load in flight; cancel it when the view that asked goes away. */
    static final class Request {
        private volatile boolean mCancelled;
        @Nullable private volatile Future<?> mFuture;

        void cancel() {
            mCancelled = true;
            Future<?> future = mFuture;
            if (future != null) {
                future.cancel(false);
            }
        }

        boolean isCancelled() {
            return mCancelled;
        }
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService DECODER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "seek-thumb");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });

    /** Decoded still pictures by file, path and stamp; sized by their bytes, not their count. */
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(cacheBytes()) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return Math.max(1, value.getAllocationByteCount());
        }
    };

    /** A mutable bitmap no view ever got (a stale or cancelled decode's), kept for the next decode's inBitmap. */
    @Nullable private static Bitmap sSpare;

    private static int cacheBytes() {
        long max = Runtime.getRuntime().maxMemory();
        return (int) Math.max(512 * 1024, Math.min(4L * 1024 * 1024, max / 64));
    }

    /**
     * Decodes the picked picture off the main thread. A new drawable per request (an animated one
     * keeps a single callback, so one per bar); a still one shares its cached bitmap. Returns
     * null, delivering nothing, when no picture is set. A request cancelled, or whose file was
     * replaced while it decoded, delivers nothing (or null) and never a stale picture.
     */
    @Nullable
    static Request loadAsync(@NonNull Context context, @NonNull Callback callback) {
        Context app = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        File f = file(app);
        if (!f.exists()) {
            return null;
        }
        Request request = new Request();
        request.mFuture = DECODER.submit(() -> {
            Drawable d = decode(app, f, request);
            MAIN.post(() -> {
                if (request.isCancelled()) {
                    return;
                }
                if (d instanceof Animatable) {
                    ((Animatable) d).start();
                }
                callback.onLoaded(d);
            });
        });
        return request;
    }

    private static String stamp(File f) {
        return f.getPath() + '|' + f.lastModified() + '|' + f.length();
    }

    /** Worker thread only. Null for a broken file, a cancelled request or a file replaced meanwhile. */
    @Nullable
    private static Drawable decode(Context context, File f, @Nullable Request request) {
        String key = stamp(f);
        Bitmap cached = CACHE.get(key);
        if (cached != null) {
            return new BitmapDrawable(context.getResources(), cached);
        }
        try {
            Drawable d;
            Bitmap fresh = null; // a bitmap only this decode has seen
            if (Build.VERSION.SDK_INT >= 28) {
                d = ImageDecoder.decodeDrawable(ImageDecoder.createSource(f), (decoder, info, source) -> {
                    int max = Math.max(info.getSize().getWidth(), info.getSize().getHeight());
                    if (max > DECODE_PX) {
                        decoder.setTargetSampleSize(Math.max(1, max / DECODE_PX));
                    }
                });
            } else {
                fresh = decodeBitmap(f, request);
                d = fresh != null ? new BitmapDrawable(context.getResources(), fresh) : null;
            }
            if ((request != null && request.isCancelled()) || !key.equals(stamp(f))) {
                offerSpare(fresh); // nobody got it
                return null;
            }
            if (d instanceof BitmapDrawable && ((BitmapDrawable) d).getBitmap() != null) {
                CACHE.put(key, ((BitmapDrawable) d).getBitmap());
            }
            return d;
        } catch (Exception | OutOfMemoryError e) {
            return null; // a broken file: the dot
        }
    }

    /** BitmapFactory path (before API 28): sampled down, into a spare bitmap when one fits. */
    @Nullable
    private static Bitmap decodeBitmap(File f, @Nullable Request request) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        if (o.outWidth <= 0 || o.outHeight <= 0 || (request != null && request.isCancelled())) {
            return null;
        }
        int sample = Integer.highestOneBit(Math.max(1, Math.max(o.outWidth, o.outHeight) / DECODE_PX));
        o.inSampleSize = sample;
        o.inJustDecodeBounds = false;
        o.inMutable = true;
        o.inPreferredConfig = Bitmap.Config.ARGB_8888;
        long needed = 4L * ((o.outWidth + sample - 1) / sample) * ((o.outHeight + sample - 1) / sample);
        o.inBitmap = takeSpare(needed);
        try {
            return BitmapFactory.decodeFile(f.getPath(), o);
        } catch (IllegalArgumentException e) { // the spare did not suit this file
            o.inBitmap = null;
            return BitmapFactory.decodeFile(f.getPath(), o);
        }
    }

    @Nullable
    private static synchronized Bitmap takeSpare(long neededBytes) {
        Bitmap spare = sSpare;
        sSpare = null;
        if (spare != null && !spare.isRecycled() && spare.isMutable() && spare.getAllocationByteCount() >= neededBytes) {
            return spare;
        }
        return null;
    }

    private static synchronized void offerSpare(@Nullable Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled() && bitmap.isMutable()) {
            sSpare = bitmap;
        }
    }

    /** Worker thread only: copies the pick in and checks it decodes; the old picture stays on a failure. */
    private static boolean install(Context app, Uri uri) {
        File tmp = new File(app.getFilesDir(), "seek_thumb.tmp");
        long total = 0;
        try (InputStream in = app.getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(tmp)) {
            byte[] buf = new byte[16 * 1024];
            int n;
            while (in != null && (n = in.read(buf)) > 0 && (total += n) <= MAX_BYTES) {
                out.write(buf, 0, n);
            }
        } catch (Exception e) {
            total = -1;
        }
        // A too big or unreadable pick leaves the previous picture (or the dot) as it was.
        File old = new File(app.getFilesDir(), "seek_thumb.old");
        boolean hadOld = file(app).renameTo(old);
        boolean ok = total > 0 && total <= MAX_BYTES && tmp.renameTo(file(app)) && decode(app, file(app), null) != null;
        if (ok) {
            //noinspection ResultOfMethodCallIgnored
            old.delete();
        } else {
            //noinspection ResultOfMethodCallIgnored
            file(app).delete();
            if (hadOld) {
                //noinspection ResultOfMethodCallIgnored
                old.renameTo(file(app));
            }
        }
        //noinspection ResultOfMethodCallIgnored
        tmp.delete();
        CACHE.evictAll();
        return ok;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            return; // the picker is already up
        }
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("image/*");
        try {
            startActivityForResult(pick, REQUEST_IMAGE);
        } catch (ActivityNotFoundException e) {
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Uri uri = resultCode == RESULT_OK && data != null ? data.getData() : null;
        if (uri == null) {
            finish();
            return;
        }
        Context app = getApplicationContext();
        DECODER.execute(() -> {
            boolean ok = install(app, uri);
            MAIN.post(() -> {
                if (!ok && !isDestroyed()) {
                    com.newtube.mobile.ui.common.MobileSnackbar.show(this,
                            com.liskovsoft.smartyoutubetv2.tv.R.string.mobile_settings_seek_thumb_failed);
                }
                finish();
            });
        });
    }
}
