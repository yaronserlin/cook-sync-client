package com.cooksync.app.ui.common;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.NumberPicker;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.cooksync.app.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Unified hours/minutes/seconds time picker dialog, supporting both coarse duration selection
 * (hours & minutes, {@link Mode#HH_MM}) and fine step timer duration selection (hours, minutes &
 * seconds, {@link Mode#HH_MM_SS}).
 *
 * Replaces and consolidates the previous {@code TimerPickerDialog} and {@code DurationPickerDialog}
 * into a single reusable component.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 09/08/2026
 */
public final class TimePickerDialog {

    /** Operating mode of the time picker dialog. */
    public enum Mode {
        /** Hours and minutes mode (coarse durations such as prep and cook times). */
        HH_MM,
        /** Hours, minutes, and seconds mode (fine durations such as step timers). */
        HH_MM_SS
    }

    /** Notified with the picked total duration in seconds when the user confirms. */
    public interface OnTimeSetListener {
        /**
         * Invoked on confirmation with the selected total time in seconds.
         *
         * @param totalSeconds total duration picked, in seconds
         */
        void onTimeSet(int totalSeconds);
    }

    private TimePickerDialog() {
        // Utility class
    }

    /**
     * Displays the time picker dialog using the specified title, initial seconds, and mode.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param context the hosting context
     * @param title the dialog title resource ID
     * @param initialSeconds initial duration in seconds
     * @param mode operating mode (HH_MM or HH_MM_SS)
     * @param listener listener invoked on positive confirmation
     */
    public static void show(@NonNull Context context, @StringRes int title, int initialSeconds,
                             @NonNull Mode mode, @NonNull OnTimeSetListener listener) {
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_set_time, null);
        NumberPicker npHours = dialogView.findViewById(R.id.np_hours);
        NumberPicker npMinutes = dialogView.findViewById(R.id.np_minutes);
        NumberPicker npSeconds = dialogView.findViewById(R.id.np_seconds);
        TextView tvSeparator = dialogView.findViewById(R.id.tv_seconds_separator);
        View containerSeconds = dialogView.findViewById(R.id.container_seconds);

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

        if (mode == Mode.HH_MM) {
            tvSeparator.setVisibility(View.GONE);
            containerSeconds.setVisibility(View.GONE);
        } else {
            tvSeparator.setVisibility(View.VISIBLE);
            containerSeconds.setVisibility(View.VISIBLE);
        }

        new MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_CookSync_Dialog)
                .setTitle(title)
                .setView(dialogView)
                .setPositiveButton(R.string.action_set, (dialog, which) -> {
                    int hours = npHours.getValue();
                    int minutes = npMinutes.getValue();
                    int seconds = (mode == Mode.HH_MM_SS) ? npSeconds.getValue() : 0;
                    int totalSeconds = hours * 3600 + minutes * 60 + seconds;
                    listener.onTimeSet(totalSeconds);
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    /**
     * Helper overload for HH:MM mode taking initial minutes instead of seconds.
     *
     * @param context the hosting context
     * @param title the dialog title resource ID
     * @param initialMinutes initial duration in minutes
     * @param listener listener invoked on positive confirmation with total minutes
     */
    public static void showMinutes(@NonNull Context context, @StringRes int title, int initialMinutes,
                                   @NonNull OnTimeSetListener listener) {
        show(context, title, initialMinutes * 60, Mode.HH_MM, totalSeconds -> listener.onTimeSet(totalSeconds / 60));
    }
}
