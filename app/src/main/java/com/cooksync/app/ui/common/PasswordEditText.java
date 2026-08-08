package com.cooksync.app.ui.common;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.core.content.ContextCompat;

import com.cooksync.app.R;

/**
 * Password input field with a built-in show/hide toggle rendered as a trailing icon inside the
 * field, instead of a separate button next to it. Extends {@link AppCompatEditText} directly
 * rather than wrapping one in a compound view: every existing call site already declares an
 * {@code EditText} field and only ever calls inherited methods such as {@code getText()} and
 * {@code setText()}, so this class needs no delegation surface — only the XML tag changes from
 * {@code EditText} to this class.
 *
 * Starts masked ({@link InputType#TYPE_TEXT_VARIATION_PASSWORD}, eye-off icon) and toggles to
 * {@link InputType#TYPE_TEXT_VARIATION_VISIBLE_PASSWORD} (eye icon) when the trailing icon is
 * tapped.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 08/08/2026
 */
public class PasswordEditText extends AppCompatEditText {

    /** Extra touch slop (dp) added around the icon so the tap target isn't limited to its glyph bounds. */
    private static final int ICON_TOUCH_SLOP_DP = 12;

    private boolean passwordVisible = false;
    private Drawable iconEye;
    private Drawable iconEyeOff;

    public PasswordEditText(@NonNull Context context) {
        super(context);
        init();
    }

    public PasswordEditText(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        iconEye = ContextCompat.getDrawable(getContext(), R.drawable.ic_eye);
        iconEyeOff = ContextCompat.getDrawable(getContext(), R.drawable.ic_eye_off);
        setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        setCompoundDrawablePadding((int) (8 * getResources().getDisplayMetrics().density));
        applyToggleIcon();
    }

    /**
     * Flips between masked and visible password entry, swaps the trailing icon, and restores
     * cursor position — changing {@code inputType} otherwise resets the selection to 0.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     */
    private void togglePasswordVisibility() {
        passwordVisible = !passwordVisible;
        int selection = Math.min(getSelectionEnd(), length());
        setInputType(InputType.TYPE_CLASS_TEXT | (passwordVisible
                ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                : InputType.TYPE_TEXT_VARIATION_PASSWORD));
        applyToggleIcon();
        setSelection(selection);
    }

    /**
     * Swaps the trailing compound drawable and the field's content description to match the
     * current visibility state.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     */
    private void applyToggleIcon() {
        Drawable icon = passwordVisible ? iconEye : iconEyeOff;
        setCompoundDrawablesWithIntrinsicBounds(null, null, icon, null);
        setContentDescription(getContext().getString(passwordVisible
                ? R.string.password_toggle_hide_desc
                : R.string.password_toggle_show_desc));
    }

    /**
     * Treats a tap anywhere in the trailing-icon region (with a generous slop margin, not just
     * the glyph's exact pixel bounds) as a visibility toggle rather than a text-cursor placement.
     *
     * @param event the touch event
     * @return {@code true} if this event toggled visibility; otherwise delegates to the superclass
     */
    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        Drawable endIcon = getCompoundDrawables()[2];
        if (endIcon != null && event.getAction() == MotionEvent.ACTION_UP) {
            float slopPx = ICON_TOUCH_SLOP_DP * getResources().getDisplayMetrics().density;
            float iconLeft = getWidth() - getPaddingRight() - endIcon.getIntrinsicWidth() - slopPx;
            if (event.getX() >= iconLeft) {
                togglePasswordVisibility();
                return true;
            }
        }
        return super.onTouchEvent(event);
    }
}
