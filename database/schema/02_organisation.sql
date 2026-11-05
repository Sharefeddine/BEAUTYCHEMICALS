-- =============================================================================
-- ChemManager ERP - MySQL 8 schema
-- 02 - organisation: cash accounts and employees
--
-- Created before the commercial and financial documents because they reference them
-- (payments are booked on a cash account, delivery routes are assigned to a driver).
-- =============================================================================

-- ------------------------------------------------------------------ treasury
CREATE TABLE cashAccounts (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code           VARCHAR(20)     NOT NULL,
    name           VARCHAR(80)     NOT NULL,
    type           ENUM('CASH','BANK') NOT NULL DEFAULT 'CASH',
    bankName       VARCHAR(120)    NOT NULL DEFAULT '',
    rib            VARCHAR(40)     NOT NULL DEFAULT '' COMMENT 'Algerian bank account number (RIB)',
    openingBalance DECIMAL(15,2)   NOT NULL DEFAULT 0.00,
    currency       CHAR(3)         NOT NULL DEFAULT 'DZD',
    isActive       TINYINT(1)      NOT NULL DEFAULT 1,
    createdAt      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updatedAt      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deletedAt      DATETIME        NULL DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uqCashAccountsCode (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Cash register and bank accounts';

-- ------------------------------------------------------------------ employees
CREATE TABLE employees (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    employeeNumber VARCHAR(20)     NOT NULL,
    firstName      VARCHAR(60)     NOT NULL,
    lastName       VARCHAR(60)     NOT NULL,
    departmentId   BIGINT UNSIGNED NULL,
    position       VARCHAR(80)     NOT NULL DEFAULT '',
    phone          VARCHAR(40)     NOT NULL DEFAULT '',
    email          VARCHAR(120)    NOT NULL DEFAULT '',
    address        VARCHAR(255)    NOT NULL DEFAULT '',
    hireDate       DATE            NOT NULL,
    contractType   ENUM('CDI','CDD','INTERIM','STAGE') NOT NULL DEFAULT 'CDI',
    baseSalary     DECIMAL(15,2)   NOT NULL DEFAULT 0.00,
    userId         BIGINT UNSIGNED NULL COMMENT 'login account of the employee, when he has one',
    isActive       TINYINT(1)      NOT NULL DEFAULT 1,
    createdAt      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updatedAt      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    createdBy      BIGINT UNSIGNED NULL,
    updatedBy      BIGINT UNSIGNED NULL,
    deletedAt      DATETIME        NULL DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uqEmployeesNumber (employeeNumber),
    KEY idxEmployeesDepartment (departmentId),
    KEY idxEmployeesName (lastName, firstName),
    CONSTRAINT fkEmployeesDepartment FOREIGN KEY (departmentId) REFERENCES departments (id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT fkEmployeesUser FOREIGN KEY (userId) REFERENCES users (id)
        ON UPDATE CASCADE ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Staff records, independent from application accounts';
