package com.chemmanager.dto;

/** Search, segment filter and pagination of the customer list. */
public record CustomerQuery(String search, String segment, int page, int pageSize) {

    public static CustomerQuery initial(int pageSize) {
        return new CustomerQuery("", "", 0, pageSize);
    }

    public CustomerQuery withSearch(String value) {
        return new CustomerQuery(value == null ? "" : value, segment, page, pageSize);
    }

    public CustomerQuery withSegment(String value) {
        return new CustomerQuery(search, value == null ? "" : value, page, pageSize);
    }

    public CustomerQuery withPage(int value) {
        return new CustomerQuery(search, segment, value, pageSize);
    }

    public CustomerQuery withPageSize(int value) {
        return new CustomerQuery(search, segment, page, value);
    }
}
