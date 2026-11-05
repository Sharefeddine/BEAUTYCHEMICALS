package com.chemmanager.dto;

import java.time.LocalDate;

/** Query parameters sent to {@code /dashboard/summary}. */
public record DashboardRequest(LocalDate from, LocalDate to, String warehouse, String category) {
}
