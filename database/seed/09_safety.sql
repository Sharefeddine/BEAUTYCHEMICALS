-- =============================================================================
-- ChemManager ERP - MySQL 8 seed
-- 09 - safety: the sheets, the handling instructions and the incident register
--
-- A safety file is only useful if it shows a real gap: one material has an obsolete sheet
-- (issued in 2021, older than the three-year validity), one has none at all, and the register
-- holds an open incident and a closed one, so the alerts have something to point at.
-- =============================================================================

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Current sheets for the two most used materials
INSERT INTO safetyDataSheets (materialId, documentType, filePath, issueDate, revision, source, isCurrent, uploadedBy)
SELECT m.id, 'SDS', CONCAT('sds/', m.code, '-2025.pdf'), '2025-02-14', 'REV-4',
       'Fournisseur - Chimie Sétif', 1, u.id
FROM rawMaterials m, users u
WHERE m.code = 'MP-0001' AND u.username = 'admin'
  AND NOT EXISTS (SELECT 1 FROM safetyDataSheets s WHERE s.materialId = m.id AND s.revision = 'REV-4');

INSERT INTO safetyDataSheets (materialId, documentType, filePath, issueDate, revision, source, isCurrent, uploadedBy)
SELECT m.id, 'SDS', CONCAT('sds/', m.code, '-2024.pdf'), '2024-09-02', 'REV-2',
       'Fournisseur - Chimie Sétif', 1, u.id
FROM rawMaterials m, users u
WHERE m.code = 'MP-0002' AND u.username = 'admin'
  AND NOT EXISTS (SELECT 1 FROM safetyDataSheets s WHERE s.materialId = m.id AND s.revision = 'REV-2');

-- An obsolete sheet, still the current one: the overview must flag it
INSERT INTO safetyDataSheets (materialId, documentType, filePath, issueDate, revision, source, isCurrent, uploadedBy)
SELECT m.id, 'SDS', CONCAT('sds/', m.code, '-2021.pdf'), '2021-03-11', 'REV-1',
       'Base officielle', 1, u.id
FROM rawMaterials m, users u
WHERE m.code = 'MP-0003' AND u.username = 'admin'
  AND NOT EXISTS (SELECT 1 FROM safetyDataSheets s WHERE s.materialId = m.id AND s.revision = 'REV-1');

-- Handling instructions
INSERT INTO safetyInstructions (materialId, ppe, storage, handling, firstAid, fireMeasures, spillage, updatedBy)
SELECT m.id, 'Gants nitrile, lunettes, tablier', 'Fût fermé, local ventilé, à l''abri de l''humidité',
       'Verser lentement dans l''eau, jamais l''inverse', 'Rincer 15 minutes, consulter un médecin',
       'Poudre ABC, ne pas utiliser d''eau directe', 'Absorber avec de la vermiculite, ventiler', u.id
FROM rawMaterials m, users u
WHERE m.code = 'MP-0001' AND u.username = 'admin'
  AND NOT EXISTS (SELECT 1 FROM safetyInstructions i WHERE i.materialId = m.id);

INSERT INTO safetyInstructions (materialId, ppe, storage, handling, firstAid, fireMeasures, spillage, updatedBy)
SELECT m.id, 'Gants, masque FFP2', 'Local sec et frais', 'Éviter la poussière',
       'Rincer les yeux, consulter', 'Milieu inapproprié : eau', 'Balayer sans soulever de poussière', u.id
FROM rawMaterials m, users u
WHERE m.code = 'MP-0002' AND u.username = 'admin'
  AND NOT EXISTS (SELECT 1 FROM safetyInstructions i WHERE i.materialId = m.id);

-- Pictogram links, aligned with the hazard codes of the material
INSERT INTO materialPictograms (materialId, pictogramId)
SELECT m.id, p.id
FROM rawMaterials m, safetyPictograms p
WHERE m.code = 'MP-0001' AND p.code = 'GHS05'
  AND NOT EXISTS (SELECT 1 FROM materialPictograms mp WHERE mp.materialId = m.id AND mp.pictogramId = p.id);

INSERT INTO materialPictograms (materialId, pictogramId)
SELECT m.id, p.id
FROM rawMaterials m, safetyPictograms p
WHERE m.code = 'MP-0002' AND p.code = 'GHS07'
  AND NOT EXISTS (SELECT 1 FROM materialPictograms mp WHERE mp.materialId = m.id AND mp.pictogramId = p.id);

INSERT INTO materialPictograms (materialId, pictogramId)
SELECT m.id, p.id
FROM rawMaterials m, safetyPictograms p
WHERE m.code = 'MP-0003' AND p.code = 'GHS05'
  AND NOT EXISTS (SELECT 1 FROM materialPictograms mp
                  WHERE mp.materialId = m.id AND mp.pictogramId = p.id);

-- The register: one open spill, one closed exposure
INSERT INTO safetyIncidents (incidentNumber, materialId, incidentDate, incidentType, severity, description,
                             actionsTaken, reportedBy, status, closedAt)
SELECT 'INC-00001', m.id, '2026-11-06 10:20:00', 'SPILL', 'MINOR',
       'Fût de LABSA renversé au dépotage, environ 3 litres au sol',
       'Zone balisée, produit absorbé, ventilation forcée', u.id, 'CLOSED', '2026-11-07 09:00:00'
FROM rawMaterials m, users u
WHERE m.code = 'MP-0001' AND u.username = 'admin'
  AND NOT EXISTS (SELECT 1 FROM safetyIncidents i WHERE i.incidentNumber = 'INC-00001');

INSERT INTO safetyIncidents (incidentNumber, materialId, incidentDate, incidentType, severity, description,
                             actionsTaken, reportedBy, status)
SELECT 'INC-00002', m.id, '2026-11-12 14:45:00', 'EXPOSURE', 'MODERATE',
       'Projection oculaire lors du transvasement, port de lunettes incomplet',
       'Rinçage immédiat, évacuation vers l''infirmerie', u.id, 'OPEN'
FROM rawMaterials m, users u
WHERE m.code = 'MP-0003' AND u.username = 'admin'
  AND NOT EXISTS (SELECT 1 FROM safetyIncidents i WHERE i.incidentNumber = 'INC-00002');
