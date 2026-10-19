# Autenticação e equipe

Todas as rotas estão em `/api/v1`. Antes de qualquer escrita, obtenha `GET /auth/csrf` e envie o token no header retornado. Use cookies da mesma sessão. Renove o CSRF após login, logout, ativação ou troca/reset de senha.

| Método e rota | Acesso | Corpo/retorno |
|---|---|---|
| POST /auth/register | Público + CSRF | name, companyName, document, email, password; 201 |
| POST /auth/login | Público + CSRF | email, password; usuário com role e permissions |
| GET /auth/me | Autenticado | id, name, email, companyId, companyName, role, permissions |
| POST /auth/logout | Sessão + CSRF | 204 |
| POST /auth/forgot-password | Público + CSRF | email; 202 genérico, 503 se desabilitado |
| POST /auth/reset-password | Público + CSRF | token, password; 204 |
| POST /auth/invitation | Público + CSRF | token; name, email, companyName, role, expiresAt |
| POST /auth/accept-invite | Público + CSRF | token, name, password; 204 |
| POST /auth/change-password | Autenticado + CSRF | currentPassword, password; 204, encerra sessões |
| GET /users | ADMIN | search, role, page, size (1–100); content + paginação + active/pending |
| POST /users | ADMIN + CSRF | name, email, role; 201 com link e expiresAt |
| PUT /users/{id} | ADMIN + CSRF | name, role, active; 204 |
| POST /users/{id}/invitation | ADMIN + CSRF | 200 com novo link e expiresAt; somente inativos |
| DELETE /users/{id}/invitation | ADMIN + CSRF | Revoga convite pendente; 204 |
| GET /audit | ADMIN | start/end obrigatórios (até 366 dias), resource, search, page, size (1–100) |

Perfis: ADMIN, FINANCE, VIEWER. Senhas de 10–64 caracteres e no máximo 72 bytes UTF-8 (BCrypt). Token é Base64 URL de 43 caracteres; expirado, revogado, usado ou de finalidade incorreta retorna 400. Nenhuma rota pública devolve token de recuperação. Limites de tentativas retornam 429. Convite e recuperação não autenticam automaticamente.

`GET /app` retorna a versão e `passwordRecoveryAvailable`. Recursos de equipe/histórico são isolados por empresa; id de outra empresa retorna 404. O histórico retorna content, totalElements, totalPages, number; cada evento inclui actorName, action, resource, resourceId, label, occurredAt e changes [{field,before,after}]. Horários são Instants UTC; a interface exibe Brasília.

[Regras de segurança e configuração](../03-architecture/security.md)
