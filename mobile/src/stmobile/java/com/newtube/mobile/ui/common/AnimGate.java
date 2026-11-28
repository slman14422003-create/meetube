package com.newtube.mobile.ui.common;

import android.animation.ValueAnimator;
import android.view.View;

public final class AnimGate {
    private AnimGate() {
    }

    public static boolean allowed() {
        return ValueAnimator.areAnimatorsEnabled() && !BatterySaver.lite();
    }

    public static boolean shouldRun(View view) {
        return view.isAttachedToWindow() && view.isShown() && view.hasWindowFocus() && allowed();
    }

    public static void sync(ValueAnimator animator, boolean run) {
        if (animator == null) {
            return;
        }
        if (run) {
            if (!animator.isStarted()) {
                animator.start();
            } else if (animator.isPaused()) {
                animator.resume();
            }
        } else if (animator.isStarted() && !animator.isPaused()) {
            animator.pause();
        }
    }
}
