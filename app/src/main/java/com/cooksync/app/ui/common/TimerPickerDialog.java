package com.cooksync.app.ui.common;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.NumberPicker;

import androidx.annotation.NonNull;

import com.cooksync.app.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Shared hours/minutes/seconds timer picker dialog, matching the design's timer-set spec.
 * Originally built inline for {@link com.cooksync.app.ui.recipe.cooking.CookingModeActivity},
 * extracted here so the Create Recipe wizard's per-step timer pill can present the identical
 * picker instead of a bespoke one.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 08/08/2026
 */
public final class TimerPickerDialog {

    /** Notified with the picked total duration in seconds when the user confirms. */
    public interface OnTimerSetListener {
        void onTimerSet(int totalSeconds);
    }

    private TimerPickerDialog() {
    }

    /**
     * Shows the picker, seeded with {@code initialSeconds}. Does not clamp or otherwise
     * interpret the picked value (e.g. treating 0 as "no timer") — that policy is the caller's
     * responsibility, since it differs between call sites.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param context the hosting screen's context
     * @param initialSeconds the duration to seed the picker with, in seconds
     * @param listener invoked with the picked total duration in seconds on confirm; not called on cancel
     */
    public static void show(@NonNull Context context, int initialSeconds, @NonNull OnTimerSetListener listener) {
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_set_timer, null);
        NumberPicker npHours = dialogView.findViewById(R.id.np_hours);
        NumberPicker npMinutes = dialogView.findViewById(R.id.np_minutes);
        NumberPicker npSeconds = dialogView.findViewById(R.id.np_seconds);
        npHours.setMinValue(0);
        npHours.setMaxValue(23);
        npMinutes.setMinValue(0);
        npMinutes.setMaxValue(59);
        npSeconds.setMinValue(0);
        npSeconds.setMaxValue(59);

        int seed = Math.max(initialSeconds, 0);
        npHours.setValue(seed / 3600);
        npMinutes.setValue((seed % 3600) / 60);
        npSeconds.setValue(seed % 60);

        new MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_CookSync_Dialog)
                .setTitle(R.string.dialog_set_timer_title)
                .setView(dialogView)
                .setPositiveButton(R.string.action_set, (dialog, which) -> {
                    int totalSeconds = npHours.getValue() * 3600 + npMinutes.getValue() * 60 + npSeconds.getValue();
                    listener.onTimerSet(totalSeconds);
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }
}
