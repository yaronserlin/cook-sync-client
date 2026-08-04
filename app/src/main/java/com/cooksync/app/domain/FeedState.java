package com.cooksync.app.domain;

import com.dtos.response.recipe.RecipePreviewResponse;

import java.util.List;

/**
 * Specialized state hierarchy for the home feed, extending the basic {@link ApiResult}
 * to support incremental paginated loading and empty states.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 04/08/2026
 */
public abstract class FeedState {

    private FeedState() {}

    /** Initial state before any network requests. */
    public static final class Idle extends FeedState {}

    /** Feed is currently fetching either the first page or a subsequent page. */
    public static final class Loading extends FeedState {
        private final boolean initial;

        public Loading(boolean initial) {
            this.initial = initial;
        }

        public boolean isInitial() {
            return initial;
        }
    }

    /** Feed has successfully loaded a list of recipes. */
    public static final class Success extends FeedState {
        private final List<RecipePreviewResponse> recipes;
        private final boolean hasMore;

        public Success(List<RecipePreviewResponse> recipes, boolean hasMore) {
            this.recipes = recipes;
            this.hasMore = hasMore;
        }

        public List<RecipePreviewResponse> getRecipes() {
            return recipes;
        }

        public boolean hasMore() {
            return hasMore;
        }
    }

    /** Feed encountered a network or logic error. */
    public static final class Error extends FeedState {
        private final String message;

        public Error(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }
}
