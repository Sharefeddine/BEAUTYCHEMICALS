-- =============================================================================
-- ChemManager ERP - MySQL 8 schema
-- 01 - organisation, security, audit
--
-- Conventions (they apply to every file of database/schema):
--   * identifiers are lowerCamelCase: NO underscore anywhere (tables, columns,
--     indexes, constraints, views, triggers);
--   * surrogate primary keys: BIGINT UNSIGNED AUTO_INCREMENT `id`;
--   * business codes are UNIQUE and exposed to the UI;
--   * audit columns on every business table: createdAt, updatedAt, createdBy, updatedBy;
--   * soft delete with `deletedAt` - financial and stock history is never hard deleted;
--   * money DECIMAL(15,2), quantities DECIMAL(15,3), rates DECIMAL(7,4);
--   * MySQL must run with lowerCaseTableNames=0 (Linux default) so that the
--     camelCase table names keep their case; see database/README.md.
-- =============================================================================

SET NAMES utf8mb4;
SET time_zone = '+01:00';

-- ------------------------------------------------------------------ company
CREATE TABLE companies (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name            VARCHAR(150)    NOT NULL,
    legalName       VARCHAR(180)    NOT NULL,
    activity        VARCHAR(180)    NOT NULL DEFAULT '',
    address         VARCHAR(255)    NOT NULL DEFAULT '',
    city            VARCHAR(80)     NOT NULL DEFAULT '',
    wilaya          VARCHAR(80)     NOT NULL DEFAULT '',
    postalCode      VARCHAR(12)     NOT NULL DEFAULT '',
    country         VARCHAR(80)     NOT NULL DEFAULT 'Algérie',
    phone           VARCHAR(40)     NOT NULL DEFAULT '',
    phoneSecond     VARCHAR(40)     NULL,
    email           VARCHAR(120)    NOT NULL DEFAULT '',
    website         VARCHAR(120)    NULL,
    taxId           VARCHAR(40)     NOT NULL DEFAULT '',
    tradeRegister   VARCHAR(40)     NOT NULL DEFAULT '',
    nif             VARCHAR(40)     NOT NULL DEFAULT '',
    taxArticle      VARCHAR(40)     NOT NULL DEFAULT '',
    logoPath        VARCHAR(255)    NULL,
    currency        CHAR(3)         NOT NULL DEFAULT 'DZD',
    vatRate         DECIMAL(7,4)    NOT NULL DEFAULT 19.0000,
    invoicePrefix   VARCHAR(10)     NOT NULL DEFAULT 'FA',
    quotationPrefix VARCHAR(10)     NOT NULL DEFAULT 'DE',
    deliveryPrefix  VARCHAR(10)     NOT NULL DEFAULT 'BL',
    fiscalYearStart TINYINT UNSIGNED NOT NULL DEFAULT 1,
    createdAt       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updatedAt       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updatedBy       BIGINT UNSIGNED NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Company identity printed on documents';

-- ------------------------------------------------------------------ organisation
CREATE TABLE departments (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code        VARCHAR(20)     NOT NULL,
    nameFr      VARCHAR(80)     NOT NULL,
    nameAr      VARCHAR(80)     NOT NULL,
    nameEn      VARCHAR(80)     NULL,
    managerId   BIGINT UNSIGNED NULL,
    isActive    TINYINT(1)      NOT NULL DEFAULT 1,
    createdAt   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updatedAt   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deletedAt   DATETIME        NULL DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uqDepartmentsCode (code),
    KEY idxDepartmentsManager (managerId)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Production, warehouse, sales, finance, quality...';

-- departments.managerId references users: the foreign key is added once users exists.

-- ------------------------------------------------------------------ users
CREATE TABLE users (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    username       VARCHAR(40)     NOT NULL,
    passwordHash   VARCHAR(255)    NOT NULL,
    fullName       VARCHAR(120)    NOT NULL,
    email          VARCHAR(120)    NOT NULL,
    phone          VARCHAR(40)     NOT NULL DEFAULT '',
    jobTitle       VARCHAR(80)     NOT NULL DEFAULT '',
    departmentId   BIGINT UNSIGNED NULL,
    isActive       TINYINT(1)      NOT NULL DEFAULT 1,
    lastLoginAt    DATETIME        NULL,
    lastLoginIp    VARCHAR(45)     NULL,
    failedAttempts SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    lockedUntil    DATETIME        NULL,
    passwordChangedAt DATETIME     NULL,
    mustChangePassword TINYINT(1)  NOT NULL DEFAULT 0,
    createdAt      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updatedAt      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    createdBy      BIGINT UNSIGNED NULL,
    updatedBy      BIGINT UNSIGNED NULL,
    deletedAt      DATETIME        NULL DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uqUsersUsername (username),
    UNIQUE KEY uqUsersEmail (email),
    KEY idxUsersDepartment (departmentId),
    KEY idxUsersActive (isActive, deletedAt),
    CONSTRAINT fkUsersDepartment FOREIGN KEY (departmentId) REFERENCES departments (id)
        ON UPDATE CASCADE ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Application accounts - passwords are bcrypt hashes produced by the API';

CREATE TABLE permissions (
    id       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code     VARCHAR(40)     NOT NULL,
    module   VARCHAR(40)     NOT NULL,
    labelFr  VARCHAR(80)     NOT NULL,
    labelAr  VARCHAR(80)     NOT NULL,
    labelEn  VARCHAR(80)     NULL,
    sortOrder SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uqPermissionsCode (code),
    KEY idxPermissionsModule (module)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '44 atomic permissions - mirror of com.chemmanager.security.Permission';

CREATE TABLE roles (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code           VARCHAR(40)     NOT NULL,
    nameFr         VARCHAR(80)     NOT NULL,
    nameAr         VARCHAR(80)     NOT NULL,
    nameEn         VARCHAR(80)     NULL,
    descriptionFr  VARCHAR(180)    NOT NULL DEFAULT '',
    descriptionAr  VARCHAR(180)    NOT NULL DEFAULT '',
    descriptionEn  VARCHAR(180)    NULL,
    isSystem       TINYINT(1)      NOT NULL DEFAULT 1,
    createdAt      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updatedAt      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deletedAt      DATETIME        NULL DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uqRolesCode (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'The 12 seeded roles; system roles cannot be deleted';

CREATE TABLE rolePermissions (
    roleId       BIGINT UNSIGNED NOT NULL,
    permissionId BIGINT UNSIGNED NOT NULL,
    grantedAt    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    grantedBy    BIGINT UNSIGNED NULL,
    PRIMARY KEY (roleId, permissionId),
    KEY idxRolePermissionsPermission (permissionId),
    CONSTRAINT fkRolePermissionsRole FOREIGN KEY (roleId) REFERENCES roles (id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fkRolePermissionsPermission FOREIGN KEY (permissionId) REFERENCES permissions (id)
        ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Role to permission matrix enforced by the API';

CREATE TABLE userRoles (
    userId    BIGINT UNSIGNED NOT NULL,
    roleId    BIGINT UNSIGNED NOT NULL,
    grantedAt DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    grantedBy BIGINT UNSIGNED NULL,
    PRIMARY KEY (userId, roleId),
    KEY idxUserRolesRole (roleId),
    CONSTRAINT fkUserRolesUser FOREIGN KEY (userId) REFERENCES users (id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fkUserRolesRole FOREIGN KEY (roleId) REFERENCES roles (id)
        ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Effective rights of a user = union of its roles';

-- ------------------------------------------------------------------ sessions
CREATE TABLE apiTokens (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    userId     BIGINT UNSIGNED NOT NULL,
    tokenHash  CHAR(64)        NOT NULL COMMENT 'sha256 of the bearer token, never the token itself',
    issuedAt   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expiresAt  DATETIME        NOT NULL,
    revokedAt  DATETIME        NULL,
    ipAddress  VARCHAR(45)     NULL,
    userAgent  VARCHAR(255)    NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uqApiTokensHash (tokenHash),
    KEY idxApiTokensUser (userId, revokedAt),
    KEY idxApiTokensExpiry (expiresAt),
    CONSTRAINT fkApiTokensUser FOREIGN KEY (userId) REFERENCES users (id)
        ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Bearer tokens - expired rows are purged weekly';

-- ------------------------------------------------------------------ audit & notifications
CREATE TABLE auditLogs (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    userId     BIGINT UNSIGNED NULL,
    username   VARCHAR(40)     NOT NULL DEFAULT '' COMMENT 'denormalised so the log survives user deletion',
    action     VARCHAR(40)     NOT NULL COMMENT 'CREATE, UPDATE, DELETE, LOGIN, LOGOUT, APPROVE, EXPORT...',
    module     VARCHAR(40)     NOT NULL,
    entity     VARCHAR(60)     NOT NULL DEFAULT '',
    entityId   BIGINT UNSIGNED NULL,
    reference  VARCHAR(60)     NOT NULL DEFAULT '',
    severity   ENUM('INFO','WARNING','CRITICAL') NOT NULL DEFAULT 'INFO',
    ipAddress  VARCHAR(45)     NULL,
    payload    JSON            NULL,
    occurredAt DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idxAuditLogsOccurred (occurredAt),
    KEY idxAuditLogsUser (userId, occurredAt),
    KEY idxAuditLogsEntity (entity, entityId),
    KEY idxAuditLogsModule (module, occurredAt),
    CONSTRAINT fkAuditLogsUser FOREIGN KEY (userId) REFERENCES users (id)
        ON UPDATE CASCADE ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Append-only trail - the API account has no UPDATE or DELETE grant';

CREATE TABLE notifications (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    userId     BIGINT UNSIGNED NULL COMMENT 'NULL = broadcast to every user holding the permission',
    titleKey   VARCHAR(80)     NOT NULL,
    message    VARCHAR(255)    NOT NULL DEFAULT '',
    severity   ENUM('INFO','WARNING','CRITICAL') NOT NULL DEFAULT 'INFO',
    linkModule VARCHAR(40)     NULL,
    linkId     BIGINT UNSIGNED NULL,
    isRead     TINYINT(1)      NOT NULL DEFAULT 0,
    readAt     DATETIME        NULL,
    createdAt  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idxNotificationsUser (userId, isRead, createdAt),
    CONSTRAINT fkNotificationsUser FOREIGN KEY (userId) REFERENCES users (id)
        ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Badge and popup of the application header';

-- departments.managerId could only be constrained once users exists.
ALTER TABLE departments
    ADD CONSTRAINT fkDepartmentsManager FOREIGN KEY (managerId) REFERENCES users (id)
        ON UPDATE CASCADE ON DELETE SET NULL;
