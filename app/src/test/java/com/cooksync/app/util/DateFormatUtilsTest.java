package com.cooksync.app.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.time.LocalDate;

/**
 * Unit tests for {@link DateFormatUtils}. Covers the shared ISO-date parsing used by both
 * {@code RecipeDetailViewModel} and {@code ReviewAdapter}.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 12/08/2026
 */
public class DateFormatUtilsTest {

    @Test
    public void parseIsoDate_parsesDatePortion_ofFullTimestamp() {
        assertEquals(LocalDate.of(2026, 4, 15), DateFormatUtils.parseIsoDate("2026-04-15T10:30:00.000Z"));
    }

    @Test
    public void parseIsoDate_parsesBareDateString() {
        assertEquals(LocalDate.of(2026, 4, 15), DateFormatUtils.parseIsoDate("2026-04-15"));
    }

    @Test
    public void parseIsoDate_null_forNullOrBlank() {
        assertNull(DateFormatUtils.parseIsoDate(null));
        assertNull(DateFormatUtils.parseIsoDate("   "));
    }

    @Test
    public void parseIsoDate_null_forUnparseableString() {
        assertNull(DateFormatUtils.parseIsoDate("not-a-date"));
    }

    @Test
    public void parseIsoDate_null_forStringShorterThanDatePortion() {
        assertNull(DateFormatUtils.parseIsoDate("2026"));
    }
}
