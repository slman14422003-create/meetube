package com.newtube.mobile.ui.browse;

import androidx.annotation.NonNull;

import java.util.function.Supplier;

/**
 * NEWTUBE(startup): builds a {@link GreetingOverlay} the first time it is asked for, on the main
 * thread, and hands the same one back after that. The caller asks for it once after the first
 * frame so the overlay is ready, and again from the long press, which builds it if that came first.
 */
public final class GreetingHolder {
    private final Supplier<GreetingOverlay> mBuilder;
    private GreetingOverlay mOverlay;

    public GreetingHolder(@NonNull Supplier<GreetingOverlay> builder) {
        mBuilder = builder;
    }

    @NonNull
    public GreetingOverlay get() {
        if (mOverlay == null) {
            mOverlay = mBuilder.get();
        }
        return mOverlay;
    }
}
