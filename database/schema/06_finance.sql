-- =============================================================================
-- ChemManager ERP - MySQL 8 schema
-- 06 - finance, expenses and payroll
--
-- Nothing is posted into a closed accounting period: the API checks accountingPeriods
-- before any insert, and every financial row keeps a link to its source document.
-- =============================================================================

-- ------------------------------------------------------------------ cash book
CREATE TABLE cashTransactions (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    cashAccountId  BIGINT UNSIGNED NOT NULL,
    direction      ENUM('IN','OUT') NOT NULL,
    amount         DECIMAL(15,2)   NOT NULL,
    sourceType     ENUM('INVOICE','PAYMENT','SUPPLIERPAYMENT','EXPENSE','SALARY','TRANSFER','OTHER') NOT NULL,
    sourceId       BIGINT UNSIGNED NULL,
    description    VARCHAR(255)    NOT NULL DEFAULT '',
    occurredAt     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    accountingPeriodId BIGINT UNSIGNED NULL,
    userId         BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (id),
    KEY idxCashTransactionsAccount (cashAccountId, occurredAt),
    KEY idxCashTransactionsSource (sourceType, sourceId),
    CONSTRAINT fkCashTransactionsAccount FOREIGN KEY (cashAccountId) REFERENCES cashAccounts (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fkCashTransactionsUser FOREIGN KEY (userId) REFERENCES users (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT ckCashTransactionsAmount CHECK (amount > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Cash book: one row per receipt or disbursement';

-- ------------------------------------------------------------------ expenses
CREATE TABLE expenseCategories (
    id        BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code      VARCHAR(40)     NOT NULL,
    nameFr    VARCHAR(80)     NOT NULL,
    nameAr    VARCHAR(80)     NOT NULL,
    nameEn    VARCHAR(80)     NULL,
    isSalary  TINYINT(1)      NOT NULL DEFAULT 0,
    isVehicle TINYINT(1)      NOT NULL DEFAULT 0,
    isActive  TINYINT(1)      NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uqExpenseCategoriesCode (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Rent, salaries, energy, fuel, maintenance...';

CREATE TABLE expenses (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    expenseNumber  VARCHAR(30)     NOT NULL,
    categoryId     BIGINT UNSIGNED NOT NULL,
    label          VARCHAR(180)    NOT NULL,
    amount         DECIMAL(15,2)   NOT NULL,
    vatAmount      DECIMAL(15,2)   NOT NULL DEFAULT 0.00,
    expenseDate    DATE            NOT NULL,
    paymentMethod  ENUM('CASH','CHEQUE','TRANSFER','CARD','CREDIT') NOT NULL DEFAULT 'CASH',
    cashAccountId  BIGINT UNSIGNED NULL,
    supplierId     BIGINT UNSIGNED NULL,
    vehicleId      BIGINT UNSIGNED NULL,
    employeeId     BIGINT UNSIGNED NULL,
    productionOrderId BIGINT UNSIGNED NULL,
    attachmentPath VARCHAR(255)    NULL,
    status         ENUM('DRAFT','APPROVED','PAID','CANCELLED') NOT NULL DEFAULT 'DRAFT',
    notes          VARCHAR(255)    NOT NULL DEFAULT '',
    createdAt      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updatedAt      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    createdBy      BIGINT UNSIGNED NULL,
    approvedBy     BIGINT UNSIGNED NULL,
    deletedAt      DATETIME        NULL DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uqExpensesNumber (expenseNumber),
    KEY idxExpensesDate (expenseDate, status),
    KEY idxExpensesCategory (categoryId),
    KEY idxExpensesVehicle (vehicleId),
    CONSTRAINT fkExpensesCategory FOREIGN KEY (categoryId) REFERENCES expenseCategories (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fkExpensesCash FOREIGN KEY (cashAccountId) REFERENCES cashAccounts (id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT fkExpensesSupplier FOREIGN KEY (supplierId) REFERENCES suppliers (id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT fkExpensesVehicle FOREIGN KEY (vehicleId) REFERENCES vehicles (id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT fkExpensesEmployee FOREIGN KEY (employeeId) REFERENCES employees (id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT ckExpensesAmount CHECK (amount > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Operating expenses with supporting document';

-- ------------------------------------------------------------------ periods
CREATE TABLE accountingPeriods (
    id        BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code      VARCHAR(20)     NOT NULL COMMENT 'YYYY-MM',
    startDate DATE            NOT NULL,
    endDate   DATE            NOT NULL,
    status    ENUM('OPEN','CLOSED') NOT NULL DEFAULT 'OPEN',
    closedBy  BIGINT UNSIGNED NULL,
    closedAt  DATETIME        NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uqAccountingPeriodsCode (code),
    CONSTRAINT fkAccountingPeriodsClosedBy FOREIGN KEY (closedBy) REFERENCES users (id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT ckAccountingPeriodsDates CHECK (endDate >= startDate)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'A closed period refuses new postings';

ALTER TABLE cashTransactions
    ADD CONSTRAINT fkCashTransactionsPeriod FOREIGN KEY (accountingPeriodId) REFERENCES accountingPeriods (id)
        ON UPDATE CASCADE ON DELETE SET NULL;

-- ------------------------------------------------------------------ human resources
CREATE TABLE attendance (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    employeeId BIGINT UNSIGNED NOT NULL,
    workDate   DATE            NOT NULL,
    checkIn    TIME            NULL,
    checkOut   TIME            NULL,
    status     ENUM('PRESENT','ABSENT','LEAVE','SICK','HOLIDAY','SITE') NOT NULL DEFAULT 'PRESENT',
    notes      VARCHAR(255)    NOT NULL DEFAULT '',
    createdAt  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uqAttendanceEmployeeDay (employeeId, workDate),
    KEY idxAttendanceDate (workDate, status),
    CONSTRAINT fkAttendanceEmployee FOREIGN KEY (employeeId) REFERENCES employees (id)
        ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Daily attendance sheet';

CREATE TABLE leaveRequests (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    employeeId BIGINT UNSIGNED NOT NULL,
    leaveType  ENUM('ANNUAL','SICK','MATERNITY','UNPAID','EXCEPTIONAL') NOT NULL DEFAULT 'ANNUAL',
    startDate  DATE            NOT NULL,
    endDate    DATE            NOT NULL,
    status     ENUM('REQUESTED','APPROVED','REJECTED','CANCELLED') NOT NULL DEFAULT 'REQUESTED',
    reason     VARCHAR(255)    NOT NULL DEFAULT '',
    approvedBy BIGINT UNSIGNED NULL,
    approvedAt DATETIME        NULL,
    createdAt  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idxLeaveRequestsEmployee (employeeId, startDate),
    CONSTRAINT fkLeaveRequestsEmployee FOREIGN KEY (employeeId) REFERENCES employees (id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fkLeaveRequestsApprover FOREIGN KEY (approvedBy) REFERENCES users (id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT ckLeaveRequestsDates CHECK (endDate >= startDate)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Leave requests and approvals';

CREATE TABLE salaryPayments (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    employeeId  BIGINT UNSIGNED NOT NULL,
    period      VARCHAR(7)      NOT NULL COMMENT 'YYYY-MM',
    baseAmount  DECIMAL(15,2)   NOT NULL DEFAULT 0.00,
    bonus       DECIMAL(15,2)   NOT NULL DEFAULT 0.00,
    deductions  DECIMAL(15,2)   NOT NULL DEFAULT 0.00,
    advance     DECIMAL(15,2)   NOT NULL DEFAULT 0.00,
    netAmount   DECIMAL(15,2)   GENERATED ALWAYS AS (baseAmount + bonus - deductions - advance) STORED,
    paidAt      DATETIME        NULL,
    expenseId   BIGINT UNSIGNED NULL COMMENT 'expense row generated by the payroll run',
    userId      BIGINT UNSIGNED NULL,
    createdAt   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uqSalaryPaymentsEmployeePeriod (employeeId, period),
    KEY idxSalaryPaymentsPeriod (period),
    CONSTRAINT fkSalaryPaymentsEmployee FOREIGN KEY (employeeId) REFERENCES employees (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fkSalaryPaymentsExpense FOREIGN KEY (expenseId) REFERENCES expenses (id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT fkSalaryPaymentsUser FOREIGN KEY (userId) REFERENCES users (id)
        ON UPDATE CASCADE ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Monthly payroll, booked as an expense';
