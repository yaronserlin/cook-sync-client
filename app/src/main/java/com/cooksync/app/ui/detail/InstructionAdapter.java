package com.cooksync.app.ui.detail;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cooksync.app.R;
import com.dtos.response.instruction.InstructionResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for the step-by-step instructions list.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 04/08/2026
 */
public class InstructionAdapter extends RecyclerView.Adapter<InstructionAdapter.ViewHolder> {

    private final List<InstructionResponse> instructions = new ArrayList<>();

    public void setInstructions(List<InstructionResponse> newInstructions) {
        instructions.clear();
        instructions.addAll(newInstructions);
        notifyDataSetChanged();
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
    }

    @Override
    public int getItemCount() {
        return instructions.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView stepNumber;
        TextView description;

        ViewHolder(View view) {
            super(view);
            stepNumber = view.findViewById(R.id.step_number);
            description = view.findViewById(R.id.step_description);
        }
    }
}
