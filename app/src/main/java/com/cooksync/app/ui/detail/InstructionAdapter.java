package com.cooksync.app.ui.detail;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cooksync.app.R;
import com.dtos.response.instruction.InstructionResponse;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapter for the step-by-step instructions list, including each step's own private note
 * (view-only here; editing is delegated to the host Activity via {@link OnNoteEditListener}).
 *
 * @author Yaron Serlin
 * @version 1.1
 * @since 04/08/2026
 */
public class InstructionAdapter extends RecyclerView.Adapter<InstructionAdapter.ViewHolder> {

    /** Notified when the user taps the note affordance for a given step. */
    public interface OnNoteEditListener {
        /**
         * @param step the instruction step whose note should be edited
         * @param existingNote the step's current note text, or {@code null} if it has none
         */
        void onEditNote(InstructionResponse step, String existingNote);
    }

    private final List<InstructionResponse> instructions = new ArrayList<>();
    private final Map<String, String> notesByInstructionId = new HashMap<>();
    private OnNoteEditListener noteEditListener;

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

    public void setOnNoteEditListener(OnNoteEditListener listener) {
        this.noteEditListener = listener;
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

        String note = notesByInstructionId.get(step.id());
        boolean hasNote = note != null && !note.isEmpty();
        holder.note.setVisibility(hasNote ? View.VISIBLE : View.GONE);
        if (hasNote) {
            holder.note.setText(note);
        }
        holder.editNoteButton.setText(hasNote ? "Edit note" : "Add a note");

        View.OnClickListener openEditor = v -> {
            if (noteEditListener != null) {
                noteEditListener.onEditNote(step, hasNote ? note : null);
            }
        };
        holder.editNoteButton.setOnClickListener(openEditor);
        holder.note.setOnClickListener(openEditor);
    }

    @Override
    public int getItemCount() {
        return instructions.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView stepNumber;
        TextView description;
        TextView note;
        MaterialButton editNoteButton;

        ViewHolder(View view) {
            super(view);
            stepNumber = view.findViewById(R.id.step_number);
            description = view.findViewById(R.id.step_description);
            note = view.findViewById(R.id.step_note);
            editNoteButton = view.findViewById(R.id.btn_step_note);
        }
    }
}
