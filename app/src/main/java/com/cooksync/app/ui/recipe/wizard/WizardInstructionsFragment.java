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
import com.cooksync.app.ui.base.ViewModelFactory;

import java.util.Collections;

/**
 * Wizard step 3: the editable instruction-step list, each optionally carrying a timer, linked
 * ingredients, and a photo. Shares {@link AddRecipeViewModel} with the other three steps via an
 * activity-scoped {@link ViewModelProvider}.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 08/08/2026
 */
public class WizardInstructionsFragment extends Fragment {

    private AddRecipeViewModel viewModel;
    private WizardInstructionAdapter adapter;
    private RecipeImagePicker imagePicker;
    private ItemTouchHelper touchHelper;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        imagePicker = new RecipeImagePicker(this);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_wizard_instructions, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity(), new ViewModelFactory()).get(AddRecipeViewModel.class);

        RecyclerView recyclerView = view.findViewById(R.id.rv_instructions);
        adapter = new WizardInstructionAdapter(viewModel.getInstructions());
        adapter.setIngredients(viewModel.getIngredients());
        adapter.setListener(new WizardInstructionAdapter.Listener() {
            @Override
            public void onRemove(RecipeDraft.DraftInstruction instruction) {
                int position = viewModel.getInstructions().indexOf(instruction);
                viewModel.removeInstruction(instruction);
                adapter.notifyItemRemoved(position);
                adapter.notifyItemRangeChanged(position, adapter.getItemCount());
            }

            @Override
            public void onPhotoClick(RecipeDraft.DraftInstruction instruction, int position) {
                // Not uploaded yet — only the local URI is stored until Publish, matching the
                // wizard's "nothing leaves the device before Publish" rule.
                imagePicker.pick(uri -> {
                    instruction.imageUrl = uri.toString();
                    adapter.notifyItemChanged(position);
                });
            }
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        touchHelper = new ItemTouchHelper(new DragReorderTouchHelperCallback(new DragReorderTouchHelperCallback.OnMoveListener() {
            @Override
            public void onMove(int from, int to) {
                Collections.swap(viewModel.getInstructions(), from, to);
                adapter.notifyItemMoved(from, to);
            }

            @Override
            public void onDragFinished() {
                adapter.refreshStepNumbers();
            }
        }));
        touchHelper.attachToRecyclerView(recyclerView);
        adapter.setItemTouchHelper(touchHelper);

        View btnAddInstruction = view.findViewById(R.id.btn_add_instruction);
        // Dashed shape-drawable strokes don't reliably render under hardware acceleration.
        btnAddInstruction.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        btnAddInstruction.setOnClickListener(v -> {
            viewModel.addInstruction();
            adapter.notifyItemInserted(viewModel.getInstructions().size() - 1);
        });

        if (viewModel.getInstructions().isEmpty()) {
            viewModel.addInstruction();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // The ingredient catalog may have changed on the previous step since this fragment's
        // view was last bound, so refresh the "uses ingredients" chip row every time this step
        // becomes visible again.
        if (adapter != null) {
            adapter.setIngredients(viewModel.getIngredients());
        }
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
