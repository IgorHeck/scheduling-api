-- V10: Vincula todos os usuários sem empresa à company 1
-- Necessário após implantação do multi-tenancy por company (roteamento /:companyId/dashboard).
-- ADMIN e MANAGER precisam de company_id para acessar o dashboard.
-- CLIENT também é incluído para consistência, mas não afeta o acesso deles.

UPDATE users
SET company_id = 1
WHERE company_id IS NULL;
