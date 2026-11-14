package com.chemmanager.controller.purchasing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.chemmanager.component.FormFields;
import com.chemmanager.component.Toasts;
import com.chemmanager.localization.I18n;
import com.chemmanager.model.inventory.Warehouse;
import com.chemmanager.model.purchasing.PurchaseOrder;
import com.chemmanager.model.purchasing.ReceptionDraft;
import com.chemmanager.util.Formatters;

import javafx.collections.FXCollections;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.StringConverter;

/**
 * Receives goods against an order: which warehouse, one quantity and batch number per arrived line,
 * and whether quality releases the goods immediately.
 *
 * <p>What arrives stays in QUARANTINE unless a qualified user ticks "release", and released goods
 * can never be received into the quarantine warehouse — the server refuses both mistakes.</p>
 */
public final class GoodsReceiptDialog {

    private GoodsReceiptDialog() {
    }

    public static Optional<ReceptionDraft> open(Window owner, PurchaseOrder order, List<Warehouse> warehouses) {
        List<PurchaseOrder.Line> open = order.items().stream()
                .filter(line -> line.remainingQty() != null && line.remainingQty().signum() > 0)
                .toList();
        if (open.isEmpty()) {
            Toasts.info(I18n.get().t("purchasing.receive.nothing"));
            return Optional.empty();
        }
        if (warehouses.isEmpty()) {
            Toasts.info(I18n.get().t("purchasing.receive.no_warehouse"));
            return Optional.empty();
        }
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.getDialogPane().getStyleClass().add("dialog-pane");
        dialog.setTitle(I18n.get().t("purchasing.receive.title"));
        ButtonType save = new ButtonType(I18n.get().t("common.confirm"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);

        ComboBox<Warehouse> warehouse = new ComboBox<>(FXCollections.observableArrayList(warehouses));
        warehouse.setConverter(new StringConverter<>() {
            @Override
            public String toString(Warehouse value) {
                return value == null ? "" : value.code() + " · " + value.name();
            }

            @Override
            public Warehouse fromString(String text) {
                return null;
            }
        });
        warehouse.getSelectionModel().selectFirst();

        CheckBox release = new CheckBox(I18n.get().t("purchasing.receive.release"));
        Label releaseHint = new Label(I18n.get().t("purchasing.receive.release_hint"));
        releaseHint.getStyleClass().add("text-muted");
        releaseHint.setWrapText(true);
        boolean quarantineOnly = warehouse.getValue() != null && warehouse.getValue().quarantine();
        release.setDisable(quarantineOnly);
        warehouse.valueProperty().addListener((observable, oldValue, newValue) -> {
            boolean quarantined = newValue != null && newValue.quarantine();
            release.setDisable(quarantined);
            if (quarantined) {
                release.setSelected(false);
            }
        });

        TextField notes = new TextField();

        GridPane lines = new GridPane();
        lines.setHgap(10);
        lines.setVgap(8);
        lines.addRow(0, header(I18n.get().t("common.item")), header(I18n.get().t("purchasing.column.remaining")),
                header(I18n.get().t("purchasing.receive.quantity")), header(I18n.get().t("purchasing.receive.batch")),
                header(I18n.get().t("purchasing.receive.expiry")));
        List<LineInputs> inputs = new ArrayList<>();
        int row = 1;
        for (PurchaseOrder.Line line : open) {
            TextField quantity = new TextField(line.remainingQty().toPlainString());
            quantity.setPrefWidth(100);
            TextField batch = new TextField(suggestion(line));
            batch.setPrefWidth(150);
            DatePicker expiry = new DatePicker(LocalDate.now().plusMonths(12));
            expiry.setPrefWidth(150);
            lines.addRow(row++, new Label(line.materialCode() + " · " + line.materialName()),
                    new Label(Formatters.quantity(line.remainingQty(), line.unit())), quantity, batch, expiry);
            inputs.add(new LineInputs(line, quantity, batch, expiry));
        }
        ScrollPane scroll = new ScrollPane(lines);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(260);

        VBox content = new VBox(12,
                FormFields.field("purchasing.receive.warehouse", warehouse),
                new VBox(4, release, releaseHint),
                FormFields.field("purchasing.new.notes", notes),
                FormFields.sectionTitle("purchasing.detail.lines"),
                scroll);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(760);

        Optional<ButtonType> answer = dialog.showAndWait();
        if (answer.isEmpty() || answer.get() != save) {
            return Optional.empty();
        }
        Warehouse target = warehouse.getValue();
        if (target == null) {
            Toasts.warning(I18n.get().t("validation.required"));
            return Optional.empty();
        }
        if (release.isSelected() && target.quarantine()) {
            Toasts.warning(I18n.get().t("inventory.error.quarantine"));
            return Optional.empty();
        }

        List<ReceptionDraft.Line> collected = new ArrayList<>();
        for (LineInputs input : inputs) {
            if (input.quantity().getText() == null || input.quantity().getText().isBlank()) {
                continue;
            }
            BigDecimal amount = Formatters.parseDecimal(input.quantity().getText());
            if (amount.signum() <= 0) {
                continue;
            }
            if (input.batch().getText() == null || input.batch().getText().isBlank()) {
                Toasts.warning(I18n.get().t("purchasing.receive.batch_required"));
                return Optional.empty();
            }
            collected.add(new ReceptionDraft.Line(input.line().id(), amount, input.batch().getText().trim(),
                    input.expiry().getValue(), LocalDate.now()));
        }
        if (collected.isEmpty()) {
            Toasts.warning(I18n.get().t("purchasing.receive.nothing"));
            return Optional.empty();
        }

        return Optional.of(new ReceptionDraft(target.id(), release.isSelected(), notes.getText(), collected));
    }

    private static Label header(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("field-label");

        return label;
    }

    private static String suggestion(PurchaseOrder.Line line) {
        String code = line.materialCode() == null || line.materialCode().isBlank() ? "LOT" : line.materialCode();

        return code + "-" + LocalDate.now().toString().replace("-", "").substring(2);
    }

    private record LineInputs(PurchaseOrder.Line line, TextField quantity, TextField batch, DatePicker expiry) {
    }
}
