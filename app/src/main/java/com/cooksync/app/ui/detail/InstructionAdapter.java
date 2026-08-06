package com.cooksync.app.ui.detail;

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
import com.dtos.response.instruction.InstructionResponse;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapter for the step-by-step instructions list: each step's mandatory description, an
 * optional illustration image, and an inline, edit-in-place private note.
 *
 * @author Yaron Serlin
 * @version 2.0
 * @since 04/08/2026
 */
public class InstructionAdapter extends RecyclerView.Adapter<InstructionAdapter.ViewHolder> {

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

    private final List<InstructionResponse> instructions = new ArrayList<>();
    private final Map<String, String> notesByInstructionId = new HashMap<>();
    private OnNoteChangeListener noteChangeListener;
    private OnImageClickListener imageClickListener;

    /** ID of the instruction step currently in inline note-edit mode, or {@code null} if none. */
    private String editingInstructionId;

    public void setInstructions(List<InstructionResponse> newInstructions) {
        instructions.clear();
        instructions.addAll(newInstructions);
        notifyDataSetChanged();
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
        InstructionResponse step = instructions.get(position);
        holder.stepNumber.setText(String.valueOf(step.stepNumber()));
        holder.description.setText(step.description());

        String imageUrl = step.imageUrl();
        boolean hasImage = imageUrl != null && !imageUrl.isBlank();
        // Stays hidden (collapsed, not just an empty placeholder tile) until the load actually
        // succeeds, so a slow or failed fetch never leaves a blank card sitting mid-step.
        holder.stepImageContainer.setVisibility(View.GONE);
        if (hasImage) {
            Glide.with(holder.stepImage.getContext())
                    .load(imageUrl)
                    .centerCrop()
                    .listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model,
                                                     Target<Drawable> target, boolean isFirstResource) {
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target,
                                                        DataSource dataSource, boolean isFirstResource) {
                            holder.stepImageContainer.setVisibility(View.VISIBLE);
                            return false;
                        }
                    })
                    .into(holder.stepImage);
            holder.stepImage.setOnClickListener(v -> {
                if (imageClickListener != null) {
                    imageClickListener.onImageClick(imageUrl);
                }
            });
        }

        String note = notesByInstructionId.get(step.id());
        boolean hasNote = note != null && !note.isEmpty();
        boolean editing = step.id().equals(editingInstructionId);

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
            if (!text.isEmpty() && !text.equals(note) && noteChangeListener != null) {
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

    @Override
    public int getItemCount() {
        return instructions.size();
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
