package com.cooksync.app.ui.recipe.wizard;
import com.cooksync.app.ui.base.BaseActivity;
import com.cooksync.app.ui.base.BaseViewModel;
import com.cooksync.app.ui.base.Navigator;
import com.cooksync.app.ui.base.ViewModelFactory;
import com.cooksync.app.data.model.recipe.RecipeDraft;
import com.cooksync.app.data.model.recipe.RecipeDraftMapper;
import com.cooksync.app.data.model.recipe.RecipeDraftValidator;
import com.cooksync.app.data.model.recipe.RecipeDraftMediaHelper;

import android.content.Context;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.cooksync.app.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.cooksync.app.ui.base.BaseAdapter;
import com.cooksync.app.util.TextWatchers;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Renders the editable instruction-step cards on the Create Recipe wizard's Instructions step:
 * a numbered badge, a drag handle for reordering, a description field, and a row of tappable
 * pills for the step's timer and linked ingredients — matching the design's compact per-step
 * card. Binds directly against the live {@link RecipeDraft.DraftInstruction} instances held by
 * {@link AddRecipeViewModel#getInstructions()}. Step photo picking is delegated to the host
 * fragment via {@link Listener#onPhotoClick} since it needs the fragment's
 * {@link RecipeImagePicker} and {@link AddRecipeViewModel}.
 *
 * @author Yaron Serlin
 * @version 1.1
 * @since 08/08/2026
 */
public class WizardInstructionAdapter extends BaseAdapter<RecipeDraft.DraftInstruction, WizardInstructionAdapter.ViewHolder> {

    /** Notified on row actions the host fragment needs to act on. */
    public interface Listener {
        void onRemove(RecipeDraft.DraftInstruction instruction);

        /** @param position this instruction's current adapter position, needed to route the upload result back */
        void onPhotoClick(RecipeDraft.DraftInstruction instruction, int position);
    }

    private List<RecipeDraft.DraftIngredient> ingredients = new ArrayList<>();
    private Listener listener;
    private ItemTouchHelper itemTouchHelper;

    public WizardInstructionAdapter(@NonNull List<RecipeDraft.DraftInstruction> instructions) {
        super(instructions);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /** Supplies the current draft ingredients so each step can link the ones it uses. */
    public void setIngredients(@NonNull List<RecipeDraft.DraftIngredient> ingredients) {
        this.ingredients = ingredients;
        notifyDataSetChanged();
    }

    /** Wires up the drag handle to start a reorder drag on the host's {@link ItemTouchHelper}. */
    public void setItemTouchHelper(ItemTouchHelper itemTouchHelper) {
        this.itemTouchHelper = itemTouchHelper;
    }

    /** Renumbers every visible "STEP N" badge after a reorder, without a full rebind. */
    public void refreshStepNumbers() {
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_wizard_instruction, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position), position, ingredients, listener, this);
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvStepNumber;
        private final TextView tvStepLabel;
        private final ImageView ivDragHandle;
        private final EditText etDescription;
        private final ChipGroup cgPills;
        private final TextView tvTimerPill;
        private final TextView tvAddIngredientPill;
        private final MaterialButton btnPhoto;
        private final MaterialCardView cardPhotoPreview;
        private final ImageButton btnRemove;
        private TextWatcher descriptionWatcher;

        ViewHolder(View view) {
            super(view);
            tvStepNumber = view.findViewById(R.id.tv_instruction_step_number);
            tvStepLabel = view.findViewById(R.id.tv_instruction_step_label);
            ivDragHandle = view.findViewById(R.id.iv_instruction_drag_handle);
            etDescription = view.findViewById(R.id.et_instruction_description);
            cgPills = view.findViewById(R.id.cg_instruction_pills);
            tvTimerPill = view.findViewById(R.id.tv_instruction_timer_pill);
            tvAddIngredientPill = view.findViewById(R.id.tv_instruction_add_ingredient_pill);
            btnPhoto = view.findViewById(R.id.btn_instruction_photo);
            cardPhotoPreview = view.findViewById(R.id.card_instruction_photo_preview);
            btnRemove = view.findViewById(R.id.btn_remove_instruction);
        }

        @SuppressWarnings("ClickableViewAccessibility")
        void bind(RecipeDraft.DraftInstruction instruction, int position, List<RecipeDraft.DraftIngredient> ingredients,
                  Listener listener, WizardInstructionAdapter adapter) {
            Context context = itemView.getContext();

            tvStepNumber.setText(String.valueOf(position + 1));
            tvStepLabel.setText(context.getString(R.string.wizard_step_number_format, position + 1).toUpperCase(Locale.getDefault()));

            if (descriptionWatcher != null) etDescription.removeTextChangedListener(descriptionWatcher);
            etDescription.setText(instruction.description);
            descriptionWatcher = TextWatchers.onChanged(value -> instruction.description = value);
            etDescription.addTextChangedListener(descriptionWatcher);

            bindTimerPill(context, instruction);
            bindIngredientPills(context, instruction, ingredients);

            if (instruction.imageUrl != null && !instruction.imageUrl.isEmpty()) {
                cardPhotoPreview.setVisibility(View.VISIBLE);
                Glide.with(context).load(instruction.imageUrl)
                        .placeholder(R.drawable.bg_skeleton_bone)
                        .error(R.drawable.ic_image_failed)
                        .into((ImageView) cardPhotoPreview.findViewById(R.id.iv_instruction_photo_preview));
            } else {
                cardPhotoPreview.setVisibility(View.GONE);
            }

            btnPhoto.setOnClickListener(v -> {
                if (listener != null) listener.onPhotoClick(instruction, getBindingAdapterPosition());
            });
            btnRemove.setOnClickListener(v -> {
                if (listener != null) listener.onRemove(instruction);
            });
            ivDragHandle.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN && itemTouchHelper != null) {
                    itemTouchHelper.startDrag(this);
                }
                return false;
            });
        }

        private void bindTimerPill(Context context, RecipeDraft.DraftInstruction instruction) {
            if (instruction.hasTimer && instruction.timeSeconds != null && instruction.timeSeconds > 0) {
                tvTimerPill.setText("⏱ " + com.cooksync.app.util.DurationFormatter.format(instruction.timeSeconds));
                tvTimerPill.setBackgroundResource(R.drawable.bg_tag_accent);
                tvTimerPill.setTextColor(context.getColor(R.color.color_accent_800));
            } else {
                tvTimerPill.setText(R.string.wizard_instruction_no_timer_pill);
                tvTimerPill.setBackgroundResource(R.drawable.bg_tag_neutral);
                tvTimerPill.setTextColor(context.getColor(R.color.color_text));
            }
            tvTimerPill.setOnClickListener(v -> showTimerDialog(context, instruction));
        }

        /**
         * Renders one removable {@link Chip} per linked ingredient (showing quantity, unit, and
         * name), inserted between the timer pill and the "+ ingredient" pill — both of which stay
         * static in the layout so this only needs to manage the dynamic chips between them.
         */
        private void bindIngredientPills(Context context, RecipeDraft.DraftInstruction instruction,
                                          List<RecipeDraft.DraftIngredient> ingredients) {
            for (int i = cgPills.getChildCount() - 1; i >= 0; i--) {
                if ("dynamic_ingredient_chip".equals(cgPills.getChildAt(i).getTag())) {
                    cgPills.removeViewAt(i);
                }
            }
            int insertIndex = 1;
            LayoutInflater inflater = LayoutInflater.from(context);
            for (RecipeDraft.DraftIngredient ingredient : ingredients) {
                if (!instruction.linkedIngredientTmpIds.contains(ingredient.tmpId)) continue;
                Chip chip = (Chip) inflater.inflate(R.layout.item_removable_chip, cgPills, false);
                chip.setTag("dynamic_ingredient_chip");
                chip.setText(ingredientChipLabel(context, ingredient));
                chip.setOnCloseIconClickListener(v -> {
                    instruction.linkedIngredientTmpIds.remove(ingredient.tmpId);
                    bindIngredientPills(context, instruction, ingredients);
                });
                cgPills.addView(chip, insertIndex++);
            }
            tvAddIngredientPill.setVisibility(ingredients.isEmpty() ? View.GONE : View.VISIBLE);
            tvAddIngredientPill.setOnClickListener(v -> showLinkIngredientsDialog(context, instruction, ingredients));
        }

        private String ingredientChipLabel(Context context, RecipeDraft.DraftIngredient ingredient) {
            String name = ingredient.name.isEmpty() ? context.getString(R.string.wizard_ingredient_name_hint) : ingredient.name;
            String unitCode = unitCode(ingredient.unitLabel);
            if (ingredient.quantity.isEmpty() && unitCode.isEmpty()) {
                return name;
            }
            return (ingredient.quantity + " " + unitCode).trim() + " " + name;
        }

        /** Extracts the short code from a "Name (code)" unit label, e.g. "Kilogram (kg)" → "kg". */
        private String unitCode(String unitLabel) {
            if (unitLabel == null) return "";
            int open = unitLabel.lastIndexOf('(');
            int close = unitLabel.lastIndexOf(')');
            return open >= 0 && close > open ? unitLabel.substring(open + 1, close) : "";
        }

        private void showTimerDialog(Context context, RecipeDraft.DraftInstruction instruction) {
            int initialSeconds = instruction.hasTimer && instruction.timeSeconds != null ? instruction.timeSeconds : 0;
            com.cooksync.app.ui.common.TimePickerDialog.show(context, R.string.wizard_instruction_set_timer_title, initialSeconds,
                    com.cooksync.app.ui.common.TimePickerDialog.Mode.HH_MM_SS, totalSeconds -> {
                if (totalSeconds > 0) {
                    instruction.hasTimer = true;
                    instruction.timeSeconds = totalSeconds;
                } else {
                    instruction.hasTimer = false;
                    instruction.timeSeconds = null;
                }
                bindTimerPill(context, instruction);
            });
        }

        private void showLinkIngredientsDialog(Context context, RecipeDraft.DraftInstruction instruction,
                                                List<RecipeDraft.DraftIngredient> ingredients) {
            if (ingredients.isEmpty()) return;
            CharSequence[] names = new CharSequence[ingredients.size()];
            boolean[] checked = new boolean[ingredients.size()];
            for (int i = 0; i < ingredients.size(); i++) {
                RecipeDraft.DraftIngredient ingredient = ingredients.get(i);
                names[i] = ingredient.name.isEmpty() ? context.getString(R.string.wizard_ingredient_name_hint) : ingredient.name;
                checked[i] = instruction.linkedIngredientTmpIds.contains(ingredient.tmpId);
            }
            new MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_CookSync_Dialog)
                    .setTitle(R.string.wizard_instruction_link_ingredients_title)
                    .setMultiChoiceItems(names, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                    .setPositiveButton(R.string.action_set, (dialog, which) -> {
                        instruction.linkedIngredientTmpIds.clear();
                        for (int i = 0; i < ingredients.size(); i++) {
                            if (checked[i]) instruction.linkedIngredientTmpIds.add(ingredients.get(i).tmpId);
                        }
                        bindIngredientPills(context, instruction, ingredients);
                    })
                    .setNegativeButton(R.string.action_cancel, null)
                    .show();
        }
    }
}
