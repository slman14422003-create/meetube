package com.newtube.mobile.crash;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.os.Build;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Locale;

/**
 * MEETUBE(crash): instead of the app just closing, an uncaught exception is written to a file and
 * {@link CrashActivity} (its own process) shows it with Copy, Share and Restart.
 */
public final class CrashHandler implements Thread.UncaughtExceptionHandler {
    static final String FILE = "last_crash.txt";
    static final String EXTRA_REPORT = "report";

    private final Application mApp;
    private final Thread.UncaughtExceptionHandler mPrevious;

    private CrashHandler(Application app, Thread.UncaughtExceptionHandler previous) {
        mApp = app;
        mPrevious = previous;
    }

    /** Safe to call more than once: it wraps whatever handler is installed now, unless that is already this one. */
    public static void install(Application app) {
        Thread.UncaughtExceptionHandler current = Thread.getDefaultUncaughtExceptionHandler();
        if (current instanceof CrashHandler) {
            return;
        }
        Thread.setDefaultUncaughtExceptionHandler(new CrashHandler(app, current));
    }

    @Override
    public void uncaughtException(Thread thread, Throwable error) {
        try {
            String report = build(thread, error);
            write(mApp, report);
            Intent intent = new Intent(mApp, CrashActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    .putExtra(EXTRA_REPORT, report.length() > 60_000 ? report.substring(0, 60_000) : report);
            mApp.startActivity(intent);
            // Give the system a moment to start the crash screen before this process goes.
            try {
                Thread.sleep(400);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(10);
        } catch (Throwable t) {
            // The crash screen itself failed: hand over to the usual handling.
            if (mPrevious != null) {
                mPrevious.uncaughtException(thread, error);
            }
        }
    }

    private static String build(Thread thread, Throwable error) {
        StringWriter trace = new StringWriter();
        error.printStackTrace(new PrintWriter(trace));
        StringBuilder out = new StringBuilder();
        out.append("Time: ").append(new Date()).append('\n');
        out.append("Thread: ").append(thread.getName()).append('\n');
        out.append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append(", Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        out.append("Locale: ").append(Locale.getDefault()).append('\n');
        out.append("Memory: ").append(Runtime.getRuntime().totalMemory() / 1048576).append(" MB used of ")
                .append(Runtime.getRuntime().maxMemory() / 1048576).append(" MB\n");
        out.append("\n").append(trace);
        return out.toString();
    }

    static String versionLine(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return context.getPackageName() + " " + info.versionName;
        } catch (Exception e) {
            return context.getPackageName();
        }
    }

    private static void write(Context context, String report) {
        try (FileOutputStream out = new FileOutputStream(new File(context.getFilesDir(), FILE))) {
            out.write((versionLine(context) + "\n" + report).getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }
}
