package com.chemmanager.controller.formulations;

import com.chemmanager.component.Dialogs;
import com.chemmanager.dto.FormulationComparison;
import com.chemmanager.localization.I18n;
import com.chemmanager.util.Formatters;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/** Read-only version-to-version diff: what changed, by how much, and what it costs. */
final class FormulationCompareDialog {

    private FormulationCompareDialog() {
    }

    static void show(Window owner, FormulationComparison comparison) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner);
        Dialogs.applyTheme(dialog.getDialogPane());
        dialog.setTitle(I18n.get().t("formulations.compare_title",
                comparison.leftVersion(), comparison.rightVersion()));
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        TableView<FormulationComparison.Line> table = new TableView<>();
        table.setItems(FXCollections.observableArrayList(comparison.lines()));
        table.setPrefSize(780, 340);

        TableColumn<FormulationComparison.Line, String> code = new TableColumn<>(I18n.get().t("material.code"));
        code.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().materialCode()));
        code.setPrefWidth(120);

        TableColumn<FormulationComparison.Line, String> name = new TableColumn<>(I18n.get().t("material.name"));
        name.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().materialName()));
        name.setPrefWidth(240);

        TableColumn<FormulationComparison.Line, String> left = new TableColumn<>("v" + comparison.leftVersion());
        left.setCellValueFactory(cell -> new SimpleStringProperty(
                Formatters.quantity(cell.getValue().leftQuantity(), cell.getValue().unit())));
        left.setPrefWidth(110);

        TableColumn<FormulationComparison.Line, String> right = new TableColumn<>("v" + comparison.rightVersion());
        right.setCellValueFactory(cell -> new SimpleStringProperty(
                Formatters.quantity(cell.getValue().rightQuantity(), cell.getValue().unit())));
        right.setPrefWidth(110);

        TableColumn<FormulationComparison.Line, String> delta = new TableColumn<>(I18n.get().t("formulations.delta"));
        delta.setCellValueFactory(cell -> new SimpleStringProperty(
                Formatters.quantity(cell.getValue().delta(), cell.getValue().unit())));
        delta.setPrefWidth(110);

        TableColumn<FormulationComparison.Line, String> status = new TableColumn<>(I18n.get().t("common.status"));
        status.setCellValueFactory(cell -> new SimpleStringProperty(
                I18n.get().t("formulations.change." + cell.getValue().status().toLowerCase())));
        status.setPrefWidth(120);

        table.getColumns().addAll(code, name, left, right, delta, status);

        Label cost = new Label(I18n.get().t("formulations.cost_delta",
                Formatters.currency(comparison.leftCost()),
                Formatters.currency(comparison.rightCost()),
                Formatters.currency(comparison.costDelta())));
        cost.getStyleClass().add("text-muted");

        HBox summary = new HBox(12, cost);

        dialog.getDialogPane().setContent(new VBox(10, table, summary));
        dialog.getDialogPane().setPrefWidth(820);
        dialog.showAndWait();
    }
}
