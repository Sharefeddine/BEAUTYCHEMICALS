-- =============================================================================
-- ChemManager ERP - seed 02: the 12 roles and their permission matrix
-- Must stay identical to com.chemmanager.security.DefaultRoles (a test checks the count).
-- =============================================================================

SET NAMES utf8mb4;

INSERT INTO roles (code, nameFr, nameAr, nameEn, descriptionFr, descriptionAr, descriptionEn, isSystem) VALUES
  ('SUPER_ADMIN', 'Super administrateur', 'مدير النظام', 'Super administrator', 'Accès total à toutes les fonctions', 'وصول كامل إلى جميع الوظائف', 'Full access to every function', 1),
  ('GENERAL_MANAGER', 'Directeur général', 'المدير العام', 'General manager', 'Pilotage et validations', 'القيادة والمصادقات', 'Steering and approvals', 1),
  ('PRODUCTION_MANAGER', 'Responsable production', 'مسؤول الإنتاج', 'Production manager', 'Formules et ordres de fabrication', 'التركيبات وأوامر التصنيع', 'Formulas and manufacturing orders', 1),
  ('PRODUCTION_OPERATOR', 'Opérateur de production', 'عامل إنتاج', 'Production operator', 'Exécution des ordres', 'تنفيذ أوامر التصنيع', 'Executes orders', 1),
  ('WAREHOUSE_MANAGER', 'Responsable entrepôt', 'مسؤول المستودع', 'Warehouse manager', 'Stocks, réceptions et expéditions', 'المخزون والاستلام والإرسال', 'Stock, receipts and shipments', 1),
  ('WAREHOUSE_EMPLOYEE', 'Magasinier', 'أمين المخزن', 'Warehouse employee', 'Mouvements et transferts', 'الحركات والتحويلات', 'Movements and transfers', 1),
  ('SALES_MANAGER', 'Responsable commercial', 'مسؤول المبيعات', 'Sales manager', 'Ventes, prix et recouvrement', 'المبيعات والأسعار والتحصيل', 'Sales, prices and collection', 1),
  ('SALES_REPRESENTATIVE', 'Représentant', 'مندوب مبيعات', 'Sales representative', 'Ventes terrain et livraisons', 'المبيعات الميدانية والتسليم', 'Field sales and delivery', 1),
  ('ACCOUNTANT', 'Comptable', 'محاسب', 'Accountant', 'Finance, règlements et clôtures', 'المالية والتسديدات والإقفال', 'Finance, payments and closing', 1),
  ('PURCHASING_MANAGER', 'Responsable achats', 'مسؤول المشتريات', 'Purchasing manager', 'Achats et fournisseurs', 'المشتريات والموردون', 'Purchasing and suppliers', 1),
  ('DELIVERY_MANAGER', 'Responsable livraison', 'مسؤول التوصيل', 'Delivery manager', 'Tournées et flotte', 'الجولات والأسطول', 'Routes and fleet', 1),
  ('VIEWER', 'Consultation', 'استعراض', 'Viewer', 'Lecture seule', 'قراءة فقط', 'Read only', 1);

-- role -> permission matrix
INSERT INTO rolePermissions (roleId, permissionId)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.code = 'SUPER_ADMIN';
INSERT INTO rolePermissions (roleId, permissionId)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN ('DASHBOARD_VIEW', 'PRODUCTS_VIEW', 'PRODUCTS_PRICE_EDIT', 'MATERIALS_VIEW', 'FORMULATIONS_VIEW', 'FORMULATIONS_APPROVE', 'PRODUCTION_VIEW', 'PRODUCTION_APPROVE', 'INVENTORY_VIEW', 'PURCHASING_VIEW', 'PURCHASING_APPROVE', 'SUPPLIERS_VIEW', 'SALES_VIEW', 'SALES_DISCOUNT', 'CUSTOMERS_VIEW', 'DISTRIBUTION_VIEW', 'FINANCE_VIEW', 'FINANCE_CLOSE_PERIOD', 'EXPENSES_VIEW', 'EMPLOYEES_VIEW', 'REPORTS_VIEW', 'REPORTS_EXPORT', 'REPORTS_PRINT', 'SAFETY_VIEW', 'SETTINGS_VIEW', 'USERS_VIEW', 'AUDIT_VIEW')
WHERE r.code = 'GENERAL_MANAGER';
INSERT INTO rolePermissions (roleId, permissionId)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN ('DASHBOARD_VIEW', 'PRODUCTS_VIEW', 'PRODUCTS_CREATE', 'PRODUCTS_EDIT', 'MATERIALS_VIEW', 'MATERIALS_EDIT', 'FORMULATIONS_VIEW', 'FORMULATIONS_EDIT', 'FORMULATIONS_APPROVE', 'PRODUCTION_VIEW', 'PRODUCTION_CREATE', 'PRODUCTION_APPROVE', 'INVENTORY_VIEW', 'PURCHASING_VIEW', 'SUPPLIERS_VIEW', 'REPORTS_VIEW', 'REPORTS_EXPORT', 'REPORTS_PRINT', 'SAFETY_VIEW', 'SAFETY_EDIT')
WHERE r.code = 'PRODUCTION_MANAGER';
INSERT INTO rolePermissions (roleId, permissionId)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN ('DASHBOARD_VIEW', 'PRODUCTS_VIEW', 'MATERIALS_VIEW', 'FORMULATIONS_VIEW', 'PRODUCTION_VIEW', 'INVENTORY_VIEW', 'SAFETY_VIEW')
WHERE r.code = 'PRODUCTION_OPERATOR';
INSERT INTO rolePermissions (roleId, permissionId)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN ('DASHBOARD_VIEW', 'PRODUCTS_VIEW', 'MATERIALS_VIEW', 'MATERIALS_EDIT', 'PRODUCTION_VIEW', 'INVENTORY_VIEW', 'INVENTORY_ADJUST', 'INVENTORY_TRANSFER', 'PURCHASING_VIEW', 'SUPPLIERS_VIEW', 'SALES_VIEW', 'DISTRIBUTION_VIEW', 'DISTRIBUTION_MANAGE', 'REPORTS_VIEW', 'REPORTS_EXPORT', 'REPORTS_PRINT', 'SAFETY_VIEW', 'SAFETY_EDIT')
WHERE r.code = 'WAREHOUSE_MANAGER';
INSERT INTO rolePermissions (roleId, permissionId)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN ('DASHBOARD_VIEW', 'PRODUCTS_VIEW', 'MATERIALS_VIEW', 'PRODUCTION_VIEW', 'INVENTORY_VIEW', 'INVENTORY_TRANSFER', 'PURCHASING_VIEW', 'DISTRIBUTION_VIEW', 'SAFETY_VIEW')
WHERE r.code = 'WAREHOUSE_EMPLOYEE';
INSERT INTO rolePermissions (roleId, permissionId)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN ('DASHBOARD_VIEW', 'PRODUCTS_VIEW', 'PRODUCTS_PRICE_EDIT', 'INVENTORY_VIEW', 'SALES_VIEW', 'SALES_CREATE', 'SALES_INVOICE', 'SALES_DISCOUNT', 'CUSTOMERS_VIEW', 'DISTRIBUTION_VIEW', 'EXPENSES_VIEW', 'REPORTS_VIEW', 'REPORTS_EXPORT', 'REPORTS_PRINT')
WHERE r.code = 'SALES_MANAGER';
INSERT INTO rolePermissions (roleId, permissionId)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN ('DASHBOARD_VIEW', 'PRODUCTS_VIEW', 'INVENTORY_VIEW', 'SALES_VIEW', 'SALES_CREATE', 'CUSTOMERS_VIEW', 'DISTRIBUTION_VIEW', 'REPORTS_VIEW', 'REPORTS_PRINT')
WHERE r.code = 'SALES_REPRESENTATIVE';
INSERT INTO rolePermissions (roleId, permissionId)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN ('DASHBOARD_VIEW', 'PRODUCTS_VIEW', 'INVENTORY_VIEW', 'PURCHASING_VIEW', 'SUPPLIERS_VIEW', 'SALES_VIEW', 'SALES_INVOICE', 'CUSTOMERS_VIEW', 'FINANCE_VIEW', 'FINANCE_PAYMENT', 'FINANCE_CLOSE_PERIOD', 'EXPENSES_VIEW', 'EXPENSES_CREATE', 'REPORTS_VIEW', 'REPORTS_EXPORT', 'REPORTS_PRINT')
WHERE r.code = 'ACCOUNTANT';
INSERT INTO rolePermissions (roleId, permissionId)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN ('DASHBOARD_VIEW', 'PRODUCTS_VIEW', 'PRODUCTS_CREATE', 'MATERIALS_VIEW', 'MATERIALS_EDIT', 'INVENTORY_VIEW', 'PURCHASING_VIEW', 'PURCHASING_CREATE', 'PURCHASING_APPROVE', 'SUPPLIERS_VIEW', 'EXPENSES_VIEW', 'REPORTS_VIEW', 'REPORTS_EXPORT', 'REPORTS_PRINT', 'SAFETY_VIEW')
WHERE r.code = 'PURCHASING_MANAGER';
INSERT INTO rolePermissions (roleId, permissionId)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN ('DASHBOARD_VIEW', 'PRODUCTS_VIEW', 'INVENTORY_VIEW', 'CUSTOMERS_VIEW', 'DISTRIBUTION_VIEW', 'DISTRIBUTION_MANAGE', 'EXPENSES_VIEW', 'EXPENSES_CREATE', 'REPORTS_VIEW', 'REPORTS_PRINT')
WHERE r.code = 'DELIVERY_MANAGER';
INSERT INTO rolePermissions (roleId, permissionId)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN ('DASHBOARD_VIEW', 'PRODUCTS_VIEW', 'MATERIALS_VIEW', 'FORMULATIONS_VIEW', 'PRODUCTION_VIEW', 'INVENTORY_VIEW', 'PURCHASING_VIEW', 'SUPPLIERS_VIEW', 'SALES_VIEW', 'CUSTOMERS_VIEW', 'DISTRIBUTION_VIEW', 'FINANCE_VIEW', 'EXPENSES_VIEW', 'EMPLOYEES_VIEW', 'REPORTS_VIEW', 'SAFETY_VIEW')
WHERE r.code = 'VIEWER';
