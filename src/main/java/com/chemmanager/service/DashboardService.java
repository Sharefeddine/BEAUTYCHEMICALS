package com.chemmanager.service;

import com.chemmanager.model.DateRange;
import com.chemmanager.model.analytics.DashboardData;

/** Supplies every figure and series of the executive dashboard for a date range. */
public interface DashboardService {

    DashboardData load(DateRange range);
}
