package com.newtube.mobile.crash;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * MEETUBE(crash): shown in place of the app closing. Runs in its own process (":crash") and uses
 * only framework views, so it works even when the app's own start-up is what failed.
 */
public final class CrashActivity extends Activity {
    private String mReport = "";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        String extra = getIntent().getStringExtra(CrashHandler.EXTRA_REPORT);
        mReport = CrashHandler.versionLine(this) + "\n" + (extra != null ? extra : "(no report)");
        float dp = getResources().getDisplayMetrics().density;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0B0B0F"));
        root.setPadding((int) (16 * dp), (int) (40 * dp), (int) (16 * dp), (int) (16 * dp));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);

        TextView title = new TextView(this);
        title.setText("MeeTube crashed");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView hint = new TextView(this);
        hint.setText("Copy or share the report below so the cause can be fixed.");
        hint.setTextColor(Color.parseColor("#B0B0B8"));
        hint.setTextSize(13);
        hint.setPadding(0, (int) (4 * dp), 0, (int) (12 * dp));
        root.addView(hint);

        ScrollView scroll = new ScrollView(this);
        GradientDrawable box = new GradientDrawable();
        box.setColor(Color.parseColor("#16161C"));
        box.setCornerRadius(12 * dp);
        scroll.setBackground(box);
        scroll.setPadding((int) (12 * dp), (int) (12 * dp), (int) (12 * dp), (int) (12 * dp));
        TextView trace = new TextView(this);
        trace.setText(mReport);
        trace.setTextColor(Color.parseColor("#FF8A80"));
        trace.setTextSize(11);
        trace.setTypeface(Typeface.MONOSPACE);
        trace.setTextIsSelectable(true);
        trace.setTextDirection(View.TEXT_DIRECTION_LTR);
        scroll.addView(trace);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER);
        buttons.setPadding(0, (int) (12 * dp), 0, 0);
        buttons.addView(button("Copy", v -> copy()), weight());
        buttons.addView(button("Share", v -> share()), weight());
        buttons.addView(button("Restart", v -> restart()), weight());
        root.addView(buttons);

        setContentView(root);
    }

    private Button button(String label, View.OnClickListener click) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setOnClickListener(click);
        return b;
    }

    private LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        p.setMargins(4, 0, 4, 0);
        return p;
    }

    private void copy() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("MeeTube crash", mReport));
            Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show();
        }
    }

    private void share() {
        Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, "MeeTube crash report")
                .putExtra(Intent.EXTRA_TEXT, mReport);
        startActivity(Intent.createChooser(send, "Share crash report"));
    }

    private void restart() {
        Intent launch = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(launch);
        }
        finishAndRemoveTask();
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    @Override
    public void onBackPressed() {
        restart();
    }
}
