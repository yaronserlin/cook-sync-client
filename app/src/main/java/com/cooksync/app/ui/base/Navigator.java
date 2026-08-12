package com.cooksync.app.ui.base;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityOptionsCompat;

import com.cooksync.app.R;
import com.cooksync.app.ui.common.OrganicConfirmDialog;
import com.cooksync.app.ui.common.OrganicToast;

/**
 * Centralizes every activity-to-activity transition in the app behind a single utility, so the
 * design's fade + slide-up motion is applied consistently everywhere instead of each screen
 * wiring its own transition (or, as was the case before this class existed, none at all). A pure
 * navigation concern, so it lives here in {@code ui/common} next to {@link OrganicToast} and
 * {@link OrganicConfirmDialog} rather than in any ViewModel.
 *
 * <p>{@link #finish(Activity)} is a thin, symmetric counterpart to {@link #start}; the exit
 * transition itself is applied once, centrally, by {@link BaseActivity#finish()}. Every Activity
 * in the app extends {@link BaseActivity}, so every close — explicit, or hardware/gesture back —
 * gets the same animation without each screen needing to opt in.</p>
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 07/08/2026
 */
public final class Navigator {

    /**
     * Intent extra key for a recipe's ID, shared by every screen that navigates to
     * {@link com.cooksync.app.ui.recipe.detail.RecipeDetailActivity} or reads a recipe ID off an
     * intent that originated there (e.g. {@link com.cooksync.app.ui.recipe.cooking.CookingModeActivity}),
     * so those screens depend on this one shared constant instead of reaching into each other's
     * class just for it.
     */
    public static final String EXTRA_RECIPE_ID = "extra_recipe_id";

    private Navigator() {
    }

    /**
     * Starts {@code target} from {@code from} with no extras, applying the app's standard enter
     * transition.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param from   the activity initiating the navigation
     * @param target the activity class to start
     */
    public static void start(@NonNull Activity from, @NonNull Class<? extends Activity> target) {
        start(from, target, null);
    }

    /**
     * Starts {@code target} from {@code from}, applying the app's standard enter transition.
     * {@code extras}, if given, is expected to carry only {@code putExtra} data and/or intent
     * flags (no component set) — both are merged onto the intent actually used to start
     * {@code target}, since {@link Intent#putExtras(Intent)} alone does not copy flags.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param from   the activity initiating the navigation
     * @param target the activity class to start
     * @param extras optional extras/flags to merge onto the navigation intent, may be {@code null}
     */
    public static void start(@NonNull Activity from, @NonNull Class<? extends Activity> target,
                              @Nullable Intent extras) {
        start(from, buildIntent(from, target, extras));
    }

    /**
     * Starts an already fully-built {@code intent} (e.g. from a screen's own
     * {@code newIntentWith...} factory method) from {@code from}, applying the app's standard
     * enter transition.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param from   the activity initiating the navigation
     * @param intent the intent to start, already targeting a destination activity
     */
    public static void start(@NonNull Activity from, @NonNull Intent intent) {
        from.startActivity(intent);
        applyOpenTransition(from);
    }

    /**
     * Starts {@code target} from a non-{@link Activity} {@code context}. The app's single such
     * call site is the forced-logout redirect in {@code CookSyncApplication}, which may run with
     * no foreground activity to hand a transition to. Adds {@link Intent#FLAG_ACTIVITY_NEW_TASK}
     * automatically if {@code extras} did not already set it, since starting an activity from a
     * non-activity context requires it.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param context a non-activity context (typically the {@code Application})
     * @param target  the activity class to start
     * @param extras  optional extras/flags to merge onto the navigation intent, may be {@code null}
     */
    public static void start(@NonNull Context context, @NonNull Class<? extends Activity> target,
                              @Nullable Intent extras) {
        Intent intent = buildIntent(context, target, extras);
        if ((intent.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) == 0) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        ActivityOptionsCompat options = ActivityOptionsCompat.makeCustomAnimation(
                context, R.anim.enter_fadeup, R.anim.exit_fadeup);
        context.startActivity(intent, options.toBundle());
    }

    /**
     * Finishes {@code activity}. A thin, symmetric counterpart to {@link #start} kept for call
     * sites that read more clearly paired with it (e.g. "start the next screen, then finish this
     * one"); the exit transition itself is applied by {@link BaseActivity#finish()}, not here.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param activity the activity to finish
     */
    public static void finish(@NonNull Activity activity) {
        activity.finish();
    }

    /**
     * Applies the app's standard exit transition to a just-finished {@code activity}. Called
     * exclusively by {@link BaseActivity#finish()} so every close in the app is covered from one
     * place, regardless of whether it went through {@link #finish(Activity)} or a screen's own
     * bare {@code finish()} call, including hardware/gesture back.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param activity the activity that just finished
     */
    static void applyCloseTransition(@NonNull Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            activity.overrideActivityTransition(
                    Activity.OVERRIDE_TRANSITION_CLOSE, R.anim.enter_fadeup, R.anim.exit_fadeup);
        } else {
            activity.overridePendingTransition(R.anim.enter_fadeup, R.anim.exit_fadeup);
        }
    }

    /**
     * Applies the app's standard enter transition immediately after {@code from} has started
     * another activity.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param from the activity that just started another activity
     */
    private static void applyOpenTransition(@NonNull Activity from) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            from.overrideActivityTransition(
                    Activity.OVERRIDE_TRANSITION_OPEN, R.anim.enter_fadeup, R.anim.exit_fadeup);
        } else {
            from.overridePendingTransition(R.anim.enter_fadeup, R.anim.exit_fadeup);
        }
    }

    /**
     * Builds an intent targeting {@code target}, merging in {@code extras}' bundle and flags if
     * given.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param context the context to build the intent with
     * @param target  the activity class to target
     * @param extras  optional extras/flags to merge in, may be {@code null}
     * @return an intent ready to start {@code target}
     */
    private static Intent buildIntent(@NonNull Context context, @NonNull Class<? extends Activity> target,
                                       @Nullable Intent extras) {
        Intent intent = new Intent(context, target);
        if (extras != null) {
            intent.putExtras(extras);
            intent.addFlags(extras.getFlags());
        }
        return intent;
    }
}
