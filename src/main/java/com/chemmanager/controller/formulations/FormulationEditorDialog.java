package com.chemmanager.controller.formulations;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.chemmanager.component.Dialogs;
import com.chemmanager.component.Toasts;
import com.chemmanager.dto.PagedResult;
import com.chemmanager.localization.I18n;
import com.chemmanager.model.FormulationItem;
import com.chemmanager.model.FormulationVersion;
import com.chemmanager.model.RawMaterial;
import com.chemmanager.service.ServiceFactory;
import com.chemmanager.dto.MaterialQuery;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.StringConverter;

/**
 * Editor of one draft version: the raw materials, their quantities, the phase and the mixing
 * instructions written by the production manager.
 *
 * <p>Nothing is suggested or pre-filled beyond the quantities already stored: the application
 * records what the user types and refuses an empty bill of materials.</p>
 */
final class FormulationEditorDialog {

    /** Result of the dialog: the content the user confirmed. */
    record Content(String instructions, BigDecimal expectedYield, String yieldUnit, List<FormulationItem> items) {
    }

    private final List<RawMaterial> materials;
    private final ObservableList<Line> lines = FXCollections.observableArrayList();

    private FormulationEditorDialog(List<RawMaterial> materials) {
        this.materials = materials;
    }

    static Optional<Content> open(Window owner, FormulationVersion version) {
        List<RawMaterial> materials = loadMaterials();
        if (materials.isEmpty()) {
            Toasts.warning(I18n.get().t("formulations.no_materials"));
            return Optional.empty();
        }

        FormulationEditorDialog editor = new FormulationEditorDialog(materials);
        return editor.build(owner, version);
    }

    private Optional<Content> build(Window owner, FormulationVersion version) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner);
        Dialogs.applyTheme(dialog.getDialogPane());
        dialog.setTitle(I18n.get().t("formulations.edit_version", version.label()));
        ButtonType save = new ButtonType(I18n.get().t("common.save"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);

        TableView<Line> table = new TableView<>(lines);
        table.setEditable(true);
        table.setPrefSize(760, 300);
        table.setPlaceholder(new Label(I18n.get().t("formulations.items_empty")));

        TableColumn<Line, String> material = new TableColumn<>(I18n.get().t("material.name"));
        material.setPrefWidth(300);
        material.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().materialLabel()));
        table.getColumns().add(material);

        TableColumn<Line, String> quantity = new TableColumn<>(I18n.get().t("formulations.quantity"));
        quantity.setPrefWidth(110);
        quantity.setCellValueFactory(cell -> cell.getValue().quantity);
        quantity.setCellFactory(TextFieldTableCell.forTableColumn());
        quantity.setOnEditCommit(event -> event.getRowValue().quantity.set(event.getNewValue()));
        table.getColumns().add(quantity);

        TableColumn<Line, String> unit = new TableColumn<>(I18n.get().t("common.unit"));
        unit.setPrefWidth(80);
        unit.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().material.storageUnit()));
        table.getColumns().add(unit);

        TableColumn<Line, String> phase = new TableColumn<>(I18n.get().t("formulations.phase"));
        phase.setPrefWidth(110);
        phase.setCellValueFactory(cell -> cell.getValue().phase);
        phase.setCellFactory(TextFieldTableCell.forTableColumn());
        phase.setOnEditCommit(event -> event.getRowValue().phase.set(event.getNewValue()));
        table.getColumns().add(phase);

        TableColumn<Line, String> remove = new TableColumn<>("");
        remove.setPrefWidth(90);
        remove.setCellFactory(column -> new TableCell<>() {
            private final Button button = new Button(I18n.get().t("common.remove"));

            {
                button.getStyleClass().addAll("btn", "btn-danger", "btn-sm");
                button.setOnAction(event -> lines.remove(getTableView().getItems().get(getIndex())));
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : button);
            }
        });
        table.getColumns().add(remove);

        ComboBox<RawMaterial> picker = new ComboBox<>(FXCollections.observableArrayList(materials));
        picker.setPrefWidth(330);
        picker.setConverter(new StringConverter<>() {
            @Override
            public String toString(RawMaterial material) {
                return material == null ? "" : material.code() + " · " + material.commercialName();
            }

            @Override
            public RawMaterial fromString(String text) {
                return null;
            }
        });
        Button add = new Button(I18n.get().t("formulations.add_line"));
        add.getStyleClass().addAll("btn", "btn-secondary", "btn-sm");
        add.setOnAction(event -> {
            RawMaterial material1 = picker.getValue();
            if (material1 == null) {
                return;
            }
            if (lines.stream().anyMatch(line -> line.material.id() == material1.id())) {
                Toasts.warning(I18n.get().t("formulations.duplicate_line"));
                return;
            }
            lines.add(new Line(material1, BigDecimal.ONE, "A"));
            picker.setValue(null);
        });

        HBox adder = new HBox(8, picker, add);

        TextField yield = new TextField(version.expectedYield().stripTrailingZeros().toPlainString());
        yield.setPrefWidth(120);
        ComboBox<String> yieldUnit = new ComboBox<>(FXCollections.observableArrayList("KG", "L", "ML", "PCE"));
        yieldUnit.setValue(version.yieldUnit() == null || version.yieldUnit().isBlank() ? "KG" : version.yieldUnit());

        TextArea instructions = new TextArea(version.instructions());
        instructions.setPrefWidth(760);
        instructions.setPrefRowCount(4);
        instructions.setPromptText(I18n.get().t("formulations.instructions_prompt"));

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.add(new Label(I18n.get().t("formulations.expected_yield")), 0, 0);
        grid.add(new HBox(8, yield, yieldUnit), 1, 0);
        grid.add(new Label(I18n.get().t("formulations.instructions")), 0, 1);
        grid.add(instructions, 1, 1);

        Label note = new Label(I18n.get().t("formulations.manual_note"));
        note.getStyleClass().add("text-muted");
        note.setWrapText(true);

        VBox content = new VBox(12, adder, table, grid, note);
        content.setPadding(new Insets(4, 0, 0, 0));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(820);

        for (FormulationItem item : version.items()) {
            materials.stream()
                    .filter(material1 -> material1.id() == item.materialId())
                    .findFirst()
                    .ifPresent(material1 -> lines.add(new Line(material1, item.quantity(), item.phase())));
        }

        Button saveButton = (Button) dialog.getDialogPane().lookupButton(save);
        saveButton.getStyleClass().addAll("btn", "btn-primary");

        Optional<ButtonType> answer = dialog.showAndWait();
        if (answer.isEmpty() || answer.get() != save) {
            return Optional.empty();
        }
        if (lines.isEmpty()) {
            Toasts.warning(I18n.get().t("formulations.items_required"));
            return Optional.empty();
        }

        List<FormulationItem> items = new ArrayList<>();
        for (Line line : lines) {
            BigDecimal quantityValue = line.quantityValue();
            if (quantityValue.signum() <= 0) {
                Toasts.warning(I18n.get().t("formulations.quantity_required"));
                return Optional.empty();
            }
            items.add(FormulationItem.entry(line.material.id(), line.material.code(),
                    line.material.commercialName(), quantityValue, line.material.storageUnit(),
                    line.phase.get()));
        }

        return Optional.of(new Content(instructions.getText(),
                parseDecimal(yield.getText()), yieldUnit.getValue(), items));
    }

    private static BigDecimal parseDecimal(String value) {
        try {
            return new BigDecimal(value.trim().replace(',', '.'));
        } catch (RuntimeException ex) {
            return BigDecimal.ZERO;
        }
    }

    private static List<RawMaterial> loadMaterials() {
        try {
            PagedResult<RawMaterial> page = ServiceFactory.rawMaterials().search(MaterialQuery.initial(200));
            Set<Long> seen = new LinkedHashSet<>();
            List<RawMaterial> rows = new ArrayList<>();
            for (RawMaterial material : page.items()) {
                if (seen.add(material.id())) {
                    rows.add(material);
                }
            }
            return rows;
        } catch (RuntimeException ex) {
            return List.of();
        }
    }

    /** One editable line of the bill of materials, backed by observable values for the table. */
    private static final class Line {
        private final RawMaterial material;
        private final javafx.beans.property.SimpleStringProperty quantity;
        private final javafx.beans.property.SimpleStringProperty phase;

        Line(RawMaterial material, BigDecimal quantity, String phase) {
            this.material = material;
            this.quantity = new javafx.beans.property.SimpleStringProperty(
                    quantity.stripTrailingZeros().toPlainString());
            this.phase = new javafx.beans.property.SimpleStringProperty(phase == null ? "A" : phase);
        }

        String materialLabel() {
            return material.code() + " · " + material.commercialName();
        }

        BigDecimal quantityValue() {
            return parseDecimal(quantity.get());
        }
    }
}
