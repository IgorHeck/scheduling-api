
-- aplicamos um cadastro admin padrão para sempre iniciar o projeto ser criado, utilizado para facilitar testes.

INSERT INTO users (name, email, password, role, active)
VALUES (
           'Admin',
           'admin@scheduling.com',
           '$2a$12$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', -- senha: password
           'ADMIN',
           true
       ) ON CONFLICT (email) DO NOTHING;