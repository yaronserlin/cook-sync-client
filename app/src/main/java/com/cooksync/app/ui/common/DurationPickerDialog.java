package com.cooksync.app.ui.common;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.NumberPicker;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.cooksync.app.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Shared hours/minutes duration picker dialog (no seconds), used for the Create Recipe wizard's
 * Prep/Cook time fields — a coarser sibling of {@link TimerPickerDialog}, which additionally
 * picks seconds for cooking-mode step timers.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 08/08/2026
 */
public final class DurationPickerDialog {

    /** Notified with the picked total duration in minutes when the user confirms. */
    public interface OnDurationSetListener {
        void onDurationSet(int totalMinutes);
    }

    private DurationPickerDialog() {
    }

    /**
     * Shows the picker, seeded with {@code initialMinutes}.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param context the hosting screen's context
     * @param title the dialog's title, e.g. "Prep time" or "Cook time"
     * @param initialMinutes the duration to seed the picker with, in minutes
     * @param listener invoked with the picked total duration in minutes on confirm; not called on cancel
     */
    public static void show(@NonNull Context context, @StringRes int title, int initialMinutes,
                             @NonNull OnDurationSetListener listener) {
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_set_duration, null);
        NumberPicker npHours = dialogView.findViewById(R.id.np_duration_hours);
        NumberPicker npMinutes = dialogView.findViewById(R.id.np_duration_minutes);
        npHours.setMinValue(0);
        npHours.setMaxValue(23);
        npMinutes.setMinValue(0);
        npMinutes.setMaxValue(59);

        int seed = Math.max(initialMinutes, 0);
        npHours.setValue(seed / 60);
        npMinutes.setValue(seed % 60);

        new MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_CookSync_Dialog)
                .setTitle(title)
                .setView(dialogView)
                .setPositiveButton(R.string.action_set, (dialog, which) ->
                        listener.onDurationSet(npHours.getValue() * 60 + npMinutes.getValue()))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }
}
