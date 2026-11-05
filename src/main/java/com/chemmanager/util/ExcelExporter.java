package com.chemmanager.util;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.chemmanager.localization.I18n;
import com.chemmanager.model.analytics.CategoryShare;
import com.chemmanager.model.analytics.DashboardData;
import com.chemmanager.model.analytics.KpiSet;
import com.chemmanager.model.analytics.MonthPoint;
import com.chemmanager.model.analytics.StockAlert;
import com.chemmanager.model.analytics.TopProduct;

/**
 * Apache POI based Excel export. Used by the dashboard and (from Phase 8) by the report center.
 * Writes a formatted workbook: bold headers, frozen panes, auto-size and numeric cells for
 * amounts so that the finance team can continue working in Excel.
 */
public final class ExcelExporter {

    private ExcelExporter() {
    }

    public static void exportRows(Path target, String sheetName, List<String> headers, List<List<Object>> rows)
            throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(sheetName == null ? "Export" : sheetName);
            CellStyle headerStyle = headerStyle(workbook);
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(headerStyle);
            }
            int rowIndex = 1;
            for (List<Object> values : rows) {
                Row row = sheet.createRow(rowIndex++);
                for (int i = 0; i < values.size(); i++) {
                    writeCell(row.createCell(i), values.get(i));
                }
            }
            for (int i = 0; i < headers.size(); i++) {
                sheet.autoSizeColumn(i);
                sheet.setColumnWidth(i, Math.min(sheet.getColumnWidth(i) + 900, 12000));
            }
            sheet.createFreezePane(0, 1);
            try (OutputStream out = Files.newOutputStream(target)) {
                workbook.write(out);
            }
        }
    }

    public static void exportDashboard(Path target, DashboardData data) throws IOException {
        I18n i18n = I18n.get();
        try (Workbook workbook = new XSSFWorkbook()) {
            CellStyle header = headerStyle(workbook);
            KpiSet kpis = data.kpis();

            Sheet summary = workbook.createSheet(i18n.t("dashboard.kpi_title"));
            int r = 0;
            r = writePair(summary, header, r, i18n.t("dashboard.sales_today"), Formatters.currency(kpis.salesToday()));
            r = writePair(summary, header, r, i18n.t("dashboard.sales_month"), Formatters.currency(kpis.salesMonth()));
            r = writePair(summary, header, r, i18n.t("dashboard.sales_year"), Formatters.currency(kpis.salesYear()));
            r = writePair(summary, header, r, i18n.t("dashboard.sales_range"), Formatters.currency(kpis.salesInRange()));
            r = writePair(summary, header, r, i18n.t("dashboard.purchases"), Formatters.currency(kpis.purchasesInRange()));
            r = writePair(summary, header, r, i18n.t("dashboard.expenses"), Formatters.currency(kpis.expensesInRange()));
            r = writePair(summary, header, r, i18n.t("dashboard.gross_profit"), Formatters.currency(kpis.grossProfitInRange()));
            r = writePair(summary, header, r, i18n.t("dashboard.net_profit"), Formatters.currency(kpis.netProfitInRange()));
            r = writePair(summary, header, r, i18n.t("dashboard.inventory_value"), Formatters.currency(kpis.inventoryValue()));
            writePair(summary, header, r, i18n.t("dashboard.receivables"), Formatters.currency(kpis.receivables()));
            summary.autoSizeColumn(0);
            summary.autoSizeColumn(1);

            Sheet monthly = workbook.createSheet(i18n.t("dashboard.chart.monthly"));
            String[] monthlyHeaders = {
                    i18n.t("common.month"), i18n.t("dashboard.sales"), i18n.t("dashboard.purchases"),
                    i18n.t("dashboard.expenses"), i18n.t("production.cost"),
                    i18n.t("production.quantity")};
            writeHeader(monthly, header, monthlyHeaders);
            int row = 1;
            for (MonthPoint point : data.monthly()) {
                Row excelRow = monthly.createRow(row++);
                excelRow.createCell(0).setCellValue(Formatters.monthLong(point.month()));
                writeCell(excelRow.createCell(1), point.sales());
                writeCell(excelRow.createCell(2), point.purchases());
                writeCell(excelRow.createCell(3), point.expenses());
                writeCell(excelRow.createCell(4), point.productionCost());
                writeCell(excelRow.createCell(5), point.productionQuantity());
            }
            autoSize(monthly, monthlyHeaders.length);

            Sheet categories = workbook.createSheet(i18n.t("dashboard.chart.categories"));
            writeHeader(categories, header, new String[]{i18n.t("common.category"), i18n.t("dashboard.sales")});
            row = 1;
            for (CategoryShare share : data.salesByCategory()) {
                Row excelRow = categories.createRow(row++);
                excelRow.createCell(0).setCellValue(share.label());
                writeCell(excelRow.createCell(1), share.value());
            }
            autoSize(categories, 2);

            Sheet bestSellers = workbook.createSheet(i18n.t("dashboard.best_sellers"));
            writeHeader(bestSellers, header, new String[]{i18n.t("product.sku"), i18n.t("product.name"),
                    i18n.t("common.category"), i18n.t("common.quantity"), i18n.t("dashboard.revenue")});
            row = 1;
            for (TopProduct product : data.bestSellers()) {
                Row excelRow = bestSellers.createRow(row++);
                excelRow.createCell(0).setCellValue(product.sku());
                excelRow.createCell(1).setCellValue(product.name());
                excelRow.createCell(2).setCellValue(product.category());
                writeCell(excelRow.createCell(3), product.quantity());
                writeCell(excelRow.createCell(4), product.revenue());
            }
            autoSize(bestSellers, 5);

            Sheet alerts = workbook.createSheet(i18n.t("dashboard.alerts_title"));
            writeHeader(alerts, header, new String[]{i18n.t("common.type"), i18n.t("common.code"),
                    i18n.t("common.item"), i18n.t("common.quantity"), i18n.t("inventory.warehouse"),
                    i18n.t("material.expiry_date")});
            row = 1;
            for (StockAlert alert : data.lowStockAlerts()) {
                Row excelRow = alerts.createRow(row++);
                excelRow.createCell(0).setCellValue(alert.type().name());
                excelRow.createCell(1).setCellValue(alert.itemCode());
                excelRow.createCell(2).setCellValue(alert.itemName());
                writeCell(excelRow.createCell(3), alert.quantity());
                excelRow.createCell(4).setCellValue(alert.warehouse() == null ? "" : alert.warehouse());
                excelRow.createCell(5).setCellValue("");
            }
            for (StockAlert alert : data.expiryAlerts()) {
                Row excelRow = alerts.createRow(row++);
                excelRow.createCell(0).setCellValue(alert.type().name());
                excelRow.createCell(1).setCellValue(alert.itemCode());
                excelRow.createCell(2).setCellValue(alert.itemName());
                writeCell(excelRow.createCell(3), alert.quantity());
                excelRow.createCell(4).setCellValue(alert.warehouse() == null ? "" : alert.warehouse());
                LocalDate expiry = alert.expiryDate();
                excelRow.createCell(5).setCellValue(expiry == null ? "" : Formatters.shortDate(expiry));
            }
            autoSize(alerts, 6);

            try (OutputStream out = Files.newOutputStream(target)) {
                workbook.write(out);
            }
        }
    }

    private static int writePair(Sheet sheet, CellStyle headerStyle, int rowIndex, String label, String value) {
        Row row = sheet.createRow(rowIndex);
        Cell labelCell = row.createCell(0);
        labelCell.setCellValue(label);
        labelCell.setCellStyle(headerStyle);
        row.createCell(1).setCellValue(value);
        return rowIndex + 1;
    }

    private static void writeHeader(Sheet sheet, CellStyle headerStyle, String[] headers) {
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        sheet.createFreezePane(0, 1);
    }

    private static void autoSize(Sheet sheet, int columns) {
        for (int i = 0; i < columns; i++) {
            sheet.autoSizeColumn(i);
            sheet.setColumnWidth(i, Math.min(sheet.getColumnWidth(i) + 900, 12000));
        }
    }

    private static CellStyle headerStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setBorderBottom(BorderStyle.THIN);
        style.setWrapText(false);
        return style;
    }

    private static void writeCell(Cell cell, Object value) {
        if (value == null) {
            cell.setBlank();
        } else if (value instanceof BigDecimal decimal) {
            cell.setCellValue(decimal.doubleValue());
        } else if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
        } else if (value instanceof LocalDate date) {
            cell.setCellValue(Formatters.shortDate(date));
        } else {
            cell.setCellValue(value.toString());
        }
    }
}
