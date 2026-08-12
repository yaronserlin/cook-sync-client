package com.cooksync.app.ui.common;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.cooksync.app.R;
import com.google.android.material.button.MaterialButton;

/**
 * Applies the app's two selected/unselected toggle-chip visual treatments, previously
 * reimplemented near-identically at each screen offering a row of exclusive-choice chips.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 12/08/2026
 */
public final class ChipStyler {

    private ChipStyler() {
    }

    /**
     * Solid accent-fill toggle style used by the recipe wizard's difficulty and visibility
     * chip rows.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param chip the chip to style
     * @param active whether this chip is the currently selected one
     */
    public static void styleAccentChip(@NonNull TextView chip, boolean active) {
        chip.setBackgroundColor(active
                ? chip.getResources().getColor(R.color.color_accent, null)
                : Color.TRANSPARENT);
        chip.setTextColor(active
                ? chip.getResources().getColor(R.color.color_bg, null)
                : chip.getResources().getColor(R.color.color_text, null));
    }

    /**
     * Neutral-toned tint-list toggle style used by the admin console's filter chip rows.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param chip the chip to style
     * @param active whether this chip is the currently selected one
     */
    public static void styleNeutralChip(@NonNull MaterialButton chip, boolean active) {
        int bg = active ? R.color.color_neutral_900 : R.color.color_neutral_300;
        int fg = active ? R.color.color_neutral_100 : R.color.color_text;
        chip.setBackgroundTintList(ColorStateList.valueOf(chip.getResources().getColor(bg, null)));
        chip.setTextColor(chip.getResources().getColor(fg, null));
    }
}
