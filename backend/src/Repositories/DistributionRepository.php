<?php

declare(strict_types=1);

namespace ChemManager\Api\Repositories;

use ChemManager\Api\Core\ApiException;
use ChemManager\Api\Core\Database;

/**
 * Distribution: the fleet and the daily tours.
 *
 * <p>A tour groups the delivery notes that leave on the same truck, in the order the driver will
 * visit them. The stock left the warehouse when the notes were prepared, so a tour only moves the
 * paperwork and the signature: starting it puts the notes in transit, delivering a stop signs for
 * that customer, completing it closes the day and sends whatever came back to the warehouse book.</p>
 */
final class DistributionRepository
{
    private const ROUTE_STATUSES = ['PLANNED', 'LOADING', 'IN_TRANSIT', 'COMPLETED', 'CANCELLED'];

    public function __construct(private readonly Database $db, private readonly SalesRepository $sales)
    {
    }

    // -------------------------------------------------------------------- fleet

    /** @return array<int,array> */
    public function vehicles(): array
    {
        return $this->db->select(
            'SELECT v.*, CONCAT(e.firstName, \' \', e.lastName) AS driverName, e.phone AS driverPhone,
                    (SELECT COUNT(*) FROM deliveryRoutes r WHERE r.vehicleId = v.id) AS routeCount,
                    (SELECT MAX(r.routeDate) FROM deliveryRoutes r WHERE r.vehicleId = v.id) AS lastRouteDate,
                    (SELECT COUNT(*) FROM deliveryRoutes r WHERE r.vehicleId = v.id
                        AND r.status IN (\'PLANNED\', \'LOADING\', \'IN_TRANSIT\')) AS openRoutes
             FROM vehicles v LEFT JOIN employees e ON e.id = v.driverId
             ORDER BY v.isActive DESC, v.plate'
        );
    }

    /** @param array<string,mixed> $data @return array<string,mixed> */
    public function createVehicle(array $data): array
    {
        $plate = strtoupper(trim((string) ($data['plate'] ?? '')));
        if ($plate === '') {
            throw ApiException::validation(['plate' => 'validation.required']);
        }
        if ($this->db->first('SELECT id FROM vehicles WHERE plate = :plate', ['plate' => $plate]) !== null) {
            throw ApiException::conflict('A vehicle with this plate already exists.', 'VEHICLE_EXISTS',
                ['plate' => $plate]);
        }

        $this->db->execute(
            'INSERT INTO vehicles (plate, model, capacityKg, driverId, isActive)
             VALUES (:plate, :model, :capacityKg, :driverId, 1)',
            [
                'plate' => $plate,
                'model' => mb_substr(trim((string) ($data['model'] ?? '')), 0, 60),
                'capacityKg' => max(0.0, round((float) ($data['capacityKg'] ?? 0), 2)),
                'driverId' => $this->driverId($data['driverId'] ?? null),
            ]
        );

        return $this->vehicle($this->db->lastId());
    }

    /** @param array<string,mixed> $data @return array<string,mixed> */
    public function updateVehicle(int $id, array $data): array
    {
        $vehicle = $this->vehicle($id);
        $plate = array_key_exists('plate', $data) ? strtoupper(trim((string) $data['plate'])) : $vehicle['plate'];
        if ($plate === '') {
            throw ApiException::validation(['plate' => 'validation.required']);
        }
        $clash = $this->db->first('SELECT id FROM vehicles WHERE plate = :plate AND id <> :id',
            ['plate' => $plate, 'id' => $id]);
        if ($clash !== null) {
            throw ApiException::conflict('A vehicle with this plate already exists.', 'VEHICLE_EXISTS',
                ['plate' => $plate]);
        }

        $this->db->execute(
            'UPDATE vehicles SET plate = :plate, model = :model, capacityKg = :capacityKg, driverId = :driverId,
                    isActive = :isActive WHERE id = :id',
            [
                'plate' => $plate,
                'model' => array_key_exists('model', $data)
                    ? mb_substr(trim((string) $data['model']), 0, 60) : $vehicle['model'],
                'capacityKg' => array_key_exists('capacityKg', $data)
                    ? max(0.0, round((float) $data['capacityKg'], 2)) : (float) $vehicle['capacityKg'],
                'driverId' => array_key_exists('driverId', $data)
                    ? $this->driverId($data['driverId']) : $vehicle['driverId'],
                'isActive' => array_key_exists('isActive', $data)
                    ? ((bool) $data['isActive'] ? 1 : 0) : ((bool) $vehicle['isActive'] ? 1 : 0),
                'id' => $id,
            ]
        );

        return $this->vehicle($id);
    }

    /** @return array<string,mixed> */
    public function vehicle(int $id): array
    {
        $row = $this->db->first(
            'SELECT v.*, CONCAT(e.firstName, \' \', e.lastName) AS driverName, e.phone AS driverPhone
             FROM vehicles v LEFT JOIN employees e ON e.id = v.driverId WHERE v.id = :id',
            ['id' => $id]
        );
        if ($row === null) {
            throw ApiException::notFound('Vehicle');
        }

        return [
            'id' => (int) $row['id'],
            'plate' => (string) $row['plate'],
            'model' => (string) $row['model'],
            'capacityKg' => round((float) $row['capacityKg'], 2),
            'driverId' => $row['driverId'] === null ? null : (int) $row['driverId'],
            'driverName' => $row['driverName'],
            'driverPhone' => $row['driverPhone'],
            'isActive' => (bool) $row['isActive'],
        ];
    }

    /**
     * Drivers are employees; the fleet does not invent them. Phase 6 tolerates an empty fleet of
     * drivers (employees arrive with the payroll) so a vehicle can be planned without one.
     *
     * @return array<int,array>
     */
    public function drivers(): array
    {
        return $this->db->select(
            'SELECT id, employeeNumber, CONCAT(firstName, \' \', lastName) AS fullName, position, phone
             FROM employees WHERE isActive = 1 AND deletedAt IS NULL ORDER BY lastName, firstName'
        );
    }

    // -------------------------------------------------------------------- routes

    /** @return array{rows:array<int,array>,total:int} */
    public function paginateRoutes(int $page, int $pageSize, string $search, ?string $status, ?string $date): array
    {
        $where = ['1 = 1'];
        $params = [];
        if ($search !== '') {
            $where[] = '(r.routeNumber LIKE :searchNumber OR v.plate LIKE :searchPlate
                         OR r.wilayaZone LIKE :searchZone)';
            $term = '%' . $search . '%';
            $params['searchNumber'] = $term;
            $params['searchPlate'] = $term;
            $params['searchZone'] = $term;
        }
        if ($status !== null && in_array(strtoupper($status), self::ROUTE_STATUSES, true)) {
            $where[] = 'r.status = :status';
            $params['status'] = strtoupper($status);
        }
        if ($date !== null && $date !== '') {
            $where[] = 'r.routeDate = :routeDate';
            $params['routeDate'] = $date;
        }

        $clause = 'WHERE ' . implode(' AND ', $where);
        $total = (int) $this->db->scalar(
            'SELECT COUNT(*) FROM deliveryRoutes r JOIN vehicles v ON v.id = r.vehicleId ' . $clause, $params);

        $rows = $this->db->select(
            'SELECT r.*, v.plate, v.model, CONCAT(e.firstName, \' \', e.lastName) AS driverName,
                    u.fullName AS representativeName,
                    (SELECT COUNT(*) FROM routeStops s WHERE s.routeId = r.id) AS stopCount,
                    (SELECT COUNT(*) FROM routeStops s WHERE s.routeId = r.id AND s.status = \'DELIVERED\')
                        AS deliveredCount,
                    (SELECT COALESCE(SUM(n.totalQuantity), 0) FROM routeStops s
                        JOIN (SELECT deliveryNoteId, SUM(quantity) AS totalQuantity FROM deliveryItems
                              GROUP BY deliveryNoteId) n ON n.deliveryNoteId = s.deliveryNoteId
                        WHERE s.routeId = r.id) AS totalQuantity
             FROM deliveryRoutes r
             JOIN vehicles v ON v.id = r.vehicleId
             LEFT JOIN employees e ON e.id = r.driverId
             LEFT JOIN users u ON u.id = r.representativeId ' . $clause . '
             ORDER BY r.routeDate DESC, r.id DESC
             LIMIT :limit OFFSET :offset',
            $params + ['limit' => $pageSize, 'offset' => ($page - 1) * $pageSize]
        );

        return ['rows' => array_map([$this, 'hydrateRoute'], $rows), 'total' => $total];
    }

    /** @return array<string,mixed> */
    public function route(int $id): array
    {
        $row = $this->db->first(
            'SELECT r.*, v.plate, v.model, v.capacityKg, CONCAT(e.firstName, \' \', e.lastName) AS driverName,
                    e.phone AS driverPhone, u.fullName AS representativeName,
                    (SELECT COUNT(*) FROM routeStops s WHERE s.routeId = r.id) AS stopCount,
                    (SELECT COUNT(*) FROM routeStops s WHERE s.routeId = r.id AND s.status = \'DELIVERED\')
                        AS deliveredCount,
                    (SELECT COALESCE(SUM(di.quantity), 0) FROM routeStops s
                        JOIN deliveryItems di ON di.deliveryNoteId = s.deliveryNoteId
                        WHERE s.routeId = r.id) AS totalQuantity
             FROM deliveryRoutes r
             JOIN vehicles v ON v.id = r.vehicleId
             LEFT JOIN employees e ON e.id = r.driverId
             LEFT JOIN users u ON u.id = r.representativeId
             WHERE r.id = :id',
            ['id' => $id]
        );
        if ($row === null) {
            throw ApiException::notFound('Delivery route');
        }

        $route = $this->hydrateRoute($row);
        $route['stops'] = $this->db->select(
            'SELECT s.id, s.sequence, s.status, s.arrivedAt, s.remarks, d.id AS deliveryNoteId,
                    d.noteNumber, d.status AS noteStatus, d.signedBy, c.name AS customerName,
                    c.code AS customerCode, c.wilaya AS customerWilaya, c.phone AS customerPhone,
                    c.address AS customerAddress,
                    (SELECT COALESCE(SUM(di.quantity), 0) FROM deliveryItems di
                        WHERE di.deliveryNoteId = d.id) AS totalQuantity,
                    (SELECT COUNT(*) FROM deliveryItems di WHERE di.deliveryNoteId = d.id) AS lineCount
             FROM routeStops s
             JOIN deliveryNotes d ON d.id = s.deliveryNoteId
             JOIN customers c ON c.id = d.customerId
             WHERE s.routeId = :id ORDER BY s.sequence',
            ['id' => $id]
        );
        foreach ($route['stops'] as $index => $stop) {
            $route['stops'][$index] = [
                'id' => (int) $stop['id'],
                'sequence' => (int) $stop['sequence'],
                'status' => (string) $stop['status'],
                'arrivedAt' => $stop['arrivedAt'],
                'remarks' => (string) $stop['remarks'],
                'deliveryNoteId' => (int) $stop['deliveryNoteId'],
                'noteNumber' => (string) $stop['noteNumber'],
                'noteStatus' => (string) $stop['noteStatus'],
                'signedBy' => (string) $stop['signedBy'],
                'customerName' => (string) $stop['customerName'],
                'customerCode' => (string) $stop['customerCode'],
                'customerWilaya' => (string) $stop['customerWilaya'],
                'customerPhone' => (string) $stop['customerPhone'],
                'customerAddress' => (string) $stop['customerAddress'],
                'totalQuantity' => round((float) $stop['totalQuantity'], 3),
                'lineCount' => (int) $stop['lineCount'],
            ];
        }

        return $route;
    }

    /**
     * Creates the tour from the notes that must ship. The notes are attached to the tour here, so a
     * prepared note cannot be planned on two trucks.
     *
     * @param array<string,mixed> $data @return array<string,mixed>
     */
    public function createRoute(array $data, int $userId): array
    {
        $routeDate = trim((string) ($data['routeDate'] ?? ''));
        $routeDate = $routeDate === '' ? date('Y-m-d') : $routeDate;
        if (\DateTimeImmutable::createFromFormat('Y-m-d', $routeDate) === false) {
            throw ApiException::validation(['routeDate' => 'validation.date']);
        }
        $vehicleId = (int) ($data['vehicleId'] ?? 0);
        if ($vehicleId <= 0) {
            throw ApiException::validation(['vehicleId' => 'validation.required']);
        }
        $vehicle = $this->db->first('SELECT id, isActive, capacityKg FROM vehicles WHERE id = :id',
            ['id' => $vehicleId]);
        if ($vehicle === null) {
            throw ApiException::validation(['vehicleId' => 'validation.exists']);
        }

        $noteIds = [];
        foreach ((array) ($data['deliveryNoteIds'] ?? []) as $noteId) {
            $id = (int) $noteId;
            if ($id > 0 && !in_array($id, $noteIds, true)) {
                $noteIds[] = $id;
            }
        }
        if ($noteIds === []) {
            throw ApiException::validation(['deliveryNoteIds' => 'validation.required']);
        }

        $notes = [];
        foreach ($noteIds as $noteId) {
            $note = $this->db->first(
                'SELECT d.id, d.noteNumber, d.status, d.routeId FROM deliveryNotes d WHERE d.id = :id',
                ['id' => $noteId]
            );
            if ($note === null) {
                throw ApiException::validation(['deliveryNoteIds' => 'validation.exists']);
            }
            if ($note['routeId'] !== null) {
                throw ApiException::conflict('A delivery note is already on a tour.', 'NOTE_ALREADY_PLANNED',
                    ['noteNumber' => $note['noteNumber']]);
            }
            if (in_array($note['status'], ['DELIVERED', 'RETURNED'], true)) {
                throw ApiException::conflict('A delivered or returned note cannot be planned on a tour.',
                    'NOTE_NOT_PLANNABLE', ['noteNumber' => $note['noteNumber'], 'status' => $note['status']]);
            }
            $notes[] = $note;
        }

        $routeId = $this->db->transaction(function () use ($data, $routeDate, $vehicleId, $notes, $userId): int {
            $this->db->execute(
                'INSERT INTO deliveryRoutes (routeNumber, routeDate, vehicleId, driverId, representativeId,
                        wilayaZone, status, fuelCost, createdAt)
                 VALUES (:number, :routeDate, :vehicleId, :driverId, :representativeId, :wilayaZone, \'PLANNED\',
                        :fuelCost, NOW())',
                [
                    'number' => $this->nextNumber(),
                    'routeDate' => $routeDate,
                    'vehicleId' => $vehicleId,
                    'driverId' => $this->driverId($data['driverId'] ?? null),
                    'representativeId' => $userId,
                    'wilayaZone' => mb_substr(trim((string) ($data['wilayaZone'] ?? '')), 0, 120),
                    'fuelCost' => max(0.0, round((float) ($data['fuelCost'] ?? 0), 2)),
                ]
            );
            $id = $this->db->lastId();

            $sequence = 1;
            foreach ($notes as $note) {
                $this->db->execute(
                    'INSERT INTO routeStops (routeId, deliveryNoteId, sequence, status, remarks)
                     VALUES (:routeId, :noteId, :sequence, \'PENDING\', \'\')',
                    ['routeId' => $id, 'noteId' => (int) $note['id'], 'sequence' => $sequence]
                );
                $this->db->execute('UPDATE deliveryNotes SET routeId = :routeId WHERE id = :noteId',
                    ['routeId' => $id, 'noteId' => (int) $note['id']]);
                $sequence++;
            }

            return $id;
        });

        return $this->route($routeId);
    }

    /** Loading is optional: it simply records that the truck is being filled. */
    public function loadRoute(int $id): array
    {
        $route = $this->route($id);
        if ($route['status'] !== 'PLANNED') {
            throw $this->wrongStatus($route['status'], ['PLANNED']);
        }
        $this->db->execute('UPDATE deliveryRoutes SET status = \'LOADING\' WHERE id = :id', ['id' => $id]);
        $this->db->execute(
            'UPDATE deliveryNotes d JOIN routeStops s ON s.deliveryNoteId = d.id SET d.status = \'LOADED\'
             WHERE s.routeId = :id AND d.status = \'PREPARED\'',
            ['id' => $id]
        );

        return $this->route($id);
    }

    /** Leaving the yard puts every note of the tour in transit. */
    public function startRoute(int $id): array
    {
        $route = $this->route($id);
        if (!in_array($route['status'], ['PLANNED', 'LOADING'], true)) {
            throw $this->wrongStatus($route['status'], ['PLANNED', 'LOADING']);
        }
        $this->db->transaction(function () use ($id): void {
            $this->db->execute(
                'UPDATE deliveryRoutes SET status = \'IN_TRANSIT\', openedAt = NOW() WHERE id = :id', ['id' => $id]);
            $this->db->execute(
                'UPDATE deliveryNotes d JOIN routeStops s ON s.deliveryNoteId = d.id SET d.status = \'IN_TRANSIT\'
                 WHERE s.routeId = :id AND d.status IN (\'PREPARED\', \'LOADED\')',
                ['id' => $id]
            );
        });

        return $this->route($id);
    }

    /**
     * Signs one stop. A DELIVERED stop signs the delivery note for the customer; a REFUSED stop keeps
     * the note alive so it can be returned or re-planned.
     *
     * @param array<string,mixed> $data @return array<string,mixed>
     */
    public function deliverStop(int $routeId, int $stopId, array $data, int $userId): array
    {
        $route = $this->route($routeId);
        if ($route['status'] !== 'IN_TRANSIT') {
            throw $this->wrongStatus($route['status'], ['IN_TRANSIT']);
        }
        $stop = null;
        foreach ($route['stops'] as $candidate) {
            if ($candidate['id'] === $stopId) {
                $stop = $candidate;
                break;
            }
        }
        if ($stop === null) {
            throw ApiException::notFound('Route stop');
        }
        if ($stop['status'] !== 'PENDING') {
            throw ApiException::conflict('This stop is already settled.', 'STOP_SETTLED',
                ['status' => $stop['status']]);
        }

        $outcome = strtoupper(trim((string) ($data['status'] ?? 'DELIVERED')));
        if (!in_array($outcome, ['DELIVERED', 'PARTIAL', 'REFUSED'], true)) {
            throw ApiException::validation(['status' => 'validation.allowed_values']);
        }
        $remarks = mb_substr(trim((string) ($data['remarks'] ?? '')), 0, 255);
        if ($outcome === 'REFUSED' && $remarks === '') {
            throw ApiException::validation(['remarks' => 'validation.required']);
        }

        $this->db->transaction(function () use ($stopId, $stop, $outcome, $remarks, $data, $userId): void {
            $this->db->execute(
                'UPDATE routeStops SET status = :status, arrivedAt = NOW(), remarks = :remarks WHERE id = :id',
                ['status' => $outcome, 'remarks' => $remarks, 'id' => $stopId]
            );
            if ($outcome !== 'REFUSED') {
                $this->sales->deliverDelivery((int) $stop['deliveryNoteId'], [
                    'signedBy' => $data['signedBy'] ?? $stop['customerName'],
                    'signaturePath' => $data['signaturePath'] ?? null,
                    'notes' => $remarks,
                ], $userId);
            }
        });

        return $this->route($routeId);
    }

    /**
     * Closing the day: stops nobody settled send their notes back to PREPARED so they can be
     * re-planned, and the tour becomes COMPLETED.
     *
     * @return array<string,mixed>
     */
    public function completeRoute(int $id, array $data): array
    {
        $route = $this->route($id);
        if (!in_array($route['status'], ['IN_TRANSIT', 'LOADING'], true)) {
            throw $this->wrongStatus($route['status'], ['IN_TRANSIT', 'LOADING']);
        }

        $this->db->transaction(function () use ($id, $data): void {
            $this->db->execute(
                'UPDATE deliveryNotes d JOIN routeStops s ON s.deliveryNoteId = d.id
                 SET d.status = \'PREPARED\'
                 WHERE s.routeId = :id AND d.status = \'IN_TRANSIT\'',
                ['id' => $id]
            );
            $this->db->execute(
                'UPDATE deliveryRoutes SET status = \'COMPLETED\', closedAt = NOW(), fuelCost = :fuelCost
                 WHERE id = :id',
                [
                    'fuelCost' => max(0.0, round((float) ($data['fuelCost'] ?? 0), 2)),
                    'id' => $id,
                ]
            );
        });

        return $this->route($id);
    }

    /** @return array<string,mixed> */
    public function cancelRoute(int $id): array
    {
        $route = $this->route($id);
        if (!in_array($route['status'], ['PLANNED', 'LOADING'], true)) {
            throw $this->wrongStatus($route['status'], ['PLANNED', 'LOADING']);
        }
        $this->db->transaction(function () use ($id): void {
            $this->db->execute(
                'UPDATE deliveryNotes d JOIN routeStops s ON s.deliveryNoteId = d.id
                 SET d.status = \'PREPARED\', d.routeId = NULL
                 WHERE s.routeId = :id',
                ['id' => $id]
            );
            $this->db->execute('UPDATE deliveryRoutes SET status = \'CANCELLED\', closedAt = NOW() WHERE id = :id',
                ['id' => $id]);
        });

        return $this->route($id);
    }

    /** @param array<string,mixed> $row @return array<string,mixed> */
    private function hydrateRoute(array $row): array
    {
        return [
            'id' => (int) $row['id'],
            'routeNumber' => (string) $row['routeNumber'],
            'routeDate' => (string) $row['routeDate'],
            'vehicleId' => (int) $row['vehicleId'],
            'plate' => (string) ($row['plate'] ?? ''),
            'model' => (string) ($row['model'] ?? ''),
            'driverId' => $row['driverId'] === null ? null : (int) $row['driverId'],
            'driverName' => $row['driverName'] ?? null,
            'representativeName' => $row['representativeName'] ?? null,
            'wilayaZone' => (string) $row['wilayaZone'],
            'status' => (string) $row['status'],
            'fuelCost' => round((float) $row['fuelCost'], 2),
            'openedAt' => $row['openedAt'],
            'closedAt' => $row['closedAt'],
            'stopCount' => (int) ($row['stopCount'] ?? 0),
            'deliveredCount' => (int) ($row['deliveredCount'] ?? 0),
            'totalQuantity' => round((float) ($row['totalQuantity'] ?? 0), 3),
        ];
    }

    private function driverId(mixed $value): ?int
    {
        $id = (int) ($value ?? 0);
        if ($id <= 0) {
            return null;
        }
        if ($this->db->first('SELECT id FROM employees WHERE id = :id AND deletedAt IS NULL', ['id' => $id]) === null) {
            throw ApiException::validation(['driverId' => 'validation.exists']);
        }

        return $id;
    }

    private function nextNumber(): string
    {
        $sequence = (int) $this->db->scalar('SELECT COUNT(*) FROM deliveryRoutes') + 1;
        do {
            $number = sprintf('TR-%05d', $sequence);
            $exists = $this->db->first('SELECT id FROM deliveryRoutes WHERE routeNumber = :number',
                ['number' => $number]) !== null;
            $sequence++;
        } while ($exists);

        return $number;
    }

    /** @param array<int,string> $allowed */
    private function wrongStatus(string $current, array $allowed): ApiException
    {
        return ApiException::conflict(
            'This tour is ' . $current . '; the action needs ' . implode(' or ', $allowed) . '.',
            'INVALID_STATUS',
            ['current' => $current, 'allowed' => $allowed]
        );
    }
}
