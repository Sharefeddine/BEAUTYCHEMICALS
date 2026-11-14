package com.chemmanager.controller.inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.chemmanager.component.Card;
import com.chemmanager.component.DataTable;
import com.chemmanager.component.Icons;
import com.chemmanager.component.KpiCard;
import com.chemmanager.component.StatusBadge;
import com.chemmanager.component.Toasts;
import com.chemmanager.dto.MovementQuery;
import com.chemmanager.dto.PagedResult;
import com.chemmanager.dto.StockQuery;
import com.chemmanager.localization.I18n;
import com.chemmanager.model.inventory.InventorySummary;
import com.chemmanager.model.inventory.StockBatch;
import com.chemmanager.model.inventory.StockMovement;
import com.chemmanager.model.inventory.Warehouse;
import com.chemmanager.security.Permission;
import com.chemmanager.security.SessionContext;
import com.chemmanager.service.ServiceFactory;
import com.chemmanager.util.Formatters;
import com.chemmanager.util.FxTasks;
import com.chemmanager.util.Labels;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/**
 * Inventory module: the stock seen batch by batch, the append-only movement ledger, and the two
 * operations a warehouse may perform on its own authority — a physical count and a transfer.
 *
 * <p>The screen shows stock, never a product-level total: a batch carries its warehouse, its
 * expiry and its quality status, and the server refuses anything that would break the quarantine
 * walls. Every change produces a ledger line with its reason and its author, which is what makes
 * an inventory auditable in a chemical plant.</p>
 */
public final class InventoryController {

    @FXML private VBox stockHost;
    @FXML private VBox detailHost;
    @FXML private VBox movementHost;
    @FXML private HBox kpiRow;
    @FXML private Label summaryLabel;
    @FXML private ComboBox<Warehouse> warehouseFilter;
    @FXML private ComboBox<String> qualityFilter;
    @FXML private ComboBox<String> movementTypeFilter;
    @FXML private Button refreshButton;
    @FXML private Button adjustButton;
    @FXML private Button transferButton;

    private DataTable<StockBatch> stockTable;
    private DataTable<StockMovement> movementTable;
    private StockQuery stockQuery = StockQuery.initial(25);
    private MovementQuery movementQuery = MovementQuery.initial(25);
    private List<Warehouse> warehouses = List.of();
    private StockBatch selected;

    @FXML
    private void initialize() {
        adjustButton.setGraphic(Icons.glyph(Icons.Icon.EDIT, 12));
        adjustButton.setOnAction(event -> adjust());
        transferButton.setGraphic(Icons.glyph(Icons.Icon.DISTRIBUTION, 12));
        transferButton.setOnAction(event -> transfer());
        refreshButton.setGraphic(Icons.glyph(Icons.Icon.REFRESH, 12));
        refreshButton.setOnAction(event -> refreshAll());

        qualityFilter.setItems(FXCollections.observableArrayList("", "RELEASED", "QUARANTINE", "REJECTED"));
        qualityFilter.setConverter(new StringConverter<>() {
            @Override
            public String toString(String value) {
                return value == null || value.isBlank() ? I18n.get().t("common.all") : Labels.quality(value);
            }

            @Override
            public String fromString(String text) {
                return null;
            }
        });
        qualityFilter.getSelectionModel().selectFirst();
        qualityFilter.setOnAction(event -> {
            stockQuery = stockQuery.withQuality(qualityFilter.getValue());
            stockTable.resetToFirstPage();
        });

        movementTypeFilter.setItems(FXCollections.observableArrayList("", "IN", "OUT", "TRANSFER",
                "ADJUSTMENT", "RETURN", "PRODUCTION_IN", "PRODUCTION_OUT", "WASTE"));
        movementTypeFilter.setConverter(new StringConverter<>() {
            @Override
            public String toString(String value) {
                return value == null || value.isBlank() ? I18n.get().t("common.all") : Labels.movementType(value);
            }

            @Override
            public String fromString(String text) {
                return null;
            }
        });
        movementTypeFilter.getSelectionModel().selectFirst();
        movementTypeFilter.setOnAction(event -> {
            movementQuery = movementQuery.withType(movementTypeFilter.getValue());
            movementTable.resetToFirstPage();
        });

        warehouseFilter.setConverter(new StringConverter<>() {
            @Override
            public String toString(Warehouse warehouse) {
                return warehouse == null ? I18n.get().t("common.all") : warehouse.code() + " · " + warehouse.name();
            }

            @Override
            public Warehouse fromString(String text) {
                return null;
            }
        });
        warehouseFilter.setOnAction(event -> {
            Warehouse picked = warehouseFilter.getValue();
            stockQuery = stockQuery.withWarehouse(picked == null ? null : picked.id());
            stockTable.resetToFirstPage();
        });

        stockTable = new DataTable<>(this::loadStock, I18n.get().t("inventory.search_prompt"))
                .columns(
                        DataTable.Column.key(I18n.get().t("material.code"), 95, StockBatch::itemCode),
                        DataTable.Column.text(I18n.get().t("common.item"), 215, StockBatch::itemName),
                        DataTable.Column.key(I18n.get().t("material.batch"), 115, StockBatch::batchNumber),
                        DataTable.Column.text(I18n.get().t("inventory.warehouse"), 140, StockBatch::warehouseName),
                        DataTable.Column.node(I18n.get().t("inventory.quality"), 115,
                                batch -> StatusBadge.of(Labels.quality(batch.qualityStatus()),
                                        Labels.qualityStyle(batch.qualityStatus()))),
                        DataTable.Column.quantity(I18n.get().t("common.quantity"), 110,
                                batch -> Formatters.quantity(batch.quantity(), batch.unit())),
                        DataTable.Column.date(I18n.get().t("material.expiry_date"), 110, StockBatch::expiryDate),
                        DataTable.Column.node(I18n.get().t("inventory.days_left"), 95, this::expiryBadge),
                        DataTable.Column.amount(I18n.get().t("inventory.unit_cost"), 110, StockBatch::unitCost),
                        DataTable.Column.amount(I18n.get().t("inventory.value"), 120, StockBatch::stockValue))
                .onActivate(this::select)
                .onLoaded(page -> {
                    summaryLabel.setText(I18n.get().t("inventory.stock.summary", page.total()));
                    StockBatch match = selected == null ? null : page.items().stream()
                            .filter(batch -> batch.batchId() == selected.batchId()
                                    && batch.warehouseId() == selected.warehouseId()
                                    && batch.itemKind().equals(selected.itemKind()))
                            .findFirst().orElse(null);
                    if (match != null) {
                        selected = match;
                        renderDetail(match);
                    } else if (selected != null) {
                        clearSelection();
                    } else if (!page.items().isEmpty()) {
                        select(page.items().get(0));
                    }
                });
        VBox.setVgrow(stockTable, Priority.ALWAYS);
        stockHost.getChildren().setAll(stockTable);

        movementTable = new DataTable<>(this::loadMovements, I18n.get().t("inventory.movement_search_prompt"))
                .columns(
                        DataTable.Column.text(I18n.get().t("inventory.occurred_at"), 145,
                                movement -> movement.occurredAt() == null ? "—"
                                        : Formatters.dateTime(movement.occurredAt())),
                        DataTable.Column.key(I18n.get().t("common.reference"), 105,
                                StockMovement::movementNumber),
                        DataTable.Column.node(I18n.get().t("inventory.movement_type"), 130,
                                movement -> StatusBadge.of(Labels.movementType(movement.movementType()),
                                        Labels.movementTypeStyle(movement.movementType()))),
                        DataTable.Column.key(I18n.get().t("material.code"), 90, StockMovement::itemCode),
                        DataTable.Column.key(I18n.get().t("material.batch"), 105, StockMovement::batchNumber),
                        DataTable.Column.text(I18n.get().t("inventory.warehouse"), 130,
                                StockMovement::warehouseName),
                        DataTable.Column.text(I18n.get().t("inventory.target"), 130,
                                movement -> movement.targetWarehouseName() == null ? "—"
                                        : movement.targetWarehouseName()),
                        DataTable.Column.quantity(I18n.get().t("common.quantity"), 105,
                                movement -> Formatters.number(movement.quantity())),
                        DataTable.Column.amount(I18n.get().t("inventory.value"), 110, StockMovement::value),
                        DataTable.Column.text(I18n.get().t("inventory.reason"), 200, StockMovement::reason),
                        DataTable.Column.text(I18n.get().t("inventory.user"), 110, StockMovement::userName))
                .placeholder(I18n.get().t("inventory.movements_empty"));
        VBox.setVgrow(movementTable, Priority.ALWAYS);
        movementHost.getChildren().setAll(movementTable);

        FxTasks.run(() -> ServiceFactory.inventory().warehouses(), loaded -> {
            warehouses = loaded == null ? List.of() : loaded;
            List<Warehouse> choices = new ArrayList<>();
            choices.add(null);
            choices.addAll(warehouses);
            warehouseFilter.setItems(FXCollections.observableArrayList(choices));
            warehouseFilter.getSelectionModel().selectFirst();
        }, error -> Toasts.error(message(error)));

        refreshSummary();
        clearSelection();
        stockTable.resetToFirstPage();
        movementTable.reload();
    }

    // ------------------------------------------------------------------ data

    private PagedResult<StockBatch> loadStock(String search, int page, int pageSize) {
        stockQuery = stockQuery.withSearch(search).withPage(page).withPageSize(pageSize);

        return ServiceFactory.inventory().stock(stockQuery);
    }

    private PagedResult<StockMovement> loadMovements(String search, int page, int pageSize) {
        movementQuery = movementQuery.withPage(page).withPageSize(pageSize);

        return ServiceFactory.inventory().movements(movementQuery);
    }

    private void refreshSummary() {
        FxTasks.run(() -> ServiceFactory.inventory().summary(), this::renderKpis,
                error -> Toasts.error(message(error)));
    }

    private void refreshAll() {
        selected = null;
        clearSelection();
        refreshSummary();
        stockTable.resetToFirstPage();
        movementTable.resetToFirstPage();
    }

    private void renderKpis(InventorySummary summary) {
        if (summary == null) {
            return;
        }
        KpiCard value = new KpiCard(Icons.Icon.INVENTORY, "kpi-icon-primary")
                .title(I18n.get().t("inventory.kpi.value"))
                .value(Formatters.currency(summary.stockValue()))
                .hint(I18n.get().t("inventory.kpi.value_hint", summary.warehouseCount()));
        KpiCard batches = new KpiCard(Icons.Icon.CUBE, "kpi-icon-accent")
                .title(I18n.get().t("inventory.kpi.batches"))
                .value(Formatters.number(summary.batchCount()))
                .hint(I18n.get().t("inventory.kpi.batches_hint", summary.materialBatchCount(),
                        summary.productBatchCount()));
        KpiCard quarantine = new KpiCard(Icons.Icon.SHIELD_CHECK, "kpi-icon-warning")
                .title(I18n.get().t("inventory.kpi.quarantine"))
                .value(Formatters.number(summary.quarantineBatches()))
                .hint(I18n.get().t("inventory.kpi.quarantine_hint"));
        KpiCard expiring = new KpiCard(Icons.Icon.WARNING, "kpi-icon-danger")
                .title(I18n.get().t("inventory.kpi.expiring"))
                .value(Formatters.number(summary.expiringBatches()))
                .hint(I18n.get().t("inventory.kpi.expiring_hint", summary.expiryWarningDays()));
        for (KpiCard card : List.of(value, batches, quarantine, expiring)) {
            HBox.setHgrow(card, Priority.ALWAYS);
            card.setMaxWidth(Double.MAX_VALUE);
        }
        kpiRow.getChildren().setAll(value, batches, quarantine, expiring);
    }

    // ------------------------------------------------------------------ detail

    private void select(StockBatch batch) {
        selected = batch;
        renderDetail(batch);
    }

    private void clearSelection() {
        selected = null;
        adjustButton.setDisable(true);
        transferButton.setDisable(true);
        Label hint = new Label(I18n.get().t("inventory.detail.hint"));
        hint.getStyleClass().add("text-muted");
        hint.setWrapText(true);
        detailHost.getChildren().setAll(hint);
    }

    private void renderDetail(StockBatch batch) {
        Card card = new Card(batch.itemCode() + " · " + batch.itemName(),
                batch.batchNumber() + " · " + batch.warehouseName());
        VBox body = new VBox(5,
                fact("inventory.quality", Labels.quality(batch.qualityStatus())),
                fact("common.quantity", Formatters.quantity(batch.quantity(), batch.unit())),
                fact("material.expiry_date", batch.expiryDate() == null ? I18n.get().t("inventory.no_expiry")
                        : Formatters.date(batch.expiryDate())),
                fact("inventory.unit_cost", Formatters.currency(batch.unitCost())),
                fact("inventory.value", Formatters.currency(batch.stockValue())),
                fact("material.supplier", batch.supplierName() == null ? "—" : batch.supplierName()));
        card.body(body);
        detailHost.getChildren().setAll(card);

        boolean adjustable = SessionContext.get().can(Permission.INVENTORY_ADJUST) && batch.quantity().signum() >= 0;
        boolean transferable = SessionContext.get().can(Permission.INVENTORY_TRANSFER)
                && batch.quantity().signum() > 0;
        adjustButton.setDisable(!adjustable);
        transferButton.setDisable(!transferable);
    }

    private Label fact(String labelKey, String value) {
        Label node = new Label(I18n.get().t(labelKey) + " : " + value);
        node.getStyleClass().add("text-muted");
        node.setWrapText(true);
        return node;
    }

    private javafx.scene.Node expiryBadge(StockBatch batch) {
        if (batch.expiryDate() == null) {
            return StatusBadge.neutral(I18n.get().t("inventory.no_expiry"));
        }
        if (batch.expired() || (batch.daysToExpiry() != null && batch.daysToExpiry() < 0)) {
            return StatusBadge.danger(I18n.get().t("material.expired", Formatters.date(batch.expiryDate())));
        }
        if (batch.daysToExpiry() != null && batch.daysToExpiry() <= 60) {
            return StatusBadge.warning(I18n.get().t("material.expires_in", batch.daysToExpiry()));
        }

        return StatusBadge.success(I18n.get().t("material.expires_in", batch.daysToExpiry()));
    }

    // ------------------------------------------------------------------ actions

    private void adjust() {
        StockBatch batch = selected;
        if (batch == null) {
            return;
        }
        Optional<StockAdjustDialog.Result> answer = StockAdjustDialog.open(stockSceneWindow(), batch);
        if (answer.isEmpty()) {
            return;
        }
        StockAdjustDialog.Result result = answer.get();
        FxTasks.runVoid(() -> ServiceFactory.inventory().adjust(batch, result.countedQuantity(), result.reason()),
                () -> {
                    Toasts.success(I18n.get().t("inventory.adjust.done", batch.itemCode(), batch.batchNumber()));
                    refreshAll();
                },
                error -> Toasts.error(message(error)));
    }

    private void transfer() {
        StockBatch batch = selected;
        if (batch == null) {
            return;
        }
        List<Warehouse> targets = warehouses.stream()
                .filter(warehouse -> warehouse.id() != batch.warehouseId())
                .filter(warehouse -> batch.isQuarantined() ? warehouse.quarantine() : !warehouse.quarantine())
                .filter(warehouse -> batch.isQuarantined() || batch.isReleased())
                .toList();
        Optional<StockTransferDialog.Result> answer = StockTransferDialog.open(stockSceneWindow(), batch, targets);
        if (answer.isEmpty()) {
            return;
        }
        StockTransferDialog.Result result = answer.get();
        FxTasks.runVoid(() -> ServiceFactory.inventory().transfer(batch, result.targetWarehouseId(),
                        result.quantity(), result.reason()),
                () -> {
                    Toasts.success(I18n.get().t("inventory.transfer.done", batch.batchNumber()));
                    refreshAll();
                },
                error -> Toasts.error(message(error)));
    }

    private javafx.stage.Window stockSceneWindow() {
        return stockHost.getScene() == null ? null : stockHost.getScene().getWindow();
    }

    private static String message(Throwable error) {
        String message = error == null ? null : error.getMessage();
        if (message == null || message.isBlank()) {
            return I18n.get().t("error.generic");
        }
        if (message.contains("QUARANTINE")) {
            return I18n.get().t("inventory.error.quarantine");
        }
        if (message.contains("INSUFFICIENT_STOCK") || message.contains("cannot exceed")) {
            return I18n.get().t("inventory.error.insufficient");
        }
        if (message.contains("NO_ADJUSTMENT") || message.contains("nothing to adjust")) {
            return I18n.get().t("inventory.error.no_adjustment");
        }
        if (message.contains("same_warehouse") || message.contains("another warehouse")) {
            return I18n.get().t("inventory.error.same_warehouse");
        }

        return message;
    }
}
