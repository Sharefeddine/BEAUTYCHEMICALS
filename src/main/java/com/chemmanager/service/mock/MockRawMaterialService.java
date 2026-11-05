package com.chemmanager.service.mock;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.chemmanager.dto.MaterialQuery;
import com.chemmanager.dto.PagedResult;
import com.chemmanager.model.RawMaterial;
import com.chemmanager.repository.MockDataStore;
import com.chemmanager.service.RawMaterialService;
import com.chemmanager.util.Paging;

/** Raw materials backed by the demo dataset (including hazard and SDS metadata). */
public final class MockRawMaterialService implements RawMaterialService {

    private final MockDataStore store = MockDataStore.get();

    @Override
    public PagedResult<RawMaterial> search(MaterialQuery query) {
        MaterialQuery effective = query == null ? MaterialQuery.initial(20) : query;
        String search = effective.search() == null ? "" : effective.search().trim().toLowerCase(Locale.ROOT);
        List<RawMaterial> filtered = store.rawMaterials().stream()
                .filter(material -> matches(material, effective, search))
                .sorted(Comparator.comparing(RawMaterial::code))
                .toList();
        return Paging.slice(filtered, effective.page(), effective.pageSize());
    }

    @Override
    public List<String> categories() {
        return store.materialCategories();
    }

    private boolean matches(RawMaterial material, MaterialQuery query, String search) {
        if (query.category() != null && !query.category().isBlank()
                && !query.category().equalsIgnoreCase(material.category())) {
            return false;
        }
        if (query.hazardousOnly() && !material.isDangerous()) {
            return false;
        }
        if (query.lowStockOnly() && !material.isLowStock()) {
            return false;
        }
        if (search.isEmpty()) {
            return true;
        }
        return contains(material.code(), search)
                || contains(material.commercialName(), search)
                || contains(material.chemicalName(), search)
                || contains(material.supplierName(), search)
                || contains(material.category(), search);
    }

    private static boolean contains(String value, String search) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(search);
    }
}
