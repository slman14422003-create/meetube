package com.newtube.mobile.ui.settings;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.util.Property;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.transition.TransitionValues;
import androidx.transition.Visibility;

/**
 * Light page change for Settings: a short fade with a small slide, on a hardware layer. Unlike the
 * frost transition it has no animated blur (RenderEffect) - that re-blurred the whole page every
 * frame and was the heaviest part of opening a settings page.
 */
final class SettingsTransition extends Visibility {
    private static final long IN_MS = 180;
    private static final long OUT_MS = 110;
    private static final float SLIDE_DP = 18f;

    @Nullable
    @Override
    public Animator onAppear(@NonNull ViewGroup sceneRoot, @NonNull View view,
            @Nullable TransitionValues startValues, @Nullable TransitionValues endValues) {
        float slide = SLIDE_DP * view.getResources().getDisplayMetrics().density;
        view.setAlpha(0f);
        view.setTranslationX(view.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL ? -slide : slide);
        return run(view, 1f, 0f, IN_MS, new DecelerateInterpolator(1.6f));
    }

    @Nullable
    @Override
    public Animator onDisappear(@NonNull ViewGroup sceneRoot, @NonNull View view,
            @Nullable TransitionValues startValues, @Nullable TransitionValues endValues) {
        return run(view, 0f, 0f, OUT_MS, new AccelerateInterpolator());
    }

    private static Animator run(View view, float alpha, float translationX, long duration,
            android.animation.TimeInterpolator interpolator) {
        ObjectAnimator animator = ObjectAnimator.ofPropertyValuesHolder(view,
                PropertyValuesHolder.ofFloat(View.ALPHA, alpha),
                PropertyValuesHolder.ofFloat((Property<View, Float>) View.TRANSLATION_X, translationX));
        animator.setDuration(duration);
        animator.setInterpolator(interpolator);
        int oldLayer = view.getLayerType();
        view.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        animator.addListener(new AnimatorListenerAdapter() {
            private void done() {
                view.setLayerType(oldLayer, null);
                view.setAlpha(1f);
                view.setTranslationX(0f);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                done();
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                done();
            }
        });
        return animator;
    }
}
