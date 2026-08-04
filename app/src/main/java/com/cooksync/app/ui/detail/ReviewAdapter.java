package com.cooksync.app.ui.detail;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cooksync.app.R;
import com.dtos.response.review.ReviewResponse;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for the recipe reviews list.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 04/08/2026
 */
public class ReviewAdapter extends RecyclerView.Adapter<ReviewAdapter.ViewHolder> {

    private final List<ReviewResponse> reviews = new ArrayList<>();

    public void setReviews(List<ReviewResponse> newReviews) {
        reviews.clear();
        reviews.addAll(newReviews);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_review, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ReviewResponse review = reviews.get(position);
        
        String author = review.authorName();
        holder.authorName.setText(author);
        holder.initial.setText(author != null && !author.isEmpty() ? author.substring(0, 1).toUpperCase() : "?");
        
        holder.rating.setText(review.rating() != null ? review.rating().toString() : "0.0");
        holder.content.setText(review.comment());
        holder.date.setText(formatRelativeDate(review.createdAt()));
    }

    /**
     * Formats an ISO-8601 timestamp into a short relative label ("Today", "3 days ago",
     * "2 months ago", ...), matching the design's "{{ v.when }}" review timestamp.
     *
     * @param isoTimestamp the review's {@code createdAt} value
     * @return a human-readable relative-time label, or "" if unparseable
     */
    private String formatRelativeDate(String isoTimestamp) {
        if (isoTimestamp == null || isoTimestamp.isBlank()) {
            return "";
        }
        try {
            LocalDate date = LocalDate.parse(isoTimestamp.substring(0, 10));
            long days = ChronoUnit.DAYS.between(date, LocalDate.now());
            if (days <= 0) {
                return "Today";
            } else if (days == 1) {
                return "1 day ago";
            } else if (days < 30) {
                return days + " days ago";
            } else if (days < 365) {
                long months = days / 30;
                return months + (months == 1 ? " month ago" : " months ago");
            } else {
                long years = days / 365;
                return years + (years == 1 ? " year ago" : " years ago");
            }
        } catch (DateTimeParseException | IndexOutOfBoundsException e) {
            return "";
        }
    }

    @Override
    public int getItemCount() {
        return reviews.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView initial;
        TextView authorName;
        TextView date;
        TextView rating;
        TextView content;

        ViewHolder(View view) {
            super(view);
            initial = view.findViewById(R.id.review_author_initial);
            authorName = view.findViewById(R.id.review_author_name);
            date = view.findViewById(R.id.review_date);
            rating = view.findViewById(R.id.review_rating);
            content = view.findViewById(R.id.review_content);
        }
    }
}
