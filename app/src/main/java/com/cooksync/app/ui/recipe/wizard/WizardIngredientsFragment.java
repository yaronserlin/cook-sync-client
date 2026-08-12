package com.cooksync.app.ui.recipe.wizard;
import com.cooksync.app.ui.base.BaseActivity;
import com.cooksync.app.ui.base.BaseViewModel;
import com.cooksync.app.ui.base.Navigator;
import com.cooksync.app.ui.base.ViewModelFactory;
import com.cooksync.app.data.model.recipe.RecipeDraft;
import com.cooksync.app.data.model.recipe.RecipeDraftMapper;
import com.cooksync.app.data.model.recipe.RecipeDraftValidator;
import com.cooksync.app.data.model.recipe.RecipeDraftMediaHelper;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cooksync.app.R;
import com.cooksync.app.domain.ApiResult;
import com.cooksync.app.ui.base.ViewModelFactory;
import com.dtos.response.unit.UnitResponse;

import java.util.Collections;
import java.util.List;

/**
 * Wizard step 2: the editable ingredient list. Shares {@link AddRecipeViewModel} with the other
 * three steps via an activity-scoped {@link ViewModelProvider}.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 08/08/2026
 */
public class WizardIngredientsFragment extends Fragment {

    private AddRecipeViewModel viewModel;
    private WizardIngredientAdapter adapter;
    private ItemTouchHelper touchHelper;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_wizard_ingredients, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity(), new ViewModelFactory()).get(AddRecipeViewModel.class);

        RecyclerView recyclerView = view.findViewById(R.id.rv_ingredients);
        adapter = new WizardIngredientAdapter(viewModel.getIngredients());
        adapter.setListener(ingredient -> {
            int position = viewModel.getIngredients().indexOf(ingredient);
            viewModel.removeIngredient(ingredient);
            adapter.notifyItemRemoved(position);
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        touchHelper = new ItemTouchHelper(new DragReorderTouchHelperCallback((from, to) -> {
            Collections.swap(viewModel.getIngredients(), from, to);
            adapter.notifyItemMoved(from, to);
        }));
        touchHelper.attachToRecyclerView(recyclerView);
        adapter.setItemTouchHelper(touchHelper);

        View btnAddIngredient = view.findViewById(R.id.btn_add_ingredient);
        // Dashed shape-drawable strokes don't reliably render under hardware acceleration.
        btnAddIngredient.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        btnAddIngredient.setOnClickListener(v -> {
            viewModel.addIngredient();
            adapter.notifyItemInserted(viewModel.getIngredients().size() - 1);
        });

        if (viewModel.getIngredients().isEmpty()) {
            viewModel.addIngredient();
        }

        viewModel.getUnitsResult().observe(getViewLifecycleOwner(), result -> {
            if (result instanceof ApiResult.Success<List<UnitResponse>> success) {
                adapter.setUnits(success.getData());
            }
        });
        viewModel.loadUnits();
    }

    /**
     * Detaches the drag helper and drops the adapter reference before the wizard's ViewPager2
     * recycles this step's view (e.g. swiping to another step and back) — the adapter/helper
     * are rebuilt fresh in {@link #onViewCreated} against the new view tree, so nothing carries
     * over a reference to the destroyed one.
     */
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (touchHelper != null) {
            touchHelper.attachToRecyclerView(null);
            touchHelper = null;
        }
        adapter = null;
    }
}
