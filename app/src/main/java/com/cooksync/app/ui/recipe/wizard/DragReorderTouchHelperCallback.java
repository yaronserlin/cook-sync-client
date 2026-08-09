package com.cooksync.app.ui.recipe.wizard;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Shared up/down-only drag-reorder {@link ItemTouchHelper.Callback}, used by both the
 * Ingredients and Instructions steps of the Create Recipe wizard. Dragging starts only from a
 * row's drag-handle icon (see {@code setOnTouchListener} + {@code ItemTouchHelper#startDrag} in
 * each adapter), not from a long-press anywhere on the row, since the row also contains text
 * fields that need normal touch/focus behavior.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 08/08/2026
 */
public class DragReorderTouchHelperCallback extends ItemTouchHelper.Callback {

    /** Notified as rows are dragged past each other, and once the drag finishes. */
    public interface OnMoveListener {
        void onMove(int fromPosition, int toPosition);

        /** Called once the user releases the dragged row. Default no-op. */
        default void onDragFinished() {
        }
    }

    private final OnMoveListener listener;

    public DragReorderTouchHelperCallback(OnMoveListener listener) {
        this.listener = listener;
    }

    @Override
    public boolean isLongPressDragEnabled() {
        return false;
    }

    @Override
    public boolean isItemViewSwipeEnabled() {
        return false;
    }

    @Override
    public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
        return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
    }

    @Override
    public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder,
                           @NonNull RecyclerView.ViewHolder target) {
        listener.onMove(viewHolder.getBindingAdapterPosition(), target.getBindingAdapterPosition());
        return true;
    }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
    }

    @Override
    public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
        super.clearView(recyclerView, viewHolder);
        listener.onDragFinished();
    }
}
