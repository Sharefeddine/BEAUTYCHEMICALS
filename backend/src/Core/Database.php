<?php

declare(strict_types=1);

namespace ChemManager\Api\Core;

use PDO;
use PDOException;
use PDOStatement;

/**
 * Thin PDO wrapper. Every query is a prepared statement with named parameters - there is no
 * string concatenation of user input anywhere in the API.
 */
final class Database
{
    private ?PDO $pdo = null;
    private int $transactionDepth = 0;

    public function __construct(private readonly Config $config)
    {
    }

    public function pdo(): PDO
    {
        if ($this->pdo === null) {
            $dsn = sprintf(
                'mysql:host=%s;port=%d;dbname=%s;charset=utf8mb4',
                $this->config->get('database.host'),
                (int) $this->config->get('database.port'),
                $this->config->get('database.name')
            );
            try {
                $this->pdo = new PDO($dsn, (string) $this->config->get('database.user'),
                    (string) $this->config->get('database.password'), [
                        PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
                        PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
                        PDO::ATTR_EMULATE_PREPARES => false,
                        PDO::ATTR_STRINGIFY_FETCHES => false,
                    ]);
            } catch (PDOException $exception) {
                throw new ApiException(503, 'DATABASE_UNAVAILABLE',
                    'The database is not reachable.', [], $exception);
            }
        }

        return $this->pdo;
    }

    /** @param array<string,mixed> $params */
    public function run(string $sql, array $params = []): PDOStatement
    {
        $statement = $this->pdo()->prepare($sql);
        foreach ($params as $name => $value) {
            $type = match (true) {
                is_int($value) => PDO::PARAM_INT,
                is_bool($value) => PDO::PARAM_BOOL,
                $value === null => PDO::PARAM_NULL,
                default => PDO::PARAM_STR,
            };
            $statement->bindValue(is_int($name) ? $name + 1 : ':' . ltrim((string) $name, ':'), $value, $type);
        }
        try {
            $statement->execute();
        } catch (PDOException $exception) {
            throw self::translate($exception);
        }

        return $statement;
    }

    /** @param array<string,mixed> $params */
    public function select(string $sql, array $params = []): array
    {
        return $this->run($sql, $params)->fetchAll();
    }

    /** @param array<string,mixed> $params */
    public function first(string $sql, array $params = []): ?array
    {
        $row = $this->run($sql, $params)->fetch();

        return $row === false ? null : $row;
    }

    public function scalar(string $sql, array $params = []): mixed
    {
        $value = $this->run($sql, $params)->fetchColumn();

        return $value === false ? null : $value;
    }

    /** @param array<string,mixed> $params */
    public function execute(string $sql, array $params = []): int
    {
        return $this->run($sql, $params)->rowCount();
    }

    public function lastId(): int
    {
        return (int) $this->pdo()->lastInsertId();
    }

    public function transaction(callable $callback): mixed
    {
        if ($this->transactionDepth === 0) {
            $this->pdo()->beginTransaction();
        } else {
            $this->pdo()->exec('SAVEPOINT sp' . $this->transactionDepth);
        }
        $this->transactionDepth++;
        try {
            $result = $callback($this);
            $this->transactionDepth--;
            if ($this->transactionDepth === 0) {
                $this->pdo()->commit();
            } else {
                $this->pdo()->exec('RELEASE SAVEPOINT sp' . $this->transactionDepth);
            }

            return $result;
        } catch (\Throwable $exception) {
            $this->transactionDepth--;
            if ($this->transactionDepth === 0) {
                $this->pdo()->rollBack();
            } else {
                $this->pdo()->exec('ROLLBACK TO SAVEPOINT sp' . $this->transactionDepth);
            }
            throw $exception;
        }
    }

    /** Database triggers signal business rules (locked formulation, immutable invoice...). */
    private static function translate(PDOException $exception): ApiException
    {
        $driverCode = (int) ($exception->errorInfo[1] ?? 0);
        $message = (string) $exception->getMessage();

        if ($driverCode === 1451 || $driverCode === 1452 || str_contains($message, 'foreign key')) {
            return new ApiException(409, 'FOREIGN_KEY',
                'The record is still referenced by other data, or references a missing record.', [], $exception);
        }
        if ($driverCode === 1062 || str_contains($message, 'Duplicate entry')) {
            return new ApiException(422, 'DUPLICATE', 'A record with the same unique value already exists.',
                [], $exception);
        }
        if (str_contains($message, 'SQLSTATE[45000]')) {
            $reason = preg_match('/MESSAGE_TEXT.*?:\s*([a-z.]+)/i', $message, $matches) ? $matches[1] : '';

            return new ApiException(409, 'RULE_VIOLATION',
                'The database refused the change: ' . ($reason !== '' ? $reason : 'business rule not satisfied.'),
                [], $exception);
        }

        return new ApiException(500, 'DATABASE_ERROR', 'A database error occurred.', [], $exception);
    }
}
