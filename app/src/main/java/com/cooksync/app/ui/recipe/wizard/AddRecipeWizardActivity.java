package com.cooksync.app.ui.recipe.wizard;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;

import com.cooksync.app.R;
import com.cooksync.app.domain.ApiResult;
import com.cooksync.app.ui.common.BaseActivity;
import com.cooksync.app.ui.common.OrganicConfirmDialog;
import com.cooksync.app.ui.common.ViewModelFactory;
import com.cooksync.app.util.CloudinaryUploader;
import com.dtos.response.cloudinary.CloudinarySignatureResponse;
import com.dtos.response.recipe.RecipeResponse;
import com.google.android.material.button.MaterialButton;

import java.util.List;

/**
 * Hosts the four-step "Create Recipe" wizard (Basics, Ingredients, Instructions, Review) behind
 * a {@link ViewPager2} driven by {@link AddRecipeWizardPagerAdapter}, with programmatic
 * Back/Next navigation only (swiping is disabled). Steps are never gated on validity — the user
 * can move freely forward and backward, and every required-field check happens once, on the
 * Review step's checklist and its Publish button. Owns the progress bar and app bar: the close
 * (X) action asks for discard confirmation, and "Save draft" persists the in-progress
 * {@link RecipeDraft} locally via {@link com.cooksync.app.data.local.RecipeDraftStore}. On
 * launch, resumes a previously saved draft if one exists.
 *
 * @author Yaron Serlin
 * @version 1.1
 * @since 08/08/2026
 */
public class AddRecipeWizardActivity extends BaseActivity {

    private AddRecipeViewModel viewModel;
    private ViewPager2 viewPager;
    private ProgressBar progressBar;
    private TextView tvSaveDraft;
    private MaterialButton btnBack;
    private MaterialButton btnDraft;
    private MaterialButton btnNext;

    public static final String EXTRA_EDIT_RECIPE_JSON = "extra_edit_recipe_json";

    /**
     * Launches the wizard pre-populated with an existing recipe for editing.
     *
     * @param context hosting screen context
     * @param recipe the recipe response model to edit
     */
    public static void startEdit(android.content.Context context, RecipeResponse recipe) {
        android.content.Intent intent = new android.content.Intent(context, AddRecipeWizardActivity.class);
        intent.putExtra(EXTRA_EDIT_RECIPE_JSON, new com.google.gson.Gson().toJson(recipe));
        com.cooksync.app.ui.common.Navigator.start(context, AddRecipeWizardActivity.class, intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_recipe_wizard);

        viewModel = new ViewModelProvider(this, new ViewModelFactory()).get(AddRecipeViewModel.class);

        if (getIntent() != null && getIntent().hasExtra(EXTRA_EDIT_RECIPE_JSON)) {
            String json = getIntent().getStringExtra(EXTRA_EDIT_RECIPE_JSON);
            RecipeResponse recipe = new com.google.gson.Gson().fromJson(json, RecipeResponse.class);
            viewModel.startEditRecipe(recipe);
        } else {
            viewModel.loadDraftIfPresent();
        }

        viewModel.loadTags();
        viewModel.loadUnits();

        ImageButton btnClose = findViewById(R.id.btn_wizard_close);
        tvSaveDraft = findViewById(R.id.btn_wizard_save_draft);
        progressBar = findViewById(R.id.progress_wizard_steps);
        viewPager = findViewById(R.id.vp_wizard_steps);
        btnBack = findViewById(R.id.btn_wizard_back);
        btnDraft = findViewById(R.id.btn_wizard_draft);
        btnNext = findViewById(R.id.btn_wizard_next);

        viewPager.setUserInputEnabled(false);
        viewPager.setAdapter(new AddRecipeWizardPagerAdapter(this));

        int startStep = viewModel.getDraft().lastReachedStep;
        viewPager.setCurrentItem(startStep, false);
        updateStepUi(startStep);

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                viewModel.setLastReachedStep(position);
                updateStepUi(position);
            }
        });

        btnClose.setOnClickListener(v -> confirmDiscard());
        View.OnClickListener saveDraft = v -> {
            if (com.cooksync.app.data.local.RecipeDraftStore.hasDraft()) {
                OrganicConfirmDialog.show(this, "Overwrite saved draft?",
                        "Saving this recipe as a draft will overwrite your existing saved draft. Do you want to continue?",
                        "Overwrite draft", "Keep editing", true, () -> {
                            viewModel.saveDraftLocally();
                            showSuccess(getString(R.string.wizard_draft_saved_toast), null);
                            finish();
                        });
            } else {
                viewModel.saveDraftLocally();
                showSuccess(getString(R.string.wizard_draft_saved_toast), null);
                finish();
            }
        };
        tvSaveDraft.setOnClickListener(saveDraft);
        btnDraft.setOnClickListener(saveDraft);

        btnBack.setOnClickListener(v -> goToStep(viewPager.getCurrentItem() - 1));
        btnNext.setOnClickListener(v -> {
            int step = viewPager.getCurrentItem();
            if (step == RecipeDraftValidator.STEP_REVIEW) {
                startPublishFlow();
            } else {
                goToStep(step + 1);
            }
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                confirmDiscard();
            }
        });

        observePublishResult();
    }

    private void observePublishResult() {
        viewModel.getPublishResult().observe(this, event -> {
            ApiResult<RecipeResponse> result = event.getContentIfNotHandled();
            if (result == null) return;
            if (result instanceof ApiResult.Success<RecipeResponse>) {
                showSuccess(getString(R.string.wizard_published_toast), null);
                finish();
            } else if (result instanceof ApiResult.Error<RecipeResponse> error) {
                updateStepUi(viewPager.getCurrentItem());
                showError(error.getMessage(), null);
            }
        });
    }

    /**
     * Kicks off Publish: every cover/description/step photo still referenced by a local
     * local URI is uploaded to Cloudinary first (one shared signature, uploaded
     * sequentially), and only once every image is a real URL does {@link AddRecipeViewModel#publish()}
     * actually run — matching the wizard's "nothing leaves the device before Publish" rule. This
     * orchestration lives here rather than in the ViewModel because the Cloudinary Android SDK is
     * inherently {@link android.content.Context}-dependent.
     */
    private void startPublishFlow() {
        com.cooksync.app.data.publish.RecipePublishManager.getInstance().startPublish(viewModel.getDraft());
        com.cooksync.app.ui.common.Navigator.start(this, com.cooksync.app.ui.recipe.list.MyRecipesActivity.class);
        finish();
    }

    private void goToStep(int step) {
        viewModel.setLastReachedStep(step);
        viewPager.setCurrentItem(step, true);
    }

    private void updateStepUi(int step) {
        progressBar.setProgress(step);
        boolean isReview = step == RecipeDraftValidator.STEP_REVIEW;

        btnBack.setVisibility(step == RecipeDraftValidator.STEP_BASICS ? View.GONE : View.VISIBLE);
        btnDraft.setVisibility(isReview ? View.VISIBLE : View.GONE);
        // Review (w4) keeps its "Draft" action in the bottom bar instead of the top app bar —
        // showing both would duplicate it.
        tvSaveDraft.setVisibility(isReview ? View.GONE : View.VISIBLE);

        if (isReview) {
            btnNext.setText(R.string.wizard_action_publish);
            btnNext.setIcon(androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_send));
            btnNext.setIconGravity(com.google.android.material.button.MaterialButton.ICON_GRAVITY_START);
            boolean ready = RecipeDraftValidator.isStepValid(viewModel.getDraft(), RecipeDraftValidator.STEP_REVIEW);
            btnNext.setEnabled(ready);
            btnNext.setAlpha(ready ? 1f : 0.5f);
        } else {
            btnNext.setText(switch (step) {
                case RecipeDraftValidator.STEP_BASICS -> getString(R.string.wizard_next_ingredients);
                case RecipeDraftValidator.STEP_INGREDIENTS -> getString(R.string.wizard_next_instructions);
                default -> getString(R.string.wizard_next_review);
            });
            btnNext.setIcon(androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_arrow_forward));
            btnNext.setIconGravity(com.google.android.material.button.MaterialButton.ICON_GRAVITY_END);
            btnNext.setEnabled(true);
            btnNext.setAlpha(1f);
        }
    }

    private void confirmDiscard() {
        OrganicConfirmDialog.show(this, getString(R.string.wizard_discard_title), getString(R.string.wizard_discard_message),
                getString(R.string.wizard_action_discard), getString(R.string.wizard_action_keep_editing), true, () -> {
                    viewModel.discardDraft();
                    finish();
                });
    }
}
