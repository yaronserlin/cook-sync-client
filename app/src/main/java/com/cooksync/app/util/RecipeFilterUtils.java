package com.cooksync.app.util;

import com.dtos.response.recipe.RecipePreviewResponse;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Utility class for filtering and sorting recipe previews. Extracted from duplicated logic
 * that previously lived independently in both {@code HomeViewModel} and
 * {@code SearchViewModel}, so filter/sort rules are defined exactly once.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 07/08/2026
 */
public class RecipeFilterUtils {

    /**
     * Applies difficulty, minimum-rating, maximum-total-time, and tag filters to
     * {@code source}, then sorts the result. Does not mutate {@code source}.
     *
     * Complexity:
     * Time: O(n log n) where n is {@code source.size()}, dominated by the final sort
     * Space: O(n) for the filtered copy
     *
     * @param source the unfiltered recipe previews
     * @param difficulty required difficulty level, or {@code null} to skip this filter
     * @param minRating minimum average rating (inclusive), or {@code null} to skip this filter
     * @param maxTotalTimeMinutes maximum prep+cook time in minutes, or {@code null} to skip this filter
     * @param selectedTags tag names every result must have, or {@code null}/empty to skip this filter
     * @param sortBy sort mode: {@code "Top Rated"}, {@code "Shortest Time"}, or anything else
     *               (including {@code null}) for newest-first
     * @return a new, filtered and sorted list
     */
    public static List<RecipePreviewResponse> applyFiltersAndSort(
            List<RecipePreviewResponse> source,
            String difficulty,
            Double minRating,
            Integer maxTotalTimeMinutes,
            Collection<String> selectedTags,
            String sortBy
    ) {
        List<RecipePreviewResponse> displayed = new ArrayList<>(source);

        if (difficulty != null) {
            displayed.removeIf(r -> r.difficulty() == null || !r.difficulty().equalsIgnoreCase(difficulty));
        }
        if (minRating != null) {
            displayed.removeIf(r -> r.averageRating() == null || r.averageRating() < minRating);
        }
        if (maxTotalTimeMinutes != null) {
            displayed.removeIf(r -> (r.prepTimeMinutes() + r.cookTimeMinutes()) > maxTotalTimeMinutes);
        }
        if (selectedTags != null && !selectedTags.isEmpty()) {
            displayed.removeIf(r -> r.tags() == null || !selectedTags.stream().allMatch(selected ->
                    r.tags().stream().anyMatch(tag -> tag.name() != null && tag.name().equalsIgnoreCase(selected))));
        }

        String sort = Objects.requireNonNullElse(sortBy, "");
        Comparator<RecipePreviewResponse> comparator;
        if (Objects.equals(sort, "Top Rated")) {
            comparator = Comparator.comparing(
                    (RecipePreviewResponse r) -> r.averageRating() == null ? 0.0 : r.averageRating(),
                    Comparator.reverseOrder());
        } else if (Objects.equals(sort, "Shortest Time")) {
            comparator = Comparator.comparingInt(
                    r -> r.prepTimeMinutes() + r.cookTimeMinutes());
        } else {
            comparator = Comparator.comparing(
                    (RecipePreviewResponse r) -> r.createdAt() == null ? "" : r.createdAt(),
                    Comparator.reverseOrder());
        }
        displayed.sort(comparator);

        return displayed;
    }
}
