-- =============================================================================
-- ChemManager ERP - seed 06: company identity, settings and document numbering
-- =============================================================================

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

INSERT INTO companies (name, legalName, activity, address, city, wilaya, postalCode, phone, email,
                       taxId, tradeRegister, nif, taxArticle, currency, vatRate,
                       invoicePrefix, quotationPrefix, deliveryPrefix)
VALUES ('ChemPro Détergents', 'SARL ChemPro Détergents',
        'Fabrication de détergents et distribution de produits chimiques',
        'Zone industrielle, lot 42', 'Sétif', 'Sétif', '19000', '+213 36 00 00 00',
        'contact@chempro.dz', '199036000000000', '1900000000', '000190000000000',
        '1900000000000', 'DZD', 19.0000, 'FA', 'DE', 'BL')
ON DUPLICATE KEY UPDATE name = VALUES(name);

INSERT INTO settings (settingKey, value, valueType, settingGroup, labelKey) VALUES
  ('company.currency', 'DZD', 'STRING', 'general', 'settings.currency'),
  ('company.vatRate', '19', 'DECIMAL', 'general', 'settings.vat'),
  ('company.invoicePrefix', 'FA', 'STRING', 'general', 'settings.invoice_prefix'),
  ('app.defaultLocale', 'fr', 'STRING', 'general', 'settings.language'),
  ('app.defaultTheme', 'LIGHT', 'STRING', 'general', 'settings.theme'),
  ('stock.allowNegative', '0', 'BOOLEAN', 'stock', 'settings.stock_negative'),
  ('stock.fefoEnforced', '1', 'BOOLEAN', 'stock', 'settings.fefo'),
  ('stock.expiryWarningDays', '60', 'INTEGER', 'stock', 'settings.expiry_warning'),
  ('stock.quarantineRequired', '1', 'BOOLEAN', 'stock', 'settings.quarantine'),
  ('security.maxLoginAttempts', '5', 'INTEGER', 'security', 'settings.max_attempts'),
  ('security.lockoutMinutes', '2', 'INTEGER', 'security', 'settings.lockout'),
  ('security.tokenTtlHours', '8', 'INTEGER', 'security', 'settings.token_ttl'),
  ('backup.schedule', '02:00', 'STRING', 'backup', 'settings.backup_schedule'),
  ('backup.retentionDays', '30', 'INTEGER', 'backup', 'settings.backup_retention')
ON DUPLICATE KEY UPDATE value = VALUES(value), settingGroup = VALUES(settingGroup);

INSERT INTO documentSequences (documentType, periodKey, prefix, lastNumber) VALUES
  ('INVOICE', '2026', 'FA', 0),
  ('QUOTATION', '2026', 'DE', 0),
  ('DELIVERY', '2026', 'BL', 0),
  ('SALESORDER', '2026', 'BC', 0),
  ('PURCHASEORDER', '2026', 'CF', 0),
  ('PRODUCTION', '2026', 'OF', 0),
  ('PAYMENT', '2026', 'RG', 0),
  ('EXPENSE', '2026', 'DP', 0),
  ('CREDITNOTE', '2026', 'AV', 0),
  ('STOCKMOVEMENT', '2026', 'MV', 0),
  ('INCIDENT', '2026', 'IN', 0)
ON DUPLICATE KEY UPDATE prefix = VALUES(prefix);

INSERT INTO accountingPeriods (code, startDate, endDate, status) VALUES
  ('2026-01', '2026-01-01', '2026-01-31', 'CLOSED'),
  ('2026-02', '2026-02-01', '2026-02-28', 'CLOSED'),
  ('2026-03', '2026-03-01', '2026-03-31', 'CLOSED'),
  ('2026-04', '2026-04-01', '2026-04-30', 'CLOSED'),
  ('2026-05', '2026-05-01', '2026-05-31', 'CLOSED'),
  ('2026-06', '2026-06-01', '2026-06-30', 'CLOSED'),
  ('2026-07', '2026-07-01', '2026-07-31', 'CLOSED'),
  ('2026-08', '2026-08-01', '2026-08-31', 'CLOSED'),
  ('2026-09', '2026-09-01', '2026-09-30', 'CLOSED'),
  ('2026-10', '2026-10-01', '2026-10-31', 'CLOSED'),
  ('2026-11', '2026-11-01', '2026-11-30', 'OPEN'),
  ('2026-12', '2026-12-01', '2026-12-31', 'OPEN')
ON DUPLICATE KEY UPDATE status = VALUES(status);
