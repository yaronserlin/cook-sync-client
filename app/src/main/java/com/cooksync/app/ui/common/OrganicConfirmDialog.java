package com.cooksync.app.ui.common;
import com.cooksync.app.ui.base.BaseActivity;
import com.cooksync.app.ui.base.BaseViewModel;
import com.cooksync.app.ui.base.Navigator;
import com.cooksync.app.ui.base.ViewModelFactory;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.cooksync.app.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.function.Consumer;

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

    /**
     * Shows a danger confirm dialog that also collects a password re-entry, for destructive
     * actions that require re-authentication (e.g. account deletion). Always styled as danger,
     * since re-authentication is only ever asked for destructive actions.
     *
     * @param context the hosting screen's context
     * @param title dialog title
     * @param message dialog body text, shown above the password field
     * @param confirmLabel the confirm button's label, e.g. "Delete everything"
     * @param cancelLabel the cancel button's label, e.g. "Cancel"
     * @param onConfirm invoked with the entered password when the user taps confirm; never
     *                  called on cancel
     */
    public static void showWithPasswordConfirm(@NonNull Context context, @NonNull String title,
                                                 @NonNull String message, @NonNull String confirmLabel,
                                                 @NonNull String cancelLabel,
                                                 @NonNull Consumer<String> onConfirm) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_confirm_with_password, null);
        ((TextView) view.findViewById(R.id.tv_message)).setText(message);
        EditText etPassword = view.findViewById(R.id.et_password);

        new MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_CookSync_Dialog_Danger)
                .setTitle(title)
                .setView(view)
                .setPositiveButton(confirmLabel, (dialog, which) -> onConfirm.accept(etPassword.getText().toString()))
                .setNegativeButton(cancelLabel, null)
                .show();
    }
}
