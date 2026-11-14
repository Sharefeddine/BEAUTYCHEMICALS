-- =============================================================================
-- ChemManager ERP - seed 07: customers, fleet and a demo sales history
-- Idempotent: re-running the file does not duplicate rows.
-- =============================================================================

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

INSERT INTO customers (code, name, type, phone, email, address, wilaya, creditLimit, balance, priceSegment, notes)
VALUES
  ('CL-0001', 'Supérette El Baraka', 'COMPANY', '+213 36 44 12 90', 'contact@elbaraka.dz',
   'Cité 1000 logements, bloc B', 'Sétif', 150000.00, 0.00, 'WHOLESALE', 'Livraison le lundi'),
  ('CL-0002', 'Hôtel Atlas', 'COMPANY', '+213 36 88 33 21', 'achats@hotel-atlas.dz',
   'Avenue du 8 mai 1945', 'Sétif', 300000.00, 0.00, 'SPECIAL', 'Facturation mensuelle'),
  ('CL-0003', 'Épicerie Amine', 'INDIVIDUAL', '+213 550 77 41 18', '',
   'Rue Larbi Ben M''hidi', 'Sétif', 40000.00, 0.00, 'RETAIL', ''),
  ('CL-0004', 'Grossiste Oued Smar', 'COMPANY', '+213 21 55 20 74', 'commercial@ouedsmar.dz',
   'Zone commerciale, lot 41', 'Alger', 500000.00, 0.00, 'WHOLESALE', 'Enlèvement sur place'),
  ('CL-0005', 'Lycée El Hidhab', 'COMPANY', '+213 36 60 11 03', 'intendance@lycee-hidhab.dz',
   'Quartier El Hidhab', 'Sétif', 0.00, 0.00, 'RETAIL', 'Paiement comptant')
ON DUPLICATE KEY UPDATE name = VALUES(name), creditLimit = VALUES(creditLimit),
    priceSegment = VALUES(priceSegment), wilaya = VALUES(wilaya);

INSERT INTO vehicles (plate, model, capacityKg, isActive)
VALUES
  ('19-2145-116', 'Renault Kangoo', 800.00, 1),
  ('19-3320-117', 'Iveco Daily', 3500.00, 1)
ON DUPLICATE KEY UPDATE model = VALUES(model), capacityKg = VALUES(capacityKg), isActive = VALUES(isActive);
