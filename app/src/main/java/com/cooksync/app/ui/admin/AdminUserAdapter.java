package com.cooksync.app.ui.admin;
import com.cooksync.app.ui.base.BaseActivity;
import com.cooksync.app.ui.base.BaseViewModel;
import com.cooksync.app.ui.base.Navigator;
import com.cooksync.app.ui.base.ViewModelFactory;

import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cooksync.app.R;
import com.cooksync.app.ui.common.AvatarView;
import com.dtos.response.user.UserResponse;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for the Admin Console's Users tab: renders each account row with its status tag and
 * enable/disable toggle, and forwards the mail action to the hosting fragment.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 07/08/2026
 */
public class AdminUserAdapter extends RecyclerView.Adapter<AdminUserAdapter.ViewHolder> {

    /** Notified when the moderator acts on a user row. */
    public interface OnUserActionListener {
        /**
         * @param user the row's user
         * @param enabled the new enabled state requested (opposite of the user's current one)
         */
        void onToggleEnabled(UserResponse user, boolean enabled);

        /** @param user the row whose mail action was tapped */
        void onEmail(UserResponse user);
    }

    private final List<UserResponse> users = new ArrayList<>();
    private OnUserActionListener listener;

    public void setUsers(List<UserResponse> newUsers) {
        users.clear();
        users.addAll(newUsers);
        notifyDataSetChanged();
    }

    public void setOnUserActionListener(OnUserActionListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_user, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        UserResponse user = users.get(position);
        String fullName = ((user.firstName() == null ? "" : user.firstName()) + " "
                + (user.lastName() == null ? "" : user.lastName())).trim();

        holder.name.setText(fullName);
        holder.avatar.setAvatar(user.avatarUrl(), fullName);

        View.OnClickListener openProfile = v -> {
            if (user.id() != null && v.getContext() instanceof androidx.fragment.app.FragmentActivity activity) {
                com.cooksync.app.ui.auth.UserProfileDialogFragment.show(activity.getSupportFragmentManager(), user.id(), fullName);
            }
        };
        holder.avatar.setOnClickListener(openProfile);
        holder.name.setOnClickListener(openProfile);

        holder.email.setText(user.email());
        holder.adminTag.setVisibility(Boolean.TRUE.equals(user.isAdmin()) ? View.VISIBLE : View.GONE);

        Resources resources = holder.itemView.getResources();
        if ("SUSPENDED".equals(user.status())) {
            holder.status.setText(R.string.admin_user_status_suspended);
            holder.status.setBackgroundTintList(ColorStateList.valueOf(
                    resources.getColor(R.color.color_accent_200, null)));
            holder.status.setTextColor(resources.getColor(R.color.color_accent_800, null));
        } else if ("DEACTIVATED".equals(user.status())) {
            holder.status.setText(R.string.admin_user_status_deactivated);
            holder.status.setBackgroundTintList(ColorStateList.valueOf(
                    resources.getColor(R.color.color_neutral_300, null)));
            holder.status.setTextColor(resources.getColor(R.color.color_neutral_800, null));
        } else {
            holder.status.setText(R.string.admin_user_status_active);
            holder.status.setBackgroundTintList(ColorStateList.valueOf(
                    resources.getColor(R.color.color_accent_2_200, null)));
            holder.status.setTextColor(resources.getColor(R.color.color_accent_2_800, null));
        }
        if (user.enabled()) {
            holder.toggleEnabled.setText(R.string.action_suspend);
            int danger = resources.getColor(R.color.color_danger, null);
            holder.toggleEnabled.setStrokeColor(ColorStateList.valueOf(danger));
            holder.toggleEnabled.setTextColor(danger);
        } else {
            holder.toggleEnabled.setText(R.string.action_reactivate);
            int success = resources.getColor(R.color.color_success, null);
            holder.toggleEnabled.setStrokeColor(ColorStateList.valueOf(success));
            holder.toggleEnabled.setTextColor(success);
        }

        holder.toggleEnabled.setOnClickListener(v -> {
            if (listener != null) listener.onToggleEnabled(user, !user.enabled());
        });
        holder.emailButton.setOnClickListener(v -> {
            if (listener != null) listener.onEmail(user);
        });
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        AvatarView avatar;
        TextView name;
        TextView adminTag;
        TextView email;
        TextView status;
        MaterialButton toggleEnabled;
        ImageButton emailButton;

        ViewHolder(View view) {
            super(view);
            avatar = view.findViewById(R.id.user_avatar);
            name = view.findViewById(R.id.tv_user_name);
            adminTag = view.findViewById(R.id.tv_user_admin_tag);
            email = view.findViewById(R.id.tv_user_email);
            status = view.findViewById(R.id.tv_user_status);
            toggleEnabled = view.findViewById(R.id.btn_user_toggle_enabled);
            emailButton = view.findViewById(R.id.btn_user_email);
        }
    }
}
