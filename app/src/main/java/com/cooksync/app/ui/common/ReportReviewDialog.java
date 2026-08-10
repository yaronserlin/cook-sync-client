package com.cooksync.app.ui.common;
import com.cooksync.app.ui.base.BaseActivity;
import com.cooksync.app.ui.base.BaseViewModel;
import com.cooksync.app.ui.base.Navigator;
import com.cooksync.app.ui.base.ViewModelFactory;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioButton;

import androidx.annotation.NonNull;

import com.cooksync.app.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Shared "report review" dialog: lets the viewer pick a reason (spam, abuse, or off-topic) and
 * add an optional comment, then hands the result to the caller via
 * {@code RecipeRepository.reportReview}. Reusable anywhere a review can be reported.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 05/08/2026
 */
public final class ReportReviewDialog {

    /** Callback for the dialog's outcome. */
    public interface Callback {
        /**
         * Invoked when the user submits the report.
         *
         * @param reason the chosen report reason: {@code "SPAM"}, {@code "ABUSE"}, or {@code "OFF_TOPIC"}
         * @param comment the trimmed optional comment, or {@code null} if left blank
         */
        void onSubmit(@NonNull String reason, String comment);
    }

    private ReportReviewDialog() {
    }

    /**
     * Shows the report dialog.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param context the hosting screen's context
     * @param callback invoked with the chosen reason and comment; never called if the user cancels
     */
    public static void show(@NonNull Context context, @NonNull Callback callback) {
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_report_review, null);
        RadioButton reasonSpam = dialogView.findViewById(R.id.rb_spam);
        RadioButton reasonAbuse = dialogView.findViewById(R.id.rb_abusive);
        EditText comment = dialogView.findViewById(R.id.et_report_comment);

        new MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_CookSync_Dialog)
                .setTitle(R.string.report_review_title)
                .setView(dialogView)
                .setPositiveButton(R.string.action_save, (dialog, which) -> {
                    String reason;
                    if (reasonSpam.isChecked()) {
                        reason = "SPAM";
                    } else if (reasonAbuse.isChecked()) {
                        reason = "ABUSE";
                    } else {
                        reason = "OFF_TOPIC";
                    }
                    String commentText = comment.getText() == null ? "" : comment.getText().toString().trim();
                    callback.onSubmit(reason, commentText.isEmpty() ? null : commentText);
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }
}
