package com.cooksync.app.ui.common;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Factory for the two "load the next page as the user scrolls" {@link RecyclerView.OnScrollListener}
 * shapes used across the app's paginated lists, previously reimplemented near-identically at each
 * call site.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 12/08/2026
 */
public final class PaginatingScrollListener {

    /** How many items from the end of the list a prefetch triggers, for {@link #withThreshold}. */
    private static final int DEFAULT_PREFETCH_THRESHOLD = 4;

    private PaginatingScrollListener() {
    }

    /**
     * Builds a listener that fires {@code onLoadNext} once the user has scrolled to within
     * {@link #DEFAULT_PREFETCH_THRESHOLD} items of the end of the list — used by the admin
     * console's list tabs to prefetch the next page slightly ahead of the user reaching it.
     *
     * Complexity:
     * Time: O(1) per scroll callback
     * Space: O(1)
     *
     * @param layoutManager the RecyclerView's layout manager, used to read scroll position
     * @param onLoadNext invoked once the threshold is crossed while still scrolling downward
     * @return the assembled listener, ready to pass to {@code RecyclerView#addOnScrollListener}
     */
    public static RecyclerView.OnScrollListener withThreshold(
            LinearLayoutManager layoutManager, Runnable onLoadNext) {
        return new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy <= 0) return;
                int visibleItemCount = layoutManager.getChildCount();
                int totalItemCount = layoutManager.getItemCount();
                int firstVisible = layoutManager.findFirstVisibleItemPosition();
                if (visibleItemCount + firstVisible >= totalItemCount - DEFAULT_PREFETCH_THRESHOLD) {
                    onLoadNext.run();
                }
            }
        };
    }

    /**
     * Builds a listener that fires {@code onLoadNext} only once the list can no longer scroll
     * further down — used by the Home feed and Search results, whose lists are shorter and
     * don't benefit from prefetching ahead of the exact end.
     *
     * Complexity:
     * Time: O(1) per scroll callback
     * Space: O(1)
     *
     * @param onLoadNext invoked once the RecyclerView has reached its scroll bottom
     * @return the assembled listener, ready to pass to {@code RecyclerView#addOnScrollListener}
     */
    public static RecyclerView.OnScrollListener atBottom(Runnable onLoadNext) {
        return new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (!recyclerView.canScrollVertically(1)) {
                    onLoadNext.run();
                }
            }
        };
    }
}
