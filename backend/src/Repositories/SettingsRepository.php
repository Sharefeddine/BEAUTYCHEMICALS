<?php

declare(strict_types=1);

namespace ChemManager\Api\Repositories;

use ChemManager\Api\Core\Database;

/**
 * Key/value settings plus the company identity printed on documents.
 */
final class SettingsRepository
{
    public function __construct(private readonly Database $db)
    {
    }

    /** @return array<int,array> */
    public function all(): array
    {
        return $this->db->select(
            'SELECT settingKey, value, valueType, settingGroup, labelKey FROM settings ORDER BY settingGroup, settingKey'
        );
    }

    /** @return array<int,array> */
    public function grouped(): array
    {
        $groups = [];
        foreach ($this->all() as $row) {
            $groups[$row['settingGroup']][] = $row;
        }

        return $groups;
    }

    /** @param array<string,string|int|float|bool> $values */
    public function updateMany(array $values, ?int $actorId): int
    {
        $changed = 0;
        $this->db->transaction(function () use ($values, $actorId, &$changed): void {
            foreach ($values as $key => $value) {
                $updated = $this->db->execute(
                    'UPDATE settings SET value = :value, updatedBy = :actor WHERE settingKey = :key',
                    ['value' => is_bool($value) ? ($value ? '1' : '0') : (string) $value,
                        'actor' => $actorId, 'key' => $key]
                );
                if ($updated > 0) {
                    $changed++;
                }
            }
        });

        return $changed;
    }

    public function get(string $key, ?string $default = null): ?string
    {
        $value = $this->db->scalar('SELECT value FROM settings WHERE settingKey = :key', ['key' => $key]);

        return $value === null ? $default : (string) $value;
    }

    public function company(): ?array
    {
        return $this->db->first(
            'SELECT id, name, legalName, activity, address, city, wilaya, postalCode, phone, email, website,
                    taxId, tradeRegister, nif, taxArticle, currency, vatRate, invoicePrefix, quotationPrefix,
                    deliveryPrefix, logoPath
             FROM companies ORDER BY id LIMIT 1'
        );
    }

    public function updateCompany(array $data, ?int $actorId): ?array
    {
        $current = $this->company();
        if ($current === null) {
            return null;
        }
        $this->db->execute(
            'UPDATE companies SET name = :name, legalName = :legalName, activity = :activity, address = :address,
                    city = :city, wilaya = :wilaya, postalCode = :postalCode, phone = :phone, email = :email,
                    taxId = :taxId, tradeRegister = :tradeRegister, nif = :nif, taxArticle = :taxArticle,
                    vatRate = :vatRate, invoicePrefix = :invoicePrefix, quotationPrefix = :quotationPrefix,
                    deliveryPrefix = :deliveryPrefix, updatedBy = :actor
             WHERE id = :id',
            [
                'name' => $data['name'] ?? $current['name'],
                'legalName' => $data['legalName'] ?? $current['legalName'],
                'activity' => $data['activity'] ?? $current['activity'],
                'address' => $data['address'] ?? $current['address'],
                'city' => $data['city'] ?? $current['city'],
                'wilaya' => $data['wilaya'] ?? $current['wilaya'],
                'postalCode' => $data['postalCode'] ?? $current['postalCode'],
                'phone' => $data['phone'] ?? $current['phone'],
                'email' => $data['email'] ?? $current['email'],
                'taxId' => $data['taxId'] ?? $current['taxId'],
                'tradeRegister' => $data['tradeRegister'] ?? $current['tradeRegister'],
                'nif' => $data['nif'] ?? $current['nif'],
                'taxArticle' => $data['taxArticle'] ?? $current['taxArticle'],
                'vatRate' => (float) ($data['vatRate'] ?? $current['vatRate']),
                'invoicePrefix' => $data['invoicePrefix'] ?? $current['invoicePrefix'],
                'quotationPrefix' => $data['quotationPrefix'] ?? $current['quotationPrefix'],
                'deliveryPrefix' => $data['deliveryPrefix'] ?? $current['deliveryPrefix'],
                'actor' => $actorId,
                'id' => (int) $current['id'],
            ]
        );

        return $this->company();
    }
}
