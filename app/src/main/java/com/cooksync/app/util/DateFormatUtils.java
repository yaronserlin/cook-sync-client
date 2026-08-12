package com.cooksync.app.util;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Parsing helper for the ISO-8601 timestamp strings ({@code createdAt}/{@code updatedAt}) the
 * server sends on every DTO, previously reimplemented near-identically at each call site that
 * needed to turn one into a {@link LocalDate} for display formatting.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 12/08/2026
 */
public final class DateFormatUtils {

    private DateFormatUtils() {
    }

    /**
     * Parses the date portion of an ISO-8601 timestamp string.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param isoTimestamp the timestamp string, e.g. a DTO's {@code createdAt} value
     * @return the parsed date, or {@code null} if {@code isoTimestamp} is {@code null}, blank,
     *         or not a valid ISO date
     */
    public static LocalDate parseIsoDate(String isoTimestamp) {
        if (isoTimestamp == null || isoTimestamp.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(isoTimestamp.substring(0, 10));
        } catch (DateTimeParseException | IndexOutOfBoundsException e) {
            return null;
        }
    }
}
