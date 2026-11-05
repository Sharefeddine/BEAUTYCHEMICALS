package com.chemmanager.service.api;

import java.util.LinkedHashMap;
import java.util.Map;

import com.chemmanager.api.ApiClient;
import com.chemmanager.api.ApiEndpoints;
import com.chemmanager.model.DateRange;
import com.chemmanager.model.analytics.DashboardData;
import com.chemmanager.service.DashboardService;

/** Dashboard served by the SQL aggregations of the PHP backend. */
public final class ApiDashboardService implements DashboardService {

    private final ApiClient client;

    public ApiDashboardService(ApiClient client) {
        this.client = client;
    }

    @Override
    public DashboardData load(DateRange range) {
        Map<String, String> query = new LinkedHashMap<>();
        if (range != null) {
            query.put("from", range.from().toString());
            query.put("to", range.to().toString());
        }
        return client.get(ApiEndpoints.DASHBOARD, query, DashboardData.class);
    }
}
