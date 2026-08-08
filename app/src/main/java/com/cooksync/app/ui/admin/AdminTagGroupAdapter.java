package com.cooksync.app.ui.admin;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cooksync.app.R;
import com.dtos.response.admin.DuplicateTagGroupResponse;
import com.dtos.response.admin.TagVariantResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for the Admin Console's Tags tab: renders each duplicate-tag group as a card listing
 * its variants, with a single "merge →" action opening
 * {@link com.cooksync.app.ui.common.MergeTagsDialog} for the whole group.
 *
 * @author Yaron Serlin
 * @version 1.1
 * @since 07/08/2026
 */
public class AdminTagGroupAdapter extends RecyclerView.Adapter<AdminTagGroupAdapter.ViewHolder> {

    /** Notified when the moderator taps a group card's "merge →" action. */
    public interface OnMergeRequestListener {
        /** @param group the duplicate tag group to resolve */
        void onMergeRequested(DuplicateTagGroupResponse group);
    }

    private final List<DuplicateTagGroupResponse> groups = new ArrayList<>();
    private OnMergeRequestListener listener;

    public void setGroups(List<DuplicateTagGroupResponse> newGroups) {
        groups.clear();
        groups.addAll(newGroups);
        notifyDataSetChanged();
    }

    public void setOnMergeRequestListener(OnMergeRequestListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_tag_group, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DuplicateTagGroupResponse group = groups.get(position);
        holder.groupName.setText(group.normalizedName());

        holder.variantsContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(holder.itemView.getContext());
        List<TagVariantResponse> variants = group.variants();
        for (TagVariantResponse variant : variants) {
            View row = inflater.inflate(R.layout.item_admin_tag_variant_row, holder.variantsContainer, false);
            ((TextView) row.findViewById(R.id.tv_variant_name)).setText(variant.name());
            ((TextView) row.findViewById(R.id.tv_variant_recipe_count)).setText(
                    row.getContext().getString(R.string.admin_tag_recipe_count_format, variant.recipeCount()));
            holder.variantsContainer.addView(row);
        }

        holder.mergeAction.setOnClickListener(v -> {
            if (listener != null) listener.onMergeRequested(group);
        });
    }

    @Override
    public int getItemCount() {
        return groups.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView groupName;
        LinearLayout variantsContainer;
        TextView mergeAction;

        ViewHolder(View view) {
            super(view);
            groupName = view.findViewById(R.id.tv_group_name);
            variantsContainer = view.findViewById(R.id.ll_group_variants);
            mergeAction = view.findViewById(R.id.tv_group_merge_action);
        }
    }
}
