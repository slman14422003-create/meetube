package com.newtube.mobile.ui.common;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;

public class BlinkView extends AppCompatTextView {
    private static final long PERIOD_MS = 1060;

    @Nullable private ObjectAnimator mBlink;

    public BlinkView(Context context) {
        this(context, null);
    }

    public BlinkView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        sync();
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        sync();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        sync();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (mBlink != null) {
            mBlink.cancel();
            mBlink = null;
        }
        setAlpha(1f);
        super.onDetachedFromWindow();
    }

    private void sync() {
        boolean run = AnimGate.shouldRun(this);
        if (run && mBlink == null) {
            mBlink = ObjectAnimator.ofFloat(this, ALPHA, 1f, 0f);
            mBlink.setDuration(PERIOD_MS);
            mBlink.setRepeatCount(ValueAnimator.INFINITE);
            mBlink.setInterpolator(t -> t < 0.5f ? 0f : 1f);
        }
        AnimGate.sync(mBlink, run);
    }
}
