package com.cooksync.app.ui.recipe.wizard;
import com.cooksync.app.ui.base.BaseActivity;
import com.cooksync.app.ui.base.BaseViewModel;
import com.cooksync.app.ui.base.Navigator;
import com.cooksync.app.ui.base.ViewModelFactory;
import com.cooksync.app.data.model.recipe.RecipeDraft;
import com.cooksync.app.data.model.recipe.RecipeDraftMapper;
import com.cooksync.app.data.model.recipe.RecipeDraftValidator;
import com.cooksync.app.data.model.recipe.RecipeDraftMediaHelper;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.cooksync.app.R;
import com.dtos.response.unit.UnitResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Renders the editable ingredient rows on the Create Recipe wizard's Ingredients step. Binds
 * directly against the live {@link RecipeDraft.DraftIngredient} instances held by
 * {@link AddRecipeViewModel#getIngredients()} — each keystroke writes straight back into the
 * bound object rather than round-tripping through the ViewModel, since these are simple field
 * edits with no business logic attached.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 08/08/2026
 */
public class WizardIngredientAdapter extends RecyclerView.Adapter<WizardIngredientAdapter.ViewHolder> {

    /** Notified when the row's remove action is tapped. */
    public interface Listener {
        void onRemove(RecipeDraft.DraftIngredient ingredient);
    }

    private final List<RecipeDraft.DraftIngredient> ingredients;
    private List<UnitResponse> units = new ArrayList<>();
    private Listener listener;
    private ItemTouchHelper itemTouchHelper;

    public WizardIngredientAdapter(@NonNull List<RecipeDraft.DraftIngredient> ingredients) {
        this.ingredients = ingredients;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /** Wires up the drag handle to start a reorder drag on the host's {@link ItemTouchHelper}. */
    public void setItemTouchHelper(ItemTouchHelper itemTouchHelper) {
        this.itemTouchHelper = itemTouchHelper;
    }

    /**
     * Supplies the unit catalog used to populate each row's unit picker. Safe to call again
     * once units finish loading after rows already exist.
     *
     * @param units every measurement unit available
     */
    public void setUnits(@NonNull List<UnitResponse> units) {
        this.units = units;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_wizard_ingredient, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(ingredients.get(position), units, listener);
    }

    @Override
    public int getItemCount() {
        return ingredients.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final EditText etName;
        private final EditText etQuantity;
        private final AutoCompleteTextView actvUnit;
        private final ImageView ivDragHandle;
        private final ImageButton btnRemove;
        private TextWatcher nameWatcher;
        private TextWatcher quantityWatcher;

        ViewHolder(View view) {
            super(view);
            etName = view.findViewById(R.id.et_ingredient_name);
            etQuantity = view.findViewById(R.id.et_ingredient_quantity);
            actvUnit = view.findViewById(R.id.actv_ingredient_unit);
            ivDragHandle = view.findViewById(R.id.iv_ingredient_drag_handle);
            btnRemove = view.findViewById(R.id.btn_remove_ingredient);
            // Matches the tag-autocomplete popup's card styling instead of the system default.
            actvUnit.setDropDownBackgroundResource(R.drawable.bg_dropdown_popup);
        }

        @SuppressWarnings("ClickableViewAccessibility")
        void bind(RecipeDraft.DraftIngredient ingredient, List<UnitResponse> units, Listener listener) {
            if (nameWatcher != null) etName.removeTextChangedListener(nameWatcher);
            if (quantityWatcher != null) etQuantity.removeTextChangedListener(quantityWatcher);

            etName.setText(ingredient.name);
            etQuantity.setText(ingredient.quantity);
            actvUnit.setText(ingredient.unitLabel, false);

            List<String> unitLabels = new ArrayList<>();
            for (UnitResponse unit : units) {
                unitLabels.add(unitLabel(unit));
            }
            actvUnit.setAdapter(new ArrayAdapter<>(itemView.getContext(), R.layout.item_unit_suggestion, unitLabels));
            // inputType="none" means the user can never type to trigger the adapter's default
            // filter-as-you-type popup, so this field is a tap-to-open picker instead: show the
            // full suggestion list explicitly on click/focus.
            actvUnit.setOnClickListener(v -> actvUnit.showDropDown());
            actvUnit.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) actvUnit.showDropDown();
            });
            actvUnit.setOnItemClickListener((parent, view, pos, id) -> {
                String selectedLabel = (String) parent.getItemAtPosition(pos);
                for (UnitResponse unit : units) {
                    if (unitLabel(unit).equals(selectedLabel)) {
                        ingredient.unitId = unit.id();
                        ingredient.unitLabel = selectedLabel;
                        break;
                    }
                }
            });

            nameWatcher = onChanged(value -> ingredient.name = value);
            quantityWatcher = onChanged(value -> ingredient.quantity = value);
            etName.addTextChangedListener(nameWatcher);
            etQuantity.addTextChangedListener(quantityWatcher);

            btnRemove.setOnClickListener(v -> {
                if (listener != null) listener.onRemove(ingredient);
            });
            ivDragHandle.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN && itemTouchHelper != null) {
                    itemTouchHelper.startDrag(this);
                }
                return false;
            });
        }

        private static String unitLabel(UnitResponse unit) {
            return String.format(Locale.getDefault(), "%s (%s)", unit.name(), unit.code());
        }

        private static TextWatcher onChanged(Consumer<String> onChanged) {
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
}
