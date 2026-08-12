package com.cooksync.app.ui.admin;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cooksync.app.R;
import com.cooksync.app.ui.base.BaseAdapter;
import com.dtos.response.unit.UnitResponse;

import java.util.List;

/**
 * Adapter for displaying measurement units in the Admin Console.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 10/08/2026
 */
public class AdminUnitAdapter extends BaseAdapter<UnitResponse, AdminUnitAdapter.ViewHolder> {

    public interface Listener {
        void onDeleteUnit(UnitResponse unit);
    }

    private Listener listener;

    public void setUnits(List<UnitResponse> newUnits) {
        setItems(newUnits);
    }

    public void removeUnit(UnitResponse unit) {
        removeItem(unit);
    }

    public void restoreUnit(UnitResponse unit) {
        addItem(unit);
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
        UnitResponse unit = getItem(position);
        holder.tvCode.setText(unit.code());
        holder.tvName.setText(unit.name());
        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDeleteUnit(unit);
            }
        });
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
