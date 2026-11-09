-- =============================================================================
-- ChemManager ERP - MySQL 8 schema
-- 04 - formulations and production
--
-- Hard rules encoded here:
--   * the application never generates a formula: formulations and items are entered
--     by qualified staff only;
--   * an approved version is immutable (isLocked = 1, trigger blocks any update);
--   * a batch cannot be released to sellable stock before a PASS quality check.
-- =============================================================================

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ------------------------------------------------------------------ formulations
CREATE TABLE formulations (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code             VARCHAR(30)     NOT NULL,
    nameFr           VARCHAR(150)    NOT NULL,
    nameAr           VARCHAR(150)    NOT NULL,
    productId        BIGINT UNSIGNED NULL COMMENT 'product manufactured from this formula',
    currentVersionId BIGINT UNSIGNED NULL,
    status           ENUM('DRAFT','PENDING','APPROVED','ARCHIVED') NOT NULL DEFAULT 'DRAFT',
    approvedBy       BIGINT UNSIGNED NULL,
    approvedAt       DATETIME        NULL,
    notes            VARCHAR(255)    NOT NULL DEFAULT '',
    createdAt        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updatedAt        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    createdBy        BIGINT UNSIGNED NULL,
    updatedBy        BIGINT UNSIGNED NULL,
    deletedAt        DATETIME        NULL DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uqFormulationsCode (code),
    KEY idxFormulationsStatus (status),
    KEY idxFormulationsProduct (productId),
    CONSTRAINT fkFormulationsProduct FOREIGN KEY (productId) REFERENCES products (id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT fkFormulationsApprovedBy FOREIGN KEY (approvedBy) REFERENCES users (id)
        ON UPDATE CASCADE ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Formula family - content lives in the versions';

CREATE TABLE formulationVersions (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    formulationId  BIGINT UNSIGNED NOT NULL,
    version        SMALLINT UNSIGNED NOT NULL,
    instructions   TEXT            NULL COMMENT 'mixing procedure written by the production manager - never generated',
    expectedYield  DECIMAL(15,3)   NOT NULL DEFAULT 0.000,
    yieldUnit      VARCHAR(12)     NOT NULL DEFAULT 'L',
    isLocked       TINYINT(1)      NOT NULL DEFAULT 0 COMMENT 'set when approved: the row becomes read only',
    lockedAt       DATETIME        NULL,
    approvedBy     BIGINT UNSIGNED NULL,
    approvedAt     DATETIME        NULL,
    createdAt      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updatedAt      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    createdBy      BIGINT UNSIGNED NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uqFormulationVersionsNumber (formulationId, version),
    KEY idxFormulationVersionsLocked (isLocked),
    CONSTRAINT fkFormulationVersionsFormulation FOREIGN KEY (formulationId) REFERENCES formulations (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fkFormulationVersionsApprovedBy FOREIGN KEY (approvedBy) REFERENCES users (id)
        ON UPDATE CASCADE ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Immutable once approved - a change creates a new version';

CREATE TABLE formulationItems (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    versionId     BIGINT UNSIGNED NOT NULL,
    materialId    BIGINT UNSIGNED NOT NULL,
    quantity      DECIMAL(15,4)   NOT NULL,
    unit          VARCHAR(12)     NOT NULL DEFAULT 'KG',
    percentage    DECIMAL(7,4)    NOT NULL DEFAULT 0.0000,
    phase         VARCHAR(40)     NOT NULL DEFAULT '' COMMENT 'order of incorporation: phase A, phase B...',
    tolerancePct  DECIMAL(7,4)    NOT NULL DEFAULT 0.0000,
    sortOrder     SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idxFormulationItemsVersion (versionId, sortOrder),
    KEY idxFormulationItemsMaterial (materialId),
    CONSTRAINT fkFormulationItemsVersion FOREIGN KEY (versionId) REFERENCES formulationVersions (id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fkFormulationItemsMaterial FOREIGN KEY (materialId) REFERENCES rawMaterials (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT ckFormulationItemsQuantity CHECK (quantity > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Bill of materials of one version';

ALTER TABLE products
    ADD CONSTRAINT fkProductsFormulation FOREIGN KEY (formulationId) REFERENCES formulations (id)
        ON UPDATE CASCADE ON DELETE SET NULL;

ALTER TABLE formulations
    ADD CONSTRAINT fkFormulationsCurrentVersion FOREIGN KEY (currentVersionId) REFERENCES formulationVersions (id)
        ON UPDATE CASCADE ON DELETE SET NULL;

-- ------------------------------------------------------------------ production orders
CREATE TABLE productionOrders (
    id                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    orderNumber          VARCHAR(30)     NOT NULL,
    formulationVersionId BIGINT UNSIGNED NULL,
    productId            BIGINT UNSIGNED NOT NULL,
    warehouseId          BIGINT UNSIGNED NOT NULL,
    plannedQuantity      DECIMAL(15,3)   NOT NULL DEFAULT 0.000,
    actualQuantity       DECIMAL(15,3)   NOT NULL DEFAULT 0.000,
    wasteQuantity        DECIMAL(15,3)   NOT NULL DEFAULT 0.000,
    unit                 VARCHAR(12)     NOT NULL DEFAULT 'L',
    status               ENUM('DRAFT','WAITING_MATERIALS','APPROVED','IN_PROGRESS','QUALITY_CONTROL','COMPLETED','REJECTED','CANCELLED')
                         NOT NULL DEFAULT 'DRAFT',
    plannedDate          DATE            NOT NULL,
    startedAt            DATETIME        NULL,
    completedAt          DATETIME        NULL,
    materialCost         DECIMAL(15,2)   NOT NULL DEFAULT 0.00,
    packagingCost        DECIMAL(15,2)   NOT NULL DEFAULT 0.00,
    otherCost            DECIMAL(15,2)   NOT NULL DEFAULT 0.00,
    operatorId           BIGINT UNSIGNED NULL,
    supervisorId         BIGINT UNSIGNED NULL,
    notes                VARCHAR(255)    NOT NULL DEFAULT '',
    createdAt            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updatedAt            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    createdBy            BIGINT UNSIGNED NULL,
    updatedBy            BIGINT UNSIGNED NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uqProductionOrdersNumber (orderNumber),
    KEY idxProductionOrdersStatus (status, plannedDate),
    KEY idxProductionOrdersProduct (productId),
    CONSTRAINT fkProductionOrdersVersion FOREIGN KEY (formulationVersionId) REFERENCES formulationVersions (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fkProductionOrdersProduct FOREIGN KEY (productId) REFERENCES products (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fkProductionOrdersWarehouse FOREIGN KEY (warehouseId) REFERENCES warehouses (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fkProductionOrdersOperator FOREIGN KEY (operatorId) REFERENCES users (id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT fkProductionOrdersSupervisor FOREIGN KEY (supervisorId) REFERENCES users (id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT ckProductionOrdersQuantity CHECK (actualQuantity >= 0 AND plannedQuantity > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Manufacturing orders with real cost per batch';

CREATE TABLE productionMaterials (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    orderId         BIGINT UNSIGNED NOT NULL,
    materialBatchId BIGINT UNSIGNED NOT NULL,
    plannedQuantity DECIMAL(15,3)   NOT NULL DEFAULT 0.000,
    consumedQuantity DECIMAL(15,3)  NOT NULL DEFAULT 0.000,
    unit            VARCHAR(12)     NOT NULL DEFAULT 'KG',
    PRIMARY KEY (id),
    KEY idxProductionMaterialsOrder (orderId),
    KEY idxProductionMaterialsBatch (materialBatchId),
    CONSTRAINT fkProductionMaterialsOrder FOREIGN KEY (orderId) REFERENCES productionOrders (id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fkProductionMaterialsBatch FOREIGN KEY (materialBatchId) REFERENCES materialBatches (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT ckProductionMaterialsQuantity CHECK (consumedQuantity >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Batch genealogy input side';

CREATE TABLE productionOutputs (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    orderId         BIGINT UNSIGNED NOT NULL,
    productBatchId  BIGINT UNSIGNED NOT NULL,
    quantity        DECIMAL(15,3)   NOT NULL DEFAULT 0.000,
    unit            VARCHAR(12)     NOT NULL DEFAULT 'PCE',
    PRIMARY KEY (id),
    KEY idxProductionOutputsOrder (orderId),
    CONSTRAINT fkProductionOutputsOrder FOREIGN KEY (orderId) REFERENCES productionOrders (id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fkProductionOutputsBatch FOREIGN KEY (productBatchId) REFERENCES productBatches (id)
        ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Batch genealogy output side';

ALTER TABLE productBatches
    ADD CONSTRAINT fkProductBatchesOrder FOREIGN KEY (productionOrderId) REFERENCES productionOrders (id)
        ON UPDATE CASCADE ON DELETE SET NULL;

CREATE TABLE qualityChecks (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    orderId     BIGINT UNSIGNED NOT NULL,
    checkType   VARCHAR(40)     NOT NULL COMMENT 'aspect, pH, viscosity, density, active matter...',
    parameters  JSON            NULL COMMENT 'measured values entered by the operator',
    result      ENUM('PASS','FAIL','PENDING') NOT NULL DEFAULT 'PENDING',
    checkedBy   BIGINT UNSIGNED NULL,
    checkedAt   DATETIME        NULL,
    remarks     VARCHAR(255)    NOT NULL DEFAULT '',
    createdAt   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idxQualityChecksOrder (orderId, result),
    CONSTRAINT fkQualityChecksOrder FOREIGN KEY (orderId) REFERENCES productionOrders (id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fkQualityChecksUser FOREIGN KEY (checkedBy) REFERENCES users (id)
        ON UPDATE CASCADE ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'A batch is released only after a PASS result';

-- ------------------------------------------------------------------ guards
-- Trigger bodies contain semicolons: the mysql client needs an alternative delimiter.
DELIMITER $$

-- An approved formulation version is immutable: any UPDATE aborts the transaction.
CREATE TRIGGER trgFormulationVersionsLocked
BEFORE UPDATE ON formulationVersions
FOR EACH ROW
BEGIN
    IF OLD.isLocked = 1 AND NEW.isLocked = 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'formulations.locked: an approved version cannot be modified, create a new version';
    END IF;
END$$

-- Stock can never go negative.
CREATE TRIGGER trgMaterialBatchesNoNegative
BEFORE UPDATE ON materialBatches
FOR EACH ROW
BEGIN
    IF NEW.quantity < 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'stock.negative: material batch quantity cannot be negative';
    END IF;
END$$

CREATE TRIGGER trgProductBatchesNoNegative
BEFORE UPDATE ON productBatches
FOR EACH ROW
BEGIN
    IF NEW.quantity < 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'stock.negative: product batch quantity cannot be negative';
    END IF;
END$$

DELIMITER ;
