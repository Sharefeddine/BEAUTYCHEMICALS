package com.chemmanager.controller.products;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import com.chemmanager.app.AppContext;
import com.chemmanager.component.Card;
import com.chemmanager.component.DataTable;
import com.chemmanager.component.Dialogs;
import com.chemmanager.component.Icons;
import com.chemmanager.component.StatusBadge;
import com.chemmanager.component.Toasts;
import com.chemmanager.dto.PagedResult;
import com.chemmanager.dto.ProductQuery;
import com.chemmanager.localization.I18n;
import com.chemmanager.model.Product;
import com.chemmanager.model.ProductType;
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
 * Product catalogue: searchable, filterable, paginated table with valuation summary,
 * detail sheet and Excel export. Editing/creation arrives with the inventory phase.
 */
public final class ProductsController {

    @FXML private VBox contentRoot;
    @FXML private VBox tableHost;
    @FXML private Label summaryLabel;
    @FXML private ComboBox<String> categoryFilter;
    @FXML private ComboBox<ProductType> typeFilter;
    @FXML private CheckBox activeOnly;
    @FXML private Button refreshButton;
    @FXML private Button exportButton;

    private DataTable<Product> table;
    private PagedResult<Product> lastPage;

    @FXML
    private void initialize() {

        refreshButton.setGraphic(Icons.glyph(Icons.Icon.REFRESH, 12));
        refreshButton.setOnAction(event -> table.resetToFirstPage());
        exportButton.setGraphic(Icons.glyph(Icons.Icon.EXCEL, 12));
        exportButton.setOnAction(event -> export());
        activeOnly.setText(I18n.get().t("products.active_only"));

        categoryFilter.setConverter(new StringConverter<>() {
            @Override
            public String toString(String value) {
                return value == null ? "" : Labels.category(value);
            }

            @Override
            public String fromString(String text) {
                return null;
            }
        });
        typeFilter.setConverter(new StringConverter<>() {
            @Override
            public String toString(ProductType value) {
                return value == null ? "" : Labels.productType(value);
            }

            @Override
            public ProductType fromString(String text) {
                return null;
            }
        });

        table = new DataTable<>(this::loadPage, I18n.get().t("products.search_prompt"))
                .columns(
                        DataTable.Column.key(I18n.get().t("product.sku"), 100, Product::sku),
                        DataTable.Column.text(I18n.get().t("product.name"), 210,
                                product -> product.displayName(I18n.get().isRtl())),
                        DataTable.Column.node(I18n.get().t("common.category"), 115,
                                product -> StatusBadge.neutral(Labels.category(product.category()))),
                        DataTable.Column.text(I18n.get().t("product.packaging"), 105, Product::packaging),
                        DataTable.Column.node(I18n.get().t("product.stock"), 95, product ->
                                product.isOutOfStock() ? StatusBadge.danger(product.stockOnHand() + " " + product.unit())
                                        : product.isLowStock() ? StatusBadge.warning(product.stockOnHand() + " " + product.unit())
                                        : StatusBadge.success(product.stockOnHand() + " " + product.unit())),
                        DataTable.Column.amount(I18n.get().t("product.wholesale_price"), 110, Product::wholesalePrice),
                        DataTable.Column.amount(I18n.get().t("product.retail_price"), 110, Product::retailPrice),
                        DataTable.Column.text(I18n.get().t("product.margin"), 80,
                                product -> Formatters.percent(product.marginPercent())),
                        DataTable.Column.amount(I18n.get().t("product.stock_value"), 110, Product::stockValue),
                        DataTable.Column.node(I18n.get().t("common.status"), 80,
                                product -> StatusBadge.active(product.active())))
                .onActivate(this::showDetail)
                .onLoaded(page -> {
                    lastPage = page;
                    BigDecimal total = page.items().stream().map(Product::stockValue)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    summaryLabel.setText(I18n.get().t("products.summary", page.total(),
                            Money.format(total)));
                });
        VBox.setVgrow(table, javafx.scene.layout.Priority.ALWAYS);
        tableHost.getChildren().setAll(table);

        loadFilters();
        categoryFilter.setOnAction(event -> table.resetToFirstPage());
        typeFilter.setOnAction(event -> table.resetToFirstPage());
        activeOnly.setOnAction(event -> table.resetToFirstPage());
        table.resetToFirstPage();
    }

    private void loadFilters() {
        FxTasks.run(() -> ServiceFactory.products().categories(), categories -> {
            categoryFilter.getItems().clear();
            categoryFilter.getItems().add(null);
            categoryFilter.getItems().addAll(categories);
            categoryFilter.setValue(null);
        }, error -> Toasts.error(I18n.get().t("error.loading")));

        typeFilter.getItems().setAll(ProductType.values());
        typeFilter.setValue(null);
    }

    private PagedResult<Product> loadPage(String search, int page, int pageSize) {
        ProductQuery query = new ProductQuery(search, categoryFilter.getValue(), typeFilter.getValue(),
                activeOnly.isSelected(), page, pageSize);
        return ServiceFactory.products().search(query);
    }

    private void showDetail(Product product) {
        String message = String.join("\n",
                I18n.get().t("product.detail.header", product.sku(), product.displayName(I18n.get().isRtl())),
                I18n.get().t("product.detail.names", product.nameFr(), product.nameAr()),
                I18n.get().t("product.detail.category", Labels.category(product.category()),
                        Labels.productType(product.type())),
                I18n.get().t("product.detail.packaging", product.packaging(),
                        Formatters.decimal(product.packagingCapacity(), 0),
                        String.valueOf(product.unitsPerCarton())),
                I18n.get().t("product.detail.stock", product.stockOnHand(), product.unit(),
                        product.minStock(), product.maxStock()),
                I18n.get().t("product.detail.costs", Money.format(product.purchaseCost()),
                        Money.format(product.productionCost())),
                I18n.get().t("product.detail.prices", Money.format(product.wholesalePrice()),
                        Money.format(product.retailPrice()), Money.format(product.specialPrice())),
                I18n.get().t("product.detail.tax", Formatters.percent(product.vatRate()),
                        Formatters.percent(product.marginPercent())),
                I18n.get().t("product.detail.barcode", product.barcode()));
        Dialogs.info(AppContext.get().stage(), I18n.get().t("product.detail.title"), message);
    }

    private void export() {
        if (lastPage == null || lastPage.items().isEmpty()) {
            Toasts.warning(I18n.get().t("dashboard.export_empty"));
            return;
        }
        Optional<Path> target = Dialogs.chooseSaveFile(AppContext.get().stage(),
                I18n.get().t("products.export"), "products.xlsx",
                I18n.get().t("dashboard.export_filter"), "xlsx");
        if (target.isEmpty()) {
            return;
        }
        List<String> headers = List.of(I18n.get().t("product.sku"), I18n.get().t("product.name"),
                I18n.get().t("common.category"), I18n.get().t("product.packaging"),
                I18n.get().t("product.stock"), I18n.get().t("product.wholesale_price"),
                I18n.get().t("product.retail_price"), I18n.get().t("product.stock_value"));
        List<List<Object>> rows = lastPage.items().stream()
                .map(product -> List.<Object>of(product.sku(), product.displayName(I18n.get().isRtl()),
                        Labels.category(product.category()), product.packaging(),
                        BigDecimal.valueOf(product.stockOnHand()), product.wholesalePrice(),
                        product.retailPrice(), product.stockValue()))
                .toList();
        Path file = target.get();
        FxTasks.runVoid(() -> ExcelExporter.exportRows(file, I18n.get().t("nav.products"), headers, rows),
                () -> Toasts.success(I18n.get().t("dashboard.export_done", file.getFileName().toString())),
                error -> Toasts.error(I18n.get().t("dashboard.export_failed", String.valueOf(error.getMessage()))));
    }
}
