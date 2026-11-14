package com.chemmanager.model.finance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Cash flow month by month and by source, straight from the cash book. */
public record CashflowReport(LocalDate from, LocalDate to, List<Month> timeline, List<Source> bySource,
                             BigDecimal totalIn, BigDecimal totalOut, BigDecimal net) {

    public CashflowReport {
        timeline = timeline == null ? List.of() : List.copyOf(timeline);
        bySource = bySource == null ? List.of() : List.copyOf(bySource);
    }

    public record Month(String period, BigDecimal totalIn, BigDecimal totalOut, BigDecimal net) {
    }

    public record Source(String sourceType, BigDecimal totalIn, BigDecimal totalOut, BigDecimal net) {
    }
}
