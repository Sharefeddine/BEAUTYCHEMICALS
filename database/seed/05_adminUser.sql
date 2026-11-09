-- =============================================================================
-- ChemManager ERP - seed 05: first administrator account
--
-- The hash below is bcrypt ($2y$, cost 12) of the temporary password "admin123".
-- It is flagged mustChangePassword = 1: the API refuses to do anything else than
-- changing it on first sign-in. Replace it during installation:
--   php backend/tools/console.php user:password admin '<new-password>'
-- =============================================================================

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

INSERT INTO users (username, passwordHash, fullName, email, phone, jobTitle, isActive, mustChangePassword)
VALUES ('admin',
        '$2y$12$LQ42Skfx4pWNQPRc6fBDuOQ9zRbN9gbND4JwW7B41DjvXJuQP7fIy',
        'Administrateur système', 'admin@chempro.dz', '+213 000 00 00 00', 'Super Administrateur', 1, 1)
ON DUPLICATE KEY UPDATE email = VALUES(email);

INSERT INTO userRoles (userId, roleId)
SELECT u.id, r.id FROM users u JOIN roles r ON r.code = 'SUPER_ADMIN'
WHERE u.username = 'admin'
  AND NOT EXISTS (SELECT 1 FROM userRoles ur WHERE ur.userId = u.id AND ur.roleId = r.id);

-- one department manager to keep the org chart coherent
UPDATE departments SET managerId = (SELECT id FROM users WHERE username = 'admin')
WHERE code = 'DIRECTION' AND managerId IS NULL;
