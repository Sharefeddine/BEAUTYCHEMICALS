package com.chemmanager.dto;

/** Filter + paging state of the formulation list. */
public record FormulationQuery(String search, String status, int page, int pageSize) {

    public static FormulationQuery initial(int pageSize) {
        return new FormulationQuery(null, null, 0, pageSize);
    }

    public FormulationQuery withSearch(String value) {
        return new FormulationQuery(value, status, 0, pageSize);
    }

    public FormulationQuery withStatus(String value) {
        return new FormulationQuery(search, value, 0, pageSize);
    }

    public FormulationQuery withPage(int value) {
        return new FormulationQuery(search, status, value, pageSize);
    }

    public FormulationQuery withPageSize(int value) {
        return new FormulationQuery(search, status, 0, value);
    }
}
