package com.chemmanager.util;

import java.util.Locale;

import com.chemmanager.localization.I18n;
import com.chemmanager.model.ProductType;
import com.chemmanager.model.RawMaterial;
import com.chemmanager.model.Severity;
import com.chemmanager.model.analytics.StockAlertType;

/**
 * Translates business codes coming from the database (categories, statuses, hazard classes)
 * into localised labels, with a readable fallback when a code has no translation yet.
 */
public final class Labels {

    private Labels() {
    }

    public static String get(String key, String fallback) {
        String translated = I18n.get().t(key);
        return translated.startsWith("!") ? fallback : translated;
    }

    public static String nav(String moduleId) {
        return get("nav." + moduleId.replace('-', '_'), humanize(moduleId));
    }

    public static String navSubtitle(String moduleId) {
        return get("nav." + moduleId.replace('-', '_') + ".subtitle", "");
    }

    public static String category(String code) {
        if (code == null || code.isBlank()) {
            return "—";
        }
        return get("category." + code.toLowerCase(Locale.ROOT), humanize(code));
    }

    public static String productType(ProductType type) {
        return type == null ? "—" : get(type.labelKey(), humanize(type.name()));
    }

    public static String severity(Severity severity) {
        return severity == null ? "—" : get("severity." + severity.name().toLowerCase(Locale.ROOT), severity.name());
    }

    public static String alertType(StockAlertType type) {
        return type == null ? "—" : get("alert." + type.name().toLowerCase(Locale.ROOT), humanize(type.name()));
    }

    public static String productionStatus(String status) {
        if (status == null || status.isBlank()) {
            return "—";
        }
        return get("production.status." + status.toLowerCase(Locale.ROOT), humanize(status));
    }

    public static String productionStatusStyle(String status) {
        if (status == null) {
            return "badge-neutral";
        }
        return switch (status) {
            case "COMPLETED" -> "badge-success";
            case "REJECTED", "CANCELLED" -> "badge-danger";
            case "QUALITY_CONTROL", "WAITING_MATERIALS" -> "badge-warning";
            case "IN_PROGRESS", "APPROVED" -> "badge-info";
            default -> "badge-neutral";
        };
    }

    /** Customer price segments (RETAIL / WHOLESALE / SPECIAL). */
    public static String customerSegment(String segment) {
        if (segment == null || segment.isBlank()) {
            return "—";
        }
        return get("customer.segment." + segment.toLowerCase(Locale.ROOT), humanize(segment));
    }

    public static String customerSegmentStyle(String segment) {
        if (segment == null) {
            return "badge-neutral";
        }
        return switch (segment) {
            case "SPECIAL" -> "badge-primary";
            case "WHOLESALE" -> "badge-info";
            default -> "badge-neutral";
        };
    }

    /** COMPANY / INDIVIDUAL. */
    public static String customerType(String type) {
        if (type == null || type.isBlank()) {
            return "—";
        }
        return get("customer.type." + type.toLowerCase(Locale.ROOT), humanize(type));
    }

    /** Quotation statuses. */
    public static String quotationStatus(String status) {
        if (status == null || status.isBlank()) {
            return "—";
        }
        return get("sales.status.quotation." + status.toLowerCase(Locale.ROOT), humanize(status));
    }

    /** Sales order statuses. */
    public static String orderStatus(String status) {
        if (status == null || status.isBlank()) {
            return "—";
        }
        return get("sales.status.order." + status.toLowerCase(Locale.ROOT), humanize(status));
    }

    /** Delivery note statuses. */
    public static String deliveryStatus(String status) {
        if (status == null || status.isBlank()) {
            return "—";
        }
        return get("sales.status.delivery." + status.toLowerCase(Locale.ROOT), humanize(status));
    }

    /** Invoice statuses. */
    public static String invoiceStatus(String status) {
        if (status == null || status.isBlank()) {
            return "—";
        }
        return get("sales.status.invoice." + status.toLowerCase(Locale.ROOT), humanize(status));
    }

    /** Payment methods (CASH, CHEQUE, TRANSFER, CARD, CREDIT). */
    public static String paymentMethod(String method) {
        if (method == null || method.isBlank()) {
            return "—";
        }
        return get("sales.method." + method.toLowerCase(Locale.ROOT), humanize(method));
    }

    /** Tour statuses: PLANNED, LOADING, IN_TRANSIT, COMPLETED, CANCELLED. */
    public static String routeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "—";
        }
        return get("distribution.status." + status.toLowerCase(Locale.ROOT), humanize(status));
    }

    /** Expense statuses: DRAFT, APPROVED, PAID, CANCELLED. */
    public static String expenseStatus(String status) {
        if (status == null || status.isBlank()) {
            return "—";
        }
        return get("finance.expense.status." + status.toLowerCase(Locale.ROOT), humanize(status));
    }

    /** Accounting period statuses: OPEN, CLOSED. */
    public static String periodStatus(String status) {
        if (status == null || status.isBlank()) {
            return "—";
        }
        return get("finance.period.status." + status.toLowerCase(Locale.ROOT), humanize(status));
    }

    /** Leave statuses: REQUESTED, APPROVED, REJECTED, CANCELLED. */
    public static String leaveStatus(String status) {
        if (status == null || status.isBlank()) {
            return "—";
        }
        return get("employees.leave.status." + status.toLowerCase(Locale.ROOT), humanize(status));
    }

    /** Leave types: ANNUAL, SICK, MATERNITY, UNPAID, EXCEPTIONAL. */
    public static String leaveType(String type) {
        if (type == null || type.isBlank()) {
            return "—";
        }
        return get("employees.leave.type." + type.toLowerCase(Locale.ROOT), humanize(type));
    }

    /** Attendance codes: PRESENT, ABSENT, LEAVE, SICK, HOLIDAY, SITE. */
    public static String attendanceStatus(String status) {
        if (status == null || status.isBlank()) {
            return "—";
        }
        return get("employees.attendance." + status.toLowerCase(Locale.ROOT), humanize(status));
    }

    /** Contract types: CDI, CDD, INTERIM, STAGE. */
    public static String contractType(String type) {
        if (type == null || type.isBlank()) {
            return "—";
        }
        return get("employees.contract." + type.toLowerCase(Locale.ROOT), humanize(type));
    }

    /** Where a cash movement comes from: INVOICE, PAYMENT, EXPENSE, SALARY, TRANSFER, OTHER. */
    public static String cashSource(String sourceType) {
        if (sourceType == null || sourceType.isBlank()) {
            return "—";
        }
        return get("finance.source." + sourceType.toLowerCase(Locale.ROOT), humanize(sourceType));
    }

    /** IN / OUT, as the cash book reads them. */
    public static String cashDirection(String direction) {
        if (direction == null || direction.isBlank()) {
            return "—";
        }
        return get("finance.direction." + direction.toLowerCase(Locale.ROOT), humanize(direction));
    }

    /** One colour for the finance statuses, so the two lists scan the same way. */
    public static String financeStatusStyle(String status) {
        if (status == null) {
            return "badge-neutral";
        }
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "PAID", "OPEN", "APPROVED" -> "badge-success";
            case "CANCELLED", "REJECTED", "CLOSED" -> "badge-danger";
            case "DRAFT", "REQUESTED" -> "badge-warning";
            default -> "badge-neutral";
        };
    }

    /** One colour for every kind of commercial status, so the lists scan the same way. */
    public static String commercialStatusStyle(String status) {
        if (status == null) {
            return "badge-neutral";
        }
        return switch (status) {
            case "ACCEPTED", "INVOICED", "PAID", "DELIVERED", "COMPLETED" -> "badge-success";
            case "REJECTED", "CANCELLED", "EXPIRED", "REFUSED" -> "badge-danger";
            case "SENT", "PARTIAL", "RESERVED", "IN_TRANSIT", "LOADING", "RETURNED" -> "badge-warning";
            case "ISSUED", "CONFIRMED", "PREPARED", "PLANNED", "APPLIED" -> "badge-info";
            default -> "badge-neutral";
        };
    }

    /** Translates hazard classifications, including comma separated GHS pictogram codes. */
    public static String hazardClass(String hazard) {
        if (hazard == null || hazard.isBlank() || "NONE".equalsIgnoreCase(hazard)) {
            return get("hazard.none", "—");
        }
        String[] parts = hazard.split(",");
        StringBuilder label = new StringBuilder();
        for (String part : parts) {
            String code = part.trim();
            if (code.isEmpty()) {
                continue;
            }
            String key = code.toUpperCase(Locale.ROOT).startsWith("GHS")
                    ? "ghs." + code.replaceAll("\\D", "")
                    : "hazard." + code.toLowerCase(Locale.ROOT).replace('/', '_');
            if (!label.isEmpty()) {
                label.append(", ");
            }
            label.append(get(key, humanize(code)));
        }
        return label.isEmpty() ? get("hazard.none", "—") : label.toString();
    }

    public static String hazardStyle(RawMaterial material) {
        return switch (material.hazardSeverity()) {
            case CRITICAL -> "badge-danger";
            case WARNING -> "badge-warning";
            case INFO -> "badge-neutral";
        };
    }

    public static String quality(String quality) {
        if (quality == null || quality.isBlank()) {
            return "—";
        }
        return get("inventory.quality." + quality.toLowerCase(Locale.ROOT), humanize(quality));
    }

    public static String qualityStyle(String quality) {
        if (quality == null) {
            return "badge-neutral";
        }
        return switch (quality) {
            case "RELEASED" -> "badge-success";
            case "QUARANTINE" -> "badge-warning";
            case "REJECTED" -> "badge-danger";
            default -> "badge-neutral";
        };
    }

    public static String movementType(String type) {
        if (type == null || type.isBlank()) {
            return "—";
        }
        return get("inventory.movement." + type.toLowerCase(Locale.ROOT), humanize(type));
    }

    public static String movementTypeStyle(String type) {
        if (type == null) {
            return "badge-neutral";
        }
        return switch (type) {
            case "IN", "PRODUCTION_IN", "RETURN" -> "badge-success";
            case "OUT", "PRODUCTION_OUT", "WASTE" -> "badge-danger";
            case "TRANSFER" -> "badge-info";
            case "ADJUSTMENT" -> "badge-warning";
            default -> "badge-neutral";
        };
    }

    public static String purchaseStatus(String status) {
        if (status == null || status.isBlank()) {
            return "—";
        }
        return get("purchasing.status." + status.toLowerCase(Locale.ROOT), humanize(status));
    }

    public static String purchaseStatusStyle(String status) {
        if (status == null) {
            return "badge-neutral";
        }
        return switch (status) {
            case "DRAFT" -> "badge-neutral";
            case "SENT" -> "badge-info";
            case "PARTIAL" -> "badge-warning";
            case "RECEIVED" -> "badge-success";
            case "CANCELLED" -> "badge-danger";
            default -> "badge-neutral";
        };
    }

    public static String materialCategory(String code) {
        return get("mcat." + (code == null ? "" : code.toLowerCase(Locale.ROOT)), humanize(code));
    }

    public static String permission(String name) {
        return get("permission." + name.toLowerCase(Locale.ROOT), humanize(name));
    }

    public static String phase(int phase) {
        return get("phase.label", "Phase %s").formatted(phase);
    }

    /** {@code FLOOR_CLEANER} -> {@code Floor cleaner}. */
    public static String humanize(String code) {
        if (code == null || code.isBlank()) {
            return "";
        }
        String spaced = code.replace('_', ' ').replace('-', ' ').trim().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }
}
