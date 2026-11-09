-- =============================================================================
-- ChemManager ERP - seed 01: permissions (45)
-- Generated from the Permission enum; regenerate with tools/db/generateSeeds.py
-- Idempotent: re-running the file does not duplicate rows.
-- =============================================================================

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

INSERT INTO permissions (code, module, labelFr, labelAr, labelEn, sortOrder) VALUES
  ('DASHBOARD_VIEW', 'DASHBOARD', 'Tableau de bord', 'لوحة التحكم', 'Dashboard', 0),
  ('PRODUCTS_VIEW', 'PRODUCTS', 'Produits - consulter', 'المنتجات - استعراض', 'Products - view', 1),
  ('PRODUCTS_CREATE', 'PRODUCTS', 'Produits - créer', 'المنتجات - إنشاء', 'Products - create', 2),
  ('PRODUCTS_EDIT', 'PRODUCTS', 'Produits - modifier', 'المنتجات - تعديل', 'Products - edit', 3),
  ('PRODUCTS_DELETE', 'PRODUCTS', 'Produits - supprimer', 'المنتجات - حذف', 'Products - delete', 4),
  ('PRODUCTS_PRICE_EDIT', 'PRODUCTS', 'Produits - modifier les prix', 'المنتجات - تعديل الأسعار', 'Products - edit prices', 5),
  ('MATERIALS_VIEW', 'MATERIALS', 'Matières - consulter', 'المواد - استعراض', 'Materials - view', 6),
  ('MATERIALS_EDIT', 'MATERIALS', 'Matières - modifier', 'المواد - تعديل', 'Materials - edit', 7),
  ('FORMULATIONS_VIEW', 'FORMULATIONS', 'Formules - consulter', 'التركيبات - استعراض', 'Formulas - view', 8),
  ('FORMULATIONS_EDIT', 'FORMULATIONS', 'Formules - modifier', 'التركيبات - تعديل', 'Formulas - edit', 9),
  ('FORMULATIONS_APPROVE', 'FORMULATIONS', 'Formules - approuver', 'التركيبات - مصادقة', 'Formulas - approve', 10),
  ('PRODUCTION_VIEW', 'PRODUCTION', 'Production - consulter', 'الإنتاج - استعراض', 'Production - view', 11),
  ('PRODUCTION_CREATE', 'PRODUCTION', 'Production - créer un ordre', 'الإنتاج - إنشاء أمر', 'Production - create order', 12),
  ('PRODUCTION_APPROVE', 'PRODUCTION', 'Production - approuver un lot', 'الإنتاج - مصادقة دفعة', 'Production - approve batch', 13),
  ('INVENTORY_VIEW', 'INVENTORY', 'Stocks - consulter', 'المخزون - استعراض', 'Inventory - view', 14),
  ('INVENTORY_ADJUST', 'INVENTORY', 'Stocks - ajuster', 'المخزون - تسوية', 'Inventory - adjust', 15),
  ('INVENTORY_TRANSFER', 'INVENTORY', 'Stocks - transférer', 'المخزون - تحويل', 'Inventory - transfer', 16),
  ('PURCHASING_VIEW', 'PURCHASING', 'Achats - consulter', 'المشتريات - استعراض', 'Purchasing - view', 17),
  ('PURCHASING_CREATE', 'PURCHASING', 'Achats - créer', 'المشتريات - إنشاء', 'Purchasing - create', 18),
  ('PURCHASING_APPROVE', 'PURCHASING', 'Achats - approuver', 'المشتريات - مصادقة', 'Purchasing - approve', 19),
  ('SUPPLIERS_VIEW', 'SUPPLIERS', 'Fournisseurs - consulter', 'الموردون - استعراض', 'Suppliers - view', 20),
  ('SALES_VIEW', 'SALES', 'Ventes - consulter', 'المبيعات - استعراض', 'Sales - view', 21),
  ('SALES_CREATE', 'SALES', 'Ventes - créer', 'المبيعات - إنشاء', 'Sales - create', 22),
  ('SALES_INVOICE', 'SALES', 'Ventes - facturer', 'المبيعات - فوترة', 'Sales - invoice', 23),
  ('SALES_DISCOUNT', 'SALES', 'Ventes - remises', 'المبيعات - تخفيضات', 'Sales - discounts', 24),
  ('CUSTOMERS_VIEW', 'CUSTOMERS', 'Clients - consulter', 'العملاء - استعراض', 'Customers - view', 25),
  ('DISTRIBUTION_VIEW', 'DISTRIBUTION', 'Livraison - consulter', 'التوصيل - استعراض', 'Delivery - view', 26),
  ('DISTRIBUTION_MANAGE', 'DISTRIBUTION', 'Livraison - gérer', 'التوصيل - إدارة', 'Delivery - manage', 27),
  ('FINANCE_VIEW', 'FINANCE', 'Finance - consulter', 'المالية - استعراض', 'Finance - view', 28),
  ('FINANCE_PAYMENT', 'FINANCE', 'Finance - encaissements', 'المالية - التحصيل', 'Finance - payments', 29),
  ('FINANCE_CLOSE_PERIOD', 'FINANCE', 'Finance - clôturer une période', 'المالية - إقفال فترة', 'Finance - close period', 30),
  ('EXPENSES_VIEW', 'EXPENSES', 'Dépenses - consulter', 'المصاريف - استعراض', 'Expenses - view', 31),
  ('EXPENSES_CREATE', 'EXPENSES', 'Dépenses - saisir', 'المصاريف - تسجيل', 'Expenses - record', 32),
  ('EMPLOYEES_VIEW', 'EMPLOYEES', 'Employés - consulter', 'الموظفون - استعراض', 'Employees - view', 33),
  ('EMPLOYEES_EDIT', 'EMPLOYEES', 'Employés - modifier', 'الموظفون - تعديل', 'Employees - edit', 34),
  ('REPORTS_VIEW', 'REPORTS', 'Rapports - consulter', 'التقارير - استعراض', 'Reports - view', 35),
  ('REPORTS_EXPORT', 'REPORTS', 'Rapports - exporter', 'التقارير - تصدير', 'Reports - export', 36),
  ('REPORTS_PRINT', 'REPORTS', 'Rapports - imprimer', 'التقارير - طباعة', 'Reports - print', 37),
  ('SAFETY_VIEW', 'SAFETY', 'Sécurité - consulter', 'السلامة - استعراض', 'Safety - view', 38),
  ('SAFETY_EDIT', 'SAFETY', 'Sécurité - modifier', 'السلامة - تعديل', 'Safety - edit', 39),
  ('SETTINGS_VIEW', 'SETTINGS', 'Paramètres - consulter', 'الإعدادات - استعراض', 'Settings - view', 40),
  ('SETTINGS_EDIT', 'SETTINGS', 'Paramètres - modifier', 'الإعدادات - تعديل', 'Settings - edit', 41),
  ('USERS_VIEW', 'USERS', 'Utilisateurs - consulter', 'المستخدمون - استعراض', 'Users - view', 42),
  ('USERS_MANAGE', 'USERS', 'Utilisateurs - gérer', 'المستخدمون - إدارة', 'Users - manage', 43),
  ('AUDIT_VIEW', 'AUDIT', 'Journal d''audit', 'سجل التدقيق', 'Audit log', 44);

-- the same table is the reference for the API route guards
