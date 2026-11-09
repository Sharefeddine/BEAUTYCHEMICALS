-- =============================================================================
-- ChemManager ERP - seed 03: reference data
-- Product/material categories, departments, warehouses, treasury, expense types.
-- Idempotent: INSERT ... ON DUPLICATE KEY UPDATE keeps a re-run harmless.
-- =============================================================================

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

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

INSERT INTO suppliers (code, name, contactName, phone, email, address, wilaya, paymentTerms, isActive) VALUES
  ('FR-0001', 'Sotrachem Import', 'Karim Belkacem', '+213 36 55 12 08', 'contact@sotrachem.dz',
   'Zone industrielle, Lot 12', 'Sétif', '30 jours', 1),
  ('FR-0002', 'Chimie Méditerranée', 'Nadia Cherif', '+213 21 44 90 15', 'achats@chimie-med.dz',
   'Route nationale 5, km 8', 'Alger', '60 jours', 1),
  ('FR-0003', 'Emballage Maghreb', 'Samir Haddad', '+213 33 27 60 44', 'commercial@emb-maghreb.dz',
   'Zone artisanale', 'Béjaïa', 'Comptant', 1)
ON DUPLICATE KEY UPDATE name = VALUES(name), phone = VALUES(phone), paymentTerms = VALUES(paymentTerms);

-- Raw materials of a detergent/cosmetics plant. They exist so the formulation module can be
-- populated and demonstrated from a clean install; purchasing adds the real receptions later.
INSERT INTO rawMaterials
    (code, commercialName, chemicalName, categoryId, purchaseUnit, storageUnit, conversionFactor,
     purchasePrice, minStock, storageLocation, hazardClass, hazardPictograms, storageConditions,
     sdsAvailable, isActive)
SELECT v.code, v.commercialName, v.chemicalName, c.id, v.purchaseUnit, v.storageUnit,
       v.conversionFactor, v.purchasePrice, v.minStock, v.storageLocation, v.hazardClass,
       v.hazardPictograms, v.storageConditions, 1, 1
FROM (
    SELECT 'MP-0001' AS code, 'Tensioactif anionique LABSA 96%' AS commercialName,
           'Alkylbenzènesulfonate linéaire' AS chemicalName, 'SURFACTANT' AS categoryCode,
           'KG' AS purchaseUnit, 'KG' AS storageUnit, 1.0000 AS conversionFactor, 245.00 AS purchasePrice,
           500.000 AS minStock, 'A-01' AS storageLocation, 'CORROSIVE' AS hazardClass,
           'GHS05' AS hazardPictograms, 'Fût fermé, à l''abri de l''humidité' AS storageConditions
    UNION ALL SELECT 'MP-0002', 'Tensioactif non ionique Alcacare 7EO', 'Alcool gras éthoxylé',
           'SURFACTANT', 'KG', 'KG', 1.0000, 310.00, 300.000, 'A-02', 'IRRITANT', 'GHS07',
           'Récipient fermé, température < 40 °C'
    UNION ALL SELECT 'MP-0003', 'Soude caustique perles 99%', 'Hydroxyde de sodium',
           'CHEMICAL', 'KG', 'KG', 1.0000, 180.00, 200.000, 'B-01', 'CORROSIVE', 'GHS05',
           'Absorber toute fuite, local ventilé'
    UNION ALL SELECT 'MP-0004', 'Hypochlorite de sodium 12%', 'Hypochlorite de sodium',
           'CHEMICAL', 'L', 'L', 1.0000, 95.00, 400.000, 'B-02', 'CORROSIVE', 'GHS05,GHS09',
           'À l''abri de la lumière et de la chaleur'
    UNION ALL SELECT 'MP-0005', 'Parfum citron concentré', 'Mélange de substances odorantes',
           'FRAGRANCE', 'KG', 'KG', 1.0000, 1850.00, 25.000, 'C-01', 'IRRITANT', 'GHS07',
           'Récipient d''origine, local frais'
    UNION ALL SELECT 'MP-0006', 'Parfum lavande', 'Mélange de substances odorantes',
           'FRAGRANCE', 'KG', 'KG', 1.0000, 1920.00, 25.000, 'C-02', 'IRRITANT', 'GHS07',
           'Récipient d''origine, local frais'
    UNION ALL SELECT 'MP-0007', 'Colorant bleu CI 42090', 'Bleu brillant FCF',
           'COLORANT', 'KG', 'KG', 1.0000, 4200.00, 5.000, 'C-03', '', '',
           'Bidon opaque, à l''abri de l''humidité'
    UNION ALL SELECT 'MP-0008', 'Épaississant gomme xanthane', 'Polysaccharide xanthane',
           'ADDITIVE', 'KG', 'KG', 1.0000, 980.00, 50.000, 'D-01', '', '',
           'Sac fermé, local sec'
    UNION ALL SELECT 'MP-0009', 'Conservateur phenoxyethanol', 'Phénoxyéthanol',
           'ADDITIVE', 'KG', 'KG', 1.0000, 1350.00, 30.000, 'D-02', 'HARMFUL', 'GHS07',
           'Bidon fermé, local ventilé'
    UNION ALL SELECT 'MP-0010', 'Alcool éthylique 96%', 'Éthanol',
           'SOLVENT', 'L', 'L', 1.0000, 320.00, 200.000, 'E-01', 'FLAMMABLE', 'GHS02',
           'Local ventilé, loin des sources d''ignition'
    UNION ALL SELECT 'MP-0011', 'Eau déminéralisée', 'Eau purifiée',
           'CHEMICAL', 'L', 'L', 1.0000, 12.00, 1000.000, 'E-02', '', '',
           'Cuve alimentaire, à l''abri du soleil'
    UNION ALL SELECT 'MP-0012', 'Flacon PET 1 L blanc', 'Emballage',
           'PACKAGING', 'PCE', 'PCE', 0.0450, 18.50, 5000.000, 'F-01', '', '',
           'Palette filmée, stockage à plat'
) AS v
JOIN rawMaterialCategories c ON c.code = v.categoryCode
ON DUPLICATE KEY UPDATE commercialName = VALUES(commercialName),
    chemicalName = VALUES(chemicalName), purchasePrice = VALUES(purchasePrice),
    minStock = VALUES(minStock), hazardClass = VALUES(hazardClass);

INSERT INTO warehouses (code, name, address, isQuarantine) VALUES
  ('MP', 'Magasin matières premières', 'Zone industrielle', 0),
  ('PF', 'Magasin produits finis', 'Zone industrielle', 0),
  ('QUAR', 'Zone de quarantaine', 'Zone industrielle', 1)
ON DUPLICATE KEY UPDATE name = VALUES(name), isQuarantine = VALUES(isQuarantine);

INSERT INTO cashAccounts (code, name, type, bankName, openingBalance) VALUES
  ('CAISSE', 'Caisse principale', 'CASH', '', 0.00),
  ('BANQUE', 'Compte bancaire BNA', 'BANK', 'BNA', 0.00)
ON DUPLICATE KEY UPDATE name = VALUES(name), type = VALUES(type);

-- Finished goods catalogue: the manufactured lines the formulation module links to.
INSERT INTO products
    (sku, barcode, nameFr, nameAr, description, categoryId, type, unit, packaging, packagingCapacity,
     capacityUnit, unitsPerCarton, minStock, maxStock, purchaseCost, productionCost, wholesalePrice,
     retailPrice, specialPrice, vatRate, isActive)
SELECT v.sku, v.barcode, v.nameFr, v.nameAr, v.description, c.id, 'MANUFACTURED', 'PCE',
       v.packaging, v.packagingCapacity, v.capacityUnit, v.unitsPerCarton, v.minStock, v.maxStock,
       v.purchaseCost, v.productionCost, v.wholesalePrice, v.retailPrice, v.specialPrice, 19.0000, 1
FROM (
    SELECT 'PF-0001' AS sku, '6130000000011' AS barcode, 'Liquide vaisselle citron 1 L' AS nameFr,
           'سائل غسل الأواني بالليمون 1 ل' AS nameAr, 'Détergent vaisselle concentré au citron' AS description,
           'DISHWASHING' AS categoryCode, 'Flacon 1 L' AS packaging, 1000.000 AS packagingCapacity,
           'ML' AS capacityUnit, 12 AS unitsPerCarton, 120 AS minStock, 3000 AS maxStock,
           210.00 AS purchaseCost, 185.00 AS productionCost, 320.00 AS wholesalePrice,
           375.00 AS retailPrice, 350.00 AS specialPrice
    UNION ALL SELECT 'PF-0002', '6130000000028', 'Liquide vaisselle citron 5 L',
           'سائل غسل الأواني بالليمون 5 ل', 'Format économique bidon 5 L', 'DISHWASHING',
           'Bidon 5 L', 5000.000, 'ML', 4, 40, 800, 890.00, 780.00, 1150.00, 1350.00, 1250.00
    UNION ALL SELECT 'PF-0003', '6130000000035', 'Nettoyant sols lavande 5 L',
           'منظف الأرضيات بالخزامى 5 ل', 'Nettoyant sols parfumé, usage domestique', 'FLOORCLEANER',
           'Bidon 5 L', 5000.000, 'ML', 4, 40, 900, 620.00, 540.00, 850.00, 990.00, 920.00
    UNION ALL SELECT 'PF-0004', '6130000000042', 'Eau de Javel 12° 2 L',
           'ماء جافيل 12° 2 ل', 'Hypochlorite de sodium, désinfection', 'BLEACH',
           'Flacon 2 L', 2000.000, 'ML', 6, 80, 1500, 240.00, 205.00, 350.00, 420.00, 390.00
    UNION ALL SELECT 'PF-0005', '6130000000059', 'Savon liquide mains 500 ML',
           'صابون سائل لليدين 500 مل', 'Savon doux pH neutre', 'LIQUIDSOAP',
           'Flacon 500 ML', 500.000, 'ML', 12, 100, 2000, 165.00, 140.00, 240.00, 285.00, 260.00
    UNION ALL SELECT 'PF-0006', '6130000000066', 'Lessive liquide 3 L',
           'منظف الغسيل السائل 3 ل', 'Lessive concentrée pour machine', 'LAUNDRY',
           'Bidon 3 L', 3000.000, 'ML', 6, 60, 1200, 720.00, 640.00, 950.00, 1090.00, 1020.00
) AS v
JOIN productCategories c ON c.code = v.categoryCode
ON DUPLICATE KEY UPDATE nameFr = VALUES(nameFr), nameAr = VALUES(nameAr),
    wholesalePrice = VALUES(wholesalePrice), retailPrice = VALUES(retailPrice);

