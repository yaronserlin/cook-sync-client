package com.cooksync.app.ui.admin;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.cooksync.app.data.repository.BaseRepository;
import com.cooksync.app.data.repository.UnitRepository;
import com.cooksync.app.domain.ApiResult;
import com.cooksync.app.ui.base.BaseViewModel;
import com.cooksync.app.util.PendingActionScheduler;
import com.dtos.request.unit.UnitRequestDTO;
import com.dtos.response.unit.UnitResponse;

import java.util.List;

/**
 * Manages the Admin Console's Units tab: listing, creating, and deleting measurement units.
 * Deletion follows the same deferred "act now, send later" pattern as the Reports/Tags/Users
 * tabs (see {@link #pendingActions}) rather than the Fragment scheduling it itself.
 *
 * @author Yaron Serlin
 * @version 1.1
 * @since 12/08/2026
 */
public class AdminUnitsViewModel extends BaseViewModel {

    private final UnitRepository unitRepository;
    private final PendingActionScheduler pendingActions = new PendingActionScheduler();

    private final MutableLiveData<ApiResult<List<UnitResponse>>> unitsResult = new MutableLiveData<>();
    private final MutableLiveData<ApiResult<UnitResponse>> unitCreateResult = new MutableLiveData<>();
    private final MutableLiveData<ApiResult<Void>> unitDeleteResult = new MutableLiveData<>();

    /**
     * Constructs the ViewModel with the given repository, injected by
     * {@link com.cooksync.app.ui.base.ViewModelFactory}.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param unitRepository the repository used for the Units tab's CRUD calls
     */
    public AdminUnitsViewModel(UnitRepository unitRepository) {
        this.unitRepository = unitRepository;
    }

    /** @return observable list of every measurement unit (Loading → Success/Error) */
    public LiveData<ApiResult<List<UnitResponse>>> getUnitsResult() { return unitsResult; }
    /** @return observable result of the most recent unit-creation call */
    public LiveData<ApiResult<UnitResponse>> getUnitCreateResult() { return unitCreateResult; }
    /** @return observable result of the most recent unit-deletion call */
    public LiveData<ApiResult<Void>> getUnitDeleteResult() { return unitDeleteResult; }

    /**
     * Loads every measurement unit for the Units tab.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     */
    public void loadUnits() {
        unitRepository.getAllUnits(unitsResult);
    }

    /**
     * Creates a new measurement unit.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param name display name of the new unit
     * @param code short symbol code of the new unit
     */
    public void createUnit(String name, String code) {
        unitRepository.createUnit(new UnitRequestDTO(name, code), unitCreateResult);
    }

    /**
     * Deletes a measurement unit.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param id unique identifier of the unit to delete
     */
    public void deleteUnit(String id) {
        unitRepository.deleteUnit(id, unitDeleteResult);
    }

    /**
     * Defers deleting a unit by {@link BaseRepository#UNDO_WINDOW_MS}, matching the
     * Reports/Tags/Users tabs' "act now, send later" pattern: the Fragment removes the row from
     * its adapter immediately, and this call only reaches the server if
     * {@link #cancelPendingDelete} isn't called first (e.g. via the undo toast).
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param id unique identifier of the unit to delete
     */
    public void scheduleDeleteUnit(String id) {
        pendingActions.schedule(id, BaseRepository.UNDO_WINDOW_MS, () -> deleteUnit(id));
    }

    /**
     * Cancels a still-pending {@link #scheduleDeleteUnit} before it reaches the server. Does
     * nothing if the undo window already elapsed.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param id unique identifier of the unit whose deletion should be undone
     * @return {@code true} if a pending deletion was actually cancelled
     */
    public boolean cancelPendingDelete(String id) {
        return pendingActions.cancel(id);
    }

    /**
     * Runs any still-pending deletion immediately rather than dropping it, so navigating away
     * before the undo window elapses doesn't silently discard a deletion the user never undid.
     */
    @Override
    protected void onCleared() {
        super.onCleared();
        pendingActions.flushAll();
    }
}
