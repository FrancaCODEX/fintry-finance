# Disponibilidade e diagnóstico

| Verificação | Acesso | Significado |
|---|---|---|
| /healthz | HTTPS público pelo Caddy | 200/UP quando processo está pronto e banco responde; 503 em indisponibilidade |
| /actuator/health/liveness | Interno à aplicação | Estado do processo, sem depender do banco/SMTP |
| /actuator/health/readiness | Interno à aplicação | Estado de prontidão e banco; usado no healthcheck Docker |
| /api/v1/operations | Administrador autenticado | Versão, início do processo, disponibilidade, modo de e-mail e cookies, sem host/senha/conexão |
| POST /api/v1/operations/mail-test | Administrador + CSRF | Teste somente para o e-mail do próprio acesso, até três por dez minutos |

O Caddy bloqueia /actuator e seus subcaminhos externamente. /healthz retorna só o estado, sem detalhes do banco. Liveness/readiness não fazem consulta de usuário mesmo se vier um cookie de sessão antigo: uma queda do banco não impede verificar que o processo segue vivo. SMTP não faz parte da prontidão; uma falha no provedor de e-mail não precisa bloquear consultas financeiras.

No painel Ambiente, SMTP configurado não significa entrega confirmada. O teste retorna aceite do SMTP; confirme na caixa de entrada/spam. O e-mail de teste não altera senha ou financeiro e registra um evento na auditoria. O limite usa memória do processo e reinicia com a instância.

```bash
python3 scripts/ops.py health
docker compose --env-file deploy/production.env -f deploy/compose.production.yml ps
docker compose --env-file deploy/production.env -f deploy/compose.production.yml logs --tail 100 app proxy db
```

A rotina health exige HTTPS, valida o certificado, não segue redirecionamentos e retorna erro em indisponibilidade. Os exemplos systemd registram falhas no journal; alertas por canal externo ou serviço de uptime dependem da escolha/configuração da operação. Monitore também espaço disponível, uso de memória, falhas SMTP, idade das cópias e restaurações de teste. Não exponha endpoints de configuração, dumps ou logs para acompanhar disponibilidade.
