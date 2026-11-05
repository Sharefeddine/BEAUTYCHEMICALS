package com.chemmanager.dto;

import java.util.List;
import java.util.function.Function;

/** Immutable page of results with the metadata a table footer needs. */
public record PagedResult<T>(List<T> items, int page, int pageSize, long total) {

    public PagedResult {
        items = items == null ? List.of() : List.copyOf(items);
        pageSize = Math.max(1, pageSize);
    }

    public static <T> PagedResult<T> empty(int pageSize) {
        return new PagedResult<>(List.of(), 0, pageSize, 0);
    }

    public int totalPages() {
        return Math.max(1, (int) Math.ceil(total / (double) pageSize));
    }

    public boolean hasNext() {
        return page + 1 < totalPages();
    }

    public boolean hasPrevious() {
        return page > 0;
    }

    public int firstIndex() {
        return total == 0 ? 0 : page * pageSize + 1;
    }

    public int lastIndex() {
        return (int) Math.min(total, (long) (page + 1) * pageSize);
    }

    public <R> PagedResult<R> map(Function<T, R> mapper) {
        return new PagedResult<>(items.stream().map(mapper).toList(), page, pageSize, total);
    }
}
