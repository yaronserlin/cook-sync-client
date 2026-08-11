package com.cooksync.app.ui.admin;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cooksync.app.R;
import com.cooksync.app.data.repository.BaseRepository;
import com.cooksync.app.domain.ApiResult;
import com.cooksync.app.ui.base.ViewModelFactory;
import com.cooksync.app.ui.common.OrganicConfirmDialog;
import com.cooksync.app.ui.common.OrganicToast;
import com.dtos.response.unit.UnitResponse;

import java.util.List;

/**
 * Admin Console fragment for viewing, adding, and deleting measurement units.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 10/08/2026
 */
public class AdminUnitsFragment extends Fragment {

    private AdminViewModel viewModel;
    private AdminUnitAdapter adapter;
    private EditText etName;
    private EditText etCode;
    private ProgressBar progressBar;

    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable pendingDeleteRunnable;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_admin_units, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity(), new ViewModelFactory()).get(AdminViewModel.class);

        etName = view.findViewById(R.id.et_unit_name);
        etCode = view.findViewById(R.id.et_unit_code);
        progressBar = view.findViewById(R.id.progress_bar);

        RecyclerView rvUnits = view.findViewById(R.id.rv_units);
        rvUnits.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new AdminUnitAdapter();
        rvUnits.setAdapter(adapter);

        adapter.setListener(unit -> {
            if (pendingDeleteRunnable != null) {
                handler.removeCallbacks(pendingDeleteRunnable);
                pendingDeleteRunnable.run();
            }
            adapter.removeUnit(unit);

            pendingDeleteRunnable = () -> {
                viewModel.deleteUnit(unit.id());
                pendingDeleteRunnable = null;
            };

            handler.postDelayed(pendingDeleteRunnable, BaseRepository.UNDO_WINDOW_MS);

            OrganicToast.showWithAction(requireActivity(), null, R.drawable.ic_delete,
                    getString(R.string.admin_units_deleted_format, unit.name()), getString(R.string.action_undo), () -> {
                        if (pendingDeleteRunnable != null) {
                            handler.removeCallbacks(pendingDeleteRunnable);
                            pendingDeleteRunnable = null;
                        }
                        adapter.restoreUnit(unit);
                    });
        });

        view.findViewById(R.id.btn_add_unit).setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String code = etCode.getText().toString().trim();
            if (name.isEmpty() || code.isEmpty()) {
                OrganicToast.showError(requireActivity(), null, getString(R.string.admin_units_validation_required));
                return;
            }
            viewModel.createUnit(name, code);
        });

        observeViewModel();
        viewModel.loadUnits();
    }

    private void observeViewModel() {
        viewModel.getUnitsResult().observe(getViewLifecycleOwner(), result -> {
            if (result instanceof ApiResult.Loading) {
                progressBar.setVisibility(View.VISIBLE);
            } else if (result instanceof ApiResult.Success<List<UnitResponse>> success) {
                progressBar.setVisibility(View.GONE);
                adapter.setUnits(success.getData());
            } else if (result instanceof ApiResult.Error<?> error) {
                progressBar.setVisibility(View.GONE);
                OrganicToast.showError(requireActivity(), null, error.getMessage());
            }
        });

        viewModel.getUnitCreateResult().observe(getViewLifecycleOwner(), result -> {
            if (result instanceof ApiResult.Success) {
                etName.setText("");
                etCode.setText("");
                OrganicToast.showSuccess(requireActivity(), null, getString(R.string.admin_units_create_success));
                viewModel.loadUnits();
            } else if (result instanceof ApiResult.Error<?> error) {
                OrganicToast.showError(requireActivity(), null, error.getMessage());
            }
        });

        viewModel.getUnitDeleteResult().observe(getViewLifecycleOwner(), result -> {
            if (result instanceof ApiResult.Success) {
                viewModel.loadUnits();
            } else if (result instanceof ApiResult.Error<?> error) {
                OrganicToast.showError(requireActivity(), null, error.getMessage());
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (pendingDeleteRunnable != null) {
            handler.removeCallbacks(pendingDeleteRunnable);
            pendingDeleteRunnable.run();
            pendingDeleteRunnable = null;
        }
    }
}
