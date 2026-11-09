-- =============================================================================
-- ChemManager ERP - MySQL 8 schema
-- 07 - chemical safety
--
-- The library of safety data sheets, GHS pictograms and storage instructions that the
-- warehouse and production screens read before handling a hazardous material.
-- =============================================================================

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE safetyPictograms (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code        VARCHAR(12)     NOT NULL COMMENT 'GHS01 ... GHS09',
    nameFr      VARCHAR(80)     NOT NULL,
    nameAr      VARCHAR(80)     NOT NULL,
    nameEn      VARCHAR(80)     NULL,
    imagePath   VARCHAR(255)    NULL,
    description VARCHAR(255)    NOT NULL DEFAULT '',
    sortOrder   SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uqSafetyPictogramsCode (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'GHS pictograms with their trilingual names';

CREATE TABLE safetyDataSheets (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    materialId  BIGINT UNSIGNED NOT NULL,
    documentType ENUM('SDS','TECHNICAL','DISPOSAL','TRANSPORT') NOT NULL DEFAULT 'SDS',
    filePath    VARCHAR(255)    NOT NULL,
    issueDate   DATE            NULL,
    revision    VARCHAR(20)     NOT NULL DEFAULT '',
    source      VARCHAR(120)    NOT NULL DEFAULT '' COMMENT 'supplier or official database',
    isCurrent   TINYINT(1)      NOT NULL DEFAULT 1,
    uploadedBy  BIGINT UNSIGNED NULL,
    createdAt   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idxSafetyDataSheetsMaterial (materialId, documentType, isCurrent),
    CONSTRAINT fkSafetyDataSheetsMaterial FOREIGN KEY (materialId) REFERENCES rawMaterials (id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fkSafetyDataSheetsUser FOREIGN KEY (uploadedBy) REFERENCES users (id)
        ON UPDATE CASCADE ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'One current SDS per material, previous revisions kept';

CREATE TABLE materialPictograms (
    materialId  BIGINT UNSIGNED NOT NULL,
    pictogramId BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (materialId, pictogramId),
    CONSTRAINT fkMaterialPictogramsMaterial FOREIGN KEY (materialId) REFERENCES rawMaterials (id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fkMaterialPictogramsPictogram FOREIGN KEY (pictogramId) REFERENCES safetyPictograms (id)
        ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Hazard labels displayed on the material sheet';

CREATE TABLE safetyInstructions (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    materialId  BIGINT UNSIGNED NOT NULL,
    ppe         VARCHAR(255)    NOT NULL DEFAULT '' COMMENT 'gloves, goggles, mask, apron...',
    storage     VARCHAR(255)    NOT NULL DEFAULT '',
    handling    VARCHAR(255)    NOT NULL DEFAULT '',
    firstAid    VARCHAR(255)    NOT NULL DEFAULT '',
    fireMeasures VARCHAR(255)   NOT NULL DEFAULT '',
    spillage    VARCHAR(255)    NOT NULL DEFAULT '',
    updatedAt   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updatedBy   BIGINT UNSIGNED NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uqSafetyInstructionsMaterial (materialId),
    CONSTRAINT fkSafetyInstructionsMaterial FOREIGN KEY (materialId) REFERENCES rawMaterials (id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fkSafetyInstructionsUser FOREIGN KEY (updatedBy) REFERENCES users (id)
        ON UPDATE CASCADE ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Handling and emergency instructions per material';

CREATE TABLE safetyIncidents (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    incidentNumber VARCHAR(30)     NOT NULL,
    materialId     BIGINT UNSIGNED NULL,
    incidentDate   DATETIME        NOT NULL,
    incidentType   ENUM('SPILL','EXPOSURE','FIRE','DAMAGE','OTHER') NOT NULL DEFAULT 'SPILL',
    severity       ENUM('MINOR','MODERATE','MAJOR','CRITICAL') NOT NULL DEFAULT 'MINOR',
    description    TEXT            NOT NULL,
    actionsTaken   TEXT            NULL,
    warehouseId    BIGINT UNSIGNED NULL,
    reportedBy     BIGINT UNSIGNED NOT NULL,
    status         ENUM('OPEN','INVESTIGATING','CLOSED') NOT NULL DEFAULT 'OPEN',
    closedAt       DATETIME        NULL,
    attachmentPath VARCHAR(255)    NULL,
    createdAt      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updatedAt      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uqSafetyIncidentsNumber (incidentNumber),
    KEY idxSafetyIncidentsDate (incidentDate, severity),
    CONSTRAINT fkSafetyIncidentsMaterial FOREIGN KEY (materialId) REFERENCES rawMaterials (id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT fkSafetyIncidentsWarehouse FOREIGN KEY (warehouseId) REFERENCES warehouses (id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT fkSafetyIncidentsUser FOREIGN KEY (reportedBy) REFERENCES users (id)
        ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Incident register required by the HSE procedure';
