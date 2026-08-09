package com.cooksync.app.ui.recipe.wizard;

import android.content.Context;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.cooksync.app.R;
import com.dtos.response.recipe.DescriptionBlockDTO;

import java.util.List;
import java.util.function.Consumer;

/**
 * Renders the Basics step's description as an ordered, reorderable list of TEXT and IMAGE
 * blocks — matching the design's inline description editor, where photos sit wherever the
 * author drags them relative to the text rather than in a separate strip. Binds directly
 * against the live {@link DescriptionBlockDTO} list held by
 * {@link AddRecipeViewModel#getDescriptionBlocks()}; since the DTO is an immutable record,
 * edits go through {@link AddRecipeViewModel#updateDescriptionBlockText} /
 * {@code setDescriptionImageCaption} rather than direct field mutation.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 08/08/2026
 */
public class WizardDescriptionBlockAdapter extends RecyclerView.Adapter<WizardDescriptionBlockAdapter.ViewHolder> {

    /** Notified on block actions the host fragment needs to act on. */
    public interface Listener {
        void onTextChanged(DescriptionBlockDTO block, String text);

        void onCaptionChanged(DescriptionBlockDTO block, String caption);

        void onRemoveImage(DescriptionBlockDTO block);

        /** The author pressed Enter mid-paragraph — {@code block} keeps {@code beforeText}, a new TEXT block gets {@code afterText}. */
        void onSplitTextBlock(DescriptionBlockDTO block, String beforeText, String afterText);

        /** The author pressed Backspace on an already-empty TEXT block — remove it and merge focus into the previous block. */
        void onRemoveEmptyTextBlock(DescriptionBlockDTO block);
    }

    private final List<DescriptionBlockDTO> blocks;
    private Listener listener;
    private ItemTouchHelper itemTouchHelper;
    private RecyclerView recyclerView;

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        this.recyclerView = recyclerView;
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        this.recyclerView = null;
    }

    /** Focuses the TEXT block at {@code position}, cursor at the start — used right after a split/insert. */
    public void focusTextBlockAt(int position) {
        focusTextBlockAt(position, false);
    }

    /** Focuses the TEXT block at {@code position}, cursor at the end — used after merging an empty block away. */
    public void focusTextBlockAtEnd(int position) {
        focusTextBlockAt(position, true);
    }

    private void focusTextBlockAt(int position, boolean atEnd) {
        if (recyclerView == null) return;
        recyclerView.post(() -> {
            RecyclerView.ViewHolder holder = recyclerView.findViewHolderForAdapterPosition(position);
            if (holder instanceof ViewHolder viewHolder) {
                viewHolder.etText.requestFocus();
                viewHolder.etText.setSelection(atEnd ? viewHolder.etText.length() : 0);
                InputMethodManager imm = (InputMethodManager) recyclerView.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(viewHolder.etText, InputMethodManager.SHOW_IMPLICIT);
            }
        });
    }

    public WizardDescriptionBlockAdapter(@NonNull List<DescriptionBlockDTO> blocks) {
        this.blocks = blocks;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /** Wires up the drag handle to start a reorder drag on the host's {@link ItemTouchHelper}. */
    public void setItemTouchHelper(ItemTouchHelper itemTouchHelper) {
        this.itemTouchHelper = itemTouchHelper;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_wizard_description_block, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(blocks.get(position), listener);
    }

    @Override
    public int getItemCount() {
        return blocks.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ImageView ivDragHandle;
        private final EditText etText;
        private final LinearLayout llImageGroup;
        private final ImageView ivImage;
        private final EditText etCaption;
        private final ImageButton btnRemove;
        private TextWatcher textWatcher;
        private TextWatcher captionWatcher;

        ViewHolder(View view) {
            super(view);
            ivDragHandle = view.findViewById(R.id.iv_block_drag_handle);
            etText = view.findViewById(R.id.et_block_text);
            llImageGroup = view.findViewById(R.id.ll_block_image);
            ivImage = view.findViewById(R.id.iv_block_image);
            etCaption = view.findViewById(R.id.et_block_caption);
            btnRemove = view.findViewById(R.id.btn_block_remove);
        }

        /**
         * The block currently at this holder's live adapter position — never the block object
         * that happened to be bound originally. {@link DescriptionBlockDTO} is an immutable
         * record, so every edit replaces the list element with a new instance; a closure that
         * captured the bind-time {@code block} reference would go stale after the very first
         * keystroke (its {@code equals()}-based list lookups would silently stop matching), so
         * every callback below re-resolves the current block by position instead.
         */
        private DescriptionBlockDTO currentBlock() {
            int position = getBindingAdapterPosition();
            if (position == RecyclerView.NO_POSITION || position >= blocks.size()) return null;
            return blocks.get(position);
        }

        @SuppressWarnings("ClickableViewAccessibility")
        void bind(DescriptionBlockDTO block, Listener listener) {
            boolean isText = "TEXT".equals(block.type());
            etText.setVisibility(isText ? View.VISIBLE : View.GONE);
            llImageGroup.setVisibility(isText ? View.GONE : View.VISIBLE);

            if (textWatcher != null) etText.removeTextChangedListener(textWatcher);
            if (captionWatcher != null) etCaption.removeTextChangedListener(captionWatcher);

            if (isText) {
                etText.setText(block.text());
                textWatcher = onChanged(value -> {
                    DescriptionBlockDTO current = currentBlock();
                    if (listener != null && current != null) listener.onTextChanged(current, value);
                });
                etText.addTextChangedListener(textWatcher);
                etText.setFilters(new InputFilter[]{newlineSplitFilter()});
                etText.setOnKeyListener((v, keyCode, event) -> {
                    if (keyCode == KeyEvent.KEYCODE_DEL && event.getAction() == KeyEvent.ACTION_DOWN
                            && etText.getText().length() == 0 && blocks.size() > 1) {
                        DescriptionBlockDTO current = currentBlock();
                        if (listener != null && current != null) listener.onRemoveEmptyTextBlock(current);
                        return true;
                    }
                    return false;
                });
            } else {
                Glide.with(itemView.getContext()).load(block.imageUrl()).into(ivImage);
                etCaption.setText(block.caption());
                captionWatcher = onChanged(value -> {
                    DescriptionBlockDTO current = currentBlock();
                    if (listener != null && current != null) listener.onCaptionChanged(current, value);
                });
                etCaption.addTextChangedListener(captionWatcher);
                btnRemove.setOnClickListener(v -> {
                    DescriptionBlockDTO current = currentBlock();
                    if (listener != null && current != null) listener.onRemoveImage(current);
                });
            }

            // Only image blocks are draggable — text blocks stay put but can still be jumped over
            // by a dragged image, which is how a photo ends up "between" two paragraphs.
            ivDragHandle.setVisibility(isText ? View.INVISIBLE : View.VISIBLE);
            if (isText) {
                ivDragHandle.setOnTouchListener(null);
            } else {
                ivDragHandle.setOnTouchListener((v, event) -> {
                    if (event.getActionMasked() == MotionEvent.ACTION_DOWN && itemTouchHelper != null) {
                        itemTouchHelper.startDrag(this);
                    }
                    return false;
                });
            }
        }

        /**
         * Swallows a typed Enter/newline and reports it as a block split instead of letting it
         * insert a literal {@code '\n'} — the caller applies the split on the next UI loop tick
         * (mutating the adapter's backing list from inside an active {@link InputFilter} callback
         * is not safe).
         */
        private InputFilter newlineSplitFilter() {
            return (source, start, end, dest, dstart, dend) -> {
                String insert = source.subSequence(start, end).toString();
                int newlineIndex = insert.indexOf('\n');
                if (newlineIndex < 0) {
                    return null;
                }
                DescriptionBlockDTO current = currentBlock();
                if (listener != null && current != null) {
                    String currentText = dest.toString();
                    String before = currentText.substring(0, dstart) + insert.substring(0, newlineIndex);
                    String after = insert.substring(newlineIndex + 1) + currentText.substring(dend);
                    listener.onSplitTextBlock(current, before, after);
                }
                return "";
            };
        }

        private static TextWatcher onChanged(Consumer<String> onChanged) {
            return new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    onChanged.accept(s.toString());
                }
            };
        }
    }
}
