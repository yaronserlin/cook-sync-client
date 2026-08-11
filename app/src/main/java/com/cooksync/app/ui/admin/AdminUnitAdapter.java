package com.cooksync.app.ui.admin;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cooksync.app.R;
import com.dtos.response.unit.UnitResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for displaying measurement units in the Admin Console.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 10/08/2026
 */
public class AdminUnitAdapter extends RecyclerView.Adapter<AdminUnitAdapter.ViewHolder> {

    public interface Listener {
        void onDeleteUnit(UnitResponse unit);
    }

    private final List<UnitResponse> units = new ArrayList<>();
    private Listener listener;

    public void setUnits(List<UnitResponse> newUnits) {
        units.clear();
        if (newUnits != null) {
            units.addAll(newUnits);
        }
        notifyDataSetChanged();
    }

    public void removeUnit(UnitResponse unit) {
        int index = units.indexOf(unit);
        if (index != -1) {
            units.remove(index);
            notifyItemRemoved(index);
        }
    }

    public void restoreUnit(UnitResponse unit) {
        units.add(unit);
        notifyItemInserted(units.size() - 1);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_unit, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        UnitResponse unit = units.get(position);
        holder.tvCode.setText(unit.code());
        holder.tvName.setText(unit.name());
        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDeleteUnit(unit);
            }
        });
    }

    @Override
    public int getItemCount() {
        return units.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvCode;
        TextView tvName;
        ImageButton btnDelete;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCode = itemView.findViewById(R.id.tv_unit_code);
            tvName = itemView.findViewById(R.id.tv_unit_name);
            btnDelete = itemView.findViewById(R.id.btn_delete_unit);
        }
    }
}
