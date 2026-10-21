# Publicação do Fintry — 0.17.0

A entrega prepara uma instalação em servidor Linux com Docker Compose, PostgreSQL 17 e Caddy. O repositório não contém servidor, domínio, conta SMTP ou cópia externa contratados. A primeira publicação pública depende desses dados; não é feita ao integrar um PR.

## Preparar o servidor

Use Docker Engine com Compose 2.24.4 ou superior, Python 3.10+, Git, um domínio apontando para o IP público e portas TCP 80/443 disponíveis. A composição utiliza uma única instância da aplicação. Dimensione memória/disco conforme a quantidade de empresas e comprovantes; o limite inicial da aplicação é 1 GiB, além dos recursos de PostgreSQL/proxy e do build Java.

Na pasta do projeto, em Linux ou WSL:

```bash
cp deploy/production.env.example deploy/production.env
chmod 600 deploy/production.env
openssl rand -hex 32
```

Use o valor gerado como DB_PASSWORD e preencha o arquivo com domínio, contato ACME, remetente e credenciais SMTP. Não compartilhe esse arquivo. Senhas que contêm `$` devem ficar entre aspas simples no formato dotenv do Compose para evitar interpolação. A configuração não é carregada por `source`, nem incluída na imagem Docker.

| Campo | Configuração |
|---|---|
| FINTRY_DOMAIN | Domínio completo, sem https://, porta ou caminho |
| ACME_EMAIL | Contato para certificados do domínio |
| DB_PASSWORD | Senha exclusiva com pelo menos 20 caracteres |
| DB_NAME | fintry inicialmente; numa recuperação, o nome da nova base validada |
| MAIL_FROM | Remetente autorizado pelo provedor SMTP |
| SMTP_HOST/PORT/USERNAME/PASSWORD | Dados do provedor de envio |
| SMTP_STARTTLS/SMTP_SSL | 587: true/false; 465: false/true |
| FINTRY_IMAGE | Tag da versão, inicialmente fintry:0.17.0 |

Configure SPF/DKIM/DMARC conforme seu provedor. O Fintry exige SMTP autenticado, STARTTLS obrigatório ou TLS implícito e verificação do certificado; não é preciso desativar a validação TLS para usar certificados públicos válidos.

## Validar e iniciar

```bash
python3 scripts/ops.py check
docker compose --env-file deploy/production.env -f deploy/compose.production.yml run --rm --no-deps proxy caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile
docker compose --env-file deploy/production.env -f deploy/compose.production.yml build app
docker compose --env-file deploy/production.env -f deploy/compose.production.yml up -d --wait --wait-timeout 240
python3 scripts/ops.py health
```

O Caddy obtém/renova o certificado e redireciona HTTP para HTTPS quando DNS, portas e validação ACME estão disponíveis. PostgreSQL e aplicação não publicam portas no host. Apenas o proxy aceita acesso externo. O perfil prod valida banco, cookies, URL e configuração SMTP antes de aceitar prontidão. A recuperação de senha usa a URL configurada, não o Host enviado pelo visitante.

A composição de produção tem projeto/volumes separados (`fintry-prod`). Ela inicia com uma base nova e **não transfere automaticamente os dados do Docker local ou do H2 demo**. Para levar dados PostgreSQL existentes, faça backup do ambiente de origem e restaure em uma base nova, conforme [backup e recuperação](backups.md). Nunca remova o volume para atualizar.

Após iniciar, crie seu acesso, abra **Ambiente** como Administrador e envie um teste para seu próprio e-mail. O aceite SMTP é exibido separadamente da entrega: confirme recebimento e spam. Confira também a recuperação de senha com seu e-mail. O CI valida o protocolo usando um capturador com TLS, mas isso não valida reputação, DNS de e-mail nem entregabilidade do provedor escolhido.

## Atualizar e voltar uma versão

1. Execute um backup e um teste de restauração antes de atualizar.
2. Preserve a tag/imagem anterior e o arquivo de configuração em armazenamento administrado fora do Git.
3. Atualize o código na main e altere FINTRY_IMAGE para uma tag nova da versão.
4. Execute `build app`, `up -d --wait --wait-timeout 240` e `python3 scripts/ops.py health` com a composição de produção acima.
5. Confira login, indicadores, comprovantes e e-mail no painel Ambiente.

Uma atualização da instância única pode interromper o acesso por alguns instantes e encerra as sessões em memória; as pessoas precisam entrar novamente. O encerramento é gracioso, com até 30 segundos para requisições e 45 segundos antes de o Docker encerrar o processo.

Voltar a imagem não desfaz migrações. Só use uma imagem anterior se ela for compatível com o esquema atual. Se a mudança exigir recuperar o banco, restaure a cópia em uma base nova, confira os dados e altere DB_NAME com a aplicação parada. Preserve a base anterior até confirmar a recuperação. Não há rollback automático destrutivo.

## Limites operacionais

Esta é uma base de implantação em uma única máquina. Alta disponibilidade, várias réplicas/sessões compartilhadas, recuperação ponto a ponto (WAL/PITR), infraestrutura como código por provedor e alertas externos integrados ficam para evolução. Certificados públicos, credenciais SMTP, hospedagem e armazenamento externo precisam ser configurados no ambiente real.

Referências: [Caddy/HTTPS](https://caddyserver.com/docs/automatic-https), [proxy e cabeçalhos](https://caddyserver.com/docs/caddyfile/directives/reverse_proxy), [Docker Compose](https://docs.docker.com/compose/).

Para ativar cobranças, configure também `BILLING_MASTER_KEY` e siga a [homologação Asaas](../05-api/billing.md). A publicação da aplicação e a homologação financeira são verificações operacionais separadas.

O modo comercial é obrigatório em produção. Novos cadastros recebem teste de 14 dias; empresas anteriores à 0.16.0 mantêm acesso legado. [Ativação e limites de planos](../05-api/subscriptions.md).

A 0.17.0 acrescenta a configuração opcional da conta recebedora e dos preços do próprio Fintry. Consulte [mensalidades integradas](../05-api/subscription-payments.md); mantenha a emissão desabilitada até homologar a conta e as condições comerciais.
