package com.cooksync.app.data.repository;

import androidx.lifecycle.MutableLiveData;

import com.cooksync.app.domain.ApiResult;
import com.dtos.response.unit.UnitResponse;

import java.util.List;

/**
 * Interface contract for measurement-unit data operations. Units populate the unit picker
 * on each ingredient row in the recipe creation wizard.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 08/08/2026
 */
public interface UnitRepository {

    /**
     * Fetches the complete set of measurement units defined in the system. The server
     * paginates this endpoint, but the client loops through every page internally (see
     * {@link UnitRepositoryImpl}) since callers need the full catalog for a picker dropdown,
     * not a scrollable subset.
     *
     * @param resultTarget LiveData target to post the outcome
     */
    void getAllUnits(MutableLiveData<ApiResult<List<UnitResponse>>> resultTarget);
}
