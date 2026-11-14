package com.chemmanager.controller.safety;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.chemmanager.component.Card;
import com.chemmanager.component.DataTable;
import com.chemmanager.component.Dialogs;
import com.chemmanager.component.FormFields;
import com.chemmanager.component.Icons;
import com.chemmanager.component.KpiCard;
import com.chemmanager.component.StatusBadge;
import com.chemmanager.component.Toasts;
import com.chemmanager.app.AppContext;
import com.chemmanager.dto.PagedResult;
import com.chemmanager.localization.I18n;
import com.chemmanager.model.safety.HazardPictogram;
import com.chemmanager.model.safety.InstructionUpdate;
import com.chemmanager.model.safety.MaterialFile;
import com.chemmanager.model.safety.MaterialHazard;
import com.chemmanager.model.safety.NewIncident;
import com.chemmanager.model.safety.NewSheet;
import com.chemmanager.model.safety.SafetyIncident;
import com.chemmanager.model.safety.SafetyInstructions;
import com.chemmanager.model.safety.SafetyOverview;
import com.chemmanager.model.safety.SafetySheet;
import com.chemmanager.security.Permission;
import com.chemmanager.security.SessionContext;
import com.chemmanager.service.ServiceFactory;
import com.chemmanager.util.ExcelExporter;
import com.chemmanager.util.Formatters;
import com.chemmanager.util.FxTasks;
import com.chemmanager.util.Labels;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/**
 * Chemical safety: the register of hazardous substances, their safety data sheets, the handling
 * instructions and the incident book.
 *
 * <p>The screen reads like the HSE inspector's checklist: a substance without a sheet is red, a sheet
 * older than three years is amber, and an incident stays open until it has been investigated and
 * closed. The rules are enforced by the server; the screen only offers the gestures the signed-in
 * user may perform.</p>
 */
public final class SafetyController {

    @FXML private HBox kpiRow;
    @FXML private Label summaryLabel;
    @FXML private ComboBox<String> riskFilter;
    @FXML private Button exportButton;
    @FXML private Button refreshButton;
    @FXML private VBox materialsTableHost;
    @FXML private VBox fileHost;
    @FXML private Label incidentSummaryLabel;
    @FXML private ComboBox<String> statusFilter;
    @FXML private ComboBox<String> severityFilter;
    @FXML private Button reportIncidentButton;
    @FXML private Button investigateButton;
    @FXML private Button closeIncidentButton;
    @FXML private VBox incidentsTableHost;

    private DataTable<MaterialHazard> materialsTable;
    private DataTable<SafetyIncident> incidentsTable;
    private PagedResult<MaterialHazard> lastMaterials = PagedResult.empty(25);
    private List<HazardPictogram> pictograms = List.of();
    private String risk = "";
    private String selectedCode = "";
    private SafetyIncident selectedIncident;

    @FXML
    private void initialize() {
        refreshButton.setGraphic(Icons.glyph(Icons.Icon.REFRESH, 12));
        refreshButton.setOnAction(event -> reload());
        exportButton.setGraphic(Icons.glyph(Icons.Icon.EXCEL, 12));
        exportButton.setOnAction(event -> export());

        riskFilter.setConverter(new StringConverter<>() {
            @Override
            public String toString(String value) {
                return Labels.riskFilter(value);
            }

            @Override
            public String fromString(String text) {
                return null;
            }
        });
        riskFilter.setItems(FXCollections.observableArrayList("", "hazardous", "missing", "obsolete"));
        riskFilter.setValue("");
        riskFilter.setOnAction(event -> {
            risk = riskFilter.getValue() == null ? "" : riskFilter.getValue();
            if (materialsTable != null) {
                materialsTable.resetToFirstPage();
            }
        });

        statusFilter.setItems(FXCollections.observableArrayList("", "OPEN", "INVESTIGATING", "CLOSED"));
        statusFilter.setConverter(new StringConverter<>() {
            @Override
            public String toString(String value) {
                return value == null || value.isBlank() ? I18n.get().t("common.all")
                        : Labels.incidentStatus(value);
            }

            @Override
            public String fromString(String text) {
                return null;
            }
        });
        statusFilter.getSelectionModel().selectFirst();
        statusFilter.setOnAction(event -> reloadIncidents());

        severityFilter.setItems(FXCollections.observableArrayList("", "MINOR", "MODERATE", "MAJOR", "CRITICAL"));
        severityFilter.setConverter(new StringConverter<>() {
            @Override
            public String toString(String value) {
                return value == null || value.isBlank() ? I18n.get().t("common.all")
                        : Labels.incidentSeverity(value);
            }

            @Override
            public String fromString(String text) {
                return null;
            }
        });
        severityFilter.getSelectionModel().selectFirst();
        severityFilter.setOnAction(event -> reloadIncidents());

        buildMaterialsTable();
        buildIncidentsTable();

        boolean mayEdit = SessionContext.get().can(Permission.SAFETY_EDIT);
        reportIncidentButton.setDisable(!mayEdit);
        reportIncidentButton.setGraphic(Icons.glyph(Icons.Icon.WARNING, 12));
        reportIncidentButton.setOnAction(event -> openIncidentDialog());
        investigateButton.setDisable(true);
        investigateButton.setOnAction(event -> moveIncident("INVESTIGATING"));
        closeIncidentButton.setDisable(true);
        closeIncidentButton.setOnAction(event -> moveIncident("CLOSED"));

        FxTasks.run(() -> ServiceFactory.safety().pictograms(), loaded -> pictograms = loaded,
                error -> pictograms = List.of());

        reload();
    }

    // ------------------------------------------------------------------ tables

    private void buildMaterialsTable() {
        materialsTable = new DataTable<>(this::loadMaterials, I18n.get().t("safety.search_prompt"))
                .columns(
                        DataTable.Column.key(I18n.get().t("material.code"), 82, MaterialHazard::code),
                        DataTable.Column.text(I18n.get().t("material.commercial_name"), 230,
                                MaterialHazard::commercialName),
                        DataTable.Column.node(I18n.get().t("safety.column.hazard"), 140,
                                row -> row.isHazardous()
                                        ? StatusBadge.warning(Labels.hazardClass(row.hazardClass()))
                                        : StatusBadge.neutral(I18n.get().t("safety.hazard.none"))),
                        DataTable.Column.node(I18n.get().t("safety.column.sheet"), 132,
                                row -> StatusBadge.of(Labels.sheetState(row.hasSheet(), row.sheetObsolete()),
                                        Labels.sheetStateStyle(row.hasSheet(), row.sheetObsolete()))),
                        DataTable.Column.text(I18n.get().t("safety.column.revision"), 78,
                                row -> row.sheetRevision() == null ? "—" : row.sheetRevision()),
                        DataTable.Column.date(I18n.get().t("safety.column.issue_date"), 105,
                                MaterialHazard::sheetIssueDate))
                .onActivate(this::openFile)
                .onLoaded(page -> {
                    lastMaterials = page;
                    summaryLabel.setText(I18n.get().t("safety.summary", page.total(),
                            countInPage(page, MaterialHazard::isUndocumented)));
                });
        materialsTableHost.getChildren().setAll(materialsTable);
    }

    private void buildIncidentsTable() {
        incidentsTable = new DataTable<>(this::loadIncidents, I18n.get().t("safety.incident.search_prompt"))
                .columns(
                        DataTable.Column.key(I18n.get().t("safety.column.incident"), 110,
                                SafetyIncident::incidentNumber),
                        DataTable.Column.date(I18n.get().t("material.expiry_date"), 115,
                                row -> row.incidentDate() == null ? null : row.incidentDate().toLocalDate()),
                        DataTable.Column.text(I18n.get().t("safety.column.material"), 220,
                                row -> row.materialName() == null || row.materialName().isBlank()
                                        ? I18n.get().t("safety.incident.general") : row.materialName()),
                        DataTable.Column.text(I18n.get().t("common.type"), 130,
                                row -> Labels.incidentType(row.incidentType())),
                        DataTable.Column.node(I18n.get().t("safety.column.severity"), 120,
                                row -> StatusBadge.of(Labels.incidentSeverity(row.severity()),
                                        Labels.incidentSeverityStyle(row.severity()))),
                        DataTable.Column.node(I18n.get().t("common.status"), 130,
                                row -> StatusBadge.of(Labels.incidentStatus(row.status()),
                                        Labels.incidentStatusStyle(row.status()))),
                        DataTable.Column.text(I18n.get().t("safety.column.description"), 300,
                                SafetyIncident::description))
                .onActivate(this::selectIncident);
        incidentsTable.tableView().getSelectionModel().selectedItemProperty()
                .addListener((observable, previous, selected) -> selectIncident(selected));
        incidentsTableHost.getChildren().setAll(incidentsTable);
    }

    private static <T> long countInPage(PagedResult<T> page, java.util.function.Predicate<T> predicate) {
        return page.items().stream().filter(predicate).count();
    }

    private PagedResult<MaterialHazard> loadMaterials(String search, int page, int pageSize) {
        return ServiceFactory.safety().materials(page + 1, pageSize, search, risk);
    }

    private PagedResult<SafetyIncident> loadIncidents(String search, int page, int pageSize) {
        return ServiceFactory.safety().incidents(page + 1, pageSize, statusFilter.getValue(),
                severityFilter.getValue(), search);
    }

    // ------------------------------------------------------------------ loading

    private void reload() {
        reloadOverview();
        reloadMaterials();
        reloadIncidents();
    }

    private void reloadOverview() {
        FxTasks.run(() -> ServiceFactory.safety().overview(), this::renderOverview,
                error -> Toasts.error(error.getMessage()));
    }

    private void reloadMaterials() {
        if (materialsTable != null) {
            materialsTable.reload();
        }
    }

    private void reloadIncidents() {
        if (incidentsTable != null) {
            incidentsTable.reload();
        }
        FxTasks.run(() -> ServiceFactory.safety().incidentSummary(statusFilter.getValue(),
                        severityFilter.getValue(), ""),
                summary -> incidentSummaryLabel.setText(I18n.get().t("safety.incident.summary",
                        summary.total(), summary.open(), summary.severe())),
                error -> incidentSummaryLabel.setText(I18n.get().t("common.missing")));
    }

    private void renderOverview(SafetyOverview value) {
        kpiRow.getChildren().setAll(
                new KpiCard(Icons.Icon.MATERIALS, "accent-info")
                        .title(I18n.get().t("safety.kpi.materials")).value(String.valueOf(value.materials()))
                        .hint(I18n.get().t("safety.kpi.materials_hint")),
                new KpiCard(Icons.Icon.WARNING, "accent-warning")
                        .title(I18n.get().t("safety.kpi.hazardous"))
                        .value(String.valueOf(value.hazardousMaterials()))
                        .hint(I18n.get().t("safety.kpi.hazardous_hint")),
                new KpiCard(Icons.Icon.SHIELD_CHECK, "accent-success")
                        .title(I18n.get().t("safety.kpi.documented"))
                        .value(value.documentationPct() + " %")
                        .hint(I18n.get().t("safety.kpi.documented_hint", value.materialsWithSheet())),
                new KpiCard(Icons.Icon.DOCUMENT, value.obsoleteSheets() > 0 ? "accent-danger" : "accent-neutral")
                        .title(I18n.get().t("safety.kpi.obsolete"))
                        .value(String.valueOf(value.obsoleteSheets()))
                        .hint(I18n.get().t("safety.kpi.obsolete_hint", value.sheetValidityYears())),
                new KpiCard(Icons.Icon.FIRE, value.openIncidents() > 0 ? "accent-warning" : "accent-neutral")
                        .title(I18n.get().t("safety.kpi.incidents"))
                        .value(String.valueOf(value.openIncidents()))
                        .hint(I18n.get().t("safety.kpi.incidents_hint", value.incidents())));
    }

    // ------------------------------------------------------------------ the file of a substance

    private void openFile(MaterialHazard material) {
        selectedCode = material == null ? "" : material.code();
        if (material == null) {
            return;
        }
        FxTasks.run(() -> ServiceFactory.safety().material(material.id()), this::renderFile,
                error -> Toasts.error(error.getMessage()));
    }

    private void renderFile(MaterialFile file) {
        if (file == null || file.material() == null) {
            fileHost.getChildren().setAll();
            return;
        }
        MaterialHazard material = file.material();
        Card identity = new Card(material.commercialName(), material.code());
        Label hazard = new Label(material.isHazardous()
                ? Labels.hazardClass(material.hazardClass()) : I18n.get().t("safety.hazard.none"));
        hazard.getStyleClass().add("text-muted");
        Label chemical = new Label(material.chemicalName() == null || material.chemicalName().isBlank()
                ? "—" : material.chemicalName());
        chemical.getStyleClass().add("text-muted");
        Label storage = new Label(I18n.get().t("safety.file.storage", material.storageConditions(),
                material.storageLocation()));
        storage.getStyleClass().add("text-muted");
        FlowPane badges = new FlowPane(6, 6);
        List<String> codes = material.pictogramCodes();
        for (String code : codes) {
            badges.getChildren().add(StatusBadge.of(code, "badge-warning"));
        }
        if (badges.getChildren().isEmpty()) {
            badges.getChildren().add(StatusBadge.neutral(I18n.get().t("safety.file.no_pictogram")));
        }
        identity.body(hazard, chemical, badges, storage);

        Card sheets = new Card(I18n.get().t("safety.file.sheets"),
                I18n.get().t("safety.file.sheets_hint", file.sheets().size()));
        VBox sheetRows = new VBox(6);
        for (SafetySheet sheet : file.sheets()) {
            HBox row = new HBox(8);
            row.setAlignment(Pos.CENTER_LEFT);
            Label revision = new Label(sheet.revision() == null || sheet.revision().isBlank()
                    ? Labels.sheetDocumentType(sheet.documentType()) : sheet.revision());
            revision.setMinWidth(80);
            Label issued = new Label(sheet.issueDate() == null ? "—" : Formatters.shortDate(sheet.issueDate()));
            issued.setMinWidth(90);
            issued.getStyleClass().add("text-muted");
            row.getChildren().addAll(revision, issued,
                    sheet.isCurrent()
                            ? StatusBadge.of(Labels.sheetState(true, sheet.isObsolete()),
                                    Labels.sheetStateStyle(true, sheet.isObsolete()))
                            : StatusBadge.neutral(I18n.get().t("safety.file.archive")));
            sheetRows.getChildren().add(row);
        }
        if (sheetRows.getChildren().isEmpty()) {
            Label empty = new Label(I18n.get().t("safety.file.no_sheet"));
            empty.getStyleClass().add("text-muted");
            sheetRows.getChildren().add(empty);
        }
        if (SessionContext.get().can(Permission.SAFETY_EDIT)) {
            Button register = new Button(I18n.get().t("safety.action.register_sheet"));
            register.getStyleClass().addAll("btn", "btn-primary", "btn-sm");
            register.setGraphic(Icons.glyph(Icons.Icon.PLUS, 12));
            register.setOnAction(event -> openSheetDialog(file));
            sheets.action(register);
        }
        sheets.body(sheetRows);

        Card instructions = new Card(I18n.get().t("safety.file.instructions"), null);
        VBox rules = new VBox(4);
        SafetyInstructions current = file.instructions();
        if (current == null || current.isEmpty()) {
            Label empty = new Label(I18n.get().t("safety.file.no_instructions"));
            empty.getStyleClass().add("text-muted");
            rules.getChildren().add(empty);
        } else {
            rules.getChildren().addAll(
                    rule(I18n.get().t("safety.field.ppe"), current.ppe()),
                    rule(I18n.get().t("safety.field.storage"), current.storage()),
                    rule(I18n.get().t("safety.field.handling"), current.handling()),
                    rule(I18n.get().t("safety.field.first_aid"), current.firstAid()),
                    rule(I18n.get().t("safety.field.fire"), current.fireMeasures()),
                    rule(I18n.get().t("safety.field.spillage"), current.spillage()));
        }
        if (SessionContext.get().can(Permission.SAFETY_EDIT)) {
            Button edit = new Button(I18n.get().t("safety.action.edit_instructions"));
            edit.getStyleClass().addAll("btn", "btn-secondary", "btn-sm");
            edit.setGraphic(Icons.glyph(Icons.Icon.EDIT, 12));
            edit.setOnAction(event -> openInstructionsDialog(file));
            Button pictogramsButton = new Button(I18n.get().t("safety.action.pictograms"));
            pictogramsButton.getStyleClass().addAll("btn", "btn-secondary", "btn-sm");
            pictogramsButton.setGraphic(Icons.glyph(Icons.Icon.WARNING, 12));
            pictogramsButton.setOnAction(event -> openPictogramsDialog(file));
            instructions.action(new HBox(6, edit, pictogramsButton));
        }
        instructions.body(rules);

        Card history = new Card(I18n.get().t("safety.file.history"),
                I18n.get().t("safety.file.history_hint", file.incidents().size()));
        VBox incidents = new VBox(4);
        for (SafetyIncident incident : file.incidents()) {
            HBox row = new HBox(8);
            row.setAlignment(Pos.CENTER_LEFT);
            Label number = new Label(incident.incidentNumber());
            number.setMinWidth(90);
            Label date = new Label(incident.incidentDate() == null ? "—"
                    : Formatters.shortDate(incident.incidentDate().toLocalDate()));
            date.getStyleClass().add("text-muted");
            date.setMinWidth(80);
            row.getChildren().addAll(number, date,
                    StatusBadge.of(Labels.incidentStatus(incident.status()),
                            Labels.incidentStatusStyle(incident.status())));
            incidents.getChildren().add(row);
        }
        if (incidents.getChildren().isEmpty()) {
            Label empty = new Label(I18n.get().t("safety.file.no_incident"));
            empty.getStyleClass().add("text-muted");
            incidents.getChildren().add(empty);
        }
        history.body(incidents);

        fileHost.getChildren().setAll(identity, sheets, instructions, history);
    }

    private HBox rule(String label, String value) {
        Label name = new Label(label);
        name.setMinWidth(120);
        name.getStyleClass().add("text-muted");
        Label text = new Label(value == null || value.isBlank() ? "—" : value);
        text.setWrapText(true);
        HBox row = new HBox(8, name, text);
        row.setAlignment(Pos.CENTER_LEFT);

        return row;
    }

    // ------------------------------------------------------------------ write gestures

    private void openSheetDialog(MaterialFile file) {
        if (file == null) {
            return;
        }
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(I18n.get().t("safety.sheet.title"));
        dialog.initOwner(kpiRow.getScene().getWindow());
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        ComboBox<String> type = new ComboBox<>(FXCollections.observableArrayList(
                "SDS", "TECHNICAL", "DISPOSAL", "TRANSPORT"));
        type.setConverter(new StringConverter<>() {
            @Override
            public String toString(String value) {
                return Labels.sheetDocumentType(value);
            }

            @Override
            public String fromString(String text) {
                return null;
            }
        });
        type.getSelectionModel().selectFirst();
        TextField path = new TextField();
        TextField revision = new TextField();
        TextField source = new TextField();
        DatePicker issueDate = new DatePicker(LocalDate.now());

        VBox box = new VBox(10,
                FormFields.fieldKey("safety.field.document_type", type, null),
                FormFields.fieldKey("safety.field.file", path, "safety.field.file_hint"),
                FormFields.fieldKey("safety.field.revision", revision, null),
                FormFields.fieldKey("safety.field.issue_date", issueDate, null),
                FormFields.fieldKey("safety.field.source", source, null));
        box.setPrefWidth(440);
        dialog.getDialogPane().setContent(box);

        dialog.showAndWait().ifPresent(result -> {
            if (result != ButtonType.OK) {
                return;
            }
            if (path.getText() == null || path.getText().isBlank()) {
                Toasts.warning(I18n.get().t("safety.sheet.file_required"));
                return;
            }
            NewSheet sheet = new NewSheet(file.material().id(), type.getValue(), path.getText().trim(),
                    issueDate.getValue(), revision.getText(), source.getText());
            FxTasks.run(() -> ServiceFactory.safety().registerSheet(sheet), saved -> {
                Toasts.success(I18n.get().t("safety.toast.sheet", saved.material().code()));
                renderFile(saved);
                reload();
            }, error -> Toasts.error(error.getMessage()));
        });
    }

    private void openInstructionsDialog(MaterialFile file) {
        if (file == null) {
            return;
        }
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(I18n.get().t("safety.instructions.title"));
        dialog.initOwner(kpiRow.getScene().getWindow());
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        SafetyInstructions current = file.instructions();
        TextArea ppe = area(current == null ? "" : current.ppe());
        TextArea storage = area(current == null ? "" : current.storage());
        TextArea handling = area(current == null ? "" : current.handling());
        TextArea firstAid = area(current == null ? "" : current.firstAid());
        TextArea fire = area(current == null ? "" : current.fireMeasures());
        TextArea spillage = area(current == null ? "" : current.spillage());

        VBox box = new VBox(10,
                FormFields.fieldKey("safety.field.ppe", ppe, null),
                FormFields.fieldKey("safety.field.storage", storage, null),
                FormFields.fieldKey("safety.field.handling", handling, null),
                FormFields.fieldKey("safety.field.first_aid", firstAid, null),
                FormFields.fieldKey("safety.field.fire", fire, null),
                FormFields.fieldKey("safety.field.spillage", spillage, null));
        box.setPrefWidth(480);
        dialog.getDialogPane().setContent(box);

        dialog.showAndWait().ifPresent(result -> {
            if (result != ButtonType.OK) {
                return;
            }
            InstructionUpdate update = new InstructionUpdate(ppe.getText(), storage.getText(), handling.getText(),
                    firstAid.getText(), fire.getText(), spillage.getText());
            FxTasks.run(() -> ServiceFactory.safety().saveInstructions(file.material().id(), update), saved -> {
                Toasts.success(I18n.get().t("safety.toast.instructions", saved.material().code()));
                renderFile(saved);
                reload();
            }, error -> Toasts.error(error.getMessage()));
        });
    }

    private TextArea area(String value) {
        TextArea area = new TextArea(value == null ? "" : value);
        area.setPrefRowCount(2);
        area.setWrapText(true);

        return area;
    }

    private void openPictogramsDialog(MaterialFile file) {
        if (file == null) {
            return;
        }
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(I18n.get().t("safety.pictograms.title"));
        dialog.initOwner(kpiRow.getScene().getWindow());
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Map<String, javafx.scene.control.CheckBox> boxes = new LinkedHashMap<>();
        List<String> chosen = file.material().pictogramCodes();
        VBox box = new VBox(6);
        for (HazardPictogram pictogram : pictograms) {
            javafx.scene.control.CheckBox check = new javafx.scene.control.CheckBox(pictogram.code() + " — "
                    + pictogram.nameFr());
            check.setSelected(chosen.contains(pictogram.code()));
            boxes.put(pictogram.code(), check);
            box.getChildren().add(check);
        }
        if (boxes.isEmpty()) {
            box.getChildren().add(new Label(I18n.get().t("safety.pictograms.empty")));
        }
        dialog.getDialogPane().setContent(box);

        dialog.showAndWait().ifPresent(result -> {
            if (result != ButtonType.OK) {
                return;
            }
            List<String> codes = boxes.entrySet().stream().filter(entry -> entry.getValue().isSelected())
                    .map(Map.Entry::getKey).toList();
            FxTasks.run(() -> ServiceFactory.safety().setPictograms(file.material().id(), codes), saved -> {
                Toasts.success(I18n.get().t("safety.toast.pictograms", saved.material().code()));
                renderFile(saved);
                reload();
            }, error -> Toasts.error(error.getMessage()));
        });
    }

    private void openIncidentDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(I18n.get().t("safety.incident.title"));
        dialog.initOwner(kpiRow.getScene().getWindow());
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        ComboBox<MaterialHazard> material = new ComboBox<>(FXCollections.observableArrayList(
                lastMaterials.items()));
        material.setPromptText(I18n.get().t("safety.incident.general"));
        material.setConverter(new StringConverter<>() {
            @Override
            public String toString(MaterialHazard row) {
                return row == null ? I18n.get().t("safety.incident.general")
                        : row.code() + " — " + row.commercialName();
            }

            @Override
            public MaterialHazard fromString(String text) {
                return null;
            }
        });
        ComboBox<String> type = new ComboBox<>(FXCollections.observableArrayList(
                "SPILL", "EXPOSURE", "FIRE", "DAMAGE", "OTHER"));
        type.setConverter(new StringConverter<>() {
            @Override
            public String toString(String value) {
                return Labels.incidentType(value);
            }

            @Override
            public String fromString(String text) {
                return null;
            }
        });
        type.getSelectionModel().selectFirst();
        ComboBox<String> severity = new ComboBox<>(FXCollections.observableArrayList(
                "MINOR", "MODERATE", "MAJOR", "CRITICAL"));
        severity.setConverter(new StringConverter<>() {
            @Override
            public String toString(String value) {
                return Labels.incidentSeverity(value);
            }

            @Override
            public String fromString(String text) {
                return null;
            }
        });
        severity.getSelectionModel().selectFirst();
        TextArea description = area("");
        TextArea actions = area("");

        VBox box = new VBox(10,
                FormFields.fieldKey("safety.incident.material", material, null),
                FormFields.fieldKey("safety.incident.type_label", type, null),
                FormFields.fieldKey("safety.incident.severity_label", severity, null),
                FormFields.fieldKey("safety.incident.description", description, null),
                FormFields.fieldKey("safety.incident.actions", actions, null));
        box.setPrefWidth(460);
        dialog.getDialogPane().setContent(box);

        dialog.showAndWait().ifPresent(result -> {
            if (result != ButtonType.OK) {
                return;
            }
            if (description.getText() == null || description.getText().isBlank()) {
                Toasts.warning(I18n.get().t("safety.incident.description_required"));
                return;
            }
            NewIncident incident = new NewIncident(material.getValue() == null ? null
                    : material.getValue().id(), type.getValue(), severity.getValue(), description.getText(),
                    actions.getText());
            FxTasks.run(() -> ServiceFactory.safety().reportIncident(incident), reported -> {
                Toasts.success(I18n.get().t("safety.toast.incident", reported.incidentNumber()));
                reload();
            }, error -> Toasts.error(error.getMessage()));
        });
    }

    private void selectIncident(SafetyIncident incident) {
        selectedIncident = incident;
        boolean mayEdit = SessionContext.get().can(Permission.SAFETY_EDIT);
        investigateButton.setDisable(!mayEdit || incident == null || !"OPEN".equals(incident.status()));
        closeIncidentButton.setDisable(!mayEdit || incident == null
                || !"INVESTIGATING".equals(incident.status()));
    }

    private void moveIncident(String status) {
        if (selectedIncident == null) {
            return;
        }
        long id = selectedIncident.id();
        FxTasks.run(() -> ServiceFactory.safety().changeIncidentStatus(id, status), updated -> {
            Toasts.success(I18n.get().t("safety.toast.incident_status", updated.incidentNumber(),
                    Labels.incidentStatus(updated.status())));
            selectedIncident = null;
            investigateButton.setDisable(true);
            closeIncidentButton.setDisable(true);
            reload();
        }, error -> Toasts.error(error.getMessage()));
    }

    private void export() {
        if (lastMaterials.items().isEmpty()) {
            Toasts.warning(I18n.get().t("dashboard.export_empty"));
            return;
        }
        Optional<Path> target = Dialogs.chooseSaveFile(AppContext.get().stage(),
                I18n.get().t("safety.export"), "safety-register.xlsx",
                I18n.get().t("dashboard.export_filter"), "xlsx");
        if (target.isEmpty()) {
            return;
        }
        List<String> headers = List.of(I18n.get().t("material.code"),
                I18n.get().t("material.commercial_name"), I18n.get().t("material.chemical_name"),
                I18n.get().t("safety.column.hazard"), I18n.get().t("safety.column.pictograms"),
                I18n.get().t("safety.column.sheet"), I18n.get().t("safety.column.revision"),
                I18n.get().t("safety.column.issue_date"), I18n.get().t("safety.column.storage"));
        List<List<Object>> rows = new ArrayList<>();
        for (MaterialHazard material : lastMaterials.items()) {
            rows.add(List.of(material.code(), material.commercialName(),
                    material.chemicalName() == null ? "" : material.chemicalName(),
                    material.isHazardous() ? Labels.hazardClass(material.hazardClass()) : "",
                    String.join(", ", material.pictogramCodes()),
                    Labels.sheetState(material.hasSheet(), material.sheetObsolete()),
                    material.sheetRevision() == null ? "" : material.sheetRevision(),
                    material.sheetIssueDate() == null ? "" : material.sheetIssueDate().toString(),
                    material.storageLocation() == null ? "" : material.storageLocation()));
        }
        Path file = target.get();
        FxTasks.runVoid(() -> ExcelExporter.exportRows(file, I18n.get().t("nav.safety"), headers, rows),
                () -> Toasts.success(I18n.get().t("dashboard.export_done", file.getFileName().toString())),
                error -> Toasts.error(I18n.get().t("dashboard.export_failed", String.valueOf(error.getMessage()))));
    }
}
