package com.cooksync.app.ui.common;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.cooksync.app.R;
import com.google.android.material.snackbar.Snackbar;

/**
 * Shared toast utility for CookSync: a single pill-shaped surface with an optional leading
 * icon and an optional uppercase trailing action (e.g. "Undo"), matching the design system's
 * ".toast" component. Supports multiple visual styles (DEFAULT, SUCCESS, WARNING, DANGER).
 *
 * @author Yaron Serlin
 * @version 1.1
 * @since 06/08/2026
 */
public final class OrganicToast {

    /** How long the toast stays on screen before auto-dismissing, in milliseconds. */
    private static final int DURATION_MS = 3200;

    /** Visual styles for the toast, defining background and progress fill colors. */
    public enum Style {
        DEFAULT(R.color.color_neutral_900, R.color.color_neutral_800),
        SUCCESS(R.color.color_success, R.color.color_success_dark),
        WARNING(R.color.color_warning, R.color.color_warning_dark),
        DANGER(R.color.color_danger, R.color.color_danger_dark);

        @ColorRes final int backgroundRes;
        @ColorRes final int fillRes;

        Style(int backgroundRes, int fillRes) {
            this.backgroundRes = backgroundRes;
            this.fillRes = fillRes;
        }
    }

    private OrganicToast() {
    }

    /** Shows a plain message toast with the default style. */
    public static void show(@NonNull Activity activity, @Nullable View anchor, @NonNull String message) {
        show(activity, anchor, 0, message, Style.DEFAULT);
    }

    /** Shows a message toast with a leading icon and the default style. */
    public static void show(@NonNull Activity activity, @Nullable View anchor, @DrawableRes int iconRes, @NonNull String message) {
        show(activity, anchor, iconRes, message, Style.DEFAULT);
    }

    /** Shows a message toast with a leading icon and a specific style. */
    public static void show(@NonNull Activity activity, @Nullable View anchor, @DrawableRes int iconRes,
                             @NonNull String message, @NonNull Style style) {
        display(activity, anchor, iconRes, message, null, null, style);
    }

    /** Shows a success toast with a check icon. */
    public static void showSuccess(@NonNull Activity activity, @Nullable View anchor, @NonNull String message) {
        show(activity, anchor, R.drawable.ic_check, message, Style.SUCCESS);
    }

    /** Shows an error toast with the danger style. */
    public static void showError(@NonNull Activity activity, @Nullable View anchor, @NonNull String message) {
        show(activity, anchor, 0, message, Style.DANGER);
    }

    /** Shows a toast with a trailing action (e.g. "Undo"). */
    public static void showWithAction(@NonNull Activity activity, @Nullable View anchor, @DrawableRes int iconRes,
                                       @NonNull String message, @NonNull String actionLabel, @NonNull Runnable onAction) {
        showWithAction(activity, anchor, iconRes, message, actionLabel, onAction, Style.DEFAULT);
    }

    /** Shows a toast with a trailing action and a specific style. */
    public static void showWithAction(@NonNull Activity activity, @Nullable View anchor, @DrawableRes int iconRes,
                                       @NonNull String message, @NonNull String actionLabel, @NonNull Runnable onAction,
                                       @NonNull Style style) {
        display(activity, anchor, iconRes, message, actionLabel, onAction, style);
    }

    private static void display(Activity activity, View anchor, int iconRes, String message,
                                 String actionLabel, Runnable onAction, Style style) {
        View content = activity.findViewById(android.R.id.content);
        Snackbar snackbar = Snackbar.make(content, "", Snackbar.LENGTH_INDEFINITE);
        snackbar.setDuration(DURATION_MS);
        if (anchor != null) {
            snackbar.setAnchorView(anchor);
        }

        Snackbar.SnackbarLayout layout = (Snackbar.SnackbarLayout) snackbar.getView();
        layout.setBackgroundColor(Color.TRANSPARENT);
        layout.setPadding(0, 0, 0, 0);
        layout.removeAllViews();

        View toastView = LayoutInflater.from(activity).inflate(R.layout.toast_organic, layout, false);
        ImageView icon = toastView.findViewById(R.id.iv_toast_icon);
        TextView messageView = toastView.findViewById(R.id.tv_toast_message);
        TextView actionView = toastView.findViewById(R.id.tv_toast_action);

        if (iconRes != 0) {
            icon.setImageResource(iconRes);
            icon.setVisibility(View.VISIBLE);
        }
        messageView.setText(message);

        int bgColor = ContextCompat.getColor(activity, style.backgroundRes);
        toastView.setBackgroundTintList(ColorStateList.valueOf(bgColor));

        if (actionLabel != null && onAction != null) {
            actionView.setText(actionLabel);
            actionView.setVisibility(View.VISIBLE);
            actionView.setOnClickListener(v -> {
                onAction.run();
                snackbar.dismiss();
            });

            // Drains left-to-right over the toast's lifetime so the user can see how long they
            // have left to tap "Undo".
            Drawable fillBackground = ContextCompat.getDrawable(activity, R.drawable.bg_toast_fill_pill).mutate();
            if (fillBackground instanceof android.graphics.drawable.LayerDrawable layer) {
                Drawable base = layer.getDrawable(0);
                base.setTint(bgColor);
                Drawable fill = layer.findDrawableByLayerId(R.id.toast_fill_clip);
                if (fill instanceof android.graphics.drawable.ClipDrawable clip) {
                    // This is a bit hacky because we can't easily reach into the ClipDrawable's
                    // child shape from here, but we can tint the whole thing.
                    clip.setTint(ContextCompat.getColor(activity, style.fillRes));
                }
            }

            toastView.setBackgroundTintList(null);
            toastView.setBackground(fillBackground);
            ValueAnimator drainAnimator = ValueAnimator.ofInt(10_000, 0);
            drainAnimator.setDuration(DURATION_MS);
            drainAnimator.setInterpolator(new LinearInterpolator());
            drainAnimator.addUpdateListener(anim -> fillBackground.setLevel((int) anim.getAnimatedValue()));
            drainAnimator.start();
        }

        layout.addView(toastView);
        snackbar.show();
    }
}
