package com.cooksync.app.ui.home;
import com.cooksync.app.ui.base.BaseActivity;
import com.cooksync.app.ui.base.BaseViewModel;
import com.cooksync.app.ui.base.Navigator;
import com.cooksync.app.ui.base.ViewModelFactory;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.cooksync.app.R;
import com.dtos.response.recipe.RecipePreviewResponse;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Adapter for the main recipe feed RecyclerView. Renders high-fidelity recipe cards.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 04/08/2026
 */
public class RecipeCardAdapter extends RecyclerView.Adapter<RecipeCardAdapter.ViewHolder> {

    private final List<RecipePreviewResponse> recipes = new ArrayList<>();
    private final Set<String> favoriteIds = new HashSet<>();
    private OnRecipeClickListener listener;

    public interface OnRecipeClickListener {
        void onRecipeClick(String recipeId);

        /**
         * @param recipeId the tapped card's recipe id
         * @param wasFavorite whether the recipe was already favorited before this tap (i.e.
         *                    {@code false} means this tap is adding it, {@code true} means
         *                    it's removing it)
         */
        void onFavoriteClick(String recipeId, boolean wasFavorite);
    }

    public void setOnRecipeClickListener(OnRecipeClickListener listener) {
        this.listener = listener;
    }

    public void setRecipes(List<RecipePreviewResponse> newRecipes) {
        recipes.clear();
        recipes.addAll(newRecipes);
        notifyDataSetChanged();
    }

    public void setFavorites(List<RecipePreviewResponse> favorites) {
        favoriteIds.clear();
        for (RecipePreviewResponse favorite : favorites) {
            favoriteIds.add(favorite.id());
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_recipe_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        RecipePreviewResponse recipe = recipes.get(position);

        holder.title.setText(recipe.title());
        holder.author.setText(recipe.authorName());
        holder.blurb.setText(recipe.description());
        holder.difficulty.setText(recipe.difficulty());
        holder.rating.setText(recipe.averageRating() == null ? "0.0" : String.format(java.util.Locale.US, "%.1f", recipe.averageRating()));
        holder.time.setText(holder.itemView.getContext().getString(R.string.time_format, recipe.prepTimeMinutes() + recipe.cookTimeMinutes()));

        Glide.with(holder.itemView.getContext())
                .load(recipe.primaryImageUrl())
                .placeholder(R.color.color_neutral_300)
                .centerCrop()
                .into(holder.image);

        boolean isFavorite = favoriteIds.contains(recipe.id());
        holder.btnFavorite.setImageResource(isFavorite ? R.drawable.ic_heart_filled : R.drawable.ic_heart_outline);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onRecipeClick(recipe.id());
            }
        });

        holder.btnFavorite.setOnClickListener(v -> {
            if (listener != null) {
                listener.onFavoriteClick(recipe.id(), isFavorite);
            }
        });
    }

    @Override
    public int getItemCount() {
        return recipes.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView image;
        ImageButton btnFavorite;
        TextView difficulty;
        TextView title;
        TextView author;
        TextView blurb;
        TextView rating;
        TextView time;

        ViewHolder(View view) {
            super(view);
            image = view.findViewById(R.id.recipe_image);
            btnFavorite = view.findViewById(R.id.btn_favorite);
            difficulty = view.findViewById(R.id.difficulty_text);
            title = view.findViewById(R.id.recipe_title);
            author = view.findViewById(R.id.author_name);
            blurb = view.findViewById(R.id.recipe_blurb);
            rating = view.findViewById(R.id.rating_text);
            time = view.findViewById(R.id.time_text);
        }
    }
}
