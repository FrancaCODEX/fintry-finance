# Ambientes

| Ambiente | Inicialização | Dados / e-mail |
|---|---|---|
| Docker local | docker compose up --build -d | PostgreSQL no volume local; Mailpit acessível em localhost:8025; HTTP apenas em loopback |
| demo | spring.profiles.active=demo | H2 de avaliação; não é a base de produção |
| test / postgres-test | Maven verify / CI | H2 ou PostgreSQL isolados; envio de e-mail capturado nos testes |
| prod | deploy/compose.production.yml | PostgreSQL e app sem portas publicadas; Caddy com HTTPS e SMTP autenticado com TLS |

O perfil prod exige PostgreSQL, Flyway, Hibernate validate, senha do banco com pelo menos 20 caracteres, URL HTTPS, cookies Secure e SMTP habilitado com autenticação/certificado válido. Não combine prod com demo/dev/test/postgres-test. A senha e valores das configurações não aparecem nas mensagens de validação próprias do guard.

A composição de produção usa projeto/volumes próprios e não reutiliza o banco local automaticamente. O arquivo deploy/production.env fica fora do Git e da imagem Docker. O build usa Java 21; o override Java 17 serve apenas à avaliação local quando necessário.

## Cobranças integradas

`BILLING_MASTER_KEY` habilita a configuração Asaas; deve conter 32 bytes em Base64 e ser preservada fora do banco, em cofre do operador. Compose local e de produção repassam a variável. Não há chave padrão de desenvolvimento. Sem ela, o financeiro continua disponível e Cobranças explica a configuração pendente. Consulte [homologação e operação](../05-api/billing.md).

## Planos SaaS

O Compose local usa `SAAS_MODE=EVALUATION`; o de produção fixa `COMMERCIAL`. O perfil prod recusa simulação. A migração preserva o acesso legado das empresas existentes; simulações não são períodos pagos. Preços e ativação são administrados pelo operador, conforme [operação comercial](../05-api/subscriptions.md).
