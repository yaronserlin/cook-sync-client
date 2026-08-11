package com.cooksync.app.ui.recipe.detail;
import com.cooksync.app.ui.base.BaseActivity;
import com.cooksync.app.ui.base.BaseViewModel;
import com.cooksync.app.ui.base.Navigator;
import com.cooksync.app.ui.base.ViewModelFactory;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cooksync.app.R;
import com.cooksync.app.ui.common.AvatarView;
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
 * @version 1.1
 * @since 04/08/2026
 */
public class ReviewAdapter extends RecyclerView.Adapter<ReviewAdapter.ViewHolder> {

    /** Notified when the viewer chooses an action from a review's overflow menu. */
    public interface OnReviewActionListener {
        /**
         * @param review the review the viewer, who is its author, chose to delete
         */
        void onDeleteReview(ReviewResponse review);

        /**
         * @param review the review the viewer, who is not its author, chose to report
         */
        void onReportReview(ReviewResponse review);
    }

    /** Notified when the viewer taps a review author's avatar to view it full-screen. */
    public interface OnAvatarClickListener {
        /**
         * @param avatarUrl the tapped author's avatar URL
         */
        void onAvatarClick(String avatarUrl);
    }

    private final List<ReviewResponse> reviews = new ArrayList<>();
    private String currentUserId;
    private OnReviewActionListener actionListener;
    private OnAvatarClickListener avatarClickListener;

    public void setReviews(List<ReviewResponse> newReviews) {
        reviews.clear();
        reviews.addAll(newReviews);
        notifyDataSetChanged();
    }

    /**
     * Sets the signed-in viewer's user ID, used to decide whether each review's overflow menu
     * offers "Delete" (viewer is the author) or "Report" (viewer is not).
     *
     * @param currentUserId the signed-in user's ID, or {@code null} if signed out
     */
    public void setCurrentUserId(String currentUserId) {
        this.currentUserId = currentUserId;
    }

    public void setOnReviewActionListener(OnReviewActionListener listener) {
        this.actionListener = listener;
    }

    public void setOnAvatarClickListener(OnAvatarClickListener listener) {
        this.avatarClickListener = listener;
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
        holder.avatar.setAvatar(review.authorAvatarUrl(), author);
        View.OnClickListener openProfile = v -> {
            if (review.userId() != null) {
                if (v.getContext() instanceof androidx.fragment.app.FragmentActivity activity) {
                    com.cooksync.app.ui.auth.UserProfileDialogFragment.show(activity.getSupportFragmentManager(), review.userId(), author);
                }
            }
        };
        holder.avatar.setOnClickListener(openProfile);
        holder.authorName.setOnClickListener(openProfile);

        holder.rating.setText(review.rating() != null ? review.rating().toString() : "0.0");
        holder.content.setText(review.comment());
        holder.date.setText(formatRelativeDate(review.createdAt()));

        boolean isAuthor = currentUserId != null && currentUserId.equals(review.userId());
        holder.overflow.setOnClickListener(v -> showOverflowMenu(v, review, isAuthor));
    }

    private void showOverflowMenu(View anchor, ReviewResponse review, boolean isAuthor) {
        PopupMenu popup = new PopupMenu(anchor.getContext(), anchor);
        popup.getMenu().add(0, 1, 0, isAuthor ? anchor.getContext().getString(R.string.action_delete) : anchor.getContext().getString(R.string.action_report));
        popup.setOnMenuItemClickListener(item -> {
            if (actionListener == null) {
                return true;
            }
            if (isAuthor) {
                actionListener.onDeleteReview(review);
            } else {
                actionListener.onReportReview(review);
            }
            return true;
        });
        popup.show();
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
        AvatarView avatar;
        TextView authorName;
        TextView date;
        TextView rating;
        TextView content;
        ImageButton overflow;

        ViewHolder(View view) {
            super(view);
            avatar = view.findViewById(R.id.review_author_avatar);
            authorName = view.findViewById(R.id.review_author_name);
            date = view.findViewById(R.id.review_date);
            rating = view.findViewById(R.id.review_rating);
            content = view.findViewById(R.id.review_content);
            overflow = view.findViewById(R.id.btn_review_overflow);
        }
    }
}
