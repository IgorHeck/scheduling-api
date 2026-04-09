# Scheduling API

API REST de agendamento construída com Spring Boot 3, PostgreSQL, Redis e Docker.

## Funcionalidades

- **Auth** — registro, login com JWT, refresh token, logout
- **Empresas** — gestão de empresas com configurações de agendamento público
- **Horários** — grade de disponibilidade por profissional + bloqueio de dias/horários
- **Agendamentos** — CRUD completo, fluxo PENDING → CONFIRMED, painel do gestor
- **Notificações** — email, push, preferências por usuário
- **Mensagens** — chat entre cliente e profissional
- **Integrações** — webhooks, Google Calendar
- **Swagger** — documentação interativa em `/swagger-ui.html`

## Roles

| Role    | O que pode fazer                                          |
|---------|-----------------------------------------------------------|
| ADMIN   | Tudo — cria empresa, confirma agenda, gerencia usuários  |
| MANAGER | Gerencia agenda da própria empresa, confirma agendamentos |
| CLIENT  | Solicita agendamentos, acompanha os próprios              |

## Fluxo de agendamento

```
[CLIENT]  POST /api/v1/appointments        → status: PENDING
[ADMIN]   PUT  /api/v1/appointments/{id}/confirm → status: CONFIRMED
[QUALQUER] PUT /api/v1/appointments/{id}/cancel  → status: CANCELLED
```

O dono pode desativar solicitações públicas via `PUT /api/v1/companies/{id}/settings`
com `{ "allowClientBooking": false }`. Nesse modo, apenas ADMIN/MANAGER criam agendamentos.

## Rodando com Docker

```bash
cp .env.example .env
docker compose up -d
```

Acesse:
- **API:** http://localhost:8080
- **Swagger:** http://localhost:8080/swagger-ui.html
- **MailHog (emails dev):** http://localhost:8025

## Rodando localmente

Precisa de PostgreSQL e Redis rodando. Com o Docker Compose você pode subir só os serviços:

```bash
docker compose up postgres redis mailhog -d
./mvnw spring-boot:run
```

## Estrutura do projeto

```
src/main/java/com/scheduling/api/
├── auth/           JWT, login, registro, refresh token
├── user/           Usuários e roles
├── company/        Empresas e configurações
├── scheduling/     Grade de horários e bloqueios
├── appointment/    Agendamentos, calendário, fila pendente
├── notification/   Notificações e preferências
├── message/        Chat cliente ↔ profissional
├── integration/    Webhooks e Google Calendar
├── audit/          Log de ações
├── config/         Security, Swagger, Redis
└── exception/      Handler global de erros
```

## Variáveis de ambiente

| Variável      | Padrão         | Descrição            |
|---------------|----------------|----------------------|
| DB_HOST       | localhost      | Host do PostgreSQL   |
| DB_NAME       | scheduling_db  | Nome do banco        |
| DB_USER       | postgres       | Usuário              |
| DB_PASS       | postgres       | Senha                |
| REDIS_HOST    | localhost      | Host do Redis        |
| JWT_SECRET    | (base64)       | Chave de assinatura JWT |
| MAIL_HOST     | localhost      | SMTP host            |
