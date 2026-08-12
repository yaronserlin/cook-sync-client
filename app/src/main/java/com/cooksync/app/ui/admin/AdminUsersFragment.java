package com.cooksync.app.ui.admin;
import com.cooksync.app.ui.base.BaseActivity;
import com.cooksync.app.ui.base.BaseViewModel;
import com.cooksync.app.ui.base.Navigator;
import com.cooksync.app.ui.base.ViewModelFactory;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cooksync.app.R;
import com.cooksync.app.domain.ApiResult;
import com.cooksync.app.ui.common.OrganicConfirmDialog;
import com.cooksync.app.ui.common.OrganicToast;
import com.cooksync.app.ui.base.ViewModelFactory;
import com.dtos.response.user.UserResponse;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

/**
 * The Admin Console's Users tab: search + enabled-filter chips over a paginated user
 * directory, with per-row enable/disable actions. Shares {@link AdminUsersViewModel} with the
 * other two tabs via an activity-scoped {@link ViewModelProvider}.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 07/08/2026
 */
public class AdminUsersFragment extends Fragment implements AdminUserAdapter.OnUserActionListener {

    /** Matches {@code SearchActivity}'s live search-as-you-type debounce window. */
    private static final long SEARCH_DEBOUNCE_MS = 350L;

    private AdminUsersViewModel viewModel;
    private AdminUserAdapter adapter;
    private RecyclerView recyclerView;
    private TextView tvEmpty;
    private TextView tvCountLabel;
    private MaterialButton chipAll;
    private MaterialButton chipActive;
    private MaterialButton chipDeactivated;
    private MaterialButton chipSuspended;
    private Boolean selectedEnabledFilter = null;
    /**
     * Client-side sub-filter applied on top of {@link #selectedEnabledFilter} to distinguish
     * "Deactivated" (self-service) from "Suspended" (admin-imposed) accounts, since the server's
     * {@code /admin/users} endpoint only filters by the {@code enabled} boolean, not by the
     * finer-grained {@code status} field. Applied over whatever page(s) are currently loaded.
     */
    private String selectedStatusFilter = null;
    private EditText searchInput;

    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_admin_users, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity(), new ViewModelFactory()).get(AdminUsersViewModel.class);

        recyclerView = view.findViewById(R.id.rv_admin_users);
        tvEmpty = view.findViewById(R.id.tv_users_empty);
        tvCountLabel = view.findViewById(R.id.tv_users_count_label);
        chipAll = view.findViewById(R.id.chip_users_all);
        chipActive = view.findViewById(R.id.chip_users_active);
        chipDeactivated = view.findViewById(R.id.chip_users_deactivated);
        chipSuspended = view.findViewById(R.id.chip_users_suspended);
        searchInput = view.findViewById(R.id.et_user_search);
        View sortButton = view.findViewById(R.id.btn_user_sort);

        adapter = new AdminUserAdapter();
        adapter.setOnUserActionListener(this);
        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);
        recyclerView.addOnScrollListener(com.cooksync.app.ui.common.PaginatingScrollListener.withThreshold(
                layoutManager, viewModel::loadNextUsersPage));

        chipAll.setOnClickListener(v -> selectFilter(null, null));
        chipActive.setOnClickListener(v -> selectFilter(true, null));
        chipDeactivated.setOnClickListener(v -> selectFilter(false, "DEACTIVATED"));
        chipSuspended.setOnClickListener(v -> selectFilter(false, "SUSPENDED"));
        sortButton.setOnClickListener(v -> viewModel.toggleUsersSortDirection());

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (pendingSearch != null) {
                    searchHandler.removeCallbacks(pendingSearch);
                }
                String query = s.toString();
                pendingSearch = () -> viewModel.refreshUsers(query, selectedEnabledFilter);
                searchHandler.postDelayed(pendingSearch, SEARCH_DEBOUNCE_MS);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        styleChips();
        observeViewModel();
    }

    private void selectFilter(Boolean enabled, String statusFilter) {
        selectedEnabledFilter = enabled;
        selectedStatusFilter = statusFilter;
        styleChips();
        viewModel.refreshUsers(searchInput.getText().toString(), enabled);
    }

    private void observeViewModel() {
        viewModel.getUsersResult().observe(getViewLifecycleOwner(), result -> {
            if (result instanceof ApiResult.Success<List<UserResponse>> success) {
                List<UserResponse> data = success.getData();
                if (selectedStatusFilter != null) {
                    List<UserResponse> filtered = new ArrayList<>();
                    for (UserResponse user : data) {
                        if (selectedStatusFilter.equals(user.status())) {
                            filtered.add(user);
                        }
                    }
                    data = filtered;
                }
                adapter.setUsers(data);
                recyclerView.setVisibility(data.isEmpty() ? View.GONE : View.VISIBLE);
                tvEmpty.setVisibility(data.isEmpty() ? View.VISIBLE : View.GONE);
                tvCountLabel.setText(getString(R.string.admin_users_count_format, viewModel.getUsersTotalElements()));
            } else if (result instanceof ApiResult.Error<List<UserResponse>> error) {
                OrganicToast.showError(requireActivity(), null, error.getMessage());
            }
        });

        viewModel.getUserActionResult().observe(getViewLifecycleOwner(), event -> {
            if (event == null) return;
            ApiResult<Void> result = event.getContentIfNotHandled();
            if (result instanceof ApiResult.Error<Void> error) {
                OrganicToast.showError(requireActivity(), null, error.getMessage());
            }
        });
    }

    private void styleChips() {
        com.cooksync.app.ui.common.ChipStyler.styleNeutralChip(chipAll, selectedEnabledFilter == null);
        com.cooksync.app.ui.common.ChipStyler.styleNeutralChip(chipActive, Boolean.TRUE.equals(selectedEnabledFilter));
        com.cooksync.app.ui.common.ChipStyler.styleNeutralChip(chipDeactivated, Boolean.FALSE.equals(selectedEnabledFilter) && "DEACTIVATED".equals(selectedStatusFilter));
        com.cooksync.app.ui.common.ChipStyler.styleNeutralChip(chipSuspended, Boolean.FALSE.equals(selectedEnabledFilter) && "SUSPENDED".equals(selectedStatusFilter));
    }

    @Override
    public void onToggleEnabled(UserResponse user, boolean enabled) {
        String fullName = ((user.firstName() == null ? "" : user.firstName()) + " "
                + (user.lastName() == null ? "" : user.lastName())).trim();
        if (enabled) {
            viewModel.setUserEnabled(user, true);
            OrganicToast.showWithAction(requireActivity(), null, R.drawable.ic_check,
                    getString(R.string.admin_toast_user_reactivated_format, fullName),
                    getString(R.string.action_undo), () -> viewModel.undoSetUserEnabled(user));
            return;
        }
        OrganicConfirmDialog.show(requireContext(),
                getString(R.string.admin_confirm_suspend_title),
                getString(R.string.admin_confirm_suspend_message, fullName),
                getString(R.string.action_suspend),
                getString(R.string.action_cancel),
                true,
                () -> {
                    viewModel.setUserEnabled(user, false);
                    OrganicToast.showWithAction(requireActivity(), null, R.drawable.ic_user_x,
                            getString(R.string.admin_toast_user_suspended_format, fullName),
                            getString(R.string.action_undo), () -> viewModel.undoSetUserEnabled(user));
                });
    }

    @Override
    public void onEmail(UserResponse user) {
        if (user.email() == null) return;
        Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + user.email()));
        if (intent.resolveActivity(requireContext().getPackageManager()) != null) {
            startActivity(intent);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (pendingSearch != null) {
            searchHandler.removeCallbacks(pendingSearch);
        }
    }
}
