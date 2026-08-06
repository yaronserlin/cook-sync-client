package com.cooksync.app.ui.detail;

import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import com.dtos.response.recipe.DescriptionBlockDTO;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for the recipe description shown as a vertical sequence of text and image blocks,
 * matching the design's {@code descBlocks} concept (author-ordered paragraphs and full-width
 * captioned photos interspersed in a single flow, rather than a separate paragraph + horizontal
 * gallery). Rendered directly from the server's {@code descriptionBlocks}, which preserves the
 * author's intended order and per-image captions.
 *
 * @author Yaron Serlin
 * @version 2.0
 * @since 04/08/2026
 */
public class DescriptionBlockAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final String TYPE_IMAGE = "IMAGE";

    private static final int VIEW_TYPE_TEXT = 0;
    private static final int VIEW_TYPE_IMAGE = 1;

    private final List<DescriptionBlockDTO> blocks = new ArrayList<>();

    /**
     * Replaces the displayed blocks, preserving the author-authored order returned by the
     * server.
     *
     * @param descriptionBlocks the recipe's ordered description blocks
     */
    public void setBlocks(List<DescriptionBlockDTO> descriptionBlocks) {
        blocks.clear();
        if (descriptionBlocks != null) {
            blocks.addAll(descriptionBlocks);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return TYPE_IMAGE.equalsIgnoreCase(blocks.get(position).type()) ? VIEW_TYPE_IMAGE : VIEW_TYPE_TEXT;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_IMAGE) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_description_image, parent, false);
            return new ImageViewHolder(view);
        }
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_description_text, parent, false);
        return new TextViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        DescriptionBlockDTO block = blocks.get(position);
        if (holder instanceof ImageViewHolder imageHolder) {
            // Stays hidden until the load actually succeeds, rather than showing a placeholder
            // tile, so a slow or failed fetch never renders as a gray box or broken image.
            imageHolder.image.setVisibility(View.INVISIBLE);
            Glide.with(imageHolder.image.getContext())
                    .load(block.imageUrl())
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
                            imageHolder.image.setVisibility(View.VISIBLE);
                            return false;
                        }
                    })
                    .into(imageHolder.image);

            if (block.caption() == null || block.caption().isBlank()) {
                imageHolder.caption.setVisibility(View.GONE);
            } else {
                imageHolder.caption.setVisibility(View.VISIBLE);
                imageHolder.caption.setText(block.caption());
            }
        } else if (holder instanceof TextViewHolder textHolder) {
            textHolder.text.setText(block.text());
        }
    }

    @Override
    public int getItemCount() {
        return blocks.size();
    }

    static class ImageViewHolder extends RecyclerView.ViewHolder {
        ImageView image;
        TextView caption;

        ImageViewHolder(View view) {
            super(view);
            image = view.findViewById(R.id.description_image);
            caption = view.findViewById(R.id.description_image_caption);
        }
    }

    static class TextViewHolder extends RecyclerView.ViewHolder {
        TextView text;

        TextViewHolder(View view) {
            super(view);
            text = view.findViewById(R.id.description_text);
        }
    }
}
