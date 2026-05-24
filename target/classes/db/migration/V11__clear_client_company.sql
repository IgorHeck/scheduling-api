-- Clientes não devem ter company vinculada (escolhem a clínica no momento do agendamento)
UPDATE users SET company_id = NULL WHERE role = 'CLIENT';
