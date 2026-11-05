-- =============================================================================
-- ChemManager ERP - seed 04: GHS pictograms and hazardous-material vocabulary
-- =============================================================================

SET NAMES utf8mb4;

INSERT INTO safetyPictograms (code, nameFr, nameAr, nameEn, description, sortOrder) VALUES
  ('GHS01', 'Explosif', 'متفجر', 'Explosive', 'Matière explosible', 1),
  ('GHS02', 'Inflammable', 'قابل للاشتعال', 'Flammable', 'Gaz, aérosols, liquides ou solides inflammables', 2),
  ('GHS03', 'Comburant', 'مؤكسد', 'Oxidizing', 'Peut provoquer ou aggraver un incendie', 3),
  ('GHS04', 'Gaz sous pression', 'غاز تحت الضغط', 'Gas under pressure', 'Récipients sous pression', 4),
  ('GHS05', 'Corrosif', 'مسبب للتآكل', 'Corrosive', 'Corrosion des métaux et lésions cutanées graves', 5),
  ('GHS06', 'Toxique aigu', 'سام حاد', 'Acute toxicity', 'Toxicité aiguë par inhalation, contact ou ingestion', 6),
  ('GHS07', 'Irritant / nocif', 'مهيّج / ضار', 'Irritant / harmful', 'Irritation, sensibilisation, toxicité faible', 7),
  ('GHS08', 'Nocif pour la santé', 'ضار بالصحة', 'Health hazard', 'Toxicité chronique, atteinte des organes', 8),
  ('GHS09', 'Dangereux pour l''environnement', 'خطير على البيئة', 'Environmental hazard', 'Toxicité aquatique', 9)
ON DUPLICATE KEY UPDATE nameFr = VALUES(nameFr), nameAr = VALUES(nameAr), description = VALUES(description);
