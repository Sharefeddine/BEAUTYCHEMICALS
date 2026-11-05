package com.chemmanager.component;

import com.chemmanager.model.RawMaterial;
import com.chemmanager.model.Severity;
import com.chemmanager.model.analytics.StockAlertType;
import com.chemmanager.util.Labels;

import javafx.scene.control.Label;

/** Small coloured pills used for statuses, hazard classes and severities. */
public final class StatusBadge {

    private StatusBadge() {
    }

    public static Label of(String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().addAll("badge", styleClass);
        return label;
    }

    public static Label success(String text) {
        return of(text, "badge-success");
    }

    public static Label warning(String text) {
        return of(text, "badge-warning");
    }

    public static Label danger(String text) {
        return of(text, "badge-danger");
    }

    public static Label info(String text) {
        return of(text, "badge-info");
    }

    public static Label neutral(String text) {
        return of(text, "badge-neutral");
    }

    public static Label primary(String text) {
        return of(text, "badge-primary");
    }

    public static Label severity(Severity severity) {
        if (severity == null) {
            return neutral("—");
        }
        return of(Labels.severity(severity), severity.styleClass());
    }

    public static Label alert(StockAlertType type) {
        String style = switch (type) {
            case EXPIRED, OUT_OF_STOCK -> "badge-danger";
            case EXPIRING, LOW_STOCK -> "badge-warning";
        };
        return of(Labels.alertType(type), style);
    }

    public static Label production(String status) {
        return of(Labels.productionStatus(status), Labels.productionStatusStyle(status));
    }

    public static Label hazard(RawMaterial material) {
        if (!material.isDangerous()) {
            return of(Labels.get("hazard.none", "—"), "badge-neutral");
        }
        return of(Labels.hazardClass(material.hazardClass()), Labels.hazardStyle(material));
    }

    public static Label active(boolean active) {
        return active ? success(Labels.get("common.active", "Actif"))
                : neutral(Labels.get("common.inactive", "Inactif"));
    }
}
