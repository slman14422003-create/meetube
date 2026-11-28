package com.newtube.mobile.ui.common;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.RenderNode;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewParent;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * MEETUBE(glass): the page a {@link GlassView} frosts. On API 31+ its children are recorded into a
 * RenderNode that the glass re-draws through its effect chain; because the glass holds a reference
 * to this node (not a copy), whatever scrolls here shows up under the glass on the RenderThread
 * with no extra invalidation. A GlassView must not sit inside the source it samples.
 *
 * The node is re-recorded only when a child (or this view) invalidated since the last recording;
 * every other draw reuses it. {@link #version()} is the counter glasses compare, so they look up
 * their position only when the source changed or moved.
 */
public class GlassSource extends FrameLayout {
    static final boolean HARDWARE = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;

    @Nullable
    final RenderNode node = HARDWARE ? new RenderNode("MeeTube glass source") : null;
    /**
     * Off, the children draw straight to the window and every glass on this source shows its tint
     * alone. For a SurfaceView child (the player's HDR output, #35): its hole was punched only where
     * the glass re-drew this node, so the video showed through the buttons and nowhere else.
     */
    boolean sampling = true;
    private final int[] mWindowLoc = new int[2];
    private int mLocVersion = -1;
    private int mNodeW = -1;
    private int mNodeH = -1;
    /** Bumped by anything that changes what is under a glass or where the source sits. */
    private int mVersion;
    private boolean mRecordDirty = true;
    private boolean mRecorded;
    private int mRecordedEpoch = -1;
    private float mStampX = Float.NaN;
    private float mStampY = Float.NaN;

    public GlassSource(@NonNull Context context) {
        super(context);
    }

    public GlassSource(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * Counts every change to this source's content or place. A glass that saw this value last frame
     * and has not moved itself has nothing to re-check. The place is read from plain fields up the
     * parent chain (no matrices, no window lookup).
     */
    int version() {
        float x = 0f;
        float y = 0f;
        View v = this;
        while (true) {
            x += v.getLeft() + v.getTranslationX() - v.getScrollX();
            y += v.getTop() + v.getTranslationY() - v.getScrollY();
            ViewParent parent = v.getParent();
            if (!(parent instanceof View)) {
                break;
            }
            v = (View) parent;
        }
        if (x != mStampX || y != mStampY) {
            mStampX = x;
            mStampY = y;
            mVersion++;
        }
        return mVersion;
    }

    /** Where this source sits in the window, looked up again only after {@link #version()} moved on. */
    void windowLocation(int[] out) {
        int version = version();
        if (version != mLocVersion) {
            getLocationInWindow(mWindowLoc);
            mLocVersion = version;
        }
        out[0] = mWindowLoc[0];
        out[1] = mWindowLoc[1];
    }

    public void setSampling(boolean on) {
        if (sampling != on) {
            sampling = on;
            markDirty();
            invalidate();
        }
    }

    private void markDirty() {
        mRecordDirty = true;
        mVersion++;
    }

    @Override
    @SuppressLint("NewApi")
    public void onDescendantInvalidated(@NonNull View child, @NonNull View target) {
        markDirty();
        super.onDescendantInvalidated(child, target);
    }

    @Override
    public void invalidate() {
        markDirty();
        super.invalidate();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void invalidate(Rect dirty) {
        markDirty();
        super.invalidate(dirty);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void invalidate(int l, int t, int r, int b) {
        markDirty();
        super.invalidate(l, t, r, b);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (changed) {
            markDirty();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        markDirty();
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        markDirty();
    }

    @Override
    public void onViewAdded(View child) {
        super.onViewAdded(child);
        markDirty();
    }

    @Override
    public void onViewRemoved(View child) {
        super.onViewRemoved(child);
        markDirty();
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        markDirty();
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        if (node != null && sampling && Glass.liveBlur() && canvas.isHardwareAccelerated()) {
            int w = getWidth();
            int h = getHeight();
            boolean resized = mNodeW != w || mNodeH != h;
            if (resized) {
                mNodeW = w;
                mNodeH = h;
                node.setPosition(0, 0, w, h);
            }
            int epoch = Glass.epoch();
            if (mRecordDirty || resized || !mRecorded || mRecordedEpoch != epoch) {
                mRecordDirty = false; // before recording: an invalidate raised while drawing stays pending
                mRecordedEpoch = epoch;
                Canvas recording = node.beginRecording();
                try {
                    super.dispatchDraw(recording);
                } finally {
                    node.endRecording();
                }
                mRecorded = true;
                mVersion++;
            }
            canvas.drawRenderNode(node);
        } else {
            mRecorded = false;
            super.dispatchDraw(canvas);
        }
    }
}
