package com.cooksync.app.ui.common;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cooksync.app.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Shared "add/edit private note" dialog, reused for the recipe-wide note and every per-step
 * note across the recipe detail and cooking-mode screens so each screen doesn't duplicate the
 * same prompt/EditText/Save/Delete wiring.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 04/08/2026
 */
public final class NoteEditDialog {

    /** Callback for the dialog's outcome. */
    public interface Callback {
        /**
         * Invoked when the user saves non-blank note text.
         *
         * @param noteText the trimmed note text
         */
        void onSave(@NonNull String noteText);

        /**
         * Invoked when the user deletes an existing note. Never called if there was nothing
         * to delete (the delete option is only offered when {@code existingText} was non-null).
         */
        void onDelete();
    }

    private NoteEditDialog() {
    }

    /**
     * Shows the note editor, pre-filled with {@code existingText} if a note already exists.
     *
     * @param context the hosting screen's context
     * @param title dialog title (e.g. "Recipe note" or "Note for step 3")
     * @param existingText the current note text, or {@code null}/empty if there isn't one yet
     * @param callback invoked with the outcome; never called if the user cancels
     */
    public static void show(@NonNull Context context, @NonNull String title, @Nullable String existingText, @NonNull Callback callback) {
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_edit_note, null);
        EditText input = dialogView.findViewById(R.id.et_note);
        boolean hasExisting = existingText != null && !existingText.isEmpty();
        if (hasExisting) {
            input.setText(existingText);
            input.setSelection(existingText.length());
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setView(dialogView)
                .setPositiveButton("Save", (dialog, which) -> {
                    String text = input.getText() == null ? "" : input.getText().toString().trim();
                    if (!TextUtils.isEmpty(text)) {
                        callback.onSave(text);
                    }
                })
                .setNegativeButton("Cancel", null);

        if (hasExisting) {
            builder.setNeutralButton("Delete", (dialog, which) -> callback.onDelete());
        }

        builder.show();
    }
}
