package com.cooksync.app.ui.recipe.search;
import com.cooksync.app.ui.base.BaseActivity;
import com.cooksync.app.ui.base.BaseViewModel;
import com.cooksync.app.ui.base.Navigator;
import com.cooksync.app.ui.base.ViewModelFactory;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.cooksync.app.R;
import com.dtos.response.recipe.RecipePreviewResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Adapter for the dedicated {@link SearchActivity}'s result list. Renders the compact row
 * format the design specifies for search (76dp thumbnail + title/author/rating/time + chevron),
 * distinct from the elevated card format {@code RecipeCardAdapter} uses on the Home feed.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 05/08/2026
 */
public class SearchResultAdapter extends RecyclerView.Adapter<SearchResultAdapter.ViewHolder> {

    private final List<RecipePreviewResponse> recipes = new ArrayList<>();
    private OnRecipeClickListener listener;

    public interface OnRecipeClickListener {
        void onRecipeClick(String recipeId);
    }

    public void setOnRecipeClickListener(OnRecipeClickListener listener) {
        this.listener = listener;
    }

    public void setRecipes(List<RecipePreviewResponse> newRecipes) {
        recipes.clear();
        recipes.addAll(newRecipes);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_search_result_row, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        RecipePreviewResponse recipe = recipes.get(position);

        holder.title.setText(recipe.title());
        holder.subtitle.setText(holder.itemView.getContext()
                .getString(R.string.search_result_subtitle_format, recipe.authorName(), recipe.reviewCount()));
        holder.rating.setText(recipe.averageRating() == null
                ? "0.0" : String.format(Locale.US, "%.1f", recipe.averageRating()));
        holder.time.setText(holder.itemView.getContext()
                .getString(R.string.time_format, recipe.prepTimeMinutes() + recipe.cookTimeMinutes()));

        Glide.with(holder.itemView.getContext())
                .load(recipe.primaryImageUrl())
                .placeholder(R.color.color_neutral_300)
                .centerCrop()
                .into(holder.image);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onRecipeClick(recipe.id());
            }
        });
    }

    @Override
    public int getItemCount() {
        return recipes.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView image;
        TextView title;
        TextView subtitle;
        TextView rating;
        TextView time;

        ViewHolder(View view) {
            super(view);
            image = view.findViewById(R.id.recipe_image);
            title = view.findViewById(R.id.recipe_title);
            subtitle = view.findViewById(R.id.recipe_subtitle);
            rating = view.findViewById(R.id.rating_text);
            time = view.findViewById(R.id.time_text);
        }
    }
}
