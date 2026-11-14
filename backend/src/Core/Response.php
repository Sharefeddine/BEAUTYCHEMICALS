<?php

declare(strict_types=1);

namespace ChemManager\Api\Core;

/**
 * One JSON envelope for the whole API.
 *   success: {"success":true,"data":...,"meta":{...}}
 *   failure: {"success":false,"error":{"code":"...","message":"...","details":{...}}}
 */
final class Response
{
    private int $status = 200;
    /** @var array<string,string> */
    private array $headers = ['Content-Type' => 'application/json; charset=utf-8'];
    private array $body = [];
    private ?string $raw = null;

    public static function ok($data = null, array $meta = []): self
    {
        $payload = ['success' => true, 'data' => $data];
        if ($meta !== []) {
            $payload['meta'] = $meta;
        }

        return (new self())->withBody($payload);
    }

    public static function created($data = null): self
    {
        return (new self())->status(201)->withBody(['success' => true, 'data' => $data]);
    }

    public static function error(int $status, string $code, string $message, array $details = []): self
    {
        $error = ['code' => $code, 'message' => $message];
        if ($details !== []) {
            $error['details'] = $details;
        }

        return (new self())->status($status)->withBody(['success' => false, 'error' => $error]);
    }

    /**
     * A printable document (invoice, delivery note, quotation, statement) served as HTML: the
     * desktop client shows it and prints it, and any browser can open the same URL.
     */
    public static function html(string $html, int $status = 200): self
    {
        $response = (new self())->status($status);
        $response->headers['Content-Type'] = 'text/html; charset=utf-8';
        $response->raw = $html;

        return $response;
    }

    public function status(int $status): self
    {
        $this->status = $status;

        return $this;
    }

    public function header(string $name, string $value): self
    {
        $this->headers[$name] = $value;

        return $this;
    }

    public function getStatus(): int
    {
        return $this->status;
    }

    public function json(): string
    {
        return (string) json_encode($this->body, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    }

    public function raw(): ?string
    {
        return $this->raw;
    }

    public function body(): array
    {
        return $this->body;
    }

    public function send(): void
    {
        if (!headers_sent()) {
            http_response_code($this->status);
            foreach ($this->headers as $name => $value) {
                header($name . ': ' . $value);
            }
        }
        echo $this->raw ?? $this->json();
    }

    private function withBody(array $body): self
    {
        $this->body = $body;

        return $this;
    }
}
