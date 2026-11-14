package com.chemmanager.service.mock;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.chemmanager.dto.PagedResult;
import com.chemmanager.dto.SupplierQuery;
import com.chemmanager.model.RawMaterial;
import com.chemmanager.model.Supplier;
import com.chemmanager.repository.MockDataStore;
import com.chemmanager.service.SupplierService;
import com.chemmanager.util.Paging;

/** Suppliers backed by the demo dataset. */
public final class MockSupplierService implements SupplierService {

    private final MockDataStore store = MockDataStore.get();

    @Override
    public PagedResult<Supplier> search(SupplierQuery query) {
        SupplierQuery effective = query == null ? SupplierQuery.initial(25) : query;
        String search = effective.search() == null ? "" : effective.search().trim().toLowerCase(Locale.ROOT);
        List<Supplier> filtered = suppliers().stream()
                .filter(supplier -> !effective.activeOnly() || supplier.active())
                .filter(supplier -> search.isEmpty() || matches(supplier, search))
                .sorted(Comparator.comparing(Supplier::name))
                .toList();

        return Paging.slice(filtered, effective.page(), effective.pageSize());
    }

    @Override
    public Supplier load(long id) {
        Supplier supplier = suppliers().stream().filter(candidate -> candidate.id() == id).findFirst()
                .orElseThrow(() -> new IllegalStateException("Supplier not found."));
        List<RawMaterial> materials = store.rawMaterials().stream()
                .filter(material -> supplier.name().equals(material.supplierName()))
                .toList();
        List<Supplier.Material> catalogue = new ArrayList<>();
        for (RawMaterial material : materials) {
            catalogue.add(new Supplier.Material(material.id(), material.code(), material.commercialName(),
                    material.purchasePrice(), material.storageUnit()));
        }

        return new Supplier(supplier.id(), supplier.code(), supplier.name(), supplier.contactName(),
                supplier.phone(), supplier.email(), supplier.address(), supplier.wilaya(),
                supplier.paymentTerms(), supplier.balance(), supplier.active(), supplier.orderCount(),
                supplier.lastOrderDate(), catalogue.size(), catalogue, List.of());
    }

    private List<Supplier> suppliers() {
        List<Supplier> result = new ArrayList<>();
        for (MockDataStore.SupplierRecord record : store.suppliers()) {
            long materialCount = store.rawMaterials().stream()
                    .filter(material -> record.name().equals(material.supplierName())).count();
            result.add(new Supplier(record.id(), record.code(), record.name(), record.contact(),
                    record.phone(), record.email(), "", "", record.paymentTerms(),
                    record.balance() == null ? BigDecimal.ZERO : record.balance(), true, 0, null,
                    materialCount, List.of(), List.of()));
        }
        return result;
    }

    private static boolean matches(Supplier supplier, String search) {
        return supplier.code().toLowerCase(Locale.ROOT).contains(search)
                || supplier.name().toLowerCase(Locale.ROOT).contains(search)
                || supplier.contactName().toLowerCase(Locale.ROOT).contains(search)
                || supplier.phone().toLowerCase(Locale.ROOT).contains(search);
    }
}
