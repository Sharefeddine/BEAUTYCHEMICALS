package com.chemmanager.controller.purchasing;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.chemmanager.component.FormFields;
import com.chemmanager.component.Toasts;
import com.chemmanager.localization.I18n;
import com.chemmanager.model.RawMaterial;
import com.chemmanager.model.Supplier;
import com.chemmanager.model.purchasing.PurchaseOrderDraft;
import com.chemmanager.util.Formatters;

import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.StringConverter;

/**
 * Creates a draft order: supplier, expected date, then the requested lines.
 *
 * <p>The dialog only gathers intentions. The server numbers the order, computes the VAT and the
 * totals from the lines, and keeps it a DRAFT until someone sends it to the supplier.</p>
 */
public final class PurchaseOrderDialog {

    private PurchaseOrderDialog() {
    }

    public static Optional<PurchaseOrderDraft> open(Window owner, List<Supplier> suppliers,
                                                    List<RawMaterial> materials) {
        if (suppliers.isEmpty() || materials.isEmpty()) {
            Toasts.info(I18n.get().t("purchasing.new.needs_data"));
            return Optional.empty();
        }
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.getDialogPane().getStyleClass().add("dialog-pane");
        dialog.setTitle(I18n.get().t("purchasing.new"));
        ButtonType save = new ButtonType(I18n.get().t("common.save"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);

        ComboBox<Supplier> supplier = new ComboBox<>(FXCollections.observableArrayList(suppliers));
        supplier.setConverter(new StringConverter<>() {
            @Override
            public String toString(Supplier value) {
                return value == null ? "" : value.code() + " · " + value.name();
            }

            @Override
            public Supplier fromString(String text) {
                return null;
            }
        });
        supplier.getSelectionModel().selectFirst();

        DatePicker expectedDate = new DatePicker();
        expectedDate.setPromptText(I18n.get().t("purchasing.new.expected"));
        TextField notes = new TextField();

        ComboBox<RawMaterial> material = new ComboBox<>(FXCollections.observableArrayList(materials));
        material.setPrefWidth(280);
        material.setConverter(new StringConverter<>() {
            @Override
            public String toString(RawMaterial value) {
                return value == null ? "" : value.code() + " · " + value.commercialName();
            }

            @Override
            public RawMaterial fromString(String text) {
                return null;
            }
        });
        material.getSelectionModel().selectFirst();

        TextField quantity = new TextField();
        quantity.setPrefWidth(110);
        TextField unitPrice = new TextField();
        unitPrice.setPrefWidth(120);
        material.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null && newValue.purchasePrice() != null) {
                unitPrice.setText(newValue.purchasePrice().toPlainString());
            }
        });
        if (material.getValue() != null && material.getValue().purchasePrice() != null) {
            unitPrice.setText(material.getValue().purchasePrice().toPlainString());
        }

        List<PurchaseOrderDraft.Line> collected = new ArrayList<>();
        VBox linesBox = new VBox(6);
        linesBox.getChildren().add(emptyLineLabel());

        Button add = new Button(I18n.get().t("purchasing.new.add_line"));
        add.getStyleClass().addAll("btn", "btn-secondary", "btn-sm");
        add.setOnAction(event -> {
            RawMaterial picked = material.getValue();
            BigDecimal amount = Formatters.parseDecimal(quantity.getText());
            if (picked == null || quantity.getText() == null || quantity.getText().isBlank() || amount.signum() <= 0) {
                Toasts.warning(I18n.get().t("purchasing.new.invalid"));
                return;
            }
            PurchaseOrderDraft.Line line = new PurchaseOrderDraft.Line(picked.id(), amount,
                    picked.storageUnit(), Formatters.parseDecimal(unitPrice.getText()), new BigDecimal("19"));
            collected.add(line);
            HBox row = new HBox(8, new Label(picked.code() + " · " + Formatters.quantity(amount,
                    picked.storageUnit()) + " × " + Formatters.currency(line.unitPrice())));
            row.setAlignment(Pos.CENTER_LEFT);
            Button remove = new Button(I18n.get().t("common.remove"));
            remove.getStyleClass().addAll("btn", "btn-ghost", "btn-sm");
            remove.setOnAction(removeEvent -> {
                collected.remove(line);
                linesBox.getChildren().remove(row);
                if (collected.isEmpty()) {
                    linesBox.getChildren().add(emptyLineLabel());
                }
            });
            linesBox.getChildren().removeIf(node -> node instanceof Label
                    && I18n.get().t("purchasing.new.lines_empty").equals(((Label) node).getText()));
            row.getChildren().addAll(new javafx.scene.layout.Region(), remove);
            HBox.setHgrow(row.getChildren().get(1), Priority.ALWAYS);
            linesBox.getChildren().add(row);
            quantity.clear();
        });

        HBox lineEditor = new HBox(8, material, quantity, unitPrice, add);
        lineEditor.setAlignment(Pos.CENTER_LEFT);

        ScrollPane scroll = new ScrollPane(linesBox);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(140);

        VBox content = new VBox(12,
                FormFields.field("purchasing.new.supplier", supplier),
                FormFields.field("purchasing.new.expected", expectedDate),
                FormFields.field("purchasing.new.notes", notes),
                FormFields.sectionTitle("purchasing.detail.lines"),
                lineEditor,
                scroll);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(640);

        Optional<ButtonType> answer = dialog.showAndWait();
        if (answer.isEmpty() || answer.get() != save) {
            return Optional.empty();
        }
        if (collected.isEmpty()) {
            Toasts.warning(I18n.get().t("purchasing.new.no_line"));
            return Optional.empty();
        }

        return Optional.of(new PurchaseOrderDraft(supplier.getValue().id(), expectedDate.getValue(),
                notes.getText(), collected));
    }

    private static Label emptyLineLabel() {
        Label label = new Label(I18n.get().t("purchasing.new.lines_empty"));
        label.getStyleClass().add("text-muted");

        return label;
    }
}
