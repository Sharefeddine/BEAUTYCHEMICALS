package com.chemmanager.service;

import java.util.List;

import com.chemmanager.dto.MaterialQuery;
import com.chemmanager.dto.PagedResult;
import com.chemmanager.model.RawMaterial;

/** Read operations on the raw material catalogue (batch and hazard aware). */
public interface RawMaterialService {

    PagedResult<RawMaterial> search(MaterialQuery query);

    List<String> categories();
}
