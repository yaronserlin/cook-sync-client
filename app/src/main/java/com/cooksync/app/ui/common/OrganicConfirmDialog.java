package com.cooksync.app.ui.common;

import android.content.Context;

import androidx.annotation.NonNull;

import com.cooksync.app.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Shared confirm/cancel dialog for CookSync: a rounded card with a ghost cancel button and a
 * filled confirm button, matching the design system's generic dialog spec. The confirm button
 * uses the app's accent fill for routine confirmations, or the danger fill (the darkest accent
 * ramp step, since Organic has no separate red hue) when {@code danger} is set for destructive
 * actions such as delete or deactivate.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 06/08/2026
 */
public final class OrganicConfirmDialog {

    private OrganicConfirmDialog() {
    }

    /**
     * Shows the confirm dialog.
     *
     * @param context the hosting screen's context
     * @param title dialog title
     * @param message dialog body text
     * @param confirmLabel the confirm button's label, e.g. "Delete" or "Stop"
     * @param cancelLabel the cancel button's label, e.g. "Cancel" or "Keep cooking"
     * @param danger {@code true} to style the confirm button as a destructive action
     * @param onConfirm invoked when the user taps the confirm button; never called on cancel
     */
    public static void show(@NonNull Context context, @NonNull String title, @NonNull String message,
                             @NonNull String confirmLabel, @NonNull String cancelLabel, boolean danger,
                             @NonNull Runnable onConfirm) {
        int themeRes = danger ? R.style.ThemeOverlay_CookSync_Dialog_Danger : R.style.ThemeOverlay_CookSync_Dialog;
        new MaterialAlertDialogBuilder(context, themeRes)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(confirmLabel, (dialog, which) -> onConfirm.run())
                .setNegativeButton(cancelLabel, null)
                .show();
    }
}
