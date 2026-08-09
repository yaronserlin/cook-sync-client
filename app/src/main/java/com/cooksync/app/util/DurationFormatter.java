package com.cooksync.app.util;

/**
 * Utility for formatting step timers and recipe durations into concise human-readable strings
 * using standard abbreviated units ("h", "min", "sec").
 *
 * Examples:
 * - 4 -> "4 sec"
 * - 120 -> "2 min"
 * - 150 -> "2 min 30 sec"
 * - 3600 -> "1 h"
 * - 5400 -> "1 h 30 min"
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 09/08/2026
 */
public final class DurationFormatter {

    private DurationFormatter() {
        // Utility class
    }

    /**
     * Formats a duration in seconds into a human-readable string.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param totalSeconds duration in seconds
     * @return formatted duration string (e.g. "4 sec", "2 min", "1 h 30 min")
     */
    public static String format(int totalSeconds) {
        if (totalSeconds <= 0) {
            return "0 sec";
        }

        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;

        StringBuilder sb = new StringBuilder();
        if (hours > 0) {
            sb.append(hours).append(" h");
        }

        if (minutes > 0) {
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append(minutes).append(" min");
        }

        if (seconds > 0 || (hours == 0 && minutes == 0)) {
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append(seconds).append(" sec");
        }

        return sb.toString();
    }

    /**
     * Formats a duration in minutes into a human-readable string.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param totalMinutes duration in minutes
     * @return formatted duration string (e.g. "45 min", "1 h 30 min")
     */
    public static String formatMinutes(int totalMinutes) {
        return format(totalMinutes * 60);
    }
}
