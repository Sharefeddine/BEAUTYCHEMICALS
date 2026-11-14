package com.chemmanager.dto;

/** The staff filter: search, department, active-only and paging. */
public record EmployeeQuery(String search, int page, int pageSize, long departmentId, Boolean activeOnly) {

    public static EmployeeQuery initial(int pageSize) {
        return new EmployeeQuery("", 0, pageSize, 0, null);
    }

    public EmployeeQuery withSearch(String value) {
        return new EmployeeQuery(value, page, pageSize, departmentId, activeOnly);
    }

    public EmployeeQuery withPage(int value) {
        return new EmployeeQuery(search, value, pageSize, departmentId, activeOnly);
    }

    public EmployeeQuery withPageSize(int value) {
        return new EmployeeQuery(search, page, value, departmentId, activeOnly);
    }

    public EmployeeQuery withActiveOnly(Boolean value) {
        return new EmployeeQuery(search, 0, pageSize, departmentId, value);
    }
}
