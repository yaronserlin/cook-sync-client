package com.cooksync.app.ui.common;
import com.cooksync.app.ui.base.BaseActivity;
import com.cooksync.app.ui.base.BaseViewModel;
import com.cooksync.app.ui.base.Navigator;
import com.cooksync.app.ui.base.ViewModelFactory;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

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

    /** Relative size bump applied to a {@link #dangerHeading}, over the theme's default title size. */
    private static final float DANGER_HEADING_SCALE = 1.15f;

    private OrganicConfirmDialog() {
    }

    /**
     * Builds a bold, enlarged, danger-colored dialog title for the most severe destructive
     * confirmations (e.g. an admin's permanent user deletion), where the plain default title
     * style used by {@link #show} isn't enough visual weight on its own.
     *
     * @param context the hosting screen's context, used to resolve {@code color_danger}
     * @param text the title text to style
     * @return a {@code SpannableString} usable as the {@code title} argument to {@link #show} or
     *         {@link #showWithTextConfirm}
     */
    @NonNull
    public static CharSequence dangerHeading(@NonNull Context context, @NonNull String text) {
        SpannableString styled = new SpannableString(text);
        int danger = ContextCompat.getColor(context, R.color.color_danger);
        styled.setSpan(new ForegroundColorSpan(danger), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        styled.setSpan(new StyleSpan(Typeface.BOLD), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        styled.setSpan(new RelativeSizeSpan(DANGER_HEADING_SCALE), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return styled;
    }

    /**
     * Shows the confirm dialog.
     *
     * @param context the hosting screen's context
     * @param title dialog title; a plain {@link String} renders with the theme's default title
     *              style, or pass a styled {@code SpannableString} (e.g. via
     *              {@link #dangerHeading}) for extra emphasis
     * @param message dialog body text
     * @param confirmLabel the confirm button's label, e.g. "Delete" or "Stop"
     * @param cancelLabel the cancel button's label, e.g. "Cancel" or "Keep cooking"
     * @param danger {@code true} to style the confirm button as a destructive action
     * @param onConfirm invoked when the user taps the confirm button; never called on cancel
     */
    public static void show(@NonNull Context context, @NonNull CharSequence title, @NonNull String message,
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

    /**
     * Shows a danger confirm dialog that requires the user to type an exact confirmation phrase
     * before the confirm button enables, for the most destructive actions (e.g. an admin's
     * permanent, non-undoable user deletion) that warrant more friction than a plain
     * confirm/cancel or a password re-entry. Always styled as danger.
     *
     * @param context the hosting screen's context
     * @param title dialog title; a plain {@link String} renders with the theme's default title
     *              style, or pass a styled {@code SpannableString} (e.g. via
     *              {@link #dangerHeading}) for extra emphasis
     * @param message dialog body text, shown above the confirmation field
     * @param requiredText the exact phrase the user must type (case-insensitively) for the
     *                      confirm button to enable
     * @param confirmLabel the confirm button's label, e.g. "Delete permanently"
     * @param cancelLabel the cancel button's label, e.g. "Cancel"
     * @param onConfirm invoked when the user taps the (by then enabled) confirm button; never
     *                  called on cancel
     */
    public static void showWithTextConfirm(@NonNull Context context, @NonNull CharSequence title,
                                            @NonNull String message, @NonNull String requiredText,
                                            @NonNull String confirmLabel, @NonNull String cancelLabel,
                                            @NonNull Runnable onConfirm) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_confirm_with_text, null);
        ((TextView) view.findViewById(R.id.tv_message)).setText(message);
        EditText etConfirm = view.findViewById(R.id.et_confirm_text);
        etConfirm.setHint(requiredText);

        AlertDialog dialog = new MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_CookSync_Dialog_Danger)
                .setTitle(title)
                .setView(view)
                .setPositiveButton(confirmLabel, (d, which) -> onConfirm.run())
                .setNegativeButton(cancelLabel, null)
                .create();

        dialog.setOnShowListener(d -> {
            Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            setPositiveButtonMatchState(positive, false);
            etConfirm.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    setPositiveButtonMatchState(positive, requiredText.equalsIgnoreCase(s.toString()));
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
            });
        });
        dialog.show();
    }

    /**
     * Applies both the enabled flag and a matching alpha to the text-confirm dialog's positive
     * button. The button's {@code backgroundTint} is a flat color rather than a
     * state-list-aware one, so {@link Button#setEnabled} alone leaves it fully opaque and
     * visually indistinguishable from its enabled state; the alpha makes "still locked" and
     * "ready to confirm" actually look different.
     *
     * @param button the dialog's positive button
     * @param matches whether the typed text currently matches the required phrase
     */
    private static void setPositiveButtonMatchState(@NonNull Button button, boolean matches) {
        button.setEnabled(matches);
        button.setAlpha(matches ? 1f : 0.4f);
    }
}
