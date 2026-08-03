package com.cooksync.app.ui.common;

import android.animation.ValueAnimator;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Lightweight skeleton shimmer manager. Animates any set of {@link View}s between
 * {@value #ALPHA_MIN} and {@value #ALPHA_MAX} in a repeating pulse, producing a
 * "breathing" skeleton effect without requiring an external shimmer library.
 *
 * <h3>Typical usage</h3>
 * <pre>{@code
 * // In an Activity / Fragment:
 * SkeletonHelper skeleton = new SkeletonHelper();
 *
 * // Start shimmer on a whole container:
 * skeleton.attach(skeletonContainer);
 * skeleton.start();
 *
 * // Stop and hide when content is ready:
 * skeleton.stop();
 * skeletonContainer.setVisibility(View.GONE);
 * realContentView.setVisibility(View.VISIBLE);
 * }</pre>
 *
 * <p>The animator is automatically cancelled in {@link #stop()} and the attached views are
 * restored to full opacity so there is no lingering half-transparent skeleton bone if the
 * view is ever made visible again.</p>
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 04/08/2026
 */
public class SkeletonHelper {

    /** Minimum alpha value in the shimmer pulse — faded state. */
    private static final float ALPHA_MIN = 0.35f;

    /** Maximum alpha value in the shimmer pulse — bright state. */
    private static final float ALPHA_MAX = 1.0f;

    /** Duration of one half-cycle (fade → bright or bright → fade). */
    private static final long PULSE_DURATION_MS = 750L;

    private final List<View> targets = new ArrayList<>();
    private ValueAnimator animator;

    /**
     * Registers a single {@link View} as a shimmer target.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param view the view to animate
     * @return this instance for method chaining
     */
    @NonNull
    public SkeletonHelper attach(@NonNull View view) {
        targets.add(view);
        return this;
    }

    /**
     * Recursively collects every direct and indirect child of {@code container}
     * as a shimmer target. Useful when a skeleton layout is a {@link ViewGroup}
     * with multiple bones at different nesting levels.
     *
     * Complexity:
     * Time: O(n) where n is the number of views in the hierarchy
     * Space: O(d) where d is the depth of the view tree (recursion stack)
     *
     * @param container the root of the skeleton layout
     * @return this instance for method chaining
     */
    @NonNull
    public SkeletonHelper attachAll(@NonNull ViewGroup container) {
        collectLeaves(container, targets);
        return this;
    }

    /**
     * Starts (or restarts) the shimmer animation. Safe to call even if the animator is
     * already running — it will be cancelled and recreated cleanly.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     */
    public void start() {
        if (animator != null && animator.isRunning()) {
            animator.cancel();
        }
        if (targets.isEmpty()) {
            return;
        }

        animator = ValueAnimator.ofFloat(ALPHA_MAX, ALPHA_MIN);
        animator.setDuration(PULSE_DURATION_MS);
        animator.setRepeatMode(ValueAnimator.REVERSE);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addUpdateListener(anim -> {
            float alpha = (float) anim.getAnimatedValue();
            for (View v : targets) {
                if (v != null) {
                    v.setAlpha(alpha);
                }
            }
        });
        animator.start();
    }

    /**
     * Stops the shimmer animation and restores all targets to full opacity.
     *
     * Complexity:
     * Time: O(n) where n is the number of registered target views
     * Space: O(1)
     */
    public void stop() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        for (View v : targets) {
            if (v != null) {
                v.setAlpha(ALPHA_MAX);
            }
        }
    }

    /**
     * Stops the animation and removes all registered targets. Call this when the
     * host Activity or Fragment is destroyed to avoid retaining view references.
     *
     * Complexity:
     * Time: O(n) where n is the number of registered target views
     * Space: O(1)
     */
    public void release() {
        stop();
        targets.clear();
    }

    // ─── Private helpers ─────────────────────────────────────────────────────────

    /**
     * Recursively collects leaf {@link View}s (non-{@link ViewGroup} children, plus
     * {@link ViewGroup}s that have no children) from the given root into {@code out}.
     *
     * Complexity:
     * Time: O(n) where n is the total number of views in the hierarchy
     * Space: O(d) where d is the depth of the view tree
     *
     * @param group the current root group to traverse
     * @param out   the accumulator list
     */
    private void collectLeaves(@NonNull ViewGroup group, @NonNull List<View> out) {
        int count = group.getChildCount();
        if (count == 0) {
            out.add(group);
            return;
        }
        for (int i = 0; i < count; i++) {
            View child = group.getChildAt(i);
            if (child instanceof ViewGroup vg) {
                collectLeaves(vg, out);
            } else {
                out.add(child);
            }
        }
    }
}
