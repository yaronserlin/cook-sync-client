package com.cooksync.app.domain;

/**
 * Generic wrapper for a {@code LiveData} value that represents a one-off occurrence (e.g. a
 * toast message, a navigation command) rather than a durable state snapshot. Without this
 * wrapper, a plain {@code LiveData<String>} would re-deliver the same error message to a
 * freshly (re)attached observer — for instance after a screen rotation — even though the
 * event already happened. {@link #getContentIfNotHandled()} guarantees delivery exactly once.
 *
 * @param <T> the type of the event payload
 * @author Yaron Serlin
 * @version 1.0
 * @since 04/08/2026
 */
public class Event<T> {

    private final T content;
    private boolean handled = false;

    /**
     * Wraps a payload as a new, unhandled event.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param content the event payload
     */
    public Event(T content) {
        this.content = content;
    }

    /**
     * Returns the payload exactly once; every subsequent call returns {@code null}.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @return the payload the first time this is called, {@code null} thereafter
     */
    public T getContentIfNotHandled() {
        if (handled) {
            return null;
        }
        handled = true;
        return content;
    }
}
