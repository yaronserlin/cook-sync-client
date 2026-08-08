package com.cooksync.app.ui.common;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.cooksync.app.R;
import com.dtos.response.admin.DuplicateTagGroupResponse;
import com.dtos.response.admin.TagVariantResponse;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.radiobutton.MaterialRadioButton;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared "merge duplicate tags" dialog for the Admin Console's Tags tab: lets the moderator
 * pick which variant in a {@link DuplicateTagGroupResponse} survives the merge, then hands
 * every variant id plus the chosen canonical id to the caller via
 * {@code AdminViewModel.mergeGroup}.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 07/08/2026
 */
public final class MergeTagsDialog {

    /** Callback for the dialog's outcome. */
    public interface Callback {
        /**
         * Invoked when the user confirms the merge.
         *
         * @param allVariantIds every tag id in the duplicate group, including {@code keepTagId}
         * @param keepTagId the canonical tag id chosen to survive the merge
         */
        void onConfirm(@NonNull List<String> allVariantIds, @NonNull String keepTagId);
    }

    private MergeTagsDialog() {
    }

    /**
     * Shows the merge dialog for one duplicate tag group.
     *
     * Complexity:
     * Time: O(n) over the group's variant count, to build the radio options
     * Space: O(n)
     *
     * @param context the hosting screen's context
     * @param group the duplicate tag group to resolve
     * @param callback invoked with the chosen canonical tag; never called if the user cancels
     */
    public static void show(@NonNull Context context, @NonNull DuplicateTagGroupResponse group,
                             @NonNull Callback callback) {
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_merge_tags, null);
        TextView message = dialogView.findViewById(R.id.tv_merge_message);
        message.setText(R.string.admin_merge_dialog_message);
        RadioGroup radioGroup = dialogView.findViewById(R.id.rg_merge_options);

        List<TagVariantResponse> variants = group.variants();
        List<String> allVariantIds = new ArrayList<>();
        for (TagVariantResponse variant : variants) {
            allVariantIds.add(variant.id());
            MaterialRadioButton option = new MaterialRadioButton(context);
            option.setId(View.generateViewId());
            option.setText(context.getString(R.string.admin_merge_option_format, variant.name(), variant.recipeCount()));
            option.setTag(variant);
            option.setPadding(0, 8, 0, 8);
            radioGroup.addView(option);
        }
        if (radioGroup.getChildCount() > 0) {
            ((RadioButton) radioGroup.getChildAt(0)).setChecked(true);
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_CookSync_Dialog)
                .setTitle(R.string.admin_merge_dialog_title)
                .setView(dialogView)
                .setPositiveButton(R.string.action_save, (d, which) -> {
                    RadioButton checked = radioGroup.findViewById(radioGroup.getCheckedRadioButtonId());
                    if (checked != null) {
                        callback.onConfirm(allVariantIds, ((TagVariantResponse) checked.getTag()).id());
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .create();

        radioGroup.setOnCheckedChangeListener((group1, checkedId) -> updatePositiveButtonLabel(context, dialog, group1));
        dialog.setOnShowListener(d -> updatePositiveButtonLabel(context, dialog, radioGroup));
        dialog.show();
    }

    private static void updatePositiveButtonLabel(Context context, AlertDialog dialog, RadioGroup radioGroup) {
        RadioButton checked = radioGroup.findViewById(radioGroup.getCheckedRadioButtonId());
        if (checked == null) return;
        String name = ((TagVariantResponse) checked.getTag()).name();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setText(context.getString(R.string.admin_merge_confirm_format, name));
    }
}
