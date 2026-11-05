package com.chemmanager.service.mock;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.chemmanager.dto.PagedResult;
import com.chemmanager.dto.ProductQuery;
import com.chemmanager.model.Product;
import com.chemmanager.repository.MockDataStore;
import com.chemmanager.service.ProductService;
import com.chemmanager.util.Paging;

/** Product catalogue backed by the demo dataset. */
public final class MockProductService implements ProductService {

    private final MockDataStore store = MockDataStore.get();

    @Override
    public PagedResult<Product> search(ProductQuery query) {
        ProductQuery effective = query == null ? ProductQuery.initial(20) : query;
        String search = normalize(effective.search());
        List<Product> filtered = store.products().stream()
                .filter(product -> matches(product, effective, search))
                .sorted(Comparator.comparing(Product::sku))
                .toList();
        return Paging.slice(filtered, effective.page(), effective.pageSize());
    }

    @Override
    public List<String> categories() {
        return store.productCategories();
    }

    @Override
    public Optional<Product> findById(long id) {
        return store.products().stream().filter(product -> product.id() == id).findFirst();
    }

    private boolean matches(Product product, ProductQuery query, String search) {
        if (query.type() != null && product.type() != query.type()) {
            return false;
        }
        if (query.category() != null && !query.category().isBlank()
                && !query.category().equalsIgnoreCase(product.category())) {
            return false;
        }
        if (query.activeOnly() && !product.active()) {
            return false;
        }
        if (search.isEmpty()) {
            return true;
        }
        return contains(product.sku(), search)
                || contains(product.nameFr(), search)
                || contains(product.nameAr(), search)
                || contains(product.barcode(), search)
                || contains(product.category(), search);
    }

    private static boolean contains(String value, String search) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(search);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    /** Exposed for the products screen footer: total valuation of the filtered set. */
    public BigDecimal valuation(List<Product> products) {
        return products.stream().map(Product::stockValue).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
