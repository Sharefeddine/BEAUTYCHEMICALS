<?php

declare(strict_types=1);

namespace ChemManager\Api\Repositories;

use ChemManager\Api\Core\ApiException;
use ChemManager\Api\Core\Database;

/**
 * Chemical safety: the sheet library, the handling instructions, the pictograms and the incident
 * register.
 *
 * <p>A safety data sheet is only trusted while it is current and recent: a sheet older than three
 * years is reported as obsolete even when nothing replaced it, because that is the state the HSE
 * inspector looks for.</p>
 */
final class SafetyRepository
{
    /** A sheet older than this many years is obsolete, whatever its revision. */
    public const SHEET_VALIDITY_YEARS = 3;

    private const DOCUMENT_TYPES = ['SDS', 'TECHNICAL', 'DISPOSAL', 'TRANSPORT'];

    private const INCIDENT_TYPES = ['SPILL', 'EXPOSURE', 'FIRE', 'DAMAGE', 'OTHER'];

    private const SEVERITIES = ['MINOR', 'MODERATE', 'MAJOR', 'CRITICAL'];

    private const INCIDENT_STATUSES = ['OPEN', 'INVESTIGATING', 'CLOSED'];

    public function __construct(private readonly Database $db)
    {
    }

    /** @return array<int,array<string,mixed>> */
    public function pictograms(): array
    {
        return $this->db->select(
            'SELECT p.id, p.code, p.nameFr, p.nameAr, p.nameEn, p.description, p.sortOrder,
                    (SELECT COUNT(*) FROM materialPictograms mp WHERE mp.pictogramId = p.id) AS materialCount
             FROM safetyPictograms p ORDER BY p.sortOrder, p.code'
        );
    }

    /** @return array<string,mixed> */
    public function overview(): array
    {
        $materials = $this->db->first(
            'SELECT COUNT(*) AS total,
                    SUM(CASE WHEN hazardClass <> \'\' THEN 1 ELSE 0 END) AS hazardous,
                    SUM(CASE WHEN sdsAvailable = 1 THEN 1 ELSE 0 END) AS withSheet
             FROM rawMaterials WHERE deletedAt IS NULL AND isActive = 1'
        ) ?? [];
        $hazardous = (int) ($materials['hazardous'] ?? 0);
        $withSheet = (int) ($materials['withSheet'] ?? 0);

        $current = $this->db->first(
            'SELECT COUNT(*) AS total,
                    SUM(CASE WHEN issueDate IS NULL
                                  OR issueDate < DATE_SUB(CURDATE(), INTERVAL '
                                     . self::SHEET_VALIDITY_YEARS . ' YEAR)
                             THEN 1 ELSE 0 END) AS obsolete
             FROM safetyDataSheets WHERE isCurrent = 1 AND documentType = \'SDS\''
        ) ?? [];

        $incidents = $this->db->first(
            'SELECT COUNT(*) AS total,
                    SUM(CASE WHEN status <> \'CLOSED\' THEN 1 ELSE 0 END) AS open,
                    SUM(CASE WHEN severity IN (\'MAJOR\', \'CRITICAL\') THEN 1 ELSE 0 END) AS severe
             FROM safetyIncidents'
        ) ?? [];

        $instructions = (int) $this->db->scalar('SELECT COUNT(*) FROM safetyInstructions');

        return [
            'materials' => (int) ($materials['total'] ?? 0),
            'hazardousMaterials' => $hazardous,
            'materialsWithSheet' => $withSheet,
            'materialsWithoutSheet' => max(0, $hazardous - $withSheet),
            'currentSheets' => (int) ($current['total'] ?? 0),
            'obsoleteSheets' => (int) ($current['obsolete'] ?? 0),
            'materialsWithInstructions' => $instructions,
            'incidents' => (int) ($incidents['total'] ?? 0),
            'openIncidents' => (int) ($incidents['open'] ?? 0),
            'severeIncidents' => (int) ($incidents['severe'] ?? 0),
            'sheetValidityYears' => self::SHEET_VALIDITY_YEARS,
        ];
    }

    /** @return array{rows:array<int,array>,total:int} */
    public function materials(int $page, int $pageSize, string $search, string $risk): array
    {
        $where = ['m.deletedAt IS NULL'];
        $params = [];
        if ($search !== '') {
            $where[] = '(m.code LIKE :s1 OR m.commercialName LIKE :s2 OR m.chemicalName LIKE :s3
                         OR m.hazardClass LIKE :s4)';
            foreach (['s1', 's2', 's3', 's4'] as $key) {
                $params[$key] = '%' . $search . '%';
            }
        }
        if ($risk === 'hazardous') {
            $where[] = 'm.hazardClass <> \'\'';
        } elseif ($risk === 'missing') {
            $where[] = 'm.hazardClass <> \'\' AND m.sdsAvailable = 0';
        } elseif ($risk === 'obsolete') {
            $where[] = 'm.hazardClass <> \'\'
                        AND EXISTS (SELECT 1 FROM safetyDataSheets s WHERE s.materialId = m.id
                                    AND s.documentType = \'SDS\' AND s.isCurrent = 1
                                    AND (s.issueDate IS NULL
                                         OR s.issueDate < DATE_SUB(CURDATE(), INTERVAL '
                                            . self::SHEET_VALIDITY_YEARS . ' YEAR)))';
        }

        $clause = 'WHERE ' . implode(' AND ', $where);
        $total = (int) $this->db->scalar('SELECT COUNT(*) FROM rawMaterials m ' . $clause, $params);

        $rows = $this->db->select(
            'SELECT m.id, m.code, m.commercialName, m.chemicalName, m.hazardClass, m.hazardPictograms,
                    m.storageConditions, m.storageLocation, m.sdsAvailable,
                    (SELECT s.id FROM safetyDataSheets s WHERE s.materialId = m.id AND s.documentType = \'SDS\'
                     AND s.isCurrent = 1 ORDER BY s.issueDate DESC LIMIT 1) AS sheetId,
                    (SELECT s.issueDate FROM safetyDataSheets s WHERE s.materialId = m.id
                     AND s.documentType = \'SDS\' AND s.isCurrent = 1 ORDER BY s.issueDate DESC LIMIT 1)
                        AS sheetIssueDate,
                    (SELECT s.revision FROM safetyDataSheets s WHERE s.materialId = m.id AND s.documentType = \'SDS\'
                     AND s.isCurrent = 1 ORDER BY s.issueDate DESC LIMIT 1) AS sheetRevision,
                    (SELECT COUNT(*) FROM safetyInstructions i WHERE i.materialId = m.id) AS instructionCount,
                    (SELECT COUNT(*) FROM materialPictograms mp WHERE mp.materialId = m.id) AS pictogramCount,
                    (SELECT COUNT(*) FROM safetyIncidents i WHERE i.materialId = m.id) AS incidentCount
             FROM rawMaterials m ' . $clause . '
             ORDER BY (m.hazardClass <> \'\') DESC, m.commercialName
             LIMIT :limit OFFSET :offset',
            $params + ['limit' => $pageSize, 'offset' => ($page - 1) * $pageSize]
        );

        return ['rows' => array_map([$this, 'hydrateMaterial'], $rows), 'total' => $total];
    }

    /** @return array<string,mixed> */
    public function material(int $id): array
    {
        $row = $this->db->first(
            'SELECT m.id, m.code, m.commercialName, m.chemicalName, m.hazardClass, m.hazardPictograms,
                    m.storageConditions, m.storageLocation, m.sdsAvailable,
                    (SELECT COUNT(*) FROM safetyInstructions i WHERE i.materialId = m.id) AS instructionCount,
                    (SELECT COUNT(*) FROM materialPictograms mp WHERE mp.materialId = m.id) AS pictogramCount,
                    (SELECT COUNT(*) FROM safetyIncidents i WHERE i.materialId = m.id) AS incidentCount
             FROM rawMaterials m WHERE m.id = :id AND m.deletedAt IS NULL',
            ['id' => $id]
        );
        if ($row === null) {
            throw ApiException::notFound('Material');
        }
        $material = $this->hydrateMaterial($row);
        $material['sheets'] = $this->sheetsOf($id);
        foreach ($material['sheets'] as $sheet) {
            if ((int) $sheet['isCurrent'] === 1 && $sheet['documentType'] === 'SDS') {
                $material['sheetId'] = (int) $sheet['id'];
                $material['sheetIssueDate'] = $sheet['issueDate'];
                $material['sheetRevision'] = $sheet['revision'];
                $material['sheetObsolete'] = (bool) $sheet['isObsolete'];
                break;
            }
        }
        $material['instructions'] = $this->db->first(
            'SELECT id, ppe, storage, handling, firstAid, fireMeasures, spillage, updatedAt FROM safetyInstructions
             WHERE materialId = :id',
            ['id' => $id]
        );
        $material['pictograms'] = $this->db->select(
            'SELECT p.code, p.nameFr, p.nameAr, p.nameEn FROM materialPictograms mp
             JOIN safetyPictograms p ON p.id = mp.pictogramId
             WHERE mp.materialId = :id ORDER BY p.sortOrder',
            ['id' => $id]
        );
        $material['incidents'] = $this->db->select(
            'SELECT i.id, i.incidentNumber, i.incidentDate, i.incidentType, i.severity, i.status
             FROM safetyIncidents i WHERE i.materialId = :id ORDER BY i.incidentDate DESC LIMIT 10',
            ['id' => $id]
        );

        return $material;
    }

    /** @return array{rows:array<int,array>,total:int} */
    public function sheets(int $page, int $pageSize, string $search, string $documentType, bool $currentOnly): array
    {
        $where = ['1 = 1'];
        $params = [];
        if ($search !== '') {
            $where[] = '(m.code LIKE :s1 OR m.commercialName LIKE :s2 OR s.revision LIKE :s3
                         OR s.source LIKE :s4)';
            foreach (['s1', 's2', 's3', 's4'] as $key) {
                $params[$key] = '%' . $search . '%';
            }
        }
        if (in_array(strtoupper($documentType), self::DOCUMENT_TYPES, true)) {
            $where[] = 's.documentType = :documentType';
            $params['documentType'] = strtoupper($documentType);
        }
        if ($currentOnly) {
            $where[] = 's.isCurrent = 1';
        }

        $clause = 'WHERE ' . implode(' AND ', $where);
        $total = (int) $this->db->scalar(
            'SELECT COUNT(*) FROM safetyDataSheets s JOIN rawMaterials m ON m.id = s.materialId ' . $clause,
            $params
        );
        $rows = $this->db->select(
            'SELECT s.id, s.materialId, m.code AS materialCode, m.commercialName, s.documentType, s.filePath,
                    s.issueDate, s.revision, s.source, s.isCurrent, s.createdAt,
                    CASE WHEN s.issueDate IS NULL
                              OR s.issueDate < DATE_SUB(CURDATE(), INTERVAL '
                                 . self::SHEET_VALIDITY_YEARS . ' YEAR)
                         THEN 1 ELSE 0 END AS isObsolete
             FROM safetyDataSheets s JOIN rawMaterials m ON m.id = s.materialId ' . $clause . '
             ORDER BY s.issueDate DESC, s.id DESC LIMIT :limit OFFSET :offset',
            $params + ['limit' => $pageSize, 'offset' => ($page - 1) * $pageSize]
        );

        return [
            'rows' => array_map(static function (array $row): array {
                $row['isObsolete'] = (bool) $row['isObsolete'];
                $row['isCurrent'] = (bool) $row['isCurrent'];

                return $row;
            }, $rows),
            'total' => $total,
        ];
    }

    /** @param array<string,mixed> $data @return array<string,mixed> */
    public function registerSheet(array $data, int $userId): array
    {
        $materialId = $this->materialId($data);
        $type = strtoupper((string) ($data['documentType'] ?? 'SDS'));
        if (!in_array($type, self::DOCUMENT_TYPES, true)) {
            throw ApiException::validation(['documentType' => 'validation.in']);
        }
        $path = trim((string) ($data['filePath'] ?? ''));
        if ($path === '') {
            throw ApiException::validation(['filePath' => 'validation.required']);
        }
        $issueDate = null;
        if (isset($data['issueDate']) && $data['issueDate'] !== '') {
            $parsed = date_create((string) $data['issueDate']);
            if ($parsed === false) {
                throw ApiException::validation(['issueDate' => 'validation.date']);
            }
            $issueDate = $parsed->format('Y-m-d');
        }
        $isCurrent = !isset($data['isCurrent']) || (bool) $data['isCurrent'];

        $this->db->transaction(function () use ($materialId, $type, $path, $issueDate, $isCurrent, $data, $userId): void {
            if ($isCurrent) {
                $this->db->execute(
                    'UPDATE safetyDataSheets SET isCurrent = 0
                     WHERE materialId = :materialId AND documentType = :documentType',
                    ['materialId' => $materialId, 'documentType' => $type]
                );
            }
            $this->db->execute(
                'INSERT INTO safetyDataSheets (materialId, documentType, filePath, issueDate, revision, source,
                                               isCurrent, uploadedBy)
                 VALUES (:materialId, :documentType, :filePath, :issueDate, :revision, :source, :isCurrent, :userId)',
                [
                    'materialId' => $materialId,
                    'documentType' => $type,
                    'filePath' => $path,
                    'issueDate' => $issueDate,
                    'revision' => trim((string) ($data['revision'] ?? '')),
                    'source' => trim((string) ($data['source'] ?? '')),
                    'isCurrent' => $isCurrent ? 1 : 0,
                    'userId' => $userId,
                ]
            );
            if ($type === 'SDS') {
                $this->db->execute(
                    'UPDATE rawMaterials SET sdsAvailable = 1, sdsFileName = :path WHERE id = :id',
                    ['path' => $path, 'id' => $materialId]
                );
            }
        });

        return $this->material($materialId);
    }

    /** @param array<string,mixed> $data @return array<string,mixed> */
    public function saveInstructions(int $materialId, array $data, int $userId): array
    {
        $this->material($materialId);
        $fields = ['ppe', 'storage', 'handling', 'firstAid', 'fireMeasures', 'spillage'];
        $values = [];
        foreach ($fields as $field) {
            $values[$field] = trim((string) ($data[$field] ?? ''));
        }
        $this->db->execute(
            'INSERT INTO safetyInstructions (materialId, ppe, storage, handling, firstAid, fireMeasures, spillage,
                                            updatedBy)
             VALUES (:materialId, :ppe, :storage, :handling, :firstAid, :fireMeasures, :spillage, :updatedBy)
             ON DUPLICATE KEY UPDATE ppe = VALUES(ppe), storage = VALUES(storage), handling = VALUES(handling),
                                     firstAid = VALUES(firstAid), fireMeasures = VALUES(fireMeasures),
                                     spillage = VALUES(spillage), updatedBy = VALUES(updatedBy)',
            $values + ['materialId' => $materialId, 'updatedBy' => $userId]
        );

        return $this->material($materialId);
    }

    /**
     * Replaces the pictogram set of a material and keeps the comma separated mirror on the material row
     * in step, so the warehouse screens that read only the material still show the hazard.
     *
     * @param array<int,string> $codes
     * @return array<string,mixed>
     */
    public function setPictograms(int $materialId, array $codes): array
    {
        $this->material($materialId);
        $known = $this->db->select('SELECT id, code FROM safetyPictograms');
        $byCode = [];
        foreach ($known as $row) {
            $byCode[strtoupper((string) $row['code'])] = (int) $row['id'];
        }
        $selected = [];
        foreach ($codes as $code) {
            $key = strtoupper(trim((string) $code));
            if ($key === '') {
                continue;
            }
            if (!isset($byCode[$key])) {
                throw ApiException::validation(['pictograms' => 'validation.in']);
            }
            $selected[$byCode[$key]] = $key;
        }

        $this->db->transaction(function () use ($materialId, $selected): void {
            $this->db->execute('DELETE FROM materialPictograms WHERE materialId = :id', ['id' => $materialId]);
            foreach (array_keys($selected) as $pictogramId) {
                $this->db->execute(
                    'INSERT INTO materialPictograms (materialId, pictogramId) VALUES (:materialId, :pictogramId)',
                    ['materialId' => $materialId, 'pictogramId' => $pictogramId]
                );
            }
            $this->db->execute(
                'UPDATE rawMaterials SET hazardPictograms = :codes WHERE id = :id',
                ['codes' => implode(',', array_values($selected)), 'id' => $materialId]
            );
        });

        return $this->material($materialId);
    }

    /** @return array{rows:array<int,array>,total:int,summary:array<string,mixed>} */
    public function incidents(int $page, int $pageSize, string $status, string $severity, string $search): array
    {
        $where = ['1 = 1'];
        $params = [];
        if ($status !== '' && in_array(strtoupper($status), self::INCIDENT_STATUSES, true)) {
            $where[] = 'i.status = :status';
            $params['status'] = strtoupper($status);
        }
        if ($severity !== '' && in_array(strtoupper($severity), self::SEVERITIES, true)) {
            $where[] = 'i.severity = :severity';
            $params['severity'] = strtoupper($severity);
        }
        if ($search !== '') {
            $where[] = '(i.incidentNumber LIKE :s1 OR i.description LIKE :s2 OR m.commercialName LIKE :s3)';
            foreach (['s1', 's2', 's3'] as $key) {
                $params[$key] = '%' . $search . '%';
            }
        }
        $clause = 'WHERE ' . implode(' AND ', $where);
        $total = (int) $this->db->scalar(
            'SELECT COUNT(*) FROM safetyIncidents i LEFT JOIN rawMaterials m ON m.id = i.materialId ' . $clause,
            $params
        );
        $rows = $this->db->select(
            'SELECT i.id, i.incidentNumber, i.materialId, m.commercialName AS materialName, i.incidentDate,
                    i.incidentType, i.severity, i.description, i.actionsTaken, i.status, i.closedAt,
                    u.fullName AS reportedByName
             FROM safetyIncidents i
             LEFT JOIN rawMaterials m ON m.id = i.materialId
             LEFT JOIN users u ON u.id = i.reportedBy ' . $clause . '
             ORDER BY i.incidentDate DESC, i.id DESC LIMIT :limit OFFSET :offset',
            $params + ['limit' => $pageSize, 'offset' => ($page - 1) * $pageSize]
        );
        $summary = $this->db->first(
            'SELECT COUNT(*) AS total,
                    SUM(CASE WHEN i.status <> \'CLOSED\' THEN 1 ELSE 0 END) AS open,
                    SUM(CASE WHEN i.severity IN (\'MAJOR\', \'CRITICAL\') THEN 1 ELSE 0 END) AS severe
             FROM safetyIncidents i LEFT JOIN rawMaterials m ON m.id = i.materialId ' . $clause,
            $params
        ) ?? [];

        return [
            'rows' => $rows,
            'total' => $total,
            'summary' => [
                'total' => (int) ($summary['total'] ?? 0),
                'open' => (int) ($summary['open'] ?? 0),
                'severe' => (int) ($summary['severe'] ?? 0),
            ],
        ];
    }

    /** @param array<string,mixed> $data @return array<string,mixed> */
    public function reportIncident(array $data, int $userId): array
    {
        $type = strtoupper((string) ($data['incidentType'] ?? 'SPILL'));
        if (!in_array($type, self::INCIDENT_TYPES, true)) {
            throw ApiException::validation(['incidentType' => 'validation.in']);
        }
        $severity = strtoupper((string) ($data['severity'] ?? 'MINOR'));
        if (!in_array($severity, self::SEVERITIES, true)) {
            throw ApiException::validation(['severity' => 'validation.in']);
        }
        $description = trim((string) ($data['description'] ?? ''));
        if ($description === '') {
            throw ApiException::validation(['description' => 'validation.required']);
        }
        $occurredAt = date_create((string) ($data['incidentDate'] ?? 'now'));
        if ($occurredAt === false) {
            throw ApiException::validation(['incidentDate' => 'validation.date']);
        }
        $materialId = null;
        if (isset($data['materialId']) && (int) $data['materialId'] > 0) {
            $materialId = (int) $data['materialId'];
            if ($this->db->scalar('SELECT id FROM rawMaterials WHERE id = :id AND deletedAt IS NULL',
                    ['id' => $materialId]) === null) {
                throw ApiException::validation(['materialId' => 'validation.exists']);
            }
        }

        $number = 'INC-' . str_pad((string) (((int) $this->db->scalar('SELECT COUNT(*) FROM safetyIncidents')) + 1),
            5, '0', STR_PAD_LEFT);
        while ($this->db->scalar('SELECT id FROM safetyIncidents WHERE incidentNumber = :number',
                ['number' => $number]) !== null) {
            $next = ((int) substr($number, 4)) + 1;
            $number = 'INC-' . str_pad((string) $next, 5, '0', STR_PAD_LEFT);
        }

        $this->db->execute(
            'INSERT INTO safetyIncidents (incidentNumber, materialId, incidentDate, incidentType, severity,
                                          description, actionsTaken, reportedBy, status)
             VALUES (:number, :materialId, :incidentDate, :incidentType, :severity, :description, :actionsTaken,
                     :reportedBy, \'OPEN\')',
            [
                'number' => $number,
                'materialId' => $materialId,
                'incidentDate' => $occurredAt->format('Y-m-d H:i:s'),
                'incidentType' => $type,
                'severity' => $severity,
                'description' => $description,
                'actionsTaken' => trim((string) ($data['actionsTaken'] ?? '')),
                'reportedBy' => $userId,
            ]
        );

        return $this->incident($this->db->lastId());
    }

    /** @return array<string,mixed> */
    public function changeIncidentStatus(int $id, string $status): array
    {
        $current = $this->incident($id);
        $target = strtoupper($status);
        if (!in_array($target, self::INCIDENT_STATUSES, true)) {
            throw ApiException::validation(['status' => 'validation.in']);
        }
        if ($current['status'] === 'CLOSED') {
            throw ApiException::conflict('A closed incident is not reopened: a new incident is reported.',
                'INVALID_STATUS');
        }
        if ($target === 'CLOSED' && $current['status'] === 'OPEN') {
            throw ApiException::conflict('An incident is investigated before it is closed.', 'INVALID_STATUS');
        }
        $this->db->execute(
            'UPDATE safetyIncidents SET status = :status, closedAt = CASE WHEN :status = \'CLOSED\' THEN NOW()
                                                                        ELSE NULL END
             WHERE id = :id',
            ['status' => $target, 'id' => $id]
        );

        return $this->incident($id);
    }

    /** @return array<string,mixed> */
    private function incident(int $id): array
    {
        $row = $this->db->first(
            'SELECT i.id, i.incidentNumber, i.materialId, m.commercialName AS materialName, i.incidentDate,
                    i.incidentType, i.severity, i.description, i.actionsTaken, i.status, i.closedAt,
                    u.fullName AS reportedByName
             FROM safetyIncidents i
             LEFT JOIN rawMaterials m ON m.id = i.materialId
             LEFT JOIN users u ON u.id = i.reportedBy
             WHERE i.id = :id',
            ['id' => $id]
        );
        if ($row === null) {
            throw ApiException::notFound('Incident');
        }

        return $row;
    }

    /** @return array<int,array<string,mixed>> */
    private function sheetsOf(int $materialId): array
    {
        return $this->db->select(
            'SELECT id, documentType, filePath, issueDate, revision, source, isCurrent, createdAt,
                    CASE WHEN issueDate IS NULL OR issueDate < DATE_SUB(CURDATE(), INTERVAL '
                    . self::SHEET_VALIDITY_YEARS . ' YEAR) THEN 1 ELSE 0 END AS isObsolete
             FROM safetyDataSheets WHERE materialId = :id ORDER BY issueDate DESC, id DESC',
            ['id' => $materialId]
        );
    }

    /** @param array<string,mixed> $data */
    private function materialId(array $data): int
    {
        $id = (int) ($data['materialId'] ?? 0);
        if ($id <= 0) {
            throw ApiException::validation(['materialId' => 'validation.required']);
        }
        if ($this->db->scalar('SELECT id FROM rawMaterials WHERE id = :id AND deletedAt IS NULL',
                ['id' => $id]) === null) {
            throw ApiException::validation(['materialId' => 'validation.exists']);
        }

        return $id;
    }

    /** @return array<string,mixed> */
    private function hydrateMaterial(array $row): array
    {
        $issueDate = $row['sheetIssueDate'] ?? null;
        $obsolete = $issueDate !== null
            && strtotime((string) $issueDate) < strtotime('-' . self::SHEET_VALIDITY_YEARS . ' years');

        return [
            'id' => (int) $row['id'],
            'code' => (string) $row['code'],
            'commercialName' => (string) $row['commercialName'],
            'chemicalName' => (string) ($row['chemicalName'] ?? ''),
            'hazardClass' => (string) ($row['hazardClass'] ?? ''),
            'hazardPictograms' => (string) ($row['hazardPictograms'] ?? ''),
            'storageConditions' => (string) ($row['storageConditions'] ?? ''),
            'storageLocation' => (string) ($row['storageLocation'] ?? ''),
            'isHazardous' => ($row['hazardClass'] ?? '') !== '',
            'hasSheet' => (bool) ($row['sdsAvailable'] ?? false),
            'sheetId' => isset($row['sheetId']) && $row['sheetId'] !== null ? (int) $row['sheetId'] : null,
            'sheetIssueDate' => $issueDate,
            'sheetRevision' => $row['sheetRevision'] ?? null,
            'sheetObsolete' => $obsolete,
            'instructionCount' => (int) ($row['instructionCount'] ?? 0),
            'pictogramCount' => (int) ($row['pictogramCount'] ?? 0),
            'incidentCount' => (int) ($row['incidentCount'] ?? 0),
        ];
    }
}
