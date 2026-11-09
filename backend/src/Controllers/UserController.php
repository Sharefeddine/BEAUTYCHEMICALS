<?php

declare(strict_types=1);

namespace ChemManager\Api\Controllers;

use ChemManager\Api\Core\ApiException;
use ChemManager\Api\Core\Request;
use ChemManager\Api\Core\Response;
use ChemManager\Api\Core\Validator;
use ChemManager\Api\Http\App;

final class UserController
{
    private const PAGE_SIZE_MAX = 200;

    public function __construct(private readonly App $app)
    {
    }

    public function index(Request $request): Response
    {
        $page = max(1, $request->queryInt('page', 1));
        $pageSize = min(self::PAGE_SIZE_MAX, max(1, $request->queryInt('pageSize', 25)));
        $result = $this->app->users->paginate(
            $page,
            $pageSize,
            $request->queryString('search'),
            $request->queryString('sort', 'username'),
            $request->queryString('direction', 'ASC'),
            $request->queryString('status') === 'active' ? true : null
        );

        return Response::ok($result['rows'], $this->meta($page, $pageSize, $result['total']));
    }

    public function show(Request $request): Response
    {
        $id = (int) $request->params['id'];

        return Response::ok($this->app->users->find($id));
    }

    public function store(Request $request): Response
    {
        $data = $this->validated($request, true);
        $created = $this->app->users->create($data, $this->actor($request));
        $this->app->audit->log($request->user, 'CREATE', 'USERS', 'users', $created['id'],
            ['username' => $created['username']], 'INFO', $request->ip);

        return Response::created($created);
    }

    public function update(Request $request): Response
    {
        $id = (int) $request->params['id'];
        $data = $this->validated($request, false);
        $updated = $this->app->users->update($id, $data, $this->actor($request));
        $this->app->audit->log($request->user, 'UPDATE', 'USERS', 'users', $id,
            array_keys($data), 'INFO', $request->ip);

        return Response::ok($updated);
    }

    public function destroy(Request $request): Response
    {
        $id = (int) $request->params['id'];
        if ($this->actor($request) === $id) {
            throw ApiException::conflict('You cannot delete your own account.', 'SELF_DELETE');
        }
        $this->app->users->softDelete($id, $this->actor($request));
        $this->app->audit->log($request->user, 'DELETE', 'USERS', 'users', $id, [], 'WARNING', $request->ip);

        return Response::ok(['deleted' => true, 'id' => $id]);
    }

    public function resetPassword(Request $request): Response
    {
        $id = (int) $request->params['id'];
        $password = (string) ($request->body['password'] ?? '');
        if (strlen($password) < 8 || !preg_match('/[A-Za-z]/', $password) || !preg_match('/\d/', $password)) {
            throw ApiException::validation(['password' => 'validation.password_weak']);
        }
        $this->app->users->resetPassword($id, $password);
        $this->app->audit->log($request->user, 'UPDATE', 'USERS', 'users', $id,
            ['field' => 'password', 'mode' => 'reset'], 'WARNING', $request->ip);

        return Response::ok(['reset' => true, 'id' => $id, 'mustChangePassword' => true]);
    }

    public function roles(Request $request): Response
    {
        return Response::ok($this->app->users->roles());
    }

    public function permissions(Request $request): Response
    {
        return Response::ok($this->app->users->permissions());
    }

    public function departments(Request $request): Response
    {
        return Response::ok($this->app->users->departments());
    }

    public function rolePermissions(Request $request): Response
    {
        $id = (int) $request->params['id'];

        return Response::ok([
            'roleId' => $id,
            'permissionIds' => $this->app->users->rolePermissionIds($id),
        ]);
    }

    public function updateRolePermissions(Request $request): Response
    {
        $id = (int) $request->params['id'];
        $permissionIds = $request->body['permissionIds'] ?? [];
        if (!is_array($permissionIds)) {
            throw ApiException::validation(['permissionIds' => 'validation.allowed_values']);
        }
        $this->app->users->syncRolePermissions($id, $permissionIds);
        $this->app->audit->log($request->user, 'UPDATE', 'USERS', 'roles', $id,
            ['permissionIds' => array_values(array_map('intval', $permissionIds))], 'WARNING', $request->ip);

        return Response::ok([
            'roleId' => $id,
            'permissionIds' => $this->app->users->rolePermissionIds($id),
        ]);
    }

    private function validated(Request $request, bool $creating): array
    {
        $validator = (new Validator($request->body))
            ->email('email', $creating)
            ->pattern('phone', '/^[+0-9 ()-]{6,40}$/');

        if ($creating) {
            $validator->required('username', 40)->pattern('username', '/^[a-zA-Z0-9._-]{3,40}$/')
                ->required('fullName', 120)->required('password', 200);
        }
        $validator->validate();

        $data = [
            'fullName' => $request->string('fullName'),
            'email' => $request->string('email'),
            'phone' => $request->string('phone'),
            'jobTitle' => $request->string('jobTitle'),
            'departmentId' => $request->int('departmentId') > 0 ? $request->int('departmentId') : null,
            'isActive' => $request->boolean('isActive', true),
        ];
        if ($creating) {
            $data['username'] = $request->string('username');
            $data['password'] = (string) $request->body['password'];
        }
        if (array_key_exists('roleIds', $request->body)) {
            $data['roleIds'] = array_map('intval', (array) $request->body['roleIds']);
        }

        return $data;
    }

    private function actor(Request $request): int
    {
        return (int) $request->user['id'];
    }

    private function meta(int $page, int $pageSize, int $total): array
    {
        return [
            'page' => $page,
            'pageSize' => $pageSize,
            'total' => $total,
            'totalPages' => (int) ceil($total / max(1, $pageSize)),
        ];
    }
}
