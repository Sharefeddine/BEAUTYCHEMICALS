<?php

declare(strict_types=1);

namespace ChemManager\Api\Core;

/**
 * Collects every field error before answering, so one request never hides the next mistake.
 * Messages are i18n keys resolved by the client in its current language.
 */
final class Validator
{
    /** @var array<string,string> */
    private array $errors = [];
    private array $data;

    public function __construct(array $data)
    {
        $this->data = $data;
    }

    public function required(string $field, int $maxLength = 0): self
    {
        $value = $this->data[$field] ?? null;
        if (!is_scalar($value) || trim((string) $value) === '') {
            $this->errors[$field] = 'validation.required';
        } elseif ($maxLength > 0 && self::length((string) $value) > $maxLength) {
            $this->errors[$field] = 'validation.max_length';
        }

        return $this;
    }

    public function email(string $field, bool $required = false): self
    {
        $value = trim((string) ($this->data[$field] ?? ''));
        if ($value === '') {
            if ($required) {
                $this->errors[$field] = 'validation.required';
            }

            return $this;
        }
        if (filter_var($value, FILTER_VALIDATE_EMAIL) === false) {
            $this->errors[$field] = 'validation.email';
        }

        return $this;
    }

    public function integer(string $field, int $min, int $max): self
    {
        $value = $this->data[$field] ?? null;
        if (!is_numeric($value) || (int) $value < $min || (int) $value > $max) {
            $this->errors[$field] = 'validation.range';
        }

        return $this;
    }

    public function decimal(string $field, float $min, float $max): self
    {
        $value = $this->data[$field] ?? null;
        if (!is_numeric($value) || (float) $value < $min || (float) $value > $max) {
            $this->errors[$field] = 'validation.range';
        }

        return $this;
    }

    public function inList(string $field, array $allowed): self
    {
        $value = $this->data[$field] ?? null;
        if (!in_array($value, $allowed, true)) {
            $this->errors[$field] = 'validation.allowed_values';
        }

        return $this;
    }

    public function pattern(string $field, string $regex, string $key = 'validation.format'): self
    {
        $value = (string) ($this->data[$field] ?? '');
        if ($value !== '' && preg_match($regex, $value) !== 1) {
            $this->errors[$field] = $key;
        }

        return $this;
    }

    /** Unicode length without requiring the mbstring extension. */
    private static function length(string $value): int
    {
        if (function_exists('mb_strlen')) {
            return (int) mb_strlen($value, 'UTF-8');
        }

        return (int) preg_match_all('/./us', $value);
    }

    public function errors(): array
    {
        return $this->errors;
    }

    public function passes(): bool
    {
        return $this->errors === [];
    }

    public function validate(): void
    {
        if (!$this->passes()) {
            throw ApiException::validation($this->errors);
        }
    }
}
