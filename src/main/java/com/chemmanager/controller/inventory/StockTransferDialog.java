package com.chemmanager.controller.inventory;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.chemmanager.component.Dialogs;
import com.chemmanager.component.FormFields;
import com.chemmanager.component.Toasts;
import com.chemmanager.localization.I18n;
import com.chemmanager.model.inventory.StockBatch;
import com.chemmanager.model.inventory.Warehouse;
import com.chemmanager.util.Formatters;

import javafx.collections.FXCollections;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.StringConverter;

/**
 * Moves part of a batch to another warehouse. The destination list only offers the warehouses the
 * quality status allows, and the server checks the same rule again.
 */
public final class StockTransferDialog {

    public record Result(long targetWarehouseId, BigDecimal quantity, String reason) {
    }

    private StockTransferDialog() {
    }

    public static Optional<Result> open(Window owner, StockBatch batch, List<Warehouse> targets) {
        if (targets.isEmpty()) {
            Toasts.info(I18n.get().t("inventory.transfer.no_target"));
            return Optional.empty();
        }
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner);
        Dialogs.applyTheme(dialog.getDialogPane());
        dialog.setTitle(I18n.get().t("inventory.transfer.title"));
        ButtonType save = new ButtonType(I18n.get().t("common.save"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);

        Label available = new Label(I18n.get().t("inventory.transfer.available", batch.itemCode(),
                batch.batchNumber(), Formatters.quantity(batch.quantity(), batch.unit())));
        available.getStyleClass().add("text-muted");
        available.setWrapText(true);

        ComboBox<Warehouse> target = new ComboBox<>(FXCollections.observableArrayList(targets));
        target.setConverter(new StringConverter<>() {
            @Override
            public String toString(Warehouse warehouse) {
                return warehouse == null ? "" : warehouse.code() + " · " + warehouse.name();
            }

            @Override
            public Warehouse fromString(String text) {
                return null;
            }
        });
        target.getSelectionModel().selectFirst();

        TextField quantity = new TextField(batch.quantity().toPlainString());
        TextField reason = new TextField();

        VBox content = new VBox(12, available,
                FormFields.field("inventory.transfer.target", target),
                FormFields.fieldKey("inventory.transfer.quantity", quantity, "inventory.transfer.quantity_hint"),
                FormFields.fieldKey("inventory.adjust.reason", reason, "inventory.adjust.reason_hint"));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(470);

        Optional<ButtonType> answer = dialog.showAndWait();
        if (answer.isEmpty() || answer.get() != save) {
            return Optional.empty();
        }
        Warehouse picked = target.getValue();
        if (picked == null) {
            Toasts.warning(I18n.get().t("validation.required"));
            return Optional.empty();
        }
        if (quantity.getText() == null || quantity.getText().isBlank()) {
            Toasts.warning(I18n.get().t("validation.required"));
            return Optional.empty();
        }
        BigDecimal value = Formatters.parseDecimal(quantity.getText());
        if (value.signum() <= 0) {
            Toasts.warning(I18n.get().t("validation.positive"));
            return Optional.empty();
        }
        if (reason.getText() == null || reason.getText().isBlank()) {
            Toasts.warning(I18n.get().t("inventory.adjust.reason_required"));
            return Optional.empty();
        }

        return Optional.of(new Result(picked.id(), value, reason.getText().trim()));
    }
}
