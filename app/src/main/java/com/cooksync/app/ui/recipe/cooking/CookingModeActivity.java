package com.cooksync.app.ui.recipe.cooking;
import com.cooksync.app.ui.base.BaseActivity;
import com.cooksync.app.ui.base.BaseViewModel;
import com.cooksync.app.ui.base.Navigator;
import com.cooksync.app.ui.base.ViewModelFactory;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.cooksync.app.R;
import com.cooksync.app.data.datasource.local.CookingPreferencesStore;
import com.cooksync.app.domain.ApiResult;
import com.cooksync.app.ui.base.BaseActivity;
import com.cooksync.app.ui.common.FullscreenImageActivity;
import com.cooksync.app.ui.base.Navigator;
import com.cooksync.app.ui.common.OrganicConfirmDialog;
import com.cooksync.app.ui.base.ViewModelFactory;
import com.cooksync.app.ui.recipe.detail.RecipeDetailActivity;
import com.cooksync.app.ui.recipe.review.ReviewActivity;
import com.dtos.response.ingredient.IngredientResponse;
import com.dtos.response.instruction.InstructionResponse;
import com.dtos.response.note.NoteResponse;
import com.dtos.response.recipe.RecipeResponse;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

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
public class CookingModeActivity extends BaseActivity {

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
    private View stepImageContainer;
    private ImageView stepImage;
    private View usesContainer;
    private com.google.android.material.chip.ChipGroup cgUses;
    private TextView tvStepNote;
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

        // Cooking mode is meant to stay on screen through long, hands-off cook times, unless the
        // user turned this off in Cooking preferences.
        if (CookingPreferencesStore.isScreenAwakeEnabled()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        recipeId = getIntent().getStringExtra(RecipeDetailActivity.EXTRA_RECIPE_ID);
        if (recipeId == null) {
            finish();
            return;
        }

        viewModel = new ViewModelProvider(this, new ViewModelFactory()).get(CookingModeViewModel.class);

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
        stepImageContainer = findViewById(R.id.step_image_container);
        stepImage = findViewById(R.id.step_image);
        usesContainer = findViewById(R.id.uses_container);
        cgUses = findViewById(R.id.cg_uses);
        tvStepNote = findViewById(R.id.tv_step_note);
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
        btnPrev.setOnClickListener(v -> viewModel.prevStep(steps));
        btnPrimaryAction.setOnClickListener(v -> {
            int index = currentIndex();
            if (index >= steps.size() - 1) {
                Intent intent = new Intent();
                intent.putExtra(RecipeDetailActivity.EXTRA_RECIPE_ID, recipeId);
                Navigator.start(this, ReviewActivity.class, intent);
                Navigator.finish(this);
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
                if (!steps.isEmpty()) {
                    viewModel.goToStep(0, steps);
                } else {
                    renderCurrentStep();
                }
            } else if (result instanceof ApiResult.Error<RecipeResponse> error) {
                showError(error.getMessage(), null);
                finish();
            }
        });

        viewModel.getNotesResult().observe(this, result -> {
            if (result instanceof ApiResult.Success<List<NoteResponse>> success) {
                notesByInstructionId.clear();
                for (NoteResponse note : success.getData()) {
                    if (note.instructionId() != null) notesByInstructionId.put(note.instructionId(), note);
                }
                renderCurrentStep();
            }
        });

        viewModel.getCurrentStepIndex().observe(this, index -> {
            renderCurrentStep();
            updateProgressBars(Objects.requireNonNullElse(index, 0));
        });

        viewModel.getTimerRemainingSeconds().observe(this, this::updateTimerClock);

        viewModel.getTimerRunning().observe(this, running -> updateTimerToggleButton());
        viewModel.getTimerStarted().observe(this, started -> updateTimerToggleButton());

        viewModel.getTimerFinishedEvent().observe(this, event -> {
            Boolean finished = event.getContentIfNotHandled();
            if (finished != null) notifyTimerFinished();
        });

        viewModel.getCheckedIngredientIds().observe(this, ids -> {
            if (steps.isEmpty()) return;
            int index = Math.min(currentIndex(), steps.size() - 1);
            bindUsesIngredients(steps.get(index));
        });
    }

    private void updateTimerToggleButton() {
        boolean isRunning = Objects.equals(viewModel.getTimerRunning().getValue(), true);
        boolean isStarted = Objects.equals(viewModel.getTimerStarted().getValue(), true);

        if (isRunning) {
            btnTimerToggle.setText(getString(R.string.timer_pause));
            btnTimerToggle.setIconResource(R.drawable.ic_pause);
        } else if (isStarted) {
            btnTimerToggle.setText(getString(R.string.timer_resume));
            btnTimerToggle.setIconResource(R.drawable.ic_play);
        } else {
            btnTimerToggle.setText(getString(R.string.timer_start));
            btnTimerToggle.setIconResource(R.drawable.ic_play);
        }
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
            if (i < count - 1) params.setMarginEnd(dpToPx(4));
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
        if (steps.isEmpty()) return;
        int index = Math.min(currentIndex(), steps.size() - 1);
        InstructionResponse step = steps.get(index);

        tvStepLabel.setText(getString(R.string.cook_step_label_format, index + 1, steps.size()));
        tvStepText.setText(buildStepText(step));
        applyStepTextSize(step.description());

        bindStepImage(step);
        bindUsesIngredients(step);

        NoteResponse note = notesByInstructionId.get(step.id());
        boolean hasNote = note != null && note.note() != null && !note.note().isEmpty();
        if (hasNote) {
            tvStepNote.setText(getString(R.string.cook_note_prefix_format, note.note()));
            tvStepNote.setVisibility(View.VISIBLE);
        } else {
            tvStepNote.setVisibility(View.GONE);
        }

        boolean hasTimer = Objects.equals(step.hasTimer(), true) && step.timeSeconds() != null && step.timeSeconds() > 0;
        timerContainer.setVisibility(hasTimer ? View.VISIBLE : View.GONE);
        if (hasTimer) currentStepTimerTotalSeconds = step.timeSeconds();

        btnPrev.setEnabled(index > 0);
        btnPrev.setAlpha(index > 0 ? 1f : 0.4f);

        boolean isLastStep = index == steps.size() - 1;
        btnPrimaryAction.setText(isLastStep ? getString(R.string.cook_done_action) : getString(R.string.cook_next_action));
        btnPrimaryAction.setIconResource(isLastStep ? R.drawable.ic_check : R.drawable.ic_arrow_forward);
        btnPrimaryAction.setIconGravity(isLastStep ? MaterialButton.ICON_GRAVITY_START : MaterialButton.ICON_GRAVITY_END);
    }

    /**
     * Sizes the step instruction text down as its length grows, so a short step ("Preheat the
     * oven.") reads large from across the kitchen while a long, detailed step still renders at
     * a comfortable size instead of overflowing into a wall of oversized text. Unlike Android's
     * built-in autosize-with-maxLines, this never truncates: the text always renders in full,
     * with the surrounding {@link androidx.core.widget.NestedScrollView} handling any overflow
     * by scrolling, exactly as it did before this sizing was added.
     *
     * @param description the current step's instruction text
     */
    private void applyStepTextSize(@Nullable String description) {
        int length = description == null ? 0 : description.length();
        float sizeSp;
        if (length <= 60) sizeSp = 34f;
        else if (length <= 120) sizeSp = 29f;
        else if (length <= 220) sizeSp = 25f;
        else if (length <= 350) sizeSp = 22f;
        else sizeSp = 19f;
        tvStepText.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
    }

    /** Matches a sentence boundary: punctuation immediately followed by whitespace and a capital
     *  letter or an opening parenthesis. Deliberately does not match after decimals ("1.5 cups")
     *  or abbreviations ("1 tbsp. sugar"), since the character following the space there is
     *  lowercase or a digit, not the capital/paren this pattern requires. */
    private static final Pattern SENTENCE_BOUNDARY = Pattern.compile("(?<=[.!?])\\s+(?=[A-Z(])");

    /**
     * Renders a step's instruction as short, scannable paragraphs instead of one dense block:
     * splits the description on sentence boundaries (a blank line between each), visually mutes
     * any sentence that's a parenthetical aside (e.g. an ingredient substitution note) so the
     * primary instruction reads first, and bolds/tints any mention of an ingredient this step
     * uses so the running text ties back to the "this step uses" chips below it.
     *
     * @param step the instruction step currently shown
     * @return the formatted, spannable instruction text
     */
    private CharSequence buildStepText(@NonNull InstructionResponse step) {
        String description = step.description();
        if (description == null || description.isBlank()) return "";
        String[] sentences = SENTENCE_BOUNDARY.split(description.trim());
        SpannableStringBuilder builder = new SpannableStringBuilder();
        for (int i = 0; i < sentences.length; i++) {
            String sentence = sentences[i].trim();
            if (sentence.isEmpty()) continue;
            int start = builder.length();
            builder.append(sentence);
            int end = builder.length();
            if (sentence.startsWith("(")) {
                builder.setSpan(new RelativeSizeSpan(0.85f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                builder.setSpan(new ForegroundColorSpan(getColor(R.color.color_accent_300)), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (i < sentences.length - 1) {
                builder.append("\n\n");
            }
        }
        highlightIngredientMentions(builder, step.ingredients());
        return builder;
    }

    /**
     * Bolds and tints every mention of one of this step's ingredient names within the already
     * sentence-split instruction text, so a long paragraph gets visual anchor points the eye can
     * latch onto instead of reading as undifferentiated prose. Matching is whole-word and
     * case-insensitive; an ingredient whose name doesn't literally appear in the prose (or whose
     * name is itself a long parenthetical, as recipe data occasionally has) is simply skipped —
     * this is a readability aid, not a requirement that every ingredient be found.
     *
     * @param builder the instruction text built so far, mutated in place
     * @param ingredients this step's ingredients, or {@code null}
     */
    private void highlightIngredientMentions(@NonNull SpannableStringBuilder builder, @Nullable Set<IngredientResponse> ingredients) {
        if (ingredients == null || ingredients.isEmpty()) return;
        String text = builder.toString();
        for (IngredientResponse ingredient : ingredients) {
            String name = ingredient.name() == null ? null : ingredient.name().trim();
            if (name == null || name.isEmpty()) continue;
            Matcher matcher;
            try {
                matcher = Pattern
                        .compile("\\b" + Pattern.quote(name) + "\\b", Pattern.CASE_INSENSITIVE)
                        .matcher(text);
            } catch (PatternSyntaxException e) {
                continue;
            }
            while (matcher.find()) {
                builder.setSpan(new StyleSpan(Typeface.BOLD), matcher.start(), matcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                builder.setSpan(new ForegroundColorSpan(getColor(R.color.color_accent_400)), matcher.start(), matcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }

    /**
     * Loads the current step's illustration image, if it has one, matching the recipe detail
     * screen's treatment. The container is sized as soon as we know there's an image to try,
     * since Glide can only resolve a target size for a view that actually takes part in
     * layout — a GONE ancestor is skipped during measure/layout and never gets one. Only the
     * image itself stays invisible until the load succeeds, so a slow fetch never shows a
     * broken tile.
     *
     * @param step the instruction step currently shown
     */
    private void bindStepImage(@NonNull InstructionResponse step) {
        String imageUrl = step.imageUrl();
        boolean hasImage = imageUrl != null && !imageUrl.isBlank();
        stepImageContainer.setVisibility(hasImage ? View.VISIBLE : View.GONE);
        if (!hasImage) return;
        stepImage.setVisibility(View.VISIBLE);
        Glide.with(stepImage.getContext())
                .load(imageUrl)
                .placeholder(R.drawable.bg_skeleton_bone)
                .error(R.drawable.ic_image_failed)
                .centerCrop()
                .into(stepImage);
        stepImage.setOnClickListener(v -> openFullscreenImage(imageUrl));
    }

    /**
     * Opens {@link FullscreenImageActivity} to view the current step's photo full-screen.
     *
     * @param imageUrl the step photo's URL
     */
    private void openFullscreenImage(String imageUrl) {
        Intent intent = new Intent();
        intent.putExtra(FullscreenImageActivity.EXTRA_IMAGE_URL, imageUrl);
        Navigator.start(this, FullscreenImageActivity.class, intent);
    }

    /**
     * Populates the "this step uses" chip row from the step's own embedded ingredient set,
     * matching the same "name · quantity unit" format used on the recipe detail screen. Each
     * chip is tappable so the cook can mark an ingredient as already added to the dish; the
     * checked/unchecked set lives in the ViewModel and survives step navigation.
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
        Set<String> checkedIds = viewModel.getCheckedIngredientIds().getValue();
        if (checkedIds == null) checkedIds = Collections.emptySet();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (IngredientResponse ingredient : step.ingredients()) {
            Chip chip = (Chip) inflater.inflate(R.layout.item_filter_chip, cgUses, false);
            chip.setId(View.generateViewId());
            chip.setClickable(true);
            chip.setCheckable(true);
            chip.setTextSize(15f);
            String unitCode = ingredient.unit() != null ? ingredient.unit().code() : "";
            String label = getString(R.string.cook_uses_chip_format, ingredient.name(), ingredient.quantity(), unitCode).trim();
            boolean checked = checkedIds.contains(ingredient.id());
            bindUsesChipState(chip, label, checked);
            chip.setOnClickListener(v -> viewModel.toggleIngredientChecked(ingredient.id()));
            cgUses.addView(chip);
        }
    }

    /**
     * Applies the checked/unchecked look to one "this step uses" chip: an accent-400 fill with
     * a checkmark and struck-through text once marked added, versus the default accent-700
     * fill while still pending.
     *
     * @param chip the chip to style
     * @param label the ingredient's display text
     * @param checked whether this ingredient is marked as already added
     */
    private void bindUsesChipState(@NonNull Chip chip, @NonNull String label, boolean checked) {
        chip.setChecked(checked);
        chip.setCheckedIconVisible(checked);
        if (checked) {
            chip.setCheckedIcon(ContextCompat.getDrawable(this, R.drawable.ic_check));
            chip.setCheckedIconTint(ColorStateList.valueOf(getColor(R.color.color_accent_900)));
            chip.setChipBackgroundColor(ColorStateList.valueOf(getColor(R.color.color_accent_400)));
            chip.setTextColor(getColor(R.color.color_accent_900));
            SpannableString struckText = new SpannableString(label);
            struckText.setSpan(new StrikethroughSpan(), 0, label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            chip.setText(struckText);
        } else {
            chip.setChipBackgroundColor(ColorStateList.valueOf(getColor(R.color.color_accent_700)));
            chip.setTextColor(getColor(R.color.color_accent_100));
            chip.setText(label);
        }
    }

    private void updateTimerClock(Integer remainingSeconds) {
        if (remainingSeconds == null) return;
        int hours = remainingSeconds / 3600;
        int minutes = (remainingSeconds % 3600) / 60;
        int seconds = remainingSeconds % 60;
        if (hours > 0) tvTimerClock.setText(getString(R.string.cook_timer_clock_format_hours, hours, minutes, seconds));
        else if (minutes > 0) tvTimerClock.setText(getString(R.string.cook_timer_clock_format_minutes, minutes, seconds));
        else tvTimerClock.setText(getString(R.string.cook_timer_clock_format_seconds, seconds));

        int max = timerRing.getMax();
        int elapsedFraction = currentStepTimerTotalSeconds <= 0 ? 0 : max - (int) ((remainingSeconds / (float) currentStepTimerTotalSeconds) * max);
        timerRing.setProgressCompat(elapsedFraction, true);
    }

    /**
     * Opens a hours/minutes/seconds picker to overwrite the current step's timer duration, seeded
     * with whatever time is currently showing on the clock.
     */
    private void showSetTimerDialog() {
        Integer remaining = viewModel.getTimerRemainingSeconds().getValue();
        int current = remaining == null ? 0 : remaining;
        com.cooksync.app.ui.common.TimePickerDialog.show(this, R.string.dialog_set_timer_title, current,
                com.cooksync.app.ui.common.TimePickerDialog.Mode.HH_MM_SS, totalSeconds -> {
            currentStepTimerTotalSeconds = Math.max(totalSeconds, 1);
            viewModel.setTimerSeconds(currentStepTimerTotalSeconds);
        });
    }

    /**
     * Alerts the user that the current step's timer has run out: plays the device's default
     * alarm/notification sound, vibrates, and shows a dialog so the cook notices even if the
     * phone is across the kitchen. The sound keeps playing (some default tones loop) until the
     * dialog is dismissed, however that happens — "Got it", tapping outside, or back — so it
     * never keeps ringing after the user has already acknowledged it. Sound and vibration are
     * skipped if the user turned them off in Cooking preferences; the dialog itself still shows.
     */
    private void notifyTimerFinished() {
        if (CookingPreferencesStore.isTimerSoundEnabled()) {
            playTimerFinishedSound();
            vibrate();
        }

        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_CookSync_Dialog)
                .setTitle(R.string.dialog_timer_finished_title)
                .setMessage(R.string.dialog_timer_finished_message)
                .setPositiveButton(R.string.action_got_it, null)
                .setCancelable(true)
                .setOnDismissListener(dialog -> stopTimerFinishedSound())
                .show();
    }

    private void playTimerFinishedSound() {
        stopTimerFinishedSound();
        try {
            Uri soundUri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM);
            if (soundUri == null) soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            activeRingtone = RingtoneManager.getRingtone(this, soundUri);
            if (activeRingtone != null) activeRingtone.play();
        } catch (Exception e) {
            android.util.Log.w("CookingModeActivity", "Failed to play timer finished sound", e);
        }
    }

    private void stopTimerFinishedSound() {
        if (activeRingtone != null && activeRingtone.isPlaying()) activeRingtone.stop();
        activeRingtone = null;
    }

    private void vibrate() {
        Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator == null || !vibrator.hasVibrator()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
        else vibrator.vibrate(500);
    }

    private void confirmExit() {
        OrganicConfirmDialog.show(this, getString(R.string.dialog_stop_cooking_title),
                getString(R.string.dialog_stop_cooking_message),
                getString(R.string.action_stop), getString(R.string.action_keep_cooking), false, this::finish);
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
