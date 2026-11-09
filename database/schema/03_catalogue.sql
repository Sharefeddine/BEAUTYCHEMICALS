-- =============================================================================
-- ChemManager ERP - MySQL 8 schema
-- 03 - catalogue: categories, products, raw materials, partners, warehouses, batches
--
-- Stock is NOT stored on products/rawMaterials: it is the sum of the batch rows and
-- only changes through stockMovements (append-only ledger) and the batch tables.
-- =============================================================================

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ------------------------------------------------------------------ partners
CREATE TABLE suppliers (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code          VARCHAR(20)     NOT NULL,
    name          VARCHAR(150)    NOT NULL,
    contactName   VARCHAR(120)    NOT NULL DEFAULT '',
    phone         VARCHAR(40)     NOT NULL DEFAULT '',
    email         VARCHAR(120)    NOT NULL DEFAULT '',
    address       VARCHAR(255)    NOT NULL DEFAULT '',
    wilaya        VARCHAR(80)     NOT NULL DEFAULT '',
    paymentTerms  VARCHAR(60)     NOT NULL DEFAULT 'Comptant',
    balance       DECIMAL(15,2)   NOT NULL DEFAULT 0.00 COMMENT 'maintained by supplierInvoices and supplierPayments',
    notes         VARCHAR(255)    NULL,
    isActive      TINYINT(1)      NOT NULL DEFAULT 1,
    createdAt     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updatedAt     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    createdBy     BIGINT UNSIGNED NULL,
    updatedBy     BIGINT UNSIGNED NULL,
    deletedAt     DATETIME        NULL DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uqSuppliersCode (code),
    KEY idxSuppliersName (name),
    KEY idxSuppliersActive (isActive, deletedAt)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Supplier master data';

CREATE TABLE customers (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code         VARCHAR(20)     NOT NULL,
    name         VARCHAR(150)    NOT NULL,
    type         ENUM('INDIVIDUAL','COMPANY') NOT NULL DEFAULT 'COMPANY',
    phone        VARCHAR(40)     NOT NULL DEFAULT '',
    email        VARCHAR(120)    NOT NULL DEFAULT '',
    address      VARCHAR(255)    NOT NULL DEFAULT '',
    wilaya       VARCHAR(80)     NOT NULL DEFAULT '',
    creditLimit  DECIMAL(15,2)   NOT NULL DEFAULT 0.00,
    balance      DECIMAL(15,2)   NOT NULL DEFAULT 0.00 COMMENT 'outstanding receivable, driven by invoices and payments',
    priceSegment ENUM('RETAIL','WHOLESALE','SPECIAL') NOT NULL DEFAULT 'RETAIL',
    notes        VARCHAR(255)    NULL,
    isActive     TINYINT(1)      NOT NULL DEFAULT 1,
    createdAt    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updatedAt    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    createdBy    BIGINT UNSIGNED NULL,
    updatedBy    BIGINT UNSIGNED NULL,
    deletedAt    DATETIME        NULL DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uqCustomersCode (code),
    KEY idxCustomersName (name),
    KEY idxCustomersWilaya (wilaya),
    KEY idxCustomersActive (isActive, deletedAt)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Customer master data with credit limit';

-- ------------------------------------------------------------------ catalogues
CREATE TABLE productCategories (
    id        BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code      VARCHAR(40)     NOT NULL,
    nameFr    VARCHAR(80)     NOT NULL,
    nameAr    VARCHAR(80)     NOT NULL,
    nameEn    VARCHAR(80)     NULL,
    sortOrder SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    isActive  TINYINT(1)      NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uqProductCategoriesCode (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Dishwashing, floor cleaner, bleach, disinfectant...';

CREATE TABLE rawMaterialCategories (
    id        BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code      VARCHAR(40)     NOT NULL,
    nameFr    VARCHAR(80)     NOT NULL,
    nameAr    VARCHAR(80)     NOT NULL,
    nameEn    VARCHAR(80)     NULL,
    sortOrder SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    isActive  TINYINT(1)      NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uqRawMaterialCategoriesCode (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Chemicals, surfactants, fragrances, colorants, additives, solvents, packaging';

-- ------------------------------------------------------------------ products
CREATE TABLE products (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    sku              VARCHAR(30)     NOT NULL,
    barcode          VARCHAR(40)     NULL,
    nameFr           VARCHAR(150)    NOT NULL,
    nameAr           VARCHAR(150)    NOT NULL,
    description      VARCHAR(500)    NOT NULL DEFAULT '',
    categoryId       BIGINT UNSIGNED NOT NULL,
    formulationId    BIGINT UNSIGNED NULL COMMENT 'set for manufactured products once the formula is approved',
    type             ENUM('MANUFACTURED','TRADED','RAW_MATERIAL','PACKAGING') NOT NULL DEFAULT 'MANUFACTURED',
    unit             VARCHAR(12)     NOT NULL DEFAULT 'PCE',
    packaging        VARCHAR(40)     NOT NULL DEFAULT '',
    packagingCapacity DECIMAL(15,3)  NOT NULL DEFAULT 0.000,
    capacityUnit     VARCHAR(12)     NOT NULL DEFAULT 'ML',
    unitsPerCarton   INT UNSIGNED    NOT NULL DEFAULT 1,
    minStock         INT UNSIGNED    NOT NULL DEFAULT 0,
    maxStock         INT UNSIGNED    NOT NULL DEFAULT 0,
    purchaseCost     DECIMAL(15,2)   NOT NULL DEFAULT 0.00,
    productionCost   DECIMAL(15,2)   NOT NULL DEFAULT 0.00,
    wholesalePrice   DECIMAL(15,2)   NOT NULL DEFAULT 0.00,
    retailPrice      DECIMAL(15,2)   NOT NULL DEFAULT 0.00,
    specialPrice     DECIMAL(15,2)   NOT NULL DEFAULT 0.00,
    vatRate          DECIMAL(7,4)    NOT NULL DEFAULT 19.0000,
    imagePath        VARCHAR(255)    NULL,
    isActive         TINYINT(1)      NOT NULL DEFAULT 1,
    createdAt        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updatedAt        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    createdBy        BIGINT UNSIGNED NULL,
    updatedBy        BIGINT UNSIGNED NULL,
    deletedAt        DATETIME        NULL DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uqProductsSku (sku),
    KEY idxProductsBarcode (barcode),
    KEY idxProductsCategory (categoryId),
    KEY idxProductsType (type, isActive),
    KEY idxProductsNameFr (nameFr),
    CONSTRAINT fkProductsCategory FOREIGN KEY (categoryId) REFERENCES productCategories (id)
        ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Sellable finished products and traded articles';

CREATE TABLE rawMaterials (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code              VARCHAR(30)     NOT NULL,
    commercialName    VARCHAR(150)    NOT NULL,
    chemicalName      VARCHAR(180)    NOT NULL DEFAULT '',
    categoryId        BIGINT UNSIGNED NOT NULL,
    supplierId        BIGINT UNSIGNED NULL,
    purchaseUnit      VARCHAR(12)     NOT NULL DEFAULT 'KG',
    storageUnit       VARCHAR(12)     NOT NULL DEFAULT 'KG',
    conversionFactor  DECIMAL(15,4)   NOT NULL DEFAULT 1.0000,
    purchasePrice     DECIMAL(15,2)   NOT NULL DEFAULT 0.00,
    minStock          DECIMAL(15,3)   NOT NULL DEFAULT 0.000,
    storageLocation   VARCHAR(40)     NOT NULL DEFAULT '',
    hazardClass       VARCHAR(120)    NOT NULL DEFAULT '',
    hazardPictograms  VARCHAR(120)    NOT NULL DEFAULT '' COMMENT 'comma separated GHS codes, e.g. GHS02,GHS05',
    storageConditions VARCHAR(255)    NOT NULL DEFAULT '',
    sdsAvailable      TINYINT(1)      NOT NULL DEFAULT 0,
    sdsFileName       VARCHAR(255)    NULL,
    isActive          TINYINT(1)      NOT NULL DEFAULT 1,
    createdAt         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updatedAt         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    createdBy         BIGINT UNSIGNED NULL,
    updatedBy         BIGINT UNSIGNED NULL,
    deletedAt         DATETIME        NULL DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uqRawMaterialsCode (code),
    KEY idxRawMaterialsCategory (categoryId),
    KEY idxRawMaterialsSupplier (supplierId),
    KEY idxRawMaterialsCommercialName (commercialName),
    CONSTRAINT fkRawMaterialsCategory FOREIGN KEY (categoryId) REFERENCES rawMaterialCategories (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fkRawMaterialsSupplier FOREIGN KEY (supplierId) REFERENCES suppliers (id)
        ON UPDATE CASCADE ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Batch-controlled chemicals and packaging components';

-- ------------------------------------------------------------------ warehouses
CREATE TABLE warehouses (
    id        BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code      VARCHAR(20)     NOT NULL,
    name      VARCHAR(80)     NOT NULL,
    address   VARCHAR(255)    NOT NULL DEFAULT '',
    isQuarantine TINYINT(1)   NOT NULL DEFAULT 0 COMMENT 'a released batch never stays here',
    isActive  TINYINT(1)      NOT NULL DEFAULT 1,
    createdAt DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updatedAt DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deletedAt DATETIME        NULL DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uqWarehousesCode (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Raw material store, finished goods store, quarantine';

-- ------------------------------------------------------------------ batches
CREATE TABLE materialBatches (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    materialId    BIGINT UNSIGNED NOT NULL,
    batchNumber   VARCHAR(40)     NOT NULL,
    warehouseId   BIGINT UNSIGNED NOT NULL,
    supplierId    BIGINT UNSIGNED NULL,
    quantity      DECIMAL(15,3)   NOT NULL DEFAULT 0.000 COMMENT 'remaining quantity, only stockMovements may change it',
    unit          VARCHAR(12)     NOT NULL DEFAULT 'KG',
    productionDate DATE           NULL,
    expiryDate    DATE           NULL,
    qualityStatus ENUM('QUARANTINE','RELEASED','REJECTED') NOT NULL DEFAULT 'QUARANTINE',
    purchasePrice DECIMAL(15,2)   NOT NULL DEFAULT 0.00,
    releasedAt    DATETIME        NULL,
    releasedBy    BIGINT UNSIGNED NULL,
    createdAt     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updatedAt     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uqMaterialBatchesNumber (materialId, batchNumber),
    KEY idxMaterialBatchesFefo (expiryDate, qualityStatus),
    KEY idxMaterialBatchesWarehouse (warehouseId),
    KEY idxMaterialBatchesSupplier (supplierId),
    CONSTRAINT fkMaterialBatchesMaterial FOREIGN KEY (materialId) REFERENCES rawMaterials (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fkMaterialBatchesWarehouse FOREIGN KEY (warehouseId) REFERENCES warehouses (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fkMaterialBatchesSupplier FOREIGN KEY (supplierId) REFERENCES suppliers (id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT ckMaterialBatchesQuantity CHECK (quantity >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'FEFO is served by idxMaterialBatchesFefo';

CREATE TABLE productBatches (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    productId         BIGINT UNSIGNED NOT NULL,
    batchNumber       VARCHAR(40)     NOT NULL,
    warehouseId       BIGINT UNSIGNED NOT NULL,
    productionOrderId BIGINT UNSIGNED NULL COMMENT 'NULL for traded goods',
    quantity          DECIMAL(15,3)   NOT NULL DEFAULT 0.000,
    unit              VARCHAR(12)     NOT NULL DEFAULT 'PCE',
    productionDate    DATE            NULL,
    expiryDate        DATE            NULL,
    qualityStatus     ENUM('QUARANTINE','RELEASED','REJECTED') NOT NULL DEFAULT 'QUARANTINE',
    unitCost          DECIMAL(15,4)   NOT NULL DEFAULT 0.0000,
    createdAt         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updatedAt         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uqProductBatchesNumber (productId, batchNumber),
    KEY idxProductBatchesFefo (expiryDate, qualityStatus),
    KEY idxProductBatchesWarehouse (warehouseId),
    KEY idxProductBatchesOrder (productionOrderId),
    CONSTRAINT fkProductBatchesProduct FOREIGN KEY (productId) REFERENCES products (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fkProductBatchesWarehouse FOREIGN KEY (warehouseId) REFERENCES warehouses (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT ckProductBatchesQuantity CHECK (quantity >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Finished goods batches, traceable to a production order';

-- ------------------------------------------------------------------ stock ledger
CREATE TABLE stockMovements (
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    movementNumber     VARCHAR(30)     NOT NULL,
    movementType       ENUM('IN','OUT','TRANSFER','ADJUSTMENT','RETURN','PRODUCTION_IN','PRODUCTION_OUT','WASTE') NOT NULL,
    warehouseId        BIGINT UNSIGNED NOT NULL,
    targetWarehouseId  BIGINT UNSIGNED NULL,
    materialBatchId    BIGINT UNSIGNED NULL,
    productBatchId     BIGINT UNSIGNED NULL,
    quantity           DECIMAL(15,3)   NOT NULL COMMENT 'always positive: the direction comes from movementType',
    unitCost           DECIMAL(15,4)   NOT NULL DEFAULT 0.0000,
    referenceType      VARCHAR(40)     NOT NULL DEFAULT '',
    referenceId        BIGINT UNSIGNED NULL,
    reason             VARCHAR(255)    NOT NULL DEFAULT '',
    userId             BIGINT UNSIGNED NOT NULL,
    occurredAt         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uqStockMovementsNumber (movementNumber),
    KEY idxStockMovementsMaterialBatch (materialBatchId, occurredAt),
    KEY idxStockMovementsProductBatch (productBatchId, occurredAt),
    KEY idxStockMovementsWarehouse (warehouseId, occurredAt),
    KEY idxStockMovementsReference (referenceType, referenceId),
    CONSTRAINT fkStockMovementsWarehouse FOREIGN KEY (warehouseId) REFERENCES warehouses (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fkStockMovementsTarget FOREIGN KEY (targetWarehouseId) REFERENCES warehouses (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    -- No explicit referential action below: MySQL refuses a CHECK constraint on a column that
    -- carries one. The default RESTRICT behaviour is exactly what an append-only ledger needs.
    CONSTRAINT fkStockMovementsMaterialBatch FOREIGN KEY (materialBatchId) REFERENCES materialBatches (id),
    CONSTRAINT fkStockMovementsProductBatch FOREIGN KEY (productBatchId) REFERENCES productBatches (id),
    CONSTRAINT fkStockMovementsUser FOREIGN KEY (userId) REFERENCES users (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT ckStockMovementsQuantity CHECK (quantity > 0),
    CONSTRAINT ckStockMovementsTarget CHECK (
        (materialBatchId IS NOT NULL AND productBatchId IS NULL)
        OR (materialBatchId IS NULL AND productBatchId IS NOT NULL))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Append-only stock ledger - balances are views over it';

CREATE TABLE stockCounts (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    countNumber VARCHAR(30)     NOT NULL,
    warehouseId BIGINT UNSIGNED NOT NULL,
    status      ENUM('DRAFT','COUNTING','VALIDATED','CANCELLED') NOT NULL DEFAULT 'DRAFT',
    countedAt   DATETIME        NULL,
    validatedAt DATETIME        NULL,
    userId      BIGINT UNSIGNED NOT NULL,
    notes       VARCHAR(255)    NOT NULL DEFAULT '',
    createdAt   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uqStockCountsNumber (countNumber),
    KEY idxStockCountsWarehouse (warehouseId, status),
    CONSTRAINT fkStockCountsWarehouse FOREIGN KEY (warehouseId) REFERENCES warehouses (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fkStockCountsUser FOREIGN KEY (userId) REFERENCES users (id)
        ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Cycle count header';

CREATE TABLE stockCountItems (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    stockCountId   BIGINT UNSIGNED NOT NULL,
    materialBatchId BIGINT UNSIGNED NULL,
    productBatchId BIGINT UNSIGNED NULL,
    systemQuantity DECIMAL(15,3)   NOT NULL DEFAULT 0.000,
    countedQuantity DECIMAL(15,3)  NOT NULL DEFAULT 0.000,
    difference     DECIMAL(15,3)   GENERATED ALWAYS AS (countedQuantity - systemQuantity) STORED,
    PRIMARY KEY (id),
    KEY idxStockCountItemsCount (stockCountId),
    CONSTRAINT fkStockCountItemsCount FOREIGN KEY (stockCountId) REFERENCES stockCounts (id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fkStockCountItemsMaterial FOREIGN KEY (materialBatchId) REFERENCES materialBatches (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fkStockCountItemsProduct FOREIGN KEY (productBatchId) REFERENCES productBatches (id)
        ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'A validated line posts an ADJUSTMENT movement, the history is never rewritten';
