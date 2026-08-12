package com.cooksync.app.ui.base;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for every simple, single-view-type {@code RecyclerView.Adapter} in the app:
 * owns the backing list and the item-count/replace/insert/remove boilerplate that was
 * previously duplicated across each feature adapter. Subclasses only need to implement
 * {@link #onCreateViewHolder} and {@link #onBindViewHolder} — the actual item layout and
 * binding logic, which genuinely differs per screen and isn't generalized here.
 *
 * @param <T> the type of item this adapter's list holds
 * @param <VH> the concrete {@link RecyclerView.ViewHolder} type this adapter binds
 * @author Yaron Serlin
 * @version 1.0
 * @since 12/08/2026
 */
public abstract class BaseAdapter<T, VH extends RecyclerView.ViewHolder> extends RecyclerView.Adapter<VH> {

    protected final List<T> items = new ArrayList<>();

    /**
     * Replaces the entire backing list and refreshes the whole RecyclerView.
     *
     * Complexity:
     * Time: O(n) where n is the new list's size
     * Space: O(n)
     *
     * @param newItems the full replacement list, or {@code null} to clear
     */
    public void setItems(List<T> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    /**
     * Returns the item at the given adapter position.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param position the adapter position
     * @return the item backing that position
     */
    protected T getItem(int position) {
        return items.get(position);
    }

    /**
     * Appends a single item and animates its insertion, for a "restore after undo" flow.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param item the item to append
     */
    protected void addItem(T item) {
        items.add(item);
        notifyItemInserted(items.size() - 1);
    }

    /**
     * Removes the first occurrence of an item (by {@link Object#equals}) and animates its
     * removal, a no-op if the item isn't present.
     *
     * Complexity:
     * Time: O(n) where n is the current list size
     * Space: O(1)
     *
     * @param item the item to remove
     */
    protected void removeItem(T item) {
        int index = items.indexOf(item);
        if (index != -1) {
            items.remove(index);
            notifyItemRemoved(index);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }
}
