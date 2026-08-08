package com.cooksync.app.ui.admin;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cooksync.app.R;
import com.dtos.response.admin.ReportedReviewResponse;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for the Admin Console's Reports tab: renders each queued report card and forwards
 * the Remove/Keep/ban-user row actions to the hosting fragment.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 07/08/2026
 */
public class AdminReportAdapter extends RecyclerView.Adapter<AdminReportAdapter.ViewHolder> {

    /** Notified when the moderator taps one of a report card's row actions. */
    public interface OnReportActionListener {
        /** @param report the report whose review should be deleted */
        void onRemove(ReportedReviewResponse report);

        /** @param report the report to dismiss without deleting its review */
        void onKeep(ReportedReviewResponse report);

        /** @param report the report whose author (reviewer) should be banned */
        void onBan(ReportedReviewResponse report);
    }

    private final List<ReportedReviewResponse> reports = new ArrayList<>();
    private OnReportActionListener listener;

    public void setReports(List<ReportedReviewResponse> newReports) {
        reports.clear();
        reports.addAll(newReports);
        notifyDataSetChanged();
    }

    public void setOnReportActionListener(OnReportActionListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_report, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ReportedReviewResponse report = reports.get(position);

        holder.reviewer.setText(report.reviewerName());
        holder.recipe.setText(holder.itemView.getContext()
                .getString(R.string.admin_report_on_format, report.recipeTitle()));
        holder.reasonTag.setText(report.reason());
        holder.comment.setText(report.comment());

        String reportComment = report.reportComment();
        if (reportComment != null && !reportComment.isBlank()) {
            holder.note.setText(holder.itemView.getContext()
                    .getString(R.string.admin_report_note_format, reportComment));
            holder.note.setVisibility(View.VISIBLE);
        } else {
            holder.note.setVisibility(View.GONE);
        }

        holder.remove.setOnClickListener(v -> {
            if (listener != null) listener.onRemove(report);
        });
        holder.keep.setOnClickListener(v -> {
            if (listener != null) listener.onKeep(report);
        });
        holder.ban.setOnClickListener(v -> {
            if (listener != null) listener.onBan(report);
        });
    }

    @Override
    public int getItemCount() {
        return reports.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView reviewer;
        TextView recipe;
        TextView reasonTag;
        TextView comment;
        TextView note;
        MaterialButton remove;
        MaterialButton keep;
        ImageButton ban;

        ViewHolder(View view) {
            super(view);
            reviewer = view.findViewById(R.id.tv_report_reviewer);
            recipe = view.findViewById(R.id.tv_report_recipe);
            reasonTag = view.findViewById(R.id.tv_report_reason_tag);
            comment = view.findViewById(R.id.tv_report_comment);
            note = view.findViewById(R.id.tv_report_note);
            remove = view.findViewById(R.id.btn_report_remove);
            keep = view.findViewById(R.id.btn_report_keep);
            ban = view.findViewById(R.id.btn_report_ban);
        }
    }
}
