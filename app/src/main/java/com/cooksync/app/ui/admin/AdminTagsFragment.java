package com.cooksync.app.ui.admin;

import android.os.Bundle;
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
import com.cooksync.app.ui.common.MergeTagsDialog;
import com.cooksync.app.ui.common.OrganicToast;
import com.cooksync.app.ui.common.ViewModelFactory;
import com.dtos.response.admin.DuplicateTagGroupResponse;
import com.dtos.response.admin.TagVariantResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The Admin Console's Tags tab: a client-side search over the loaded duplicate-tag groups
 * (there is no full-catalog tag search endpoint) and the merge flow for each group. Shares
 * {@link AdminViewModel} with the other two tabs via an activity-scoped
 * {@link ViewModelProvider}.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 07/08/2026
 */
public class AdminTagsFragment extends Fragment implements AdminTagGroupAdapter.OnMergeRequestListener {

    private AdminViewModel viewModel;
    private AdminTagGroupAdapter adapter;
    private RecyclerView recyclerView;
    private TextView tvEmpty;
    private TextView tvSectionLabel;
    private List<DuplicateTagGroupResponse> allGroups = Collections.emptyList();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_admin_tags, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity(), new ViewModelFactory()).get(AdminViewModel.class);

        recyclerView = view.findViewById(R.id.rv_admin_tag_groups);
        tvEmpty = view.findViewById(R.id.tv_tags_empty);
        tvSectionLabel = view.findViewById(R.id.tv_tags_section_label);
        EditText search = view.findViewById(R.id.et_tag_search);

        adapter = new AdminTagGroupAdapter();
        adapter.setOnMergeRequestListener(this);
        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy <= 0) return;
                int visibleItemCount = layoutManager.getChildCount();
                int totalItemCount = layoutManager.getItemCount();
                int firstVisible = layoutManager.findFirstVisibleItemPosition();
                if (visibleItemCount + firstVisible >= totalItemCount - 4) {
                    viewModel.loadNextTagGroupsPage();
                }
            }
        });

        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applySearch(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        viewModel.getTagGroupsResult().observe(getViewLifecycleOwner(), result -> {
            if (result instanceof ApiResult.Success<List<DuplicateTagGroupResponse>> success) {
                allGroups = success.getData();
                applySearch(search.getText().toString());
            }
        });

        viewModel.getTagMergeResult().observe(getViewLifecycleOwner(), event -> {
            if (event == null) return;
            ApiResult<Void> result = event.getContentIfNotHandled();
            if (result instanceof ApiResult.Error<Void> error) {
                OrganicToast.showError(requireActivity(), null, error.getMessage());
            }
        });
    }

    private void applySearch(String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.getDefault());
        List<DuplicateTagGroupResponse> filtered;
        if (needle.isEmpty()) {
            filtered = allGroups;
        } else {
            filtered = new ArrayList<>();
            for (DuplicateTagGroupResponse group : allGroups) {
                boolean matches = group.normalizedName() != null
                        && group.normalizedName().toLowerCase(Locale.getDefault()).contains(needle);
                if (!matches) {
                    for (TagVariantResponse variant : group.variants()) {
                        if (variant.name() != null && variant.name().toLowerCase(Locale.getDefault()).contains(needle)) {
                            matches = true;
                            break;
                        }
                    }
                }
                if (matches) {
                    filtered.add(group);
                }
            }
        }

        adapter.setGroups(filtered);
        recyclerView.setVisibility(filtered.isEmpty() ? View.GONE : View.VISIBLE);
        tvEmpty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        tvSectionLabel.setText(getString(R.string.admin_tags_section_label) + " · "
                + getString(R.string.admin_badge_tags, filtered.size()));
    }

    @Override
    public void onMergeRequested(DuplicateTagGroupResponse group) {
        MergeTagsDialog.show(requireContext(), group, (allVariantIds, keepTagId) -> {
            viewModel.mergeGroup(group, allVariantIds, keepTagId);
            OrganicToast.showWithAction(requireActivity(), null, R.drawable.ic_check,
                    getString(R.string.admin_toast_tags_merged_format, keepTagName(group, keepTagId)),
                    getString(R.string.action_undo), () -> viewModel.undoMergeGroup(group));
        });
    }

    private String keepTagName(DuplicateTagGroupResponse group, String keepTagId) {
        for (TagVariantResponse variant : group.variants()) {
            if (variant.id().equals(keepTagId)) {
                return variant.name();
            }
        }
        return keepTagId;
    }
}
