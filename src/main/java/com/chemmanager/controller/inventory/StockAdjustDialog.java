package com.chemmanager.controller.inventory;

import java.math.BigDecimal;
import java.util.Optional;

import com.chemmanager.component.Dialogs;
import com.chemmanager.component.FormFields;
import com.chemmanager.component.Toasts;
import com.chemmanager.localization.I18n;
import com.chemmanager.model.inventory.StockBatch;
import com.chemmanager.util.Formatters;

import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/**
 * Physical count of one batch. The dialog only collects the counted quantity and the reason; the
 * server refuses a count that finds no difference, so a stock movement always explains something.
 */
public final class StockAdjustDialog {

    public record Result(BigDecimal countedQuantity, String reason) {
    }

    private StockAdjustDialog() {
    }

    public static Optional<Result> open(Window owner, StockBatch batch) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner);
        Dialogs.applyTheme(dialog.getDialogPane());
        dialog.setTitle(I18n.get().t("inventory.adjust.title"));
        ButtonType save = new ButtonType(I18n.get().t("common.save"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);

        Label system = new Label(I18n.get().t("inventory.adjust.hint", batch.itemCode(),
                batch.batchNumber(), Formatters.quantity(batch.quantity(), batch.unit())));
        system.getStyleClass().add("text-muted");
        system.setWrapText(true);

        TextField counted = new TextField(batch.quantity().toPlainString());
        TextField reason = new TextField();

        VBox content = new VBox(12, system,
                FormFields.fieldKey("inventory.adjust.counted", counted, "inventory.adjust.counted_hint"),
                FormFields.fieldKey("inventory.adjust.reason", reason, "inventory.adjust.reason_hint"));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(470);

        Optional<ButtonType> answer = dialog.showAndWait();
        if (answer.isEmpty() || answer.get() != save) {
            return Optional.empty();
        }
        if (counted.getText() == null || counted.getText().isBlank()) {
            Toasts.warning(I18n.get().t("validation.required"));
            return Optional.empty();
        }
        BigDecimal value = Formatters.parseDecimal(counted.getText());
        if (value.signum() < 0) {
            Toasts.warning(I18n.get().t("validation.positive"));
            return Optional.empty();
        }
        if (reason.getText() == null || reason.getText().isBlank()) {
            Toasts.warning(I18n.get().t("inventory.adjust.reason_required"));
            return Optional.empty();
        }

        return Optional.of(new Result(value, reason.getText().trim()));
    }
}
