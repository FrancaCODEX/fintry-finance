# Ambientes

| Ambiente | Inicialização | Dados / e-mail |
|---|---|---|
| Docker local | docker compose up --build -d | PostgreSQL no volume local; Mailpit acessível em localhost:8025; HTTP apenas em loopback |
| demo | spring.profiles.active=demo | H2 de avaliação; não é a base de produção |
| test / postgres-test | Maven verify / CI | H2 ou PostgreSQL isolados; envio de e-mail capturado nos testes |
| prod | deploy/compose.production.yml | PostgreSQL e app sem portas publicadas; Caddy com HTTPS e SMTP autenticado com TLS |

O perfil prod exige PostgreSQL, Flyway, Hibernate validate, senha do banco com pelo menos 20 caracteres, URL HTTPS, cookies Secure e SMTP habilitado com autenticação/certificado válido. Não combine prod com demo/dev/test/postgres-test. A senha e valores das configurações não aparecem nas mensagens de validação próprias do guard.

A composição de produção usa projeto/volumes próprios e não reutiliza o banco local automaticamente. O arquivo deploy/production.env fica fora do Git e da imagem Docker. O build usa Java 21; o override Java 17 serve apenas à avaliação local quando necessário.
