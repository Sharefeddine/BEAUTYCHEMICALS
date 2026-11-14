package com.chemmanager.service.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.chemmanager.model.CompanySettings;
import com.chemmanager.model.Formulation;
import com.chemmanager.model.FormulationItem;
import com.chemmanager.model.FormulationVersion;
import com.chemmanager.model.NotificationItem;
import com.chemmanager.model.Product;
import com.chemmanager.model.ProductType;
import com.chemmanager.model.RawMaterial;
import com.chemmanager.model.Severity;
import com.chemmanager.model.Supplier;
import com.chemmanager.model.User;
import com.chemmanager.model.inventory.InventorySummary;
import com.chemmanager.model.inventory.StockBatch;
import com.chemmanager.model.inventory.StockMovement;
import com.chemmanager.model.inventory.Warehouse;
import com.chemmanager.model.purchasing.GoodsReceipt;
import com.chemmanager.model.purchasing.PurchaseOrder;
import com.chemmanager.security.Permission;
import com.chemmanager.security.Role;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Translates the JSON rows of the PHP API into the client model.
 *
 * <p>The API returns the database columns verbatim (camelCase), the model is the shape the screens
 * use, so exactly one class knows the difference. Missing or null values degrade to the model's
 * defaults instead of throwing: a partially filled record must never break a screen.</p>
 */
final class ApiMapper {

    private ApiMapper() {
    }

    static Product product(JsonNode node) {
        return new Product(
                node.path("id").asLong(),
                text(node, "sku", ""),
                emptyToNull(text(node, "barcode", null)),
                text(node, "nameFr", ""),
                text(node, "nameAr", ""),
                text(node, "description", ""),
                text(node, "categoryName", ""),
                enumValue(ProductType.class, text(node, "type", null), ProductType.MANUFACTURED),
                text(node, "unit", "PCE"),
                text(node, "packaging", ""),
                decimal(node, "packagingCapacity"),
                node.path("unitsPerCarton").asInt(1),
                node.path("minStock").asInt(0),
                node.path("maxStock").asInt(0),
                decimal(node, "purchaseCost"),
                decimal(node, "productionCost"),
                decimal(node, "wholesalePrice"),
                decimal(node, "retailPrice"),
                decimal(node, "specialPrice"),
                decimal(node, "vatRate"),
                node.path("stockQuantity").asInt(0),
                emptyToNull(text(node, "imagePath", null)),
                node.path("isActive").asBoolean(true));
    }

    static RawMaterial material(JsonNode node) {
        return new RawMaterial(
                node.path("id").asLong(),
                text(node, "code", ""),
                text(node, "commercialName", ""),
                text(node, "chemicalName", ""),
                text(node, "categoryName", ""),
                text(node, "supplierName", ""),
                text(node, "purchaseUnit", "KG"),
                text(node, "storageUnit", "KG"),
                decimal(node, "conversionFactor"),
                decimal(node, "purchasePrice"),
                decimal(node, "stockQuantity"),
                decimal(node, "minStock"),
                text(node, "storageLocation", ""),
                text(node, "batchNumber", ""),
                date(node, "productionDate"),
                date(node, "nearestExpiry"),
                text(node, "hazardClass", ""),
                text(node, "hazardPictograms", ""),
                text(node, "storageConditions", ""),
                node.path("sdsAvailable").asBoolean(false),
                text(node, "sdsFileName", ""),
                node.path("isActive").asBoolean(true));
    }

    static User user(JsonNode node) {
        List<Role> roles = new ArrayList<>();
        JsonNode codes = node.path("roleCodes");
        if (codes.isArray()) {
            for (JsonNode code : codes) {
                roles.add(role(code.asText(), Set.of(), true));
            }
        }
        return new User(
                node.path("id").asLong(),
                text(node, "username", ""),
                text(node, "fullName", ""),
                text(node, "email", ""),
                text(node, "phone", ""),
                text(node, "jobTitle", ""),
                roles,
                node.path("isActive").asBoolean(true),
                dateTime(node, "lastLoginAt"));
    }

    static Role role(String code, Set<Permission> permissions, boolean systemRole) {
        return new Role(0L, code, "role." + code.toLowerCase(), "role." + code.toLowerCase() + ".description",
                permissions, systemRole);
    }

    /** @param permissionCodes codes such as {@code PRODUCTS_EDIT} as returned by the API */
    static Set<Permission> permissions(JsonNode permissionCodes) {
        Set<Permission> result = EnumSet.noneOf(Permission.class);
        if (permissionCodes != null && permissionCodes.isArray()) {
            for (JsonNode code : permissionCodes) {
                try {
                    result.add(Permission.valueOf(code.asText()));
                } catch (IllegalArgumentException ignored) {
                    // a permission added on the server before the client release: ignore it here
                }
            }
        }
        return result;
    }

    static CompanySettings company(JsonNode node, JsonNode settings) {
        CompanySettings defaults = CompanySettings.defaults();
        return new CompanySettings(
                text(node, "name", defaults.companyName()),
                text(node, "legalName", defaults.legalName()),
                text(node, "activity", defaults.activity()),
                text(node, "address", defaults.address()),
                text(node, "city", defaults.city()),
                text(node, "wilaya", defaults.wilaya()),
                text(node, "postalCode", defaults.postalCode()),
                text(node, "phone", defaults.phone()),
                text(node, "email", defaults.email()),
                text(node, "website", defaults.website()),
                text(node, "taxId", defaults.taxId()),
                text(node, "tradeRegister", defaults.rc()),
                text(node, "nif", defaults.nif()),
                text(node, "taxArticle", defaults.ai()),
                text(node, "currency", defaults.currency()),
                decimal(node, "vatRate") == null ? defaults.defaultVatRate() : decimal(node, "vatRate"),
                text(node, "invoicePrefix", defaults.invoicePrefix()),
                text(node, "logoPath", defaults.logoPath()),
                setting(settings, "app.defaultLocale", defaults.localeCode()),
                setting(settings, "app.defaultTheme", defaults.theme()));
    }

    static Formulation formulation(JsonNode node) {
        JsonNode versions = node.path("versions");
        int versionCount = versions.isArray() ? versions.size() : node.path("versionCount").asInt(0);
        return new Formulation(
                node.path("id").asLong(),
                text(node, "code", ""),
                text(node, "nameFr", ""),
                text(node, "nameAr", ""),
                node.path("productId").isNull() || node.path("productId").isMissingNode()
                        ? null : node.path("productId").asLong(),
                emptyToNull(text(node, "productName", null)),
                text(node, "status", "DRAFT"),
                node.path("currentVersionId").isNull() || node.path("currentVersionId").isMissingNode()
                        ? null : node.path("currentVersionId").asLong(),
                node.path("currentVersion").isNull() || node.path("currentVersion").isMissingNode()
                        ? null : node.path("currentVersion").asInt(),
                decimalOrZero(node, "currentCost"),
                decimalOrZero(node, "currentYield"),
                text(node, "currentYieldUnit", ""),
                versionCount,
                emptyToNull(text(node, "approvedByName", null)),
                text(node, "notes", ""),
                dateTime(node, "updatedAt"));
    }

    static FormulationVersion version(JsonNode node) {
        List<FormulationItem> items = new ArrayList<>();
        for (JsonNode row : node.path("items")) {
            items.add(new FormulationItem(
                    row.path("materialId").asLong(),
                    text(row, "materialCode", ""),
                    text(row, "materialName", ""),
                    decimalOrZero(row, "quantity"),
                    text(row, "unit", "KG"),
                    decimalOrZero(row, "percentage"),
                    text(row, "phase", ""),
                    decimalOrZero(row, "lineCost"),
                    decimalOrZero(row, "stockQuantity"),
                    text(row, "hazardClass", "")));
        }

        return new FormulationVersion(
                node.path("id").asLong(),
                node.path("formulationId").asLong(),
                node.path("version").asInt(0),
                text(node, "instructions", ""),
                decimalOrZero(node, "expectedYield"),
                text(node, "yieldUnit", "L"),
                node.path("isLocked").asBoolean(false),
                emptyToNull(text(node, "approvedByName", null)),
                dateTime(node, "approvedAt"),
                decimalOrZero(node, "cost"),
                node.path("itemCount").asInt(items.size()),
                items);
    }

    static BigDecimal decimalOrZero(JsonNode node, String field) {
        BigDecimal value = decimal(node, field);
        return value == null ? BigDecimal.ZERO : value;
    }

    static NotificationItem notification(JsonNode node) {
        return new NotificationItem(
                node.path("id").asLong(),
                NotificationItem.Category.SYSTEM,
                enumValue(Severity.class, text(node, "severity", null), Severity.INFO),
                text(node, "title", text(node, "titleKey", "")),
                text(node, "message", ""),
                instant(node, "createdAt"),
                emptyToNull(text(node, "moduleId", text(node, "linkModule", null))),
                node.path("read").asBoolean(false));
    }

    // ------------------------------------------------------------------ phase 4: inventory, purchasing

    static Warehouse warehouse(JsonNode node) {
        return new Warehouse(
                node.path("id").asLong(),
                text(node, "code", ""),
                text(node, "name", ""),
                text(node, "address", ""),
                node.path("quarantine").asBoolean(false));
    }

    static InventorySummary inventorySummary(JsonNode node) {
        return new InventorySummary(
                node.path("batchCount").asLong(0),
                node.path("materialBatchCount").asLong(0),
                node.path("productBatchCount").asLong(0),
                decimalOrZero(node, "stockValue"),
                node.path("quarantineBatches").asLong(0),
                node.path("expiringBatches").asLong(0),
                node.path("expiryWarningDays").asInt(60),
                node.path("lowStockItems").asLong(0),
                node.path("warehouseCount").asLong(0),
                text(node, "currency", "DZD"));
    }

    static StockBatch stockBatch(JsonNode node) {
        JsonNode days = node.path("daysToExpiry");
        return new StockBatch(
                text(node, "itemKind", "MATERIAL"),
                node.path("batchId").asLong(0),
                node.path("itemId").asLong(0),
                text(node, "itemCode", ""),
                text(node, "itemName", ""),
                text(node, "batchNumber", ""),
                node.path("warehouseId").asLong(0),
                text(node, "warehouseName", ""),
                decimalOrZero(node, "quantity"),
                text(node, "unit", ""),
                date(node, "productionDate"),
                date(node, "expiryDate"),
                days.isMissingNode() || days.isNull() ? null : days.asInt(),
                node.path("expired").asBoolean(false),
                text(node, "qualityStatus", "QUARANTINE"),
                decimalOrZero(node, "unitCost"),
                decimalOrZero(node, "stockValue"),
                emptyToNull(text(node, "supplierName", null)));
    }

    static StockMovement stockMovement(JsonNode node) {
        JsonNode target = node.path("targetWarehouseId");
        return new StockMovement(
                node.path("id").asLong(0),
                text(node, "movementNumber", ""),
                text(node, "movementType", "IN"),
                text(node, "itemKind", "MATERIAL"),
                node.path("batchId").asLong(0),
                text(node, "batchNumber", ""),
                text(node, "itemCode", ""),
                text(node, "itemName", ""),
                node.path("warehouseId").asLong(0),
                text(node, "warehouseName", ""),
                target.isMissingNode() || target.isNull() ? null : target.asLong(),
                emptyToNull(text(node, "targetWarehouseName", null)),
                decimalOrZero(node, "quantity"),
                decimalOrZero(node, "unitCost"),
                decimalOrZero(node, "value"),
                text(node, "referenceType", ""),
                text(node, "reason", ""),
                text(node, "userName", ""),
                dateTime(node, "occurredAt"));
    }

    static Supplier supplier(JsonNode node) {
        List<Supplier.Material> materials = new ArrayList<>();
        for (JsonNode row : node.path("materials")) {
            materials.add(new Supplier.Material(
                    row.path("id").asLong(0),
                    text(row, "code", ""),
                    text(row, "commercialName", ""),
                    decimalOrZero(row, "purchasePrice"),
                    text(row, "storageUnit", "")));
        }
        List<Supplier.Order> orders = new ArrayList<>();
        for (JsonNode row : node.path("recentOrders")) {
            orders.add(new Supplier.Order(
                    row.path("id").asLong(0),
                    text(row, "orderNumber", ""),
                    date(row, "orderDate"),
                    text(row, "status", ""),
                    decimalOrZero(row, "totalTtc")));
        }
        return new Supplier(
                node.path("id").asLong(0),
                text(node, "code", ""),
                text(node, "name", ""),
                text(node, "contactName", ""),
                text(node, "phone", ""),
                text(node, "email", ""),
                text(node, "address", ""),
                text(node, "wilaya", ""),
                text(node, "paymentTerms", ""),
                decimalOrZero(node, "balance"),
                node.path("isActive").asBoolean(true),
                node.path("orderCount").asLong(0),
                date(node, "lastOrderDate"),
                node.path("materialCount").asLong(0),
                materials,
                orders);
    }

    static PurchaseOrder purchaseOrder(JsonNode node) {
        List<PurchaseOrder.Line> items = new ArrayList<>();
        for (JsonNode row : node.path("items")) {
            items.add(new PurchaseOrder.Line(
                    row.path("id").asLong(0),
                    row.path("materialId").asLong(0),
                    text(row, "materialCode", ""),
                    text(row, "materialName", ""),
                    decimalOrZero(row, "quantity"),
                    text(row, "unit", ""),
                    decimalOrZero(row, "unitPrice"),
                    decimalOrZero(row, "vatRate"),
                    decimalOrZero(row, "receivedQty"),
                    decimalOrZero(row, "remainingQty"),
                    decimalOrZero(row, "lineTotal")));
        }
        List<PurchaseOrder.Receipt> receipts = new ArrayList<>();
        for (JsonNode row : node.path("receipts")) {
            receipts.add(new PurchaseOrder.Receipt(
                    row.path("id").asLong(0),
                    text(row, "receiptNumber", ""),
                    row.path("warehouseId").asLong(0),
                    text(row, "warehouseName", ""),
                    dateTime(row, "receivedAt"),
                    text(row, "receivedByName", ""),
                    row.path("itemCount").asLong(0)));
        }
        return new PurchaseOrder(
                node.path("id").asLong(0),
                text(node, "orderNumber", ""),
                node.path("supplierId").asLong(0),
                text(node, "supplierName", ""),
                text(node, "status", "DRAFT"),
                date(node, "orderDate"),
                date(node, "expectedDate"),
                decimalOrZero(node, "amountHt"),
                decimalOrZero(node, "vatAmount"),
                decimalOrZero(node, "totalTtc"),
                text(node, "currency", "DZD"),
                text(node, "notes", ""),
                node.path("itemCount").asLong(0),
                decimalOrZero(node, "receivedQuantity"),
                node.path("receiptCount").asLong(0),
                items,
                receipts);
    }

    static GoodsReceipt goodsReceipt(JsonNode node) {
        List<GoodsReceipt.Line> items = new ArrayList<>();
        for (JsonNode row : node.path("items")) {
            items.add(new GoodsReceipt.Line(
                    row.path("id").asLong(0),
                    row.path("materialId").asLong(0),
                    text(row, "materialCode", ""),
                    text(row, "materialName", ""),
                    decimalOrZero(row, "quantity"),
                    decimalOrZero(row, "unitPrice"),
                    text(row, "batchNumber", ""),
                    date(row, "expiryDate"),
                    decimalOrZero(row, "batchQuantity"),
                    text(row, "qualityStatus", "QUARANTINE")));
        }
        return new GoodsReceipt(
                node.path("id").asLong(0),
                text(node, "receiptNumber", ""),
                node.path("orderId").asLong(0),
                text(node, "orderNumber", ""),
                node.path("warehouseId").asLong(0),
                text(node, "warehouseName", ""),
                dateTime(node, "receivedAt"),
                text(node, "receivedByName", ""),
                text(node, "notes", ""),
                text(node, "orderStatus", ""),
                items);
    }

    // ------------------------------------------------------------------ primitives

    /**
     * The API groups the settings by section ({@code {"app":[{"settingKey":"app.defaultLocale",…}]}}),
     * so one lookup walks the sections and stops at the first matching key.
     */
    private static String setting(JsonNode settings, String key, String fallback) {
        if (settings == null || !settings.isObject()) {
            return fallback;
        }
        java.util.Iterator<JsonNode> groups = settings.elements();
        while (groups.hasNext()) {
            for (JsonNode row : groups.next()) {
                if (key.equals(row.path("settingKey").asText())) {
                    String value = row.path("value").asText("");
                    return value.isBlank() ? fallback : value;
                }
            }
        }
        return fallback;
    }

    private static String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? fallback : value.asText(fallback);
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull() || value.asText().isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.asText());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static LocalDate date(JsonNode node, String field) {
        String raw = text(node, field, null);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.length() > 10 ? raw.substring(0, 10) : raw);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static LocalDateTime dateTime(JsonNode node, String field) {
        String raw = text(node, field, null);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalised = raw.replace(' ', 'T');
        try {
            return LocalDateTime.parse(normalised.length() > 19 ? normalised.substring(0, 19) : normalised);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    static Instant instant(JsonNode node, String field) {
        String raw = text(node, field, null);
        if (raw == null || raw.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException ex) {
            // MySQL DATETIME "yyyy-MM-dd HH:mm:ss" is local time
            try {
                return LocalDateTime.parse(raw.replace(' ', 'T'))
                        .atZone(java.time.ZoneId.systemDefault()).toInstant();
            } catch (DateTimeParseException ignored) {
                return Instant.now();
            }
        }
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String raw, E fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, raw);
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
