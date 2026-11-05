package com.chemmanager.util;

import java.util.List;
import java.util.function.Function;

import com.chemmanager.dto.PagedResult;

/** Client-side paging used by mock services and by in-memory table models. */
public final class Paging {

    private Paging() {
    }

    public static <T> PagedResult<T> slice(List<T> all, int page, int pageSize) {
        int size = Math.max(1, pageSize);
        int total = all == null ? 0 : all.size();
        int totalPages = Math.max(1, (int) Math.ceil(total / (double) size));
        int safePage = Math.min(Math.max(0, page), totalPages - 1);
        int fromIndex = Math.min(safePage * size, total);
        int toIndex = Math.min(fromIndex + size, total);
        List<T> items = all == null ? List.of() : all.subList(fromIndex, toIndex);
        return new PagedResult<>(List.copyOf(items), safePage, size, total);
    }

    public static <T, R> List<R> map(List<T> source, Function<T, R> mapper) {
        return source == null ? List.of() : source.stream().map(mapper).toList();
    }
}
