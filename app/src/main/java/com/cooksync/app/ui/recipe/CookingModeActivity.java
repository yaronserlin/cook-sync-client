package com.cooksync.app.ui.recipe;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.cooksync.app.R;
import com.cooksync.app.domain.ApiResult;
import com.cooksync.app.domain.Event;
import com.cooksync.app.ui.common.NoteEditDialog;
import com.cooksync.app.ui.common.OrganicConfirmDialog;
import com.cooksync.app.ui.common.OrganicToast;
import com.cooksync.app.ui.detail.RecipeDetailActivity;
import com.dtos.response.ingredient.IngredientResponse;
import com.dtos.response.instruction.InstructionResponse;
import com.dtos.response.note.NoteResponse;
import com.dtos.response.recipe.RecipeResponse;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Full-screen, step-by-step cooking view. Shows one instruction at a time with the
 * ingredients that step uses, any private note attached to that step, and an optional
 * countdown timer, matching the design's dark "is.cook" screen. Entered from
 * {@link RecipeDetailActivity}'s "Start cooking" button; on the last step, "Done — rate it"
 * hands off to {@link ReviewActivity}.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 04/08/2026
 */
public class CookingModeActivity extends AppCompatActivity {

    private CookingModeViewModel viewModel;
    private String recipeId;

    private final List<InstructionResponse> steps = new ArrayList<>();
    private final Map<String, NoteResponse> notesByInstructionId = new HashMap<>();
    private final List<View> progressBarSegments = new ArrayList<>();

    /** Total duration of the current step's timer, used to compute the ring's fill fraction. */
    private int currentStepTimerTotalSeconds;

    /** The "time's up" alert sound currently playing, if any; stopped when its dialog closes. */
    private Ringtone activeRingtone;

    private TextView tvTitle;
    private LinearLayout llProgressBars;
    private TextView tvStepLabel;
    private TextView tvStepText;
    private View usesContainer;
    private com.google.android.material.chip.ChipGroup cgUses;
    private TextView tvStepNote;
    private MaterialButton btnStepNoteEdit;
    private View timerContainer;
    private CircularProgressIndicator timerRing;
    private TextView tvTimerClock;
    private MaterialButton btnTimerToggle;
    private MaterialButton btnPrev;
    private MaterialButton btnPrimaryAction;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cooking_mode);

        // Cooking mode is meant to stay on screen through long, hands-off cook times.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        recipeId = getIntent().getStringExtra(RecipeDetailActivity.EXTRA_RECIPE_ID);
        if (recipeId == null) {
            finish();
            return;
        }

        viewModel = new ViewModelProvider(this).get(CookingModeViewModel.class);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                confirmExit();
            }
        });

        initViews();
        setupObservers();

        viewModel.loadRecipe(recipeId);
        viewModel.loadNotes(recipeId);
    }

    private void initViews() {
        tvTitle = findViewById(R.id.tv_cook_title);
        llProgressBars = findViewById(R.id.ll_progress_bars);
        tvStepLabel = findViewById(R.id.tv_step_label);
        tvStepText = findViewById(R.id.tv_step_text);
        usesContainer = findViewById(R.id.uses_container);
        cgUses = findViewById(R.id.cg_uses);
        tvStepNote = findViewById(R.id.tv_step_note);
        btnStepNoteEdit = findViewById(R.id.btn_step_note_edit);
        timerContainer = findViewById(R.id.timer_container);
        timerRing = findViewById(R.id.timer_ring);
        tvTimerClock = findViewById(R.id.tv_timer_clock);
        btnTimerToggle = findViewById(R.id.btn_timer_toggle);
        btnPrev = findViewById(R.id.btn_prev);
        btnPrimaryAction = findViewById(R.id.btn_primary_action);

        findViewById(R.id.btn_exit).setOnClickListener(v -> confirmExit());
        findViewById(R.id.btn_timer_add).setOnClickListener(v -> viewModel.addMinute());
        findViewById(R.id.btn_timer_edit).setOnClickListener(v -> showSetTimerDialog());
        tvTimerClock.setOnClickListener(v -> showSetTimerDialog());
        btnTimerToggle.setOnClickListener(v -> viewModel.toggleTimer());
        btnStepNoteEdit.setOnClickListener(v -> openStepNoteEditor());
        tvStepNote.setOnClickListener(v -> openStepNoteEditor());
        btnPrev.setOnClickListener(v -> viewModel.prevStep(steps));
        btnPrimaryAction.setOnClickListener(v -> {
            int index = currentIndex();
            if (index >= steps.size() - 1) {
                Intent intent = new Intent(this, ReviewActivity.class);
                intent.putExtra(RecipeDetailActivity.EXTRA_RECIPE_ID, recipeId);
                startActivity(intent);
                finish();
            } else {
                viewModel.nextStep(steps);
            }
        });
    }

    private void setupObservers() {
        viewModel.getRecipeResult().observe(this, result -> {
            if (result instanceof ApiResult.Success<RecipeResponse> success) {
                RecipeResponse recipe = success.getData();
                tvTitle.setText(recipe.title());
                steps.clear();
                if (recipe.instructions() != null) {
                    steps.addAll(recipe.instructions());
                    steps.sort(Comparator.comparingInt(InstructionResponse::stepNumber));
                }
                buildProgressBars(steps.size());
                renderCurrentStep();
            } else if (result instanceof ApiResult.Error<RecipeResponse> error) {
                OrganicToast.show(this, null, error.getMessage());
                finish();
            }
        });

        viewModel.getNotesResult().observe(this, result -> {
            if (result instanceof ApiResult.Success<List<NoteResponse>> success) {
                notesByInstructionId.clear();
                for (NoteResponse note : success.getData()) {
                    if (note.instructionId() != null) {
                        notesByInstructionId.put(note.instructionId(), note);
                    }
                }
                renderCurrentStep();
            }
        });

        viewModel.getNoteSaveResult().observe(this, result -> {
            if (result instanceof ApiResult.Success<Void>) {
                viewModel.loadNotes(recipeId);
            } else if (result instanceof ApiResult.Error<Void> error) {
                OrganicToast.show(this, null, error.getMessage());
            }
        });

        viewModel.getCurrentStepIndex().observe(this, index -> {
            renderCurrentStep();
            updateProgressBars(index == null ? 0 : index);
        });

        viewModel.getTimerRemainingSeconds().observe(this, this::updateTimerClock);

        viewModel.getTimerRunning().observe(this, running -> {
            boolean isRunning = Boolean.TRUE.equals(running);
            btnTimerToggle.setText(isRunning ? "Pause" : "Resume");
            btnTimerToggle.setIconResource(isRunning ? R.drawable.ic_pause : R.drawable.ic_play);
        });

        viewModel.getTimerFinishedEvent().observe(this, event -> {
            Boolean finished = event.getContentIfNotHandled();
            if (finished != null) {
                notifyTimerFinished();
            }
        });
    }

    private int currentIndex() {
        Integer index = viewModel.getCurrentStepIndex().getValue();
        return index == null ? 0 : index;
    }

    /**
     * Creates one progress-bar segment per instruction step. Rebuilt whenever the recipe
     * (re)loads, since the step count isn't known beforehand.
     *
     * @param count total number of steps
     */
    private void buildProgressBars(int count) {
        llProgressBars.removeAllViews();
        progressBarSegments.clear();
        for (int i = 0; i < count; i++) {
            View segment = new View(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            if (i < count - 1) {
                params.setMarginEnd(dpToPx(4));
            }
            segment.setLayoutParams(params);
            segment.setBackgroundResource(R.drawable.bg_pill_solid);
            llProgressBars.addView(segment);
            progressBarSegments.add(segment);
        }
        updateProgressBars(currentIndex());
    }

    private void updateProgressBars(int currentIndex) {
        for (int i = 0; i < progressBarSegments.size(); i++) {
            int colorRes = i <= currentIndex ? R.color.color_accent_400 : R.color.color_accent_800;
            progressBarSegments.get(i).setBackgroundTintList(ColorStateList.valueOf(getColor(colorRes)));
        }
    }

    private void renderCurrentStep() {
        if (steps.isEmpty()) {
            return;
        }
        int index = Math.min(currentIndex(), steps.size() - 1);
        InstructionResponse step = steps.get(index);

        tvStepLabel.setText(getString(R.string.cook_step_label_format, index + 1, steps.size()));
        tvStepText.setText(step.description());

        bindUsesIngredients(step);

        NoteResponse note = notesByInstructionId.get(step.id());
        boolean hasNote = note != null && note.note() != null && !note.note().isEmpty();
        if (hasNote) {
            tvStepNote.setText(getString(R.string.cook_note_prefix_format, note.note()));
            tvStepNote.setVisibility(View.VISIBLE);
        } else {
            tvStepNote.setVisibility(View.GONE);
        }
        btnStepNoteEdit.setText(hasNote ? "Edit note" : "Add a note");

        boolean hasTimer = Boolean.TRUE.equals(step.hasTimer()) && step.timeSeconds() != null && step.timeSeconds() > 0;
        timerContainer.setVisibility(hasTimer ? View.VISIBLE : View.GONE);
        if (hasTimer) {
            currentStepTimerTotalSeconds = step.timeSeconds();
        }

        btnPrev.setEnabled(index > 0);
        btnPrev.setAlpha(index > 0 ? 1f : 0.4f);

        boolean isLastStep = index == steps.size() - 1;
        btnPrimaryAction.setText(isLastStep ? "Done — rate it" : "Next step");
        btnPrimaryAction.setIconResource(isLastStep ? R.drawable.ic_check : R.drawable.ic_arrow_forward);
        btnPrimaryAction.setIconGravity(isLastStep ? MaterialButton.ICON_GRAVITY_START : MaterialButton.ICON_GRAVITY_END);
    }

    /**
     * Populates the "this step uses" chip row from the step's own embedded ingredient set,
     * matching the same "name · quantity unit" format used on the recipe detail screen.
     *
     * @param step the instruction step currently shown
     */
    private void bindUsesIngredients(@NonNull InstructionResponse step) {
        cgUses.removeAllViews();
        if (step.ingredients() == null || step.ingredients().isEmpty()) {
            usesContainer.setVisibility(View.GONE);
            return;
        }
        usesContainer.setVisibility(View.VISIBLE);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (IngredientResponse ingredient : step.ingredients()) {
            Chip chip = (Chip) inflater.inflate(R.layout.item_filter_chip, cgUses, false);
            chip.setId(View.generateViewId());
            chip.setClickable(false);
            chip.setCheckable(false);
            String unitCode = ingredient.unit() != null ? ingredient.unit().code() : "";
            chip.setText(getString(R.string.cook_uses_chip_format, ingredient.name(), ingredient.quantity(), unitCode).trim());
            chip.setChipBackgroundColor(ColorStateList.valueOf(getColor(R.color.color_accent_700)));
            chip.setTextColor(getColor(R.color.color_accent_100));
            cgUses.addView(chip);
        }
    }

    private void updateTimerClock(Integer remainingSeconds) {
        if (remainingSeconds == null) {
            return;
        }
        int minutes = remainingSeconds / 60;
        int seconds = remainingSeconds % 60;
        tvTimerClock.setText(getString(R.string.cook_timer_clock_format, minutes, seconds));

        int max = timerRing.getMax();
        int elapsedFraction = currentStepTimerTotalSeconds <= 0
                ? 0
                : max - (int) ((remainingSeconds / (float) currentStepTimerTotalSeconds) * max);
        timerRing.setProgressCompat(elapsedFraction, true);
    }

    /**
     * Opens the shared note editor for the currently displayed step, saving or deleting
     * through {@link CookingModeViewModel} and refreshing once the server confirms the change.
     */
    private void openStepNoteEditor() {
        if (steps.isEmpty()) {
            return;
        }
        InstructionResponse step = steps.get(Math.min(currentIndex(), steps.size() - 1));
        NoteResponse existing = notesByInstructionId.get(step.id());
        NoteEditDialog.show(this, "Note for step " + step.stepNumber(), existing != null ? existing.note() : null, new NoteEditDialog.Callback() {
            @Override
            public void onSave(@NonNull String noteText) {
                viewModel.saveNote(recipeId, step.id(), noteText);
            }

            @Override
            public void onDelete() {
                if (existing != null) {
                    viewModel.deleteNote(existing.id());
                }
            }
        });
    }

    /**
     * Opens a minutes/seconds picker to overwrite the current step's timer duration, seeded
     * with whatever time is currently showing on the clock.
     */
    private void showSetTimerDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_set_timer, null);
        NumberPicker npMinutes = dialogView.findViewById(R.id.np_minutes);
        NumberPicker npSeconds = dialogView.findViewById(R.id.np_seconds);
        npMinutes.setMinValue(0);
        npMinutes.setMaxValue(180);
        npSeconds.setMinValue(0);
        npSeconds.setMaxValue(59);

        Integer remaining = viewModel.getTimerRemainingSeconds().getValue();
        int current = remaining == null ? 0 : remaining;
        npMinutes.setValue(current / 60);
        npSeconds.setValue(current % 60);

        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_CookSync_Dialog)
                .setTitle("Set timer")
                .setView(dialogView)
                .setPositiveButton("Set", (dialog, which) -> {
                    int totalSeconds = npMinutes.getValue() * 60 + npSeconds.getValue();
                    currentStepTimerTotalSeconds = Math.max(totalSeconds, 1);
                    viewModel.setTimerSeconds(currentStepTimerTotalSeconds);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Alerts the user that the current step's timer has run out: plays the device's default
     * alarm/notification sound, vibrates, and shows a dialog so the cook notices even if the
     * phone is across the kitchen. The sound keeps playing (some default tones loop) until the
     * dialog is dismissed, however that happens — "Got it", tapping outside, or back — so it
     * never keeps ringing after the user has already acknowledged it.
     */
    private void notifyTimerFinished() {
        playTimerFinishedSound();
        vibrate();

        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_CookSync_Dialog)
                .setTitle("Time's up!")
                .setMessage("The timer for this step has finished.")
                .setPositiveButton("Got it", null)
                .setCancelable(true)
                .setOnDismissListener(dialog -> stopTimerFinishedSound())
                .show();
    }

    private void playTimerFinishedSound() {
        stopTimerFinishedSound();
        try {
            Uri soundUri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM);
            if (soundUri == null) {
                soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            }
            activeRingtone = RingtoneManager.getRingtone(this, soundUri);
            if (activeRingtone != null) {
                activeRingtone.play();
            }
        } catch (Exception e) {
            // Best-effort: a missing/unreadable system sound shouldn't block the alert dialog.
        }
    }

    private void stopTimerFinishedSound() {
        if (activeRingtone != null && activeRingtone.isPlaying()) {
            activeRingtone.stop();
        }
        activeRingtone = null;
    }

    private void vibrate() {
        Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator == null || !vibrator.hasVibrator()) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            vibrator.vibrate(500);
        }
    }

    private void confirmExit() {
        OrganicConfirmDialog.show(this, "Stop cooking?",
                "You'll pick up right where you left off — your progress is saved.",
                "Stop", "Keep cooking", false, this::finish);
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopTimerFinishedSound();
    }
}
