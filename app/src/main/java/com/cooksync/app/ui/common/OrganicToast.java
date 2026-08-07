package com.cooksync.app.ui.common;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.cooksync.app.R;
import com.google.android.material.snackbar.Snackbar;

import java.util.Objects;

/**
 * Shared toast utility for CookSync: a single pill-shaped surface with an optional leading
 * icon, an animated progress-fill background that drains over {@link #DURATION_MS}, and an
 * optional uppercase trailing action (e.g. "Undo"), matching the design system's ".toast"
 * component. Supports multiple visual types ({@link Type#SUCCESS}, {@link Type#ERROR},
 * {@link Type#NEUTRAL}).
 *
 * @author Yaron Serlin
 * @version 1.1
 * @since 06/08/2026
 */
public final class OrganicToast {

    /** How long the toast stays on screen before auto-dismissing, in milliseconds. */
    private static final long DURATION_MS = 3200;

    /** Visual types for the toast, defining the base and progress-fill colors. */
    public enum Type {
        SUCCESS(R.color.color_success, R.color.color_success_dark),
        ERROR(R.color.color_danger, R.color.color_danger_dark),
        NEUTRAL(R.color.color_neutral_800, R.color.color_neutral_900);

        @ColorRes final int baseColor;
        @ColorRes final int darkColor;

        Type(@ColorRes int baseColor, @ColorRes int darkColor) {
            this.baseColor = baseColor;
            this.darkColor = darkColor;
        }
    }

    private OrganicToast() {}

    /**
     * Shows a {@link Type#SUCCESS}-styled toast.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param activity the host activity, used to find the content view and resolve colors
     * @param anchor optional view to anchor the toast above (e.g. a bottom navigation bar)
     * @param message the text to display
     */
    public static void showSuccess(@NonNull Activity activity, @Nullable View anchor, @NonNull String message) {
        showInternal(activity, anchor, null, message, null, null, Type.SUCCESS);
    }

    /**
     * Shows a {@link Type#ERROR}-styled toast.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param activity the host activity, used to find the content view and resolve colors
     * @param anchor optional view to anchor the toast above (e.g. a bottom navigation bar)
     * @param message the text to display
     */
    public static void showError(@NonNull Activity activity, @Nullable View anchor, @NonNull String message) {
        showInternal(activity, anchor, null, message, null, null, Type.ERROR);
    }

    /**
     * Shows a {@link Type#NEUTRAL}-styled toast with no icon and no action.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param activity the host activity, used to find the content view and resolve colors
     * @param anchor optional view to anchor the toast above (e.g. a bottom navigation bar)
     * @param message the text to display
     */
    public static void show(@NonNull Activity activity, @Nullable View anchor, @NonNull String message) {
        showInternal(activity, anchor, null, message, null, null, Type.NEUTRAL);
    }

    /**
     * Shows a {@link Type#NEUTRAL}-styled toast with a leading icon and a trailing action
     * label (e.g. "UNDO") that runs {@code onAction} and dismisses the toast when tapped.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param activity the host activity, used to find the content view and resolve colors
     * @param anchor optional view to anchor the toast above (e.g. a bottom navigation bar)
     * @param iconRes optional leading icon drawable resource, or {@code null}/{@code 0} for none
     * @param message the text to display
     * @param actionText the trailing action label
     * @param onAction callback invoked when the action label is tapped
     */
    public static void showWithAction(@NonNull Activity activity, @Nullable View anchor,
                                       @DrawableRes Integer iconRes, @NonNull String message,
                                       @NonNull String actionText, @NonNull Runnable onAction) {
        showInternal(activity, anchor, iconRes, message, actionText, onAction, Type.NEUTRAL);
    }

    /**
     * Builds and shows the toast: inflates the ".toast" pill layout into an indefinite,
     * transparent {@link Snackbar} shell, tints its progress-fill background per {@code type},
     * then schedules both the auto-dismiss and a manual ~60fps progress-drain loop on the main
     * {@link Looper}.
     *
     * Complexity:
     * Time: O(1) setup, plus a bounded number of frame callbacks over {@link #DURATION_MS}
     * Space: O(1)
     *
     * @param activity the host activity, used to find the content view and resolve colors
     * @param anchor optional view to anchor the toast above
     * @param iconRes optional leading icon drawable resource, or {@code null}/{@code 0} for none
     * @param message the text to display
     * @param actionText optional trailing action label, paired with {@code onAction}
     * @param onAction optional callback invoked when the action label is tapped
     * @param type the visual type controlling base/fill colors
     */
    private static void showInternal(@NonNull Activity activity, @Nullable View anchor,
                                      @DrawableRes Integer iconRes, @NonNull String message,
                                      @Nullable String actionText, @Nullable Runnable onAction,
                                      @NonNull Type type) {
        View content = activity.findViewById(android.R.id.content);
        if (content == null) return;

        Snackbar snackbar = Snackbar.make(content, "", Snackbar.LENGTH_INDEFINITE);
        if (anchor != null) snackbar.setAnchorView(anchor);

        View snackbarView = snackbar.getView();
        snackbarView.setBackgroundColor(Color.TRANSPARENT);
        snackbarView.setPadding(0, 0, 0, 0);

        Snackbar.SnackbarLayout layout = (Snackbar.SnackbarLayout) snackbarView;
        layout.removeAllViews();

        View toastView = LayoutInflater.from(activity).inflate(R.layout.toast_organic, layout, false);
        ImageView icon = toastView.findViewById(R.id.iv_toast_icon);
        TextView messageView = toastView.findViewById(R.id.tv_toast_message);
        TextView actionView = toastView.findViewById(R.id.tv_toast_action);

        if (iconRes != null && iconRes != 0) {
            icon.setImageResource(iconRes);
            icon.setVisibility(View.VISIBLE);
        } else {
            icon.setVisibility(View.GONE);
        }

        messageView.setText(message);

        if (actionText != null && onAction != null) {
            actionView.setText(actionText);
            actionView.setVisibility(View.VISIBLE);
            actionView.setOnClickListener(v -> {
                onAction.run();
                snackbar.dismiss();
            });
        } else {
            actionView.setVisibility(View.GONE);
        }

        LayerDrawable progressBg = (LayerDrawable) ContextCompat.getDrawable(activity, R.drawable.bg_toast_fill_pill);
        if (progressBg != null) {
            progressBg = (LayerDrawable) progressBg.mutate();

            // Set base color for the first item in layer-list
            Drawable baseLayer = progressBg.getDrawable(0);
            if (baseLayer != null) {
                baseLayer.setColorFilter(ContextCompat.getColor(activity, type.baseColor), PorterDuff.Mode.SRC_IN);
            }

            // Set dark color for the clip layer
            ClipDrawable fill = (ClipDrawable) progressBg.findDrawableByLayerId(R.id.toast_fill_clip);
            if (fill != null && fill.getDrawable() != null) {
                Objects.requireNonNull(fill.getDrawable()).setColorFilter(ContextCompat.getColor(activity, type.darkColor), PorterDuff.Mode.SRC_IN);
            }
            toastView.setBackground(progressBg);
        } else {
            Drawable bg = ContextCompat.getDrawable(activity, R.drawable.bg_pill_solid);
            if (bg != null) {
                bg = bg.mutate();
                bg.setColorFilter(ContextCompat.getColor(activity, type.baseColor), PorterDuff.Mode.SRC_IN);
                toastView.setBackground(bg);
            }
        }

        layout.addView(toastView);
        snackbar.show();

        final Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(snackbar::dismiss, DURATION_MS);

        if (progressBg != null) {
            final LayerDrawable finalProgressBg = progressBg;
            long startTime = System.currentTimeMillis();
            handler.post(new Runnable() {
                @Override
                public void run() {
                    long elapsed = System.currentTimeMillis() - startTime;
                    float progress = Math.min(1.0f, (float) elapsed / DURATION_MS);
                    int level = (int) ((1.0f - progress) * 10000);
                    ClipDrawable cd = (ClipDrawable) finalProgressBg.findDrawableByLayerId(R.id.toast_fill_clip);
                    if (cd != null) cd.setLevel(level);
                    if (progress < 1.0f) handler.postDelayed(this, 16);
                }
            });
        }
    }
}
