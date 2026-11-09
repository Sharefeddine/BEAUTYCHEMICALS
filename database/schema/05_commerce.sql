-- =============================================================================
-- ChemManager ERP - MySQL 8 schema
-- 05 - purchasing, sales, distribution
--
-- Documents follow a one-way status flow. An issued invoice is final: corrections are
-- credit notes, never UPDATEs on the line amounts.
-- =============================================================================

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ------------------------------------------------------------------ purchasing
CREATE TABLE purchaseRequests (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    requestNumber VARCHAR(30)     NOT NULL,
    requestedBy   BIGINT UNSIGNED NOT NULL,
    neededBy      DATE            NULL,
    status        ENUM('DRAFT','SUBMITTED','APPROVED','REJECTED','ORDERED','CANCELLED') NOT NULL DEFAULT 'DRAFT',
    reason        VARCHAR(255)    NOT NULL DEFAULT '',
    createdAt     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updatedAt     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    approvedBy    BIGINT UNSIGNED NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uqPurchaseRequestsNumber (requestNumber),
    KEY idxPurchaseRequestsStatus (status),
    CONSTRAINT fkPurchaseRequestsUser FOREIGN KEY (requestedBy) REFERENCES users (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fkPurchaseRequestsApprover FOREIGN KEY (approvedBy) REFERENCES users (id)
        ON UPDATE CASCADE ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Internal purchase needs';

CREATE TABLE purchaseRequestItems (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    requestId         BIGINT UNSIGNED NOT NULL,
    materialId        BIGINT UNSIGNED NOT NULL,
    quantity          DECIMAL(15,3)   NOT NULL,
    unit              VARCHAR(12)     NOT NULL DEFAULT 'KG',
    estimatedUnitPrice DECIMAL(15,2)  NOT NULL DEFAULT 0.00,
    PRIMARY KEY (id),
    KEY idxPurchaseRequestItemsRequest (requestId),
    CONSTRAINT fkPurchaseRequestItemsRequest FOREIGN KEY (requestId) REFERENCES purchaseRequests (id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fkPurchaseRequestItemsMaterial FOREIGN KEY (materialId) REFERENCES rawMaterials (id)
        ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Lines of a purchase request';

CREATE TABLE purchaseOrders (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    orderNumber  VARCHAR(30)     NOT NULL,
    supplierId   BIGINT UNSIGNED NOT NULL,
    requestId    BIGINT UNSIGNED NULL,
    status       ENUM('DRAFT','SENT','PARTIAL','RECEIVED','CANCELLED') NOT NULL DEFAULT 'DRAFT',
    orderDate    DATE            NOT NULL,
    expectedDate DATE            NULL,
    amountHt     DECIMAL(15,2)   NOT NULL DEFAULT 0.00,
    vatAmount    DECIMAL(15,2)   NOT NULL DEFAULT 0.00,
    totalTtc     DECIMAL(15,2)   NOT NULL DEFAULT 0.00,
    currency     CHAR(3)         NOT NULL DEFAULT 'DZD',
    notes        VARCHAR(255)    NOT NULL DEFAULT '',
    createdAt    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updatedAt    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    createdBy    BIGINT UNSIGNED NULL,
    updatedBy    BIGINT UNSIGNED NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uqPurchaseOrdersNumber (orderNumber),
    KEY idxPurchaseOrdersSupplier (supplierId, orderDate),
    KEY idxPurchaseOrdersStatus (status),
    CONSTRAINT fkPurchaseOrdersSupplier FOREIGN KEY (supplierId) REFERENCES suppliers (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fkPurchaseOrdersRequest FOREIGN KEY (requestId) REFERENCES purchaseRequests (id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT ckPurchaseOrdersTotals CHECK (amountHt >= 0 AND vatAmount >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Supplier orders';

CREATE TABLE purchaseOrderItems (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    orderId      BIGINT UNSIGNED NOT NULL,
    materialId   BIGINT UNSIGNED NOT NULL,
    quantity     DECIMAL(15,3)   NOT NULL,
    unit         VARCHAR(12)     NOT NULL DEFAULT 'KG',
    unitPrice    DECIMAL(15,2)   NOT NULL DEFAULT 0.00,
    vatRate      DECIMAL(7,4)    NOT NULL DEFAULT 19.0000,
    receivedQty  DECIMAL(15,3)   NOT NULL DEFAULT 0.000,
    PRIMARY KEY (id),
    KEY idxPurchaseOrderItemsOrder (orderId),
    KEY idxPurchaseOrderItemsMaterial (materialId),
    CONSTRAINT fkPurchaseOrderItemsOrder FOREIGN KEY (orderId) REFERENCES purchaseOrders (id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fkPurchaseOrderItemsMaterial FOREIGN KEY (materialId) REFERENCES rawMaterials (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT ckPurchaseOrderItemsQuantity CHECK (quantity > 0 AND receivedQty >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Ordered lines, partial receipts update receivedQty';

CREATE TABLE goodsReceipts (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    receiptNumber VARCHAR(30)   NOT NULL,
    orderId     BIGINT UNSIGNED NOT NULL,
    warehouseId BIGINT UNSIGNED NOT NULL,
    receivedAt  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    receivedBy  BIGINT UNSIGNED NOT NULL,
    notes       VARCHAR(255)    NOT NULL DEFAULT '',
    PRIMARY KEY (id),
    UNIQUE KEY uqGoodsReceiptsNumber (receiptNumber),
    KEY idxGoodsReceiptsOrder (orderId),
    CONSTRAINT fkGoodsReceiptsOrder FOREIGN KEY (orderId) REFERENCES purchaseOrders (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fkGoodsReceiptsWarehouse FOREIGN KEY (warehouseId) REFERENCES warehouses (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fkGoodsReceiptsUser FOREIGN KEY (receivedBy) REFERENCES users (id)
        ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Goods reception header - creates material batches';

CREATE TABLE goodsReceiptItems (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    receiptId       BIGINT UNSIGNED NOT NULL,
    materialId      BIGINT UNSIGNED NOT NULL,
    quantity        DECIMAL(15,3)   NOT NULL,
    unitPrice       DECIMAL(15,2)   NOT NULL DEFAULT 0.00,
    batchNumber     VARCHAR(40)     NOT NULL,
    expiryDate      DATE            NULL,
    materialBatchId BIGINT UNSIGNED NULL COMMENT 'filled once the batch row exists',
    PRIMARY KEY (id),
    KEY idxGoodsReceiptItemsReceipt (receiptId),
    KEY idxGoodsReceiptItemsMaterial (materialId),
    CONSTRAINT fkGoodsReceiptItemsReceipt FOREIGN KEY (receiptId) REFERENCES goodsReceipts (id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fkGoodsReceiptItemsMaterial FOREIGN KEY (materialId) REFERENCES rawMaterials (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fkGoodsReceiptItemsBatch FOREIGN KEY (materialBatchId) REFERENCES materialBatches (id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT ckGoodsReceiptItemsQuantity CHECK (quantity > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Received lines with their batch and expiry';

CREATE TABLE supplierInvoices (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    invoiceNumber VARCHAR(40)   NOT NULL COMMENT 'supplier reference',
    supplierId   BIGINT UNSIGNED NOT NULL,
    orderId      BIGINT UNSIGNED NULL,
    invoiceDate  DATE            NOT NULL,
    dueDate      DATE            NULL,
    amountHt     DECIMAL(15,2)   NOT NULL DEFAULT 0.00,
    vatAmount    DECIMAL(15,2)   NOT NULL DEFAULT 0.00,
    totalTtc     DECIMAL(15,2)   NOT NULL DEFAULT 0.00,
    paidAmount   DECIMAL(15,2)   NOT NULL DEFAULT 0.00,
    status       ENUM('RECEIVED','PARTIAL','PAID','DISPUTED','CANCELLED') NOT NULL DEFAULT 'RECEIVED',
    documentPath VARCHAR(255)    NULL,
    createdAt    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updatedAt    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uqSupplierInvoicesNumber (supplierId, invoiceNumber),
    KEY idxSupplierInvoicesDates (invoiceDate, dueDate),
    KEY idxSupplierInvoicesStatus (status),
    CONSTRAINT fkSupplierInvoicesSupplier FOREIGN KEY (supplierId) REFERENCES suppliers (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fkSupplierInvoicesOrder FOREIGN KEY (orderId) REFERENCES purchaseOrders (id)
        ON UPDATE CASCADE ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Supplier invoices with payment state';

CREATE TABLE supplierPayments (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    paymentNumber  VARCHAR(30)     NOT NULL,
    supplierInvoiceId BIGINT UNSIGNED NOT NULL,
    cashAccountId  BIGINT UNSIGNED NOT NULL,
    amount         DECIMAL(15,2)   NOT NULL,
    paidAt         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    method         ENUM('CASH','CHEQUE','TRANSFER','CARD') NOT NULL DEFAULT 'CASH',
    reference      VARCHAR(60)     NOT NULL DEFAULT '',
    userId         BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uqSupplierPaymentsNumber (paymentNumber),
    KEY idxSupplierPaymentsInvoice (supplierInvoiceId),
    CONSTRAINT fkSupplierPaymentsInvoice FOREIGN KEY (supplierInvoiceId) REFERENCES supplierInvoices (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fkSupplierPaymentsCash FOREIGN KEY (cashAccountId) REFERENCES cashAccounts (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fkSupplierPaymentsUser FOREIGN KEY (userId) REFERENCES users (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT ckSupplierPaymentsAmount CHECK (amount > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Payments to suppliers, mirrored in cashTransactions';

-- ------------------------------------------------------------------ sales
CREATE TABLE quotations (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    quoteNumber VARCHAR(30)     NOT NULL,
    customerId  BIGINT UNSIGNED NOT NULL,
    status      ENUM('DRAFT','SENT','ACCEPTED','EXPIRED','REJECTED') NOT NULL DEFAULT 'DRAFT',
    quoteDate   DATE            NOT NULL,
    validUntil  DATE            NULL,
    amountHt    DECIMAL(15,2)   NOT NULL DEFAULT 0.00,
    vatAmount   DECIMAL(15,2)   NOT NULL DEFAULT 0.00,
    totalTtc    DECIMAL(15,2)   NOT NULL DEFAULT 0.00,
    representativeId BIGINT UNSIGNED NULL,
    notes       VARCHAR(255)    NOT NULL DEFAULT '',
    createdAt   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updatedAt   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    createdBy   BIGINT UNSIGNED NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uqQuotationsNumber (quoteNumber),
    KEY idxQuotationsCustomer (customerId, quoteDate),
    CONSTRAINT fkQuotationsCustomer FOREIGN KEY (customerId) REFERENCES customers (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fkQuotationsRepresentative FOREIGN KEY (representativeId) REFERENCES users (id)
        ON UPDATE CASCADE ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Customer quotations';

CREATE TABLE quotationItems (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    quotationId BIGINT UNSIGNED NOT NULL,
    productId   BIGINT UNSIGNED NOT NULL,
    quantity    DECIMAL(15,3)   NOT NULL,
    unitPrice   DECIMAL(15,2)   NOT NULL DEFAULT 0.00,
    discountPct DECIMAL(7,4)    NOT NULL DEFAULT 0.0000,
    vatRate     DECIMAL(7,4)    NOT NULL DEFAULT 19.0000,
    PRIMARY KEY (id),
    KEY idxQuotationItemsQuotation (quotationId),
    CONSTRAINT fkQuotationItemsQuotation FOREIGN KEY (quotationId) REFERENCES quotations (id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fkQuotationItemsProduct FOREIGN KEY (productId) REFERENCES products (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT ckQuotationItemsQuantity CHECK (quantity > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Quotation lines';

CREATE TABLE salesOrders (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    orderNumber     VARCHAR(30)     NOT NULL,
    customerId      BIGINT UNSIGNED NOT NULL,
    quotationId     BIGINT UNSIGNED NULL,
    status          ENUM('DRAFT','CONFIRMED','RESERVED','PARTIAL','INVOICED','CANCELLED') NOT NULL DEFAULT 'DRAFT',
    orderDate       DATE            NOT NULL,
    deliveryDate    DATE            NULL,
    amountHt        DECIMAL(15,2)   NOT NULL DEFAULT 0.00,
    vatAmount       DECIMAL(15,2)   NOT NULL DEFAULT 0.00,
    totalTtc        DECIMAL(15,2)   NOT NULL DEFAULT 0.00,
    representativeId BIGINT UNSIGNED NULL,
    notes           VARCHAR(255)    NOT NULL DEFAULT '',
    createdAt       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updatedAt       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    createdBy       BIGINT UNSIGNED NULL,
    updatedBy       BIGINT UNSIGNED NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uqSalesOrdersNumber (orderNumber),
    KEY idxSalesOrdersCustomer (customerId, orderDate),
    KEY idxSalesOrdersStatus (status),
    CONSTRAINT fkSalesOrdersCustomer FOREIGN KEY (customerId) REFERENCES customers (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fkSalesOrdersQuotation FOREIGN KEY (quotationId) REFERENCES quotations (id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT fkSalesOrdersRepresentative FOREIGN KEY (representativeId) REFERENCES users (id)
        ON UPDATE CASCADE ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Customer orders';

CREATE TABLE salesOrderItems (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    orderId     BIGINT UNSIGNED NOT NULL,
    productId   BIGINT UNSIGNED NOT NULL,
    quantity    DECIMAL(15,3)   NOT NULL,
    unitPrice   DECIMAL(15,2)   NOT NULL DEFAULT 0.00,
    discountPct DECIMAL(7,4)    NOT NULL DEFAULT 0.0000,
    vatRate     DECIMAL(7,4)    NOT NULL DEFAULT 19.0000,
    deliveredQty DECIMAL(15,3)  NOT NULL DEFAULT 0.000,
    invoicedQty DECIMAL(15,3)   NOT NULL DEFAULT 0.000,
    PRIMARY KEY (id),
    KEY idxSalesOrderItemsOrder (orderId),
    KEY idxSalesOrderItemsProduct (productId),
    CONSTRAINT fkSalesOrderItemsOrder FOREIGN KEY (orderId) REFERENCES salesOrders (id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fkSalesOrderItemsProduct FOREIGN KEY (productId) REFERENCES products (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT ckSalesOrderItemsQuantity CHECK (quantity > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Order lines with delivered and invoiced progress';

CREATE TABLE deliveryNotes (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    noteNumber  VARCHAR(30)     NOT NULL,
    orderId     BIGINT UNSIGNED NOT NULL,
    customerId  BIGINT UNSIGNED NOT NULL,
    routeId     BIGINT UNSIGNED NULL,
    status      ENUM('PREPARED','LOADED','IN_TRANSIT','DELIVERED','RETURNED') NOT NULL DEFAULT 'PREPARED',
    preparedAt  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deliveredAt DATETIME        NULL,
    signedBy    VARCHAR(120)    NOT NULL DEFAULT '',
    signaturePath VARCHAR(255)  NULL,
    notes       VARCHAR(255)    NOT NULL DEFAULT '',
    PRIMARY KEY (id),
    UNIQUE KEY uqDeliveryNotesNumber (noteNumber),
    KEY idxDeliveryNotesOrder (orderId),
    KEY idxDeliveryNotesRoute (routeId),
    KEY idxDeliveryNotesStatus (status),
    CONSTRAINT fkDeliveryNotesOrder FOREIGN KEY (orderId) REFERENCES salesOrders (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fkDeliveryNotesCustomer FOREIGN KEY (customerId) REFERENCES customers (id)
        ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Delivery notes - each line consumes a FEFO batch';

CREATE TABLE deliveryItems (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    deliveryNoteId BIGINT UNSIGNED NOT NULL,
    productId      BIGINT UNSIGNED NOT NULL,
    productBatchId BIGINT UNSIGNED NOT NULL,
    quantity       DECIMAL(15,3)   NOT NULL,
    PRIMARY KEY (id),
    KEY idxDeliveryItemsNote (deliveryNoteId),
    KEY idxDeliveryItemsBatch (productBatchId),
    CONSTRAINT fkDeliveryItemsNote FOREIGN KEY (deliveryNoteId) REFERENCES deliveryNotes (id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fkDeliveryItemsProduct FOREIGN KEY (productId) REFERENCES products (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fkDeliveryItemsBatch FOREIGN KEY (productBatchId) REFERENCES productBatches (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT ckDeliveryItemsQuantity CHECK (quantity > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Exact batches delivered to a customer';

CREATE TABLE invoices (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    invoiceNumber VARCHAR(30)   NOT NULL,
    customerId  BIGINT UNSIGNED NOT NULL,
    orderId     BIGINT UNSIGNED NULL,
    invoiceDate DATE            NOT NULL,
    dueDate     DATE            NULL,
    amountHt    DECIMAL(15,2)   NOT NULL DEFAULT 0.00,
    vatAmount   DECIMAL(15,2)   NOT NULL DEFAULT 0.00,
    totalTtc    DECIMAL(15,2)   NOT NULL DEFAULT 0.00,
    paidAmount  DECIMAL(15,2)   NOT NULL DEFAULT 0.00,
    status      ENUM('DRAFT','ISSUED','PARTIAL','PAID','CANCELLED') NOT NULL DEFAULT 'DRAFT',
    currency    CHAR(3)         NOT NULL DEFAULT 'DZD',
    finalizedAt DATETIME        NULL COMMENT 'set on issue: the invoice becomes immutable',
    printedCount INT UNSIGNED   NOT NULL DEFAULT 0,
    notes       VARCHAR(255)    NOT NULL DEFAULT '',
    createdAt   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updatedAt   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    createdBy   BIGINT UNSIGNED NULL,
    updatedBy   BIGINT UNSIGNED NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uqInvoicesNumber (invoiceNumber),
    KEY idxInvoicesCustomer (customerId, invoiceDate),
    KEY idxInvoicesStatus (status, dueDate),
    CONSTRAINT fkInvoicesCustomer FOREIGN KEY (customerId) REFERENCES customers (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fkInvoicesOrder FOREIGN KEY (orderId) REFERENCES salesOrders (id)
        ON UPDATE CASCADE ON DELETE SET NULL  ,
    CONSTRAINT ckInvoicesTotals CHECK (amountHt >= 0 AND paidAmount >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Sales invoices - issued invoices are corrected by credit notes only';

CREATE TABLE invoiceItems (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    invoiceId     BIGINT UNSIGNED NOT NULL,
    productId     BIGINT UNSIGNED NOT NULL,
    productBatchId BIGINT UNSIGNED NULL,
    quantity      DECIMAL(15,3)   NOT NULL,
    unitPrice     DECIMAL(15,2)   NOT NULL DEFAULT 0.00,
    discountPct   DECIMAL(7,4)    NOT NULL DEFAULT 0.0000,
    vatRate       DECIMAL(7,4)    NOT NULL DEFAULT 19.0000,
    lineTotal     DECIMAL(15,2)   NOT NULL DEFAULT 0.00,
    PRIMARY KEY (id),
    KEY idxInvoiceItemsInvoice (invoiceId),
    KEY idxInvoiceItemsProduct (productId),
    KEY idxInvoiceItemsBatch (productBatchId),
    CONSTRAINT fkInvoiceItemsInvoice FOREIGN KEY (invoiceId) REFERENCES invoices (id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fkInvoiceItemsProduct FOREIGN KEY (productId) REFERENCES products (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fkInvoiceItemsBatch FOREIGN KEY (productBatchId) REFERENCES productBatches (id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT ckInvoiceItemsQuantity CHECK (quantity > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Invoice lines - the batch reference keeps the traceability chain';

CREATE TABLE creditNotes (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    creditNumber VARCHAR(30)     NOT NULL,
    invoiceId    BIGINT UNSIGNED NOT NULL,
    customerId   BIGINT UNSIGNED NOT NULL,
    creditDate   DATE            NOT NULL,
    reason       VARCHAR(255)    NOT NULL,
    amountHt     DECIMAL(15,2)   NOT NULL DEFAULT 0.00,
    vatAmount    DECIMAL(15,2)   NOT NULL DEFAULT 0.00,
    totalTtc     DECIMAL(15,2)   NOT NULL DEFAULT 0.00,
    status       ENUM('DRAFT','ISSUED','APPLIED','CANCELLED') NOT NULL DEFAULT 'DRAFT',
    createdAt    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    createdBy    BIGINT UNSIGNED NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uqCreditNotesNumber (creditNumber),
    KEY idxCreditNotesInvoice (invoiceId),
    CONSTRAINT fkCreditNotesInvoice FOREIGN KEY (invoiceId) REFERENCES invoices (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fkCreditNotesCustomer FOREIGN KEY (customerId) REFERENCES customers (id)
        ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'The only way to correct an issued invoice';

CREATE TABLE creditNoteItems (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    creditNoteId BIGINT UNSIGNED NOT NULL,
    productId    BIGINT UNSIGNED NOT NULL,
    quantity     DECIMAL(15,3)   NOT NULL,
    unitPrice    DECIMAL(15,2)   NOT NULL DEFAULT 0.00,
    vatRate      DECIMAL(7,4)    NOT NULL DEFAULT 19.0000,
    lineTotal    DECIMAL(15,2)   NOT NULL DEFAULT 0.00,
    PRIMARY KEY (id),
    KEY idxCreditNoteItemsNote (creditNoteId),
    CONSTRAINT fkCreditNoteItemsNote FOREIGN KEY (creditNoteId) REFERENCES creditNotes (id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fkCreditNoteItemsProduct FOREIGN KEY (productId) REFERENCES products (id)
        ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Credited lines';

CREATE TABLE payments (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    paymentNumber VARCHAR(30)   NOT NULL,
    customerId   BIGINT UNSIGNED NOT NULL,
    invoiceId    BIGINT UNSIGNED NULL,
    cashAccountId BIGINT UNSIGNED NOT NULL,
    amount       DECIMAL(15,2)   NOT NULL,
    paidAt       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    method       ENUM('CASH','CHEQUE','TRANSFER','CARD','CREDIT') NOT NULL DEFAULT 'CASH',
    reference    VARCHAR(60)     NOT NULL DEFAULT '',
    userId       BIGINT UNSIGNED NOT NULL,
    notes        VARCHAR(255)    NOT NULL DEFAULT '',
    PRIMARY KEY (id),
    UNIQUE KEY uqPaymentsNumber (paymentNumber),
    KEY idxPaymentsCustomer (customerId, paidAt),
    KEY idxPaymentsInvoice (invoiceId),
    CONSTRAINT fkPaymentsCustomer FOREIGN KEY (customerId) REFERENCES customers (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fkPaymentsInvoice FOREIGN KEY (invoiceId) REFERENCES invoices (id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT fkPaymentsCash FOREIGN KEY (cashAccountId) REFERENCES cashAccounts (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fkPaymentsUser FOREIGN KEY (userId) REFERENCES users (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT ckPaymentsAmount CHECK (amount > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Customer receipts, including on-account payments';

-- ------------------------------------------------------------------ distribution
CREATE TABLE vehicles (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    plate      VARCHAR(20)     NOT NULL,
    model      VARCHAR(60)     NOT NULL DEFAULT '',
    capacityKg DECIMAL(10,2)   NOT NULL DEFAULT 0.00,
    driverId   BIGINT UNSIGNED NULL,
    isActive   TINYINT(1)      NOT NULL DEFAULT 1,
    createdAt  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updatedAt  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uqVehiclesPlate (plate),
    CONSTRAINT fkVehiclesDriver FOREIGN KEY (driverId) REFERENCES employees (id)
        ON UPDATE CASCADE ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Delivery fleet';

CREATE TABLE deliveryRoutes (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    routeNumber     VARCHAR(30)     NOT NULL,
    routeDate       DATE            NOT NULL,
    vehicleId       BIGINT UNSIGNED NOT NULL,
    driverId        BIGINT UNSIGNED NULL,
    representativeId BIGINT UNSIGNED NULL,
    wilayaZone      VARCHAR(120)    NOT NULL DEFAULT '',
    status          ENUM('PLANNED','LOADING','IN_TRANSIT','COMPLETED','CANCELLED') NOT NULL DEFAULT 'PLANNED',
    fuelCost        DECIMAL(15,2)   NOT NULL DEFAULT 0.00,
    openedAt        DATETIME        NULL,
    closedAt        DATETIME        NULL,
    createdAt       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updatedAt       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uqDeliveryRoutesNumber (routeNumber),
    KEY idxDeliveryRoutesDate (routeDate, status),
    CONSTRAINT fkDeliveryRoutesVehicle FOREIGN KEY (vehicleId) REFERENCES vehicles (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fkDeliveryRoutesDriver FOREIGN KEY (driverId) REFERENCES employees (id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT fkDeliveryRoutesRepresentative FOREIGN KEY (representativeId) REFERENCES users (id)
        ON UPDATE CASCADE ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Daily tour';

CREATE TABLE routeStops (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    routeId        BIGINT UNSIGNED NOT NULL,
    deliveryNoteId BIGINT UNSIGNED NOT NULL,
    sequence       SMALLINT UNSIGNED NOT NULL,
    arrivedAt      DATETIME        NULL,
    status         ENUM('PENDING','DELIVERED','PARTIAL','REFUSED') NOT NULL DEFAULT 'PENDING',
    remarks        VARCHAR(255)    NOT NULL DEFAULT '',
    PRIMARY KEY (id),
    UNIQUE KEY uqRouteStopsSequence (routeId, sequence),
    KEY idxRouteStopsNote (deliveryNoteId),
    CONSTRAINT fkRouteStopsRoute FOREIGN KEY (routeId) REFERENCES deliveryRoutes (id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fkRouteStopsNote FOREIGN KEY (deliveryNoteId) REFERENCES deliveryNotes (id)
        ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Ordered stops of a tour';

ALTER TABLE deliveryNotes
    ADD CONSTRAINT fkDeliveryNotesRoute FOREIGN KEY (routeId) REFERENCES deliveryRoutes (id)
        ON UPDATE CASCADE ON DELETE SET NULL;

-- Issued invoices are immutable (amounts and lines). Status/paidAmount may still change.
DELIMITER $$

CREATE TRIGGER trgInvoicesFinalized
BEFORE UPDATE ON invoices
FOR EACH ROW
BEGIN
    IF OLD.finalizedAt IS NOT NULL AND (
           NEW.amountHt <> OLD.amountHt
        OR NEW.vatAmount <> OLD.vatAmount
        OR NEW.totalTtc <> OLD.totalTtc
        OR NEW.customerId <> OLD.customerId
        OR NEW.invoiceNumber <> OLD.invoiceNumber) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'invoices.finalized: an issued invoice cannot be modified, use a credit note';
    END IF;
END$$

CREATE TRIGGER trgInvoiceItemsImmutable
BEFORE UPDATE ON invoiceItems
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM invoices i WHERE i.id = OLD.invoiceId AND i.finalizedAt IS NOT NULL) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'invoices.finalized: invoice lines cannot be modified, use a credit note';
    END IF;
END$$

DELIMITER ;
