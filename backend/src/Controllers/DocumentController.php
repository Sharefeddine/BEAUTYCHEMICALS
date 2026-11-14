<?php

declare(strict_types=1);

namespace ChemManager\Api\Controllers;

use ChemManager\Api\Core\ApiException;
use ChemManager\Api\Core\Request;
use ChemManager\Api\Core\Response;
use ChemManager\Api\Documents\DocumentRenderer;
use ChemManager\Api\Http\App;

/**
 * Printable documents. The desktop client opens this URL in its document view and prints it; the
 * same URL opens in any browser, so a customer can be sent a link instead of a PDF attachment.
 */
final class DocumentController
{
    private const TYPES = ['quotation', 'order', 'delivery', 'invoice', 'statement'];
    private const LANGUAGES = ['fr', 'en', 'ar'];

    public function __construct(private readonly App $app, private readonly DocumentRenderer $renderer)
    {
    }

    public function show(Request $request): Response
    {
        $type = strtolower((string) ($request->params['type'] ?? ''));
        if (!in_array($type, self::TYPES, true)) {
            throw ApiException::notFound('Document');
        }
        $lang = strtolower($request->queryString('lang', 'fr'));
        if (!in_array($lang, self::LANGUAGES, true)) {
            $lang = 'fr';
        }

        $id = (int) $request->params['id'];
        $document = $this->app->sales->document($type, $id, $lang);
        if ($type === 'invoice') {
            $this->app->sales->registerPrint($id);
        }

        return Response::html($this->renderer->render($document, $this->app->settings->company()));
    }
}
