package com.chemmanager.service;

import java.time.LocalDate;
import java.util.List;

import com.chemmanager.model.analytics.ProductionReport;
import com.chemmanager.model.analytics.PurchaseReport;
import com.chemmanager.model.analytics.ReportCatalogueEntry;
import com.chemmanager.model.analytics.SafetyReport;
import com.chemmanager.model.analytics.SalesReport;
import com.chemmanager.model.analytics.StockReport;

/**
 * The report centre: every operational report of the workshop in one place.
 *
 * <p>The three statements of the accountant live on {@link FinanceService}; this service covers what
 * was sold, what was bought, what the store is worth, what the workshop produced and how much of the
 * chemical register is documented. Each report is computed by the server from the documents.</p>
 */
public interface ReportCentreService {

    /** The eight reports the centre can produce. */
    List<ReportCatalogueEntry> catalogue();

    /** What was invoiced in the window, per month, customer and product. */
    SalesReport sales(LocalDate from, LocalDate to);

    /** What was ordered and received in the window. */
    PurchaseReport purchases(LocalDate from, LocalDate to);

    /**
     * What the store is worth today.
     *
     * @param expiryDays how far ahead the expiry watch looks
     */
    StockReport stock(int expiryDays);

    /** What the workshop produced in the window. */
    ProductionReport production(LocalDate from, LocalDate to);

    /** How much of the chemical register is documented, and what happened. */
    SafetyReport safety();
}
