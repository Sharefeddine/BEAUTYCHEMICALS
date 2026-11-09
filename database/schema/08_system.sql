-- =============================================================================
-- ChemManager ERP - MySQL 8 schema
-- 08 - system: settings, document numbering, backups
-- =============================================================================

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE settings (
    id        BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    settingKey VARCHAR(60)    NOT NULL,
    value     VARCHAR(255)    NOT NULL DEFAULT '',
    valueType ENUM('STRING','INTEGER','DECIMAL','BOOLEAN','JSON') NOT NULL DEFAULT 'STRING',
    settingGroup VARCHAR(40)  NOT NULL DEFAULT 'general',
    labelKey  VARCHAR(80)     NOT NULL DEFAULT '',
    updatedBy BIGINT UNSIGNED NULL,
    updatedAt DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uqSettingsKey (settingKey),
    KEY idxSettingsGroup (settingGroup),
    CONSTRAINT fkSettingsUser FOREIGN KEY (updatedBy) REFERENCES users (id)
        ON UPDATE CASCADE ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Key/value application settings (currency, VAT, prefixes, backup schedule)';

CREATE TABLE documentSequences (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    documentType ENUM('INVOICE','QUOTATION','DELIVERY','SALESORDER','PURCHASEORDER','PRODUCTION','PAYMENT','EXPENSE','CREDITNOTE','STOCKMOVEMENT','INCIDENT') NOT NULL,
    periodKey    VARCHAR(10)     NOT NULL COMMENT 'YYYY or YYYYMM depending on the document',
    prefix       VARCHAR(10)     NOT NULL DEFAULT '',
    lastNumber   BIGINT UNSIGNED NOT NULL DEFAULT 0,
    updatedAt    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uqDocumentSequencesTypePeriod (documentType, periodKey)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Gapless numbering per document type and period, locked with SELECT FOR UPDATE';

CREATE TABLE backups (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    fileName   VARCHAR(180)    NOT NULL,
    sizeBytes  BIGINT UNSIGNED NOT NULL DEFAULT 0,
    status     ENUM('RUNNING','SUCCESS','FAILED') NOT NULL DEFAULT 'RUNNING',
    kind       ENUM('DAILY','WEEKLY','MANUAL') NOT NULL DEFAULT 'DAILY',
    startedAt  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finishedAt DATETIME        NULL,
    message    VARCHAR(255)    NOT NULL DEFAULT '',
    PRIMARY KEY (id),
    KEY idxBackupsStarted (startedAt, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Backup history - failures are surfaced in the notification centre';

-- ------------------------------------------------------------------ reporting views
CREATE OR REPLACE VIEW vwStockBalances AS
SELECT
    CAST('MATERIAL' AS CHAR(8)) COLLATE utf8mb4_unicode_ci AS itemKind,
    m.id       AS itemId,
    m.code     AS itemCode,
    m.commercialName AS itemName,
    m.storageUnit AS unit,
    SUM(b.quantity) AS quantity,
    m.minStock AS minStock,
    MIN(b.expiryDate) AS nearestExpiry
FROM rawMaterials m
JOIN materialBatches b ON b.materialId = m.id AND b.qualityStatus = 'RELEASED'
WHERE m.deletedAt IS NULL
GROUP BY m.id, m.code, m.commercialName, m.storageUnit, m.minStock
UNION ALL
SELECT
    CAST('PRODUCT' AS CHAR(8)) COLLATE utf8mb4_unicode_ci AS itemKind,
    p.id       AS itemId,
    p.sku      AS itemCode,
    p.nameFr   AS itemName,
    p.unit     AS unit,
    SUM(b.quantity) AS quantity,
    p.minStock AS minStock,
    MIN(b.expiryDate) AS nearestExpiry
FROM products p
JOIN productBatches b ON b.productId = p.id AND b.qualityStatus = 'RELEASED'
WHERE p.deletedAt IS NULL
GROUP BY p.id, p.sku, p.nameFr, p.unit, p.minStock;

CREATE OR REPLACE VIEW vwLowStock AS
SELECT * FROM vwStockBalances WHERE quantity <= minStock;
