-- =============================================================================
-- ChemManager ERP - MySQL 8 seed
-- 08 - finance: the opening cash book of the demo year
--
-- The treasury must show movement before anyone records a sale: two receipts and two
-- disbursements, one of them in a period that is already closed, so the cash book can be
-- read from the first minute and the period guard has something to refuse.
-- =============================================================================

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Cash inflow, open period
INSERT INTO cashTransactions (cashAccountId, direction, amount, sourceType, sourceId, description,
                              occurredAt, accountingPeriodId, userId)
SELECT a.id, 'IN', 120000.00, 'OTHER', NULL, 'Apport de caisse - fonds de roulement',
       '2026-11-03 09:00:00', p.id, u.id
FROM cashAccounts a, accountingPeriods p, users u
WHERE a.code = 'CAISSE' AND p.code = '2026-11' AND u.username = 'admin'
  AND NOT EXISTS (SELECT 1 FROM cashTransactions WHERE description = 'Apport de caisse - fonds de roulement');

-- Bank transfer received, open period
INSERT INTO cashTransactions (cashAccountId, direction, amount, sourceType, sourceId, description,
                              occurredAt, accountingPeriodId, userId)
SELECT a.id, 'IN', 250000.00, 'TRANSFER', NULL, 'Virement reçu - client grossiste',
       '2026-11-04 11:30:00', p.id, u.id
FROM cashAccounts a, accountingPeriods p, users u
WHERE a.code = 'BANQUE' AND p.code = '2026-11' AND u.username = 'admin'
  AND NOT EXISTS (SELECT 1 FROM cashTransactions WHERE description = 'Virement reçu - client grossiste');

-- Fuel, open period
INSERT INTO cashTransactions (cashAccountId, direction, amount, sourceType, sourceId, description,
                              occurredAt, accountingPeriodId, userId)
SELECT a.id, 'OUT', 9500.00, 'EXPENSE', NULL, 'Carburant - tournée Sétif',
       '2026-11-05 07:45:00', p.id, u.id
FROM cashAccounts a, accountingPeriods p, users u
WHERE a.code = 'CAISSE' AND p.code = '2026-11' AND u.username = 'admin'
  AND NOT EXISTS (SELECT 1 FROM cashTransactions WHERE description = 'Carburant - tournée Sétif');

-- Rent, a period that is already closed
INSERT INTO cashTransactions (cashAccountId, direction, amount, sourceType, sourceId, description,
                              occurredAt, accountingPeriodId, userId)
SELECT a.id, 'OUT', 45000.00, 'EXPENSE', NULL, 'Loyer - octobre',
       '2026-10-05 10:00:00', p.id, u.id
FROM cashAccounts a, accountingPeriods p, users u
WHERE a.code = 'BANQUE' AND p.code = '2026-10' AND u.username = 'admin'
  AND NOT EXISTS (SELECT 1 FROM cashTransactions WHERE description = 'Loyer - octobre');
