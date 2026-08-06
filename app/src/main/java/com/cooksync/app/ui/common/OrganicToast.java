package com.cooksync.app.ui.common;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cooksync.app.R;
import com.google.android.material.snackbar.Snackbar;

/**
 * Shared toast utility for CookSync: a single pill-shaped, dark surface with an optional leading
 * icon and an optional uppercase trailing action (e.g. "Undo"), matching the design system's
 * ".toast" component. Built on {@link Snackbar} so it auto-dismisses, respects the activity's
 * lifecycle and can be anchored above a bottom navigation bar, but every one of Snackbar's own
 * visuals is replaced by {@code toast_organic.xml}. Used everywhere in place of
 * {@code Toast.makeText} so every screen shares one look and behavior.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 06/08/2026
 */
public final class OrganicToast {

    /** How long the toast stays on screen before auto-dismissing, in milliseconds. */
    private static final int DURATION_MS = 3200;

    private OrganicToast() {
    }

    /**
     * Shows a plain message toast with no leading icon and no action.
     *
     * @param activity the hosting screen
     * @param anchor a view to float the toast above (e.g. a bottom navigation bar), or {@code null}
     * @param message the message to display
     */
    public static void show(@NonNull Activity activity, @Nullable View anchor, @NonNull String message) {
        show(activity, anchor, 0, message);
    }

    /**
     * Shows a message toast with a leading icon and no action.
     *
     * @param activity the hosting screen
     * @param anchor a view to float the toast above (e.g. a bottom navigation bar), or {@code null}
     * @param iconRes the leading icon's drawable resource, or {@code 0} for none
     * @param message the message to display
     */
    public static void show(@NonNull Activity activity, @Nullable View anchor, @DrawableRes int iconRes, @NonNull String message) {
        display(activity, anchor, iconRes, message, null, null);
    }

    /**
     * Shows a message toast with a leading icon and a trailing action (e.g. "Undo"). Tapping the
     * action invokes {@code onAction} and dismisses the toast immediately.
     *
     * @param activity the hosting screen
     * @param anchor a view to float the toast above (e.g. a bottom navigation bar), or {@code null}
     * @param iconRes the leading icon's drawable resource, or {@code 0} for none
     * @param message the message to display
     * @param actionLabel the trailing action's label (rendered uppercase), e.g. "Undo"
     * @param onAction invoked when the action is tapped
     */
    public static void showWithAction(@NonNull Activity activity, @Nullable View anchor, @DrawableRes int iconRes,
                                       @NonNull String message, @NonNull String actionLabel, @NonNull Runnable onAction) {
        display(activity, anchor, iconRes, message, actionLabel, onAction);
    }

    private static void display(Activity activity, View anchor, int iconRes, String message,
                                 String actionLabel, Runnable onAction) {
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
        if (actionLabel != null && onAction != null) {
            actionView.setText(actionLabel);
            actionView.setVisibility(View.VISIBLE);
            actionView.setOnClickListener(v -> {
                onAction.run();
                snackbar.dismiss();
            });

            // Drains left-to-right over the toast's lifetime so the user can see how long they
            // have left to tap "Undo" — only for undo-capable toasts. Driven purely through the
            // background drawable's level (bg_toast_fill_pill's clip layer) rather than an extra
            // view, since a background drawable never affects this view's measured size.
            Drawable fillBackground = activity.getDrawable(R.drawable.bg_toast_fill_pill).mutate();
            // toast_organic_root's XML backgroundTint would otherwise be silently reapplied to
            // this new background too, painting both of its layers the same flat color and
            // hiding the fill entirely.
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
