package com.cooksync.app.util;

import android.text.Editable;
import android.text.TextWatcher;

import java.util.function.Consumer;

/**
 * Factory for the "I only care about the final text" {@link TextWatcher} shape, previously
 * reimplemented near-identically in every wizard step that binds an {@code EditText} straight
 * to a ViewModel setter or a domain-model field.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 12/08/2026
 */
public final class TextWatchers {

    private TextWatchers() {
    }

    /**
     * Builds a {@link TextWatcher} that ignores the before/during callbacks and invokes
     * {@code onChanged} with the field's full text once it settles after each edit.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param onChanged invoked with the field's current text after each change
     * @return the assembled watcher, ready to pass to {@code EditText#addTextChangedListener}
     */
    public static TextWatcher onChanged(Consumer<String> onChanged) {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                onChanged.accept(s.toString());
            }
        };
    }
}
