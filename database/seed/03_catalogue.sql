-- =============================================================================
-- ChemManager ERP - seed 03: reference data
-- Product/material categories, departments, warehouses, treasury, expense types.
-- Idempotent: INSERT ... ON DUPLICATE KEY UPDATE keeps a re-run harmless.
-- =============================================================================

SET NAMES utf8mb4;

INSERT INTO departments (code, nameFr, nameAr, nameEn) VALUES
  ('DIRECTION', 'Direction', 'الإدارة', 'Management'),
  ('PRODUCTION', 'Production', 'الإنتاج', 'Production'),
  ('QUALITE', 'Qualité', 'الجودة', 'Quality'),
  ('ENTREPOT', 'Entrepôt', 'المستودع', 'Warehouse'),
  ('COMMERCIAL', 'Commercial', 'التجاري', 'Sales'),
  ('FINANCE', 'Finance', 'المالية', 'Finance'),
  ('LOGISTIQUE', 'Logistique', 'اللوجستيك', 'Logistics')
ON DUPLICATE KEY UPDATE nameFr = VALUES(nameFr), nameAr = VALUES(nameAr);

INSERT INTO productCategories (code, nameFr, nameAr, nameEn, sortOrder) VALUES
  ('DISHWASHING', 'Liquide vaisselle', 'سائل غسل الأواني', 'Dishwashing liquid', 1),
  ('FLOORCLEANER', 'Nettoyant sols', 'منظف الأرضيات', 'Floor cleaner', 2),
  ('BLEACH', 'Eau de Javel', 'ماء جافيل', 'Bleach', 3),
  ('DISINFECTANT', 'Désinfectant', 'مطهّر', 'Disinfectant', 4),
  ('LIQUIDSOAP', 'Savon liquide', 'صابون سائل', 'Liquid soap', 5),
  ('LAUNDRY', 'Lessive', 'منظف الغسيل', 'Laundry detergent', 6),
  ('GLASSCLEANER', 'Nettoyant vitres', 'منظف الزجاج', 'Glass cleaner', 7),
  ('DEGREASER', 'Dégraissant', 'مزيل الشحوم', 'Degreaser', 8),
  ('INDUSTRIAL', 'Produits industriels', 'منتجات صناعية', 'Industrial products', 9),
  ('PACKAGING', 'Emballages', 'تغليف', 'Packaging', 10)
ON DUPLICATE KEY UPDATE nameFr = VALUES(nameFr), nameAr = VALUES(nameAr), sortOrder = VALUES(sortOrder);

INSERT INTO rawMaterialCategories (code, nameFr, nameAr, nameEn, sortOrder) VALUES
  ('CHEMICAL', 'Produits chimiques', 'مواد كيميائية', 'Chemicals', 1),
  ('SURFACTANT', 'Tensioactifs', 'مواد ذات نشاط سطحي', 'Surfactants', 2),
  ('FRAGRANCE', 'Parfums', 'عطور', 'Fragrances', 3),
  ('COLORANT', 'Colorants', 'ملونات', 'Colorants', 4),
  ('ADDITIVE', 'Additifs', 'إضافات', 'Additives', 5),
  ('SOLVENT', 'Solvants', 'مذيبات', 'Solvents', 6),
  ('PACKAGING', 'Emballages', 'تغليف', 'Packaging', 7)
ON DUPLICATE KEY UPDATE nameFr = VALUES(nameFr), nameAr = VALUES(nameAr), sortOrder = VALUES(sortOrder);

INSERT INTO expenseCategories (code, nameFr, nameAr, nameEn, isSalary, isVehicle) VALUES
  ('RENT', 'Loyer', 'الإيجار', 'Rent', 0, 0),
  ('SALARIES', 'Salaires', 'الرواتب', 'Salaries', 1, 0),
  ('ENERGY', 'Énergie et eau', 'الطاقة والماء', 'Energy and water', 0, 0),
  ('FUEL', 'Carburant', 'الوقود', 'Fuel', 0, 1),
  ('MAINTENANCE', 'Maintenance', 'الصيانة', 'Maintenance', 0, 0),
  ('TRANSPORT', 'Transport', 'النقل', 'Transport', 0, 1),
  ('PACKAGING', 'Emballages consommables', 'تغليف مستهلك', 'Consumable packaging', 0, 0),
  ('ADMIN', 'Frais administratifs', 'مصاريف إدارية', 'Administrative costs', 0, 0),
  ('TAXES', 'Taxes et impôts', 'الضرائب والرسوم', 'Taxes', 0, 0),
  ('OTHER', 'Divers', 'متنوع', 'Other', 0, 0)
ON DUPLICATE KEY UPDATE nameFr = VALUES(nameFr), nameAr = VALUES(nameAr);

INSERT INTO warehouses (code, name, address, isQuarantine) VALUES
  ('MP', 'Magasin matières premières', 'Zone industrielle', 0),
  ('PF', 'Magasin produits finis', 'Zone industrielle', 0),
  ('QUAR', 'Zone de quarantaine', 'Zone industrielle', 1)
ON DUPLICATE KEY UPDATE name = VALUES(name), isQuarantine = VALUES(isQuarantine);

INSERT INTO cashAccounts (code, name, type, bankName, openingBalance) VALUES
  ('CAISSE', 'Caisse principale', 'CASH', '', 0.00),
  ('BANQUE', 'Compte bancaire BNA', 'BANK', 'BNA', 0.00)
ON DUPLICATE KEY UPDATE name = VALUES(name), type = VALUES(type);
