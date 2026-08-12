package com.cooksync.app.ui.recipe.detail;
import com.cooksync.app.ui.base.BaseActivity;
import com.cooksync.app.ui.base.BaseViewModel;
import com.cooksync.app.ui.base.Navigator;
import com.cooksync.app.ui.base.ViewModelFactory;

import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.cooksync.app.R;
import com.cooksync.app.ui.base.BaseAdapter;
import com.dtos.response.instruction.InstructionResponse;
import com.google.android.material.button.MaterialButton;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Adapter for the step-by-step instructions list: each step's mandatory description, an
 * optional illustration image, and an inline, edit-in-place private note.
 *
 * @author Yaron Serlin
 * @version 2.0
 * @since 04/08/2026
 */
public class InstructionAdapter extends BaseAdapter<InstructionResponse, InstructionAdapter.ViewHolder> {

    /** Notified when the viewer saves or deletes a step's private note from inline editing. */
    public interface OnNoteChangeListener {
        /**
         * @param step the instruction step whose note was saved
         * @param noteText the trimmed, non-blank note text
         */
        void onSaveNote(InstructionResponse step, String noteText);

        /**
         * @param step the instruction step whose note was deleted
         */
        void onDeleteNote(InstructionResponse step);
    }

    /** Notified when the viewer taps a step's illustration image to view it full-screen. */
    public interface OnImageClickListener {
        /**
         * @param imageUrl the tapped step's image URL
         */
        void onImageClick(String imageUrl);
    }

    private final Map<String, String> notesByInstructionId = new HashMap<>();
    private OnNoteChangeListener noteChangeListener;
    private OnImageClickListener imageClickListener;

    /** ID of the instruction step currently in inline note-edit mode, or {@code null} if none. */
    private String editingInstructionId;

    public void setInstructions(List<InstructionResponse> newInstructions) {
        setItems(newInstructions);
    }

    /**
     * Updates the per-step notes shown alongside each instruction, keyed by instruction ID.
     *
     * @param notes step ID to note-text map
     */
    public void setNotes(Map<String, String> notes) {
        notesByInstructionId.clear();
        notesByInstructionId.putAll(notes);
        notifyDataSetChanged();
    }

    public void setOnNoteChangeListener(OnNoteChangeListener listener) {
        this.noteChangeListener = listener;
    }

    public void setOnImageClickListener(OnImageClickListener listener) {
        this.imageClickListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_instruction_step, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        InstructionResponse step = getItem(position);
        holder.stepNumber.setText(String.valueOf(step.stepNumber()));
        holder.description.setText(step.description());

        String imageUrl = step.imageUrl();
        boolean hasImage = imageUrl != null && !imageUrl.isBlank();
        // The container is sized as soon as we know there's an image to try, since Glide can
        // only resolve a target size for a view that actually takes part in layout — a GONE
        // ancestor is skipped during measure/layout and never gets one. Only the image itself
        // stays invisible until the load succeeds, matching the recipe description's inline
        // image treatment, so a slow fetch never shows a broken tile.
        holder.stepImageContainer.setVisibility(hasImage ? View.VISIBLE : View.GONE);
        if (hasImage) {
            holder.stepImage.setVisibility(View.VISIBLE);
            Glide.with(holder.stepImage.getContext())
                    .load(imageUrl)
                    .placeholder(R.drawable.bg_skeleton_bone)
                    .error(R.drawable.ic_image_failed)
                    .centerCrop()
                    .into(holder.stepImage);
            holder.stepImage.setOnClickListener(v -> {
                if (imageClickListener != null) {
                    imageClickListener.onImageClick(imageUrl);
                }
            });
        }

        String note = notesByInstructionId.get(step.id());
        boolean hasNote = note != null && !note.isEmpty();
        boolean editing = Objects.equals(step.id(), editingInstructionId);

        // Once a note exists, the note text itself is the edit entry point; "Add a note" is
        // only shown as an affordance when there's nothing to tap yet.
        holder.note.setVisibility(hasNote && !editing ? View.VISIBLE : View.GONE);
        if (hasNote) {
            holder.note.setText(note);
        }
        holder.editNoteButton.setVisibility(!hasNote && !editing ? View.VISIBLE : View.GONE);

        holder.noteEditGroup.setVisibility(editing ? View.VISIBLE : View.GONE);
        if (editing) {
            String editText = hasNote ? note : "";
            holder.noteEditText.setText(editText);
            holder.noteEditText.setSelection(editText.length());
            holder.deleteNoteButton.setVisibility(hasNote ? View.VISIBLE : View.GONE);
        }

        View.OnClickListener openEditor = v -> {
            editingInstructionId = step.id();
            notifyDataSetChanged();
        };
        holder.editNoteButton.setOnClickListener(openEditor);
        holder.note.setOnClickListener(openEditor);

        // Commits on either an explicit tap of the save icon, or on blur (tapping outside the
        // field) — both routes are wired below and can fire back-to-back for the same gesture
        // (tapping Save itself first blurs the field, which also fires the focus-loss commit).
        // The "already committed" guard on this holder makes the second firing a no-op, so
        // RecyclerView#notifyDataSetChanged() (and any onSaveNote/onDeleteNote callback) never
        // runs twice for one user action.
        boolean[] alreadyCommitted = {false};
        View.OnClickListener commitEditor = v -> {
            if (alreadyCommitted[0]) {
                return;
            }
            alreadyCommitted[0] = true;
            String text = holder.noteEditText.getText() == null ? "" : holder.noteEditText.getText().toString().trim();
            if (!text.isEmpty() && !Objects.equals(text, note) && noteChangeListener != null) {
                noteChangeListener.onSaveNote(step, text);
            }
            editingInstructionId = null;
            notifyDataSetChanged();
        };
        holder.saveNoteButton.setOnClickListener(commitEditor);
        holder.noteEditText.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                commitEditor.onClick(v);
            }
        });

        holder.deleteNoteButton.setOnClickListener(v -> {
            if (alreadyCommitted[0]) {
                return;
            }
            alreadyCommitted[0] = true;
            if (noteChangeListener != null) {
                noteChangeListener.onDeleteNote(step);
            }
            editingInstructionId = null;
            notifyDataSetChanged();
        });
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView stepNumber;
        TextView description;
        View stepImageContainer;
        ImageView stepImage;
        TextView note;
        MaterialButton editNoteButton;
        View noteEditGroup;
        EditText noteEditText;
        ImageButton deleteNoteButton;
        ImageButton saveNoteButton;

        ViewHolder(View view) {
            super(view);
            stepNumber = view.findViewById(R.id.step_number);
            description = view.findViewById(R.id.step_description);
            stepImageContainer = view.findViewById(R.id.step_image_container);
            stepImage = view.findViewById(R.id.step_image);
            note = view.findViewById(R.id.step_note);
            editNoteButton = view.findViewById(R.id.btn_step_note);
            noteEditGroup = view.findViewById(R.id.group_step_note_edit);
            noteEditText = view.findViewById(R.id.et_step_note);
            deleteNoteButton = view.findViewById(R.id.btn_step_note_delete);
            saveNoteButton = view.findViewById(R.id.btn_step_note_save);
        }
    }
}
