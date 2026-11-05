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
