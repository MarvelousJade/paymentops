ALTER TABLE customers
    ADD COLUMN role TEXT NOT NULL DEFAULT 'MERCHANT'
        CHECK (role IN ('MERCHANT', 'OPERATOR', 'AUDITOR'));

-- Roles are deliberately not accepted by the public registration API.
-- Provision local operators explicitly, for example:
-- UPDATE customers SET role = 'OPERATOR' WHERE email = 'operator@example.test';
