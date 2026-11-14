package com.chemmanager.service;

import com.chemmanager.dto.PagedResult;
import com.chemmanager.dto.SupplierQuery;
import com.chemmanager.model.Supplier;

/** Supplier master data and purchasing history. */
public interface SupplierService {

    PagedResult<Supplier> search(SupplierQuery query);

    Supplier load(long id);
}
