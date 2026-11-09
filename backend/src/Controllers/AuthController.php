<?php

declare(strict_types=1);

namespace ChemManager\Api\Controllers;

use ChemManager\Api\Core\ApiException;
use ChemManager\Api\Core\Request;
use ChemManager\Api\Core\Response;
use ChemManager\Api\Core\Validator;
use ChemManager\Api\Http\App;

final class AuthController
{
    public function __construct(private readonly App $app)
    {
    }

    public function login(Request $request): Response
    {
        $username = $request->string('username');
        $password = (string) ($request->body['password'] ?? '');

        $validator = (new Validator($request->body))->required('username', 40)->required('password', 200);
        $validator->validate();

        $result = $this->app->auth->login($username, $password, $request->ip, $request->userAgent);

        return Response::ok($result);
    }

    public function me(Request $request): Response
    {
        return Response::ok([
            'user' => $request->user,
            'permissions' => $request->permissions,
            'roles' => $this->app->auth->rolesFor((int) $request->user['id']),
        ]);
    }

    public function logout(Request $request): Response
    {
        $token = $request->bearerToken();
        if ($token !== null) {
            $this->app->auth->logout($token, $request->user);
        }

        return Response::ok(['signedOut' => true]);
    }

    public function changePassword(Request $request): Response
    {
        $current = (string) ($request->body['currentPassword'] ?? '');
        $new = (string) ($request->body['newPassword'] ?? '');

        $validator = (new Validator($request->body))->required('currentPassword')->required('newPassword', 200);
        $validator->validate();
        if ($current === '') {
            throw ApiException::validation(['currentPassword' => 'validation.required']);
        }

        $this->app->auth->changePassword($request->user, $current, $new, $request->ip);

        return Response::ok([
            'changed' => true,
            'message' => 'auth.password_changed',
            'sessionsRevoked' => true,
        ]);
    }
}
