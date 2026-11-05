package com.chemmanager.controller.inventory;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.chemmanager.app.AppContext;
import com.chemmanager.component.DataTable;
import com.chemmanager.component.Dialogs;
import com.chemmanager.component.Icons;
import com.chemmanager.component.StatusBadge;
import com.chemmanager.component.Toasts;
import com.chemmanager.dto.MaterialQuery;
import com.chemmanager.dto.PagedResult;
import com.chemmanager.localization.I18n;
import com.chemmanager.model.RawMaterial;
import com.chemmanager.service.ServiceFactory;
import com.chemmanager.util.ExcelExporter;
import com.chemmanager.util.Formatters;
import com.chemmanager.util.FxTasks;
import com.chemmanager.util.Labels;
import com.chemmanager.util.Money;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/**
 * Raw material catalogue with batch, expiry, hazard and safety-document indicators.
 * Hazard classification and SDS availability are surfaced directly in the table because they
 * drive storage decisions in a chemical plant.
 */
public final class RawMaterialsController {

    @FXML private VBox tableHost;
    @FXML private Label summaryLabel;
    @FXML private ComboBox<String> categoryFilter;
    @FXML private CheckBox hazardousOnly;
    @FXML private CheckBox lowStockOnly;
    @FXML private Button refreshButton;
    @FXML private Button exportButton;

    private DataTable<RawMaterial> table;
    private PagedResult<RawMaterial> lastPage;

    @FXML
    private void initialize() {
        hazardousOnly.setText(I18n.get().t("materials.hazardous_only"));
        lowStockOnly.setText(I18n.get().t("materials.low_stock_only"));

        refreshButton.setGraphic(Icons.glyph(Icons.Icon.REFRESH, 12));
        refreshButton.setOnAction(event -> table.resetToFirstPage());
        exportButton.setGraphic(Icons.glyph(Icons.Icon.EXCEL, 12));
        exportButton.setOnAction(event -> export());

        categoryFilter.setConverter(new StringConverter<>() {
            @Override
            public String toString(String value) {
                return value == null ? "" : Labels.materialCategory(value);
            }

            @Override
            public String fromString(String text) {
                return null;
            }
        });

        table = new DataTable<>(this::loadPage, I18n.get().t("materials.search_prompt"))
                .columns(
                        DataTable.Column.key(I18n.get().t("material.code"), 90, RawMaterial::code),
                        DataTable.Column.text(I18n.get().t("material.commercial_name"), 185,
                                RawMaterial::commercialName),
                        DataTable.Column.text(I18n.get().t("material.chemical_name"), 160,
                                material -> material.chemicalName() == null ? "—" : material.chemicalName()),
                        DataTable.Column.node(I18n.get().t("common.category"), 110,
                                material -> StatusBadge.neutral(Labels.materialCategory(material.category()))),
                        DataTable.Column.text(I18n.get().t("material.supplier"), 130, RawMaterial::supplierName),
                        DataTable.Column.node(I18n.get().t("material.stock"), 115,
                                material -> material.isLowStock()
                                        ? StatusBadge.warning(Formatters.quantity(material.stock(), material.storageUnit()))
                                        : StatusBadge.success(Formatters.quantity(material.stock(), material.storageUnit()))),
                        DataTable.Column.node(I18n.get().t("material.hazard"), 115, StatusBadge::hazard),
                        DataTable.Column.node(I18n.get().t("material.sds"), 70,
                                material -> material.sdsAvailable()
                                        ? StatusBadge.success(I18n.get().t("common.available"))
                                        : StatusBadge.danger(I18n.get().t("common.missing"))),
                        DataTable.Column.node(I18n.get().t("material.expiry_date"), 120,
                                material -> expiryBadge(material.expirationDate())))
                .onActivate(this::showDetail)
                .onLoaded(page -> {
                    lastPage = page;
                    BigDecimal value = page.items().stream().map(RawMaterial::stockValue)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    summaryLabel.setText(I18n.get().t("materials.summary", page.total(), Money.format(value)));
                });
        VBox.setVgrow(table, javafx.scene.layout.Priority.ALWAYS);
        tableHost.getChildren().setAll(table);

        FxTasks.run(() -> ServiceFactory.rawMaterials().categories(), categories -> {
            categoryFilter.getItems().clear();
            categoryFilter.getItems().add(null);
            categoryFilter.getItems().addAll(categories);
            categoryFilter.setValue(null);
        }, error -> Toasts.error(I18n.get().t("error.loading")));

        categoryFilter.setOnAction(event -> table.resetToFirstPage());
        hazardousOnly.setOnAction(event -> table.resetToFirstPage());
        lowStockOnly.setOnAction(event -> table.resetToFirstPage());
        table.resetToFirstPage();
    }

    private javafx.scene.Node expiryBadge(LocalDate expiry) {
        if (expiry == null) {
            return StatusBadge.neutral("—");
        }
        LocalDate today = LocalDate.now();
        if (expiry.isBefore(today)) {
            return StatusBadge.danger(I18n.get().t("material.expired", Formatters.shortDate(expiry)));
        }
        long days = java.time.temporal.ChronoUnit.DAYS.between(today, expiry);
        if (days <= 45) {
            return StatusBadge.warning(I18n.get().t("material.expires_in", days));
        }
        return StatusBadge.success(Formatters.shortDate(expiry));
    }

    private PagedResult<RawMaterial> loadPage(String search, int page, int pageSize) {
        MaterialQuery query = new MaterialQuery(search, categoryFilter.getValue(),
                hazardousOnly.isSelected(), lowStockOnly.isSelected(), page, pageSize);
        return ServiceFactory.rawMaterials().search(query);
    }

    private void showDetail(RawMaterial material) {
        String message = String.join("\n",
                I18n.get().t("material.detail.header", material.code(), material.commercialName()),
                I18n.get().t("material.detail.names", material.chemicalName(), Labels.materialCategory(material.category())),
                I18n.get().t("material.detail.supplier", material.supplierName(), material.batchNumber()),
                I18n.get().t("material.detail.stock", Formatters.quantity(material.stock(), material.storageUnit()),
                        Formatters.quantity(material.minStock(), material.storageUnit()),
                        material.storageLocation()),
                I18n.get().t("material.detail.hazard", Labels.hazardClass(material.hazardClass()),
                        material.hazardPictograms()),
                I18n.get().t("material.detail.storage", material.storageConditions()),
                I18n.get().t("material.detail.dates",
                        Formatters.shortDate(material.productionDate()),
                        Formatters.shortDate(material.expirationDate())),
                I18n.get().t("material.detail.sds", material.sdsAvailable()
                        ? I18n.get().t("common.available") + " — " + material.sdsFileName()
                        : I18n.get().t("common.missing")));
        Dialogs.info(AppContext.get().stage(), I18n.get().t("material.detail.title"), message);
    }

    private void export() {
        if (lastPage == null || lastPage.items().isEmpty()) {
            Toasts.warning(I18n.get().t("dashboard.export_empty"));
            return;
        }
        Optional<Path> target = Dialogs.chooseSaveFile(AppContext.get().stage(),
                I18n.get().t("materials.export"), "raw-materials.xlsx",
                I18n.get().t("dashboard.export_filter"), "xlsx");
        if (target.isEmpty()) {
            return;
        }
        List<String> headers = List.of(I18n.get().t("material.code"), I18n.get().t("material.commercial_name"),
                I18n.get().t("material.chemical_name"), I18n.get().t("material.supplier"),
                I18n.get().t("material.stock"), I18n.get().t("material.min_stock"),
                I18n.get().t("material.hazard"), I18n.get().t("material.expiry_date"),
                I18n.get().t("material.batch"), I18n.get().t("material.storage_location"));
        List<List<Object>> rows = lastPage.items().stream()
                .map(material -> List.<Object>of(material.code(), material.commercialName(),
                        material.chemicalName() == null ? "" : material.chemicalName(),
                        material.supplierName(), material.stock(), material.minStock(),
                        Labels.hazardClass(material.hazardClass()),
                        material.expirationDate() == null ? "" : material.expirationDate().toString(),
                        material.batchNumber() == null ? "" : material.batchNumber(),
                        material.storageLocation() == null ? "" : material.storageLocation()))
                .toList();
        Path file = target.get();
        FxTasks.runVoid(() -> ExcelExporter.exportRows(file, I18n.get().t("nav.raw_materials"), headers, rows),
                () -> Toasts.success(I18n.get().t("dashboard.export_done", file.getFileName().toString())),
                error -> Toasts.error(I18n.get().t("dashboard.export_failed", String.valueOf(error.getMessage()))));
    }
}
