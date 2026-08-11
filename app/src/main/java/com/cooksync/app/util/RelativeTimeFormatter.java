package com.cooksync.app.util;

import java.util.concurrent.TimeUnit;

/**
 * Utility for formatting a past timestamp into a concise, always-English "time ago" string.
 * Written as a small locale-independent replacement for
 * {@link android.text.format.DateUtils#getRelativeTimeSpanString(long)}, which renders using the
 * device's current system locale (e.g. Hebrew) regardless of the app's own (English-only)
 * string resources, producing inconsistent mixed-language UI.
 *
 * Examples:
 * - 30 seconds ago -> "Just now"
 * - 5 minutes ago -> "5 minutes ago"
 * - 2 hours ago -> "2 hours ago"
 * - 3 days ago -> "3 days ago"
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 11/08/2026
 */
public final class RelativeTimeFormatter {

    private RelativeTimeFormatter() {
        // Utility class
    }

    /**
     * Formats a past epoch-millis timestamp relative to now.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param pastMillis epoch millis of the past event
     * @return formatted relative time string (e.g. "Just now", "5 minutes ago", "2 days ago")
     */
    public static String format(long pastMillis) {
        long diffMillis = System.currentTimeMillis() - pastMillis;
        if (diffMillis < 0) {
            diffMillis = 0;
        }

        long minutes = TimeUnit.MILLISECONDS.toMinutes(diffMillis);
        if (minutes < 1) {
            return "Just now";
        }

        long hours = TimeUnit.MILLISECONDS.toHours(diffMillis);
        if (hours < 1) {
            return minutes + (minutes == 1 ? " minute ago" : " minutes ago");
        }

        long days = TimeUnit.MILLISECONDS.toDays(diffMillis);
        if (days < 1) {
            return hours + (hours == 1 ? " hour ago" : " hours ago");
        }
        if (days < 7) {
            return days + (days == 1 ? " day ago" : " days ago");
        }
        if (days < 30) {
            long weeks = days / 7;
            return weeks + (weeks == 1 ? " week ago" : " weeks ago");
        }
        if (days < 365) {
            long months = days / 30;
            return months + (months == 1 ? " month ago" : " months ago");
        }

        long years = days / 365;
        return years + (years == 1 ? " year ago" : " years ago");
    }
}
