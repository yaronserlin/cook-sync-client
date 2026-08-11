package com.cooksync.app.ui.recipe.cooking;
import com.cooksync.app.ui.base.BaseActivity;
import com.cooksync.app.ui.base.BaseViewModel;
import com.cooksync.app.ui.base.Navigator;
import com.cooksync.app.ui.base.ViewModelFactory;

import android.os.CountDownTimer;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.cooksync.app.data.repository.RecipeRepository;
import com.cooksync.app.domain.ApiResult;
import com.cooksync.app.domain.Event;
import com.cooksync.app.ui.base.BaseViewModel;
import com.dtos.response.instruction.InstructionResponse;
import com.dtos.response.note.NoteResponse;
import com.dtos.response.recipe.RecipeResponse;

import java.util.List;

/**
 * Manages data state for {@link CookingModeActivity}: the loaded recipe, which step is
 * currently shown, and the optional per-step countdown timer. The step index and timer are
 * both held here (rather than in the Activity) so that Activity re-creation on rotation
 * doesn't reset a cook's progress mid-recipe.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 04/08/2026
 */
public class CookingModeViewModel extends BaseViewModel {

    private final RecipeRepository repository;

    private final MutableLiveData<ApiResult<RecipeResponse>> recipeResult = new MutableLiveData<>();
    private final MutableLiveData<ApiResult<List<NoteResponse>>> notesResult = new MutableLiveData<>();
    private final MutableLiveData<Integer> currentStepIndex = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> timerRemainingSeconds = new MutableLiveData<>();
    private final MutableLiveData<Boolean> timerRunning = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> timerStarted = new MutableLiveData<>(false);
    private final MutableLiveData<Event<Boolean>> timerFinishedEvent = new MutableLiveData<>();

    private CountDownTimer countDownTimer;

    public CookingModeViewModel(RecipeRepository repository) {
        this.repository = repository;
    }

    public LiveData<ApiResult<RecipeResponse>> getRecipeResult() { return recipeResult; }
    public LiveData<ApiResult<List<NoteResponse>>> getNotesResult() { return notesResult; }
    public LiveData<Integer> getCurrentStepIndex() { return currentStepIndex; }
    public LiveData<Integer> getTimerRemainingSeconds() { return timerRemainingSeconds; }
    public LiveData<Boolean> getTimerRunning() { return timerRunning; }
    public LiveData<Boolean> getTimerStarted() { return timerStarted; }
    public LiveData<Event<Boolean>> getTimerFinishedEvent() { return timerFinishedEvent; }

    public void loadRecipe(String recipeId) {
        repository.getRecipeDetail(recipeId, recipeResult);
    }

    public void loadNotes(String recipeId) {
        repository.getAllPersonalNotes(recipeId, notesResult);
    }

    public void goToStep(int index, @NonNull List<InstructionResponse> steps) {
        if (index < 0 || index >= steps.size()) {
            return;
        }
        cancelTimer();
        currentStepIndex.setValue(index);
        InstructionResponse step = steps.get(index);
        if (Boolean.TRUE.equals(step.hasTimer()) && step.timeSeconds() != null) {
            timerRemainingSeconds.setValue(step.timeSeconds());
        } else {
            timerRemainingSeconds.setValue(null);
        }
        timerRunning.setValue(false);
        timerStarted.setValue(false);
    }

    /**
     * Advances to the next step, if one exists.
     *
     * @param steps the recipe's ordered instruction steps
     */
    public void nextStep(@NonNull List<InstructionResponse> steps) {
        int index = currentStepIndex.getValue() == null ? 0 : currentStepIndex.getValue();
        goToStep(index + 1, steps);
    }

    /**
     * Returns to the previous step, if one exists.
     *
     * @param steps the recipe's ordered instruction steps
     */
    public void prevStep(@NonNull List<InstructionResponse> steps) {
        int index = currentStepIndex.getValue() == null ? 0 : currentStepIndex.getValue();
        goToStep(index - 1, steps);
    }

    /**
     * Starts the current step's timer if it's paused, or pauses it if it's running.
     */
    public void toggleTimer() {
        if (Boolean.TRUE.equals(timerRunning.getValue())) {
            cancelTimer();
            timerRunning.setValue(false);
        } else {
            startTimer();
        }
    }

    /**
     * Adds one minute to the timer, restarting the countdown from the new remaining duration
     * if it's currently running.
     */
    public void addMinute() {
        int remaining = timerRemainingSeconds.getValue() == null ? 0 : timerRemainingSeconds.getValue();
        timerRemainingSeconds.setValue(remaining + 60);
        if (Boolean.TRUE.equals(timerRunning.getValue())) {
            startTimer();
        }
    }

    /**
     * Overwrites the timer's remaining duration with a user-chosen value (from the "edit
     * timer" dialog), pausing whatever was running so the new duration doesn't immediately
     * start counting down unexpectedly.
     *
     * @param totalSeconds the new duration in seconds; values below 1 are clamped to 1
     */
    public void setTimerSeconds(int totalSeconds) {
        cancelTimer();
        timerRemainingSeconds.setValue(Math.max(totalSeconds, 1));
        timerRunning.setValue(false);
        timerStarted.setValue(false);
    }

    private void startTimer() {
        Integer remaining = timerRemainingSeconds.getValue();
        if (remaining == null || remaining <= 0) {
            return;
        }
        cancelTimer();
        countDownTimer = new CountDownTimer(remaining * 1000L, 1000L) {
            @Override
            public void onTick(long millisUntilFinished) {
                timerRemainingSeconds.setValue((int) Math.ceil(millisUntilFinished / 1000.0));
            }

            @Override
            public void onFinish() {
                timerRemainingSeconds.setValue(0);
                timerRunning.setValue(false);
                timerStarted.setValue(false);
                timerFinishedEvent.setValue(new Event<>(true));
            }
        }.start();
        timerRunning.setValue(true);
        timerStarted.setValue(true);
    }

    private void cancelTimer() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        cancelTimer();
    }
}
