package com.cooksync.app.ui.recipe.common;
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
import com.cooksync.app.ui.base.BaseAdapter;
import com.dtos.response.recipe.RecipePreviewResponse;

import java.util.List;
import java.util.Locale;

/**
 * Shared horizontal recipe row adapter for both "My Recipes" and "Favorites", which use
 * identical card styling in the design and differ only in their trailing action (an overflow
 * menu for My Recipes, a heart toggle for Favorites) and whether the Public/Private badge is
 * relevant (favorites can only ever be public recipes, so it's hidden there).
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 04/08/2026
 */
public class RecipeRowCardAdapter extends BaseAdapter<RecipePreviewResponse, RecipeRowCardAdapter.ViewHolder> {

    /** What the trailing icon does/shows for each row. */
    public enum TrailingAction {
        /** Rotated overflow-dots icon; opens a management menu (My Recipes). */
        OPTIONS_MENU,
        /** Filled heart icon; every row here is already a favorite, tapping unfavorites it. */
        FAVORITE_TOGGLE,
        /** No trailing icon at all, for read-only contexts (e.g. another user's public profile). */
        NONE
    }

    /** Notified on row/action interactions the host Activity needs to act on. */
    public interface Listener {
        void onRecipeClick(RecipePreviewResponse recipe);

        void onTrailingActionClick(RecipePreviewResponse recipe, View anchor);
    }

    private Listener listener;
    private TrailingAction trailingAction = TrailingAction.OPTIONS_MENU;
    private boolean showVisibilityBadge = true;

    public void setRecipes(List<RecipePreviewResponse> newRecipes) {
        setItems(newRecipes);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /**
     * Configures what the trailing icon does and shows. Must be called before the first bind
     * (i.e. right after construction) since it doesn't itself trigger a rebind.
     *
     * @param trailingAction which action the trailing icon represents
     */
    public void setTrailingAction(TrailingAction trailingAction) {
        this.trailingAction = trailingAction;
    }

    /**
     * Whether to show the Public/Private badge next to the rating (relevant for My Recipes,
     * meaningless for Favorites since only public recipes can be favorited).
     *
     * @param show {@code true} to show the badge
     */
    public void setShowVisibilityBadge(boolean show) {
        this.showVisibilityBadge = show;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_recipe_row_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        RecipePreviewResponse recipe = getItem(position);
        android.content.Context context = holder.itemView.getContext();

        holder.title.setText(recipe.title());
        holder.subtitle.setText(context.getString(R.string.review_count_plain_format, recipe.reviewCount()));
        holder.rating.setText(recipe.averageRating() == null ? "0.0" : String.format(Locale.US, "%.1f", recipe.averageRating()));

        if (showVisibilityBadge) {
            boolean isPublic = "PUBLIC".equalsIgnoreCase(recipe.visibility());
            holder.visibilityBadge.setVisibility(View.VISIBLE);
            holder.visibilityBadge.setText(isPublic ? R.string.filter_public : R.string.filter_private);
            holder.visibilityBadge.setBackgroundResource(isPublic ? R.drawable.bg_tag_public : R.drawable.bg_tag_neutral);
        } else {
            holder.visibilityBadge.setVisibility(View.GONE);
        }

        if (recipe.hasPersonalNote() && recipe.personalNoteText() != null && !recipe.personalNoteText().isEmpty()) {
            holder.noteStrip.setVisibility(View.VISIBLE);
            holder.noteStrip.setText(recipe.personalNoteText());
        } else {
            holder.noteStrip.setVisibility(View.GONE);
        }

        if (trailingAction == TrailingAction.NONE) {
            holder.btnTrailing.setVisibility(View.GONE);
        } else {
            holder.btnTrailing.setVisibility(View.VISIBLE);
            boolean isFavoriteToggle = trailingAction == TrailingAction.FAVORITE_TOGGLE;
            holder.btnTrailing.setImageResource(isFavoriteToggle ? R.drawable.ic_heart_filled : R.drawable.ic_more_horiz);
            holder.btnTrailing.setRotation(isFavoriteToggle ? 0f : 90f);
            holder.btnTrailing.setColorFilter(context.getColor(isFavoriteToggle ? R.color.color_accent : R.color.color_text));
        }

        Glide.with(context)
                .load(recipe.primaryImageUrl())
                .placeholder(R.drawable.bg_skeleton_bone)
                .error(R.drawable.ic_image_failed)
                .centerCrop()
                .into(holder.image);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onRecipeClick(recipe);
            }
        });
        holder.btnTrailing.setOnClickListener(v -> {
            if (listener != null) {
                listener.onTrailingActionClick(recipe, v);
            }
        });
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView image;
        TextView title;
        TextView subtitle;
        TextView rating;
        TextView visibilityBadge;
        TextView noteStrip;
        ImageButton btnTrailing;

        ViewHolder(View view) {
            super(view);
            image = view.findViewById(R.id.recipe_image);
            title = view.findViewById(R.id.recipe_title);
            subtitle = view.findViewById(R.id.recipe_subtitle);
            rating = view.findViewById(R.id.rating_text);
            visibilityBadge = view.findViewById(R.id.visibility_badge);
            noteStrip = view.findViewById(R.id.recipe_note_strip);
            btnTrailing = view.findViewById(R.id.btn_trailing_action);
        }
    }
}
