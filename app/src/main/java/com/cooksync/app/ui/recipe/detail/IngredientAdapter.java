package com.cooksync.app.ui.recipe.detail;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cooksync.app.R;
import com.cooksync.app.ui.base.BaseAdapter;
import com.dtos.response.ingredient.IngredientResponse;

import java.util.List;

/**
 * Adapter for the recipe ingredients list.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 04/08/2026
 */
public class IngredientAdapter extends BaseAdapter<IngredientResponse, IngredientAdapter.ViewHolder> {

    public void setIngredients(List<IngredientResponse> newIngredients) {
        setItems(newIngredients);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_ingredient, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        IngredientResponse ingredient = getItem(position);
        holder.name.setText(ingredient.name());

        String unitName = ingredient.unit() != null ? ingredient.unit().code() : "";
        String amount = ingredient.quantity() + " " + unitName;
        holder.amount.setText(amount.trim());
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView name;
        TextView amount;

        ViewHolder(View view) {
            super(view);
            name = view.findViewById(R.id.ingredient_name);
            amount = view.findViewById(R.id.ingredient_amount);
        }
    }
}
