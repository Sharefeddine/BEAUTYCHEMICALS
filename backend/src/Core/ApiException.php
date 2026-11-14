<?php

declare(strict_types=1);

namespace ChemManager\Api\Core;

use RuntimeException;
use Throwable;

/**
 * Every expected failure is thrown as an ApiException so the HTTP status, the machine readable
 * code and the JSON envelope stay consistent whatever the controller does.
 */
final class ApiException extends RuntimeException
{
    public function __construct(
        public readonly int $status,
        public readonly string $errorCode,
        string $message,
        public readonly array $details = [],
        ?Throwable $previous = null
    ) {
        parent::__construct($message, 0, $previous);
    }

    public static function validation(array $details, string $message = 'The submitted data is invalid.'): self
    {
        return new self(422, 'VALIDATION_FAILED', $message, $details);
    }

    public static function unauthorized(string $message = 'Authentication required.'): self
    {
        return new self(401, 'UNAUTHENTICATED', $message);
    }

    public static function forbidden(string $message = 'You do not have the required permission.'): self
    {
        return new self(403, 'FORBIDDEN', $message);
    }

    public static function notFound(string $what = 'Resource'): self
    {
        return new self(404, 'NOT_FOUND', $what . ' not found.');
    }

    public static function conflict(string $message, string $code = 'CONFLICT', array $details = []): self
    {
        return new self(409, $code, $message, $details);
    }

    public static function tooManyRequests(string $message, int $retryAfter = 120): self
    {
        return new self(429, 'TOO_MANY_REQUESTS', $message, ['retryAfter' => $retryAfter]);
    }

    public static function passwordChangeRequired(): self
    {
        return new self(403, 'PASSWORD_CHANGE_REQUIRED', 'The password must be changed first.');
    }

    public static function methodNotAllowed(array $allowed): self
    {
        return new self(405, 'METHOD_NOT_ALLOWED', 'Method not allowed for this endpoint.', ['allowed' => $allowed]);
    }
}
