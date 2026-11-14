package com.chemmanager.dto;

import java.time.LocalDate;

/** Search, status and date filter of the tour list. */
public record RouteQuery(String search, String status, LocalDate routeDate, int page, int pageSize) {

    public static RouteQuery initial(int pageSize) {
        return new RouteQuery("", "", null, 0, pageSize);
    }

    public RouteQuery withSearch(String value) {
        return new RouteQuery(value == null ? "" : value, status, routeDate, page, pageSize);
    }

    public RouteQuery withStatus(String value) {
        return new RouteQuery(search, value == null ? "" : value, routeDate, page, pageSize);
    }

    public RouteQuery withDate(LocalDate value) {
        return new RouteQuery(search, status, value, page, pageSize);
    }

    public RouteQuery withPage(int value) {
        return new RouteQuery(search, status, routeDate, value, pageSize);
    }

    public RouteQuery withPageSize(int value) {
        return new RouteQuery(search, status, routeDate, page, value);
    }
}
