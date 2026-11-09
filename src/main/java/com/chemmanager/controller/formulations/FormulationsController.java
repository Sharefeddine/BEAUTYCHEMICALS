package com.chemmanager.controller.formulations;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.chemmanager.component.Card;
import com.chemmanager.component.DataTable;
import com.chemmanager.component.Dialogs;
import com.chemmanager.component.Icons;
import com.chemmanager.component.StatusBadge;
import com.chemmanager.component.Toasts;
import com.chemmanager.dto.FormulationComparison;
import com.chemmanager.dto.FormulationQuery;
import com.chemmanager.dto.PagedResult;
import com.chemmanager.localization.I18n;
import com.chemmanager.model.Formulation;
import com.chemmanager.model.FormulationItem;
import com.chemmanager.model.FormulationVersion;
import com.chemmanager.model.Product;
import com.chemmanager.security.Permission;
import com.chemmanager.security.SessionContext;
import com.chemmanager.service.ServiceFactory;
import com.chemmanager.util.Formatters;
import com.chemmanager.util.FxTasks;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/**
 * Formulations module: the list of formula families, their version history, the bill of materials
 * of each version, the approval that locks it and the comparison between two versions.
 *
 * <p>The screen never proposes a formula. A version is created empty, filled by a qualified user,
 * then approved by someone who holds {@code FORMULATIONS_APPROVE}; the approval is irreversible by
 * design, so a change always produces a new version and the history stays auditable.</p>
 */
public final class FormulationsController {

    @FXML private VBox contentRoot;
    @FXML private VBox tableHost;
    @FXML private VBox detailHost;
    @FXML private Label summaryLabel;
    @FXML private ComboBox<String> statusFilter;
    @FXML private Button newButton;
    @FXML private Button refreshButton;

    private DataTable<Formulation> table;
    private FormulationQuery query = FormulationQuery.initial(20);
    private Formulation selected;
    private List<FormulationVersion> versions = List.of();
    private long selectedVersionId;

    @FXML
    private void initialize() {
        newButton.setGraphic(Icons.glyph(Icons.Icon.PLUS, 12));
        newButton.setOnAction(event -> createFormulation());
        newButton.setDisable(!canEdit());
        refreshButton.setGraphic(Icons.glyph(Icons.Icon.REFRESH, 12));
        refreshButton.setOnAction(event -> table.resetToFirstPage());

        statusFilter.setItems(FXCollections.observableArrayList("", "DRAFT", "APPROVED", "ARCHIVED"));
        statusFilter.setConverter(new StringConverter<>() {
            @Override
            public String toString(String value) {
                return value == null || value.isBlank() ? I18n.get().t("common.all") : statusLabel(value);
            }

            @Override
            public String fromString(String text) {
                return text;
            }
        });
        statusFilter.getSelectionModel().selectFirst();
        statusFilter.setOnAction(event -> {
            query = query.withStatus(statusFilter.getValue());
            table.resetToFirstPage();
        });

        table = new DataTable<>(this::loadPage, I18n.get().t("formulations.search_prompt"))
                .columns(
                        DataTable.Column.key(I18n.get().t("formulation.code"), 120, Formulation::code),
                        DataTable.Column.text(I18n.get().t("formulation.name"), 230,
                                formulation -> formulation.displayName(I18n.get().isRtl())),
                        DataTable.Column.text(I18n.get().t("formulation.product"), 190,
                                formulation -> formulation.productName() == null ? "" : formulation.productName()),
                        DataTable.Column.node(I18n.get().t("common.status"), 115,
                                formulation -> StatusBadge.of(statusLabel(formulation.status()),
                                        statusStyle(formulation.status()))),
                        DataTable.Column.text(I18n.get().t("formulation.version"), 75,
                                Formulation::versionLabel),
                        DataTable.Column.text(I18n.get().t("formulation.versions"), 85,
                                formulation -> Integer.toString(formulation.versionCount())),
                        DataTable.Column.amount(I18n.get().t("formulation.cost"), 120,
                                Formulation::currentCost),
                        DataTable.Column.text(I18n.get().t("formulation.approved_by"), 130,
                                formulation -> formulation.approvedByName() == null ? "" : formulation.approvedByName()))
                .onActivate(this::select)
                .onLoaded(page -> {
                    summaryLabel.setText(I18n.get().t("formulations.summary", page.total()));
                    if (selected == null && !page.items().isEmpty()) {
                        select(page.items().get(0));
                    }
                });
        VBox.setVgrow(table, javafx.scene.layout.Priority.ALWAYS);
        tableHost.getChildren().setAll(table);
        table.resetToFirstPage();
    }

    // ------------------------------------------------------------------ data

    private PagedResult<Formulation> loadPage(String search, int page, int pageSize) {
        query = query.withSearch(search).withPageSize(pageSize).withPage(page);
        return ServiceFactory.formulations().search(query);
    }

    private void select(Formulation formulation) {
        selected = formulation;
        selectedVersionId = formulation.currentVersionId() == null ? 0 : formulation.currentVersionId();
        FxTasks.run(() -> {
            Formulation loaded = ServiceFactory.formulations().load(formulation.id());
            List<FormulationVersion> history = ServiceFactory.formulations().versions(formulation.id());
            if (selectedVersionId == 0 && !history.isEmpty()) {
                selectedVersionId = history.get(0).id();
            }
            FormulationVersion current = selectedVersionId == 0 ? null
                    : ServiceFactory.formulations().version(loaded.id(), selectedVersionId);
            return new Detail(loaded, history, current);
        }, this::renderDetail, error -> Toasts.error(I18n.get().t("error.loading")));
    }

    /** Refreshes the list and re-reads the selected formulation. */
    private void refreshSelection(Formulation formulation) {
        selected = formulation;
        select(formulation);
    }

    // ------------------------------------------------------------------ detail panel

    private void renderDetail(Detail detail) {
        Formulation formulation = detail.formulation();
        versions = detail.versions();
        FormulationVersion version = detail.version();

        Card card = new Card(formulation.code() + " · " + formulation.displayName(I18n.get().isRtl()),
                formulation.productName() == null ? I18n.get().t("formulation.no_product")
                        : I18n.get().t("formulation.for_product", formulation.productName()));
        card.getStyleClass().add("card");

        ComboBox<FormulationVersion> versionPicker = new ComboBox<>(FXCollections.observableArrayList(versions));
        versionPicker.setPrefWidth(230);
        versionPicker.setConverter(new StringConverter<>() {
            @Override
            public String toString(FormulationVersion value) {
                return value == null ? "" : value.label() + (value.locked() ? " 🔒" : "");
            }

            @Override
            public FormulationVersion fromString(String text) {
                return null;
            }
        });
        versionPicker.getSelectionModel().select(version);
        versionPicker.setOnAction(event -> {
            FormulationVersion picked = versionPicker.getValue();
            if (picked != null && picked.id() != selectedVersionId) {
                selectedVersionId = picked.id();
                select(formulation);
            }
        });

        Button newVersion = new Button(I18n.get().t("formulations.new_version"));
        newVersion.getStyleClass().addAll("btn", "btn-secondary", "btn-sm");
        newVersion.setDisable(!canEdit());
        newVersion.setOnAction(event -> createVersion(formulation));

        Button edit = new Button(I18n.get().t("formulations.edit_items"));
        edit.getStyleClass().addAll("btn", "btn-secondary", "btn-sm");
        edit.setDisable(!canEdit() || version == null || version.locked());
        edit.setOnAction(event -> editVersion(formulation, version));

        Button approve = new Button(I18n.get().t("formulations.approve"));
        approve.getStyleClass().addAll("btn", "btn-primary", "btn-sm");
        approve.setDisable(!canApprove() || version == null || version.locked() || version.items().isEmpty());
        approve.setOnAction(event -> approve(formulation, version));

        Button compare = new Button(I18n.get().t("formulations.compare"));
        compare.getStyleClass().addAll("btn", "btn-secondary", "btn-sm");
        compare.setDisable(versions.size() < 2);
        compare.setOnAction(event -> compare(formulation));

        HBox actions = new HBox(8, versionPicker, newVersion, edit, approve, compare);
        actions.setAlignment(Pos.CENTER_LEFT);
        card.action(actions);

        FlowPane facts = new FlowPane(10, 8);
        facts.getChildren().addAll(
                StatusBadge.of(statusLabel(formulation.status()), statusStyle(formulation.status())),
                fact(I18n.get().t("formulations.versions_count"), Integer.toString(versions.size())),
                fact(I18n.get().t("formulations.cost"),
                        version == null ? Formatters.currency(formulation.currentCost())
                                : Formatters.currency(version.cost())),
                fact(I18n.get().t("formulations.expected_yield"),
                        version == null ? "—"
                                : Formatters.quantity(version.expectedYield(), version.yieldUnit())),
                fact(I18n.get().t("formulations.items_count"),
                        version == null ? "0" : Integer.toString(version.itemCount())),
                fact(I18n.get().t("formulation.approved_by"),
                        version == null || version.approvedByName() == null ? "—" : version.approvedByName()));

        TableView<FormulationItem> items = itemsTable(version);
        Label instructions = new Label(version == null || version.instructions().isBlank()
                ? I18n.get().t("formulations.no_instructions")
                : version.instructions());
        instructions.setWrapText(true);
        instructions.getStyleClass().add("text-muted");

        card.body(facts, items, instructions);
        detailHost.getChildren().setAll(card);
    }

    private TableView<FormulationItem> itemsTable(FormulationVersion version) {
        TableView<FormulationItem> items = new TableView<>();
        items.setPrefHeight(210);
        items.setItems(FXCollections.observableArrayList(version == null ? List.of() : version.items()));
        items.setPlaceholder(new Label(I18n.get().t("formulations.items_empty")));

        TableColumn<FormulationItem, String> code = new TableColumn<>(I18n.get().t("material.code"));
        code.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().materialCode()));
        code.setPrefWidth(110);

        TableColumn<FormulationItem, String> name = new TableColumn<>(I18n.get().t("material.name"));
        name.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().materialName()));
        name.setPrefWidth(250);

        TableColumn<FormulationItem, String> quantity = new TableColumn<>(I18n.get().t("formulations.quantity"));
        quantity.setCellValueFactory(cell -> new SimpleStringProperty(
                Formatters.quantity(cell.getValue().quantity(), cell.getValue().unit())));
        quantity.setPrefWidth(120);

        TableColumn<FormulationItem, String> percentage = new TableColumn<>("%");
        percentage.setCellValueFactory(cell -> new SimpleStringProperty(
                Formatters.decimal(cell.getValue().percentage(), 2)));
        percentage.setPrefWidth(70);

        TableColumn<FormulationItem, String> phase = new TableColumn<>(I18n.get().t("formulations.phase"));
        phase.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().phase()));
        phase.setPrefWidth(80);

        TableColumn<FormulationItem, String> lineCost = new TableColumn<>(I18n.get().t("formulations.line_cost"));
        lineCost.setCellValueFactory(cell -> new SimpleStringProperty(
                Formatters.currency(cell.getValue().lineCost())));
        lineCost.setPrefWidth(120);

        TableColumn<FormulationItem, String> stock = new TableColumn<>(I18n.get().t("material.stock"));
        stock.setCellValueFactory(cell -> new SimpleStringProperty(
                Formatters.quantity(cell.getValue().stockQuantity(), cell.getValue().unit())));
        stock.setPrefWidth(110);

        TableColumn<FormulationItem, String> hazard = new TableColumn<>(I18n.get().t("material.hazard"));
        hazard.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().dangerous() ? cell.getValue().hazardClass() : ""));
        hazard.setPrefWidth(110);

        items.getColumns().addAll(code, name, quantity, percentage, phase, lineCost, stock, hazard);
        return items;
    }

    private Label fact(String label, String value) {
        Label node = new Label(label + " : " + value);
        node.getStyleClass().add("text-muted");
        return node;
    }

    // ------------------------------------------------------------------ actions

    private void createFormulation() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(contentRoot.getScene().getWindow());
        dialog.getDialogPane().getStyleClass().add("dialog-pane");
        dialog.setTitle(I18n.get().t("formulations.new"));
        ButtonType save = new ButtonType(I18n.get().t("common.save"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);

        TextField nameFr = new TextField();
        TextField nameAr = new TextField();
        TextArea notes = new TextArea();
        notes.setPrefRowCount(3);
        ComboBox<Product> product = new ComboBox<>(FXCollections.observableArrayList(productChoices()));
        product.setPrefWidth(320);
        product.setConverter(new StringConverter<>() {
            @Override
            public String toString(Product value) {
                return value == null ? "" : value.sku() + " · " + value.nameFr();
            }

            @Override
            public Product fromString(String text) {
                return null;
            }
        });

        VBox content = new VBox(10,
                new Label(I18n.get().t("formulation.name_fr")), nameFr,
                new Label(I18n.get().t("formulation.name_ar")), nameAr,
                new Label(I18n.get().t("formulation.product")), product,
                new Label(I18n.get().t("formulation.notes")), notes);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(460);

        Optional<ButtonType> answer = dialog.showAndWait();
        if (answer.isEmpty() || answer.get() != save) {
            return;
        }
        if (nameFr.getText().isBlank()) {
            Toasts.warning(I18n.get().t("formulations.name_required"));
            return;
        }

        Product chosen = product.getValue();
        FxTasks.run(() -> ServiceFactory.formulations().create(nameFr.getText().trim(),
                        nameAr.getText().isBlank() ? nameFr.getText().trim() : nameAr.getText().trim(),
                        chosen == null ? null : chosen.id(), notes.getText()),
                created -> {
                    Toasts.success(I18n.get().t("formulations.created", created.code()));
                    table.resetToFirstPage();
                    refreshSelection(created);
                },
                error -> Toasts.error(message(error)));
    }

    private void createVersion(Formulation formulation) {
        if (!Dialogs.confirm(contentRoot.getScene().getWindow(), I18n.get().t("formulations.new_version"),
                I18n.get().t("formulations.new_version_confirm"))) {
            return;
        }
        FxTasks.run(() -> ServiceFactory.formulations().createVersion(formulation.id()),
                version -> {
                    Toasts.success(I18n.get().t("formulations.version_created", version.label()));
                    selectedVersionId = version.id();
                    refreshSelection(formulation);
                },
                error -> Toasts.error(message(error)));
    }

    private void editVersion(Formulation formulation, FormulationVersion version) {
        Optional<FormulationEditorDialog.Content> content =
                FormulationEditorDialog.open(contentRoot.getScene().getWindow(), version);
        content.ifPresent(value -> FxTasks.run(
                () -> ServiceFactory.formulations().updateVersion(formulation.id(), version.id(),
                        value.instructions(), value.expectedYield(), value.yieldUnit(), value.items()),
                updated -> {
                    Toasts.success(I18n.get().t("formulations.saved", Formatters.currency(updated.cost())));
                    selectedVersionId = updated.id();
                    refreshSelection(formulation);
                },
                error -> Toasts.error(message(error))));
    }

    private void approve(Formulation formulation, FormulationVersion version) {
        if (!Dialogs.confirm(contentRoot.getScene().getWindow(), I18n.get().t("formulations.approve"),
                I18n.get().t("formulations.approve_confirm", version.label(),
                        Formatters.currency(version.cost())))) {
            return;
        }
        FxTasks.run(() -> ServiceFactory.formulations().approve(formulation.id(), version.id()),
                approved -> {
                    Toasts.success(I18n.get().t("formulations.approved", approved.label()));
                    selectedVersionId = approved.id();
                    refreshSelection(formulation);
                },
                error -> Toasts.error(message(error)));
    }

    private void compare(Formulation formulation) {
        List<FormulationVersion> ordered = new ArrayList<>(versions);
        if (ordered.size() < 2) {
            return;
        }
        FormulationVersion left = ordered.get(ordered.size() - 1);
        FormulationVersion right = ordered.get(0);
        FxTasks.run(() -> ServiceFactory.formulations().compare(formulation.id(), left.id(), right.id()),
                (FormulationComparison comparison) -> FormulationCompareDialog.show(
                        contentRoot.getScene().getWindow(), comparison),
                error -> Toasts.error(message(error)));
    }

    // ------------------------------------------------------------------ helpers

    private static List<Product> productChoices() {
        try {
            PagedResult<Product> page = ServiceFactory.products()
                    .search(com.chemmanager.dto.ProductQuery.initial(200));
            return page.items();
        } catch (RuntimeException ex) {
            return List.of();
        }
    }

    private static String statusLabel(String status) {
        return I18n.get().t("formulations.status." + status.toLowerCase());
    }

    private static String statusStyle(String status) {
        return switch (status == null ? "" : status.toUpperCase()) {
            case "APPROVED" -> "badge-success";
            case "ARCHIVED" -> "badge-neutral";
            case "PENDING" -> "badge-warning";
            default -> "badge-info";
        };
    }

    private static boolean canEdit() {
        return SessionContext.get().can(Permission.FORMULATIONS_EDIT);
    }

    private static boolean canApprove() {
        return SessionContext.get().can(Permission.FORMULATIONS_APPROVE);
    }

    private static String message(Throwable error) {
        if (error.getMessage() != null && error.getMessage().contains("FORMULATION_LOCKED")) {
            return I18n.get().t("formulations.locked_error");
        }
        return error.getMessage() == null ? I18n.get().t("error.generic") : error.getMessage();
    }

    /** Everything the detail panel needs for one selection. */
    private record Detail(Formulation formulation, List<FormulationVersion> versions,
                          FormulationVersion version) {
    }
}
