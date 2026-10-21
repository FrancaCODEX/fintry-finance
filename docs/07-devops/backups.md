# Backup e recuperação

Os dados de empresas, usuários, financeiro, estoque, auditoria e comprovantes ficam no PostgreSQL. O backup customizado de pg_dump inclui o banco inteiro, inclusive os bytes dos comprovantes e o histórico Flyway. É uma cópia administrativa de todas as empresas, operada no servidor; ela não é disponibilizada no painel de um administrador de empresa.

## Criar e verificar

Requisitos: Python 3.10+ e Docker Compose no host; o PostgreSQL deve estar em execução. Os utilitários pg_dump/pg_restore usados são os da imagem PostgreSQL 17. No Windows, execute pelo WSL com acesso ao Docker Desktop.

```bash
python3 scripts/ops.py backup --output /var/backups/fintry
python3 scripts/ops.py verify /var/backups/fintry/fintry-ARQUIVO.dump
python3 scripts/ops.py restore-drill /var/backups/fintry/fintry-ARQUIVO.dump
```

Para o banco do Docker local:

```bash
python3 scripts/ops.py --local backup
python3 scripts/ops.py --local restore-drill backups/fintry-ARQUIVO.dump
```

O modo local usa docker-compose.yml e o .env opcional da raiz. O modo padrão usa deploy/compose.production.yml e deploy/production.env. Use `--env-file /caminho/production.env` **antes** do comando se a configuração estiver em outro local. Não há backup de H2 demo por esta ferramenta.

A rotina confirma o banco/usuário do container contra a configuração, grava `.part`, verifica a leitura do arquivo e publica `.dump` com um manifesto `.dump.json` contendo data UTC, tamanho, SHA-256 e versão do pg_dump. Arquivos novos ficam com permissão 600; diretórios novos usam 700. Arquivos parciais da operação são removidos nas falhas tratadas; um desligamento abrupto pode deixar arquivos .part, que não são considerados cópias válidas. Backups completos anteriores são preservados. Mantenha também os diretórios existentes com acesso restrito.

`verify` confere manifesto/checksum e lista do arquivo, mas não declara restauração testada. `restore-drill` cria uma base temporária com nome aleatório, restaura em transação, verifica tabelas/histórico e retorna contagens. Se concluir, remove somente a base criada por aquele teste. Falhas mantêm a base isolada disponível para análise e não substituem a base em uso. Não execute migrações ao mesmo tempo que o backup.

O checksum detecta alterações acidentais; não é uma assinatura de autenticidade. Restaure somente cópias administradas por você. Guarde o `.dump` e o `.dump.json` juntos e copie-os para armazenamento externo criptografado, com credenciais e retenção próprias. Uma cópia no mesmo servidor não protege contra perda dessa máquina.

## Recuperar em nova base

Copie o par de arquivos para o servidor e execute:

```bash
python3 scripts/ops.py restore /caminho/fintry-ARQUIVO.dump
```

A rotina sempre cria uma base `fintry_restore_...`; não aceita sobrescrever um banco existente e não usa `pg_restore --create`. A saída informa o nome novo. Confira a cópia antes de utilizá-la. Para promovê-la:

1. Pare a aplicação e o proxy da composição de produção. Se o ambiente atual ainda recebe dados, tire uma cópia final antes da troca e impeça novas gravações.
2. Confira esquema, empresas, valores realizados/pendentes, vínculos, usuários e download de comprovantes na base restaurada. Use uma instância de validação isolada se necessário.
3. Ajuste DB_NAME para o nome retornado pela restauração. Mantenha a imagem da aplicação compatível com a versão do banco; Flyway pode aplicar migrações novas ao iniciar.
4. Suba a composição, verifique `/healthz`, entre novamente e confira os fluxos principais.
5. Preserve a base anterior e os arquivos de backup até encerrar a conferência.

A restauração conserva hashes de senha e tokens já armazenados; sessões HTTP em memória não são restauradas. Configuração SMTP, segredos, código/imagens e volumes de certificados do Caddy não fazem parte do dump e precisam ser administrados separadamente.

## Rotina diária e monitoramento

Os exemplos em `deploy/systemd` assumem código em `/opt/fintry`, configuração em `/etc/fintry/production.env`, usuário de sistema `fintry` com acesso ao grupo docker e diretório `/var/backups/fintry` pertencente a ele. Ajuste os caminhos antes de instalar.

```bash
sudo install -d -m 700 -o fintry -g fintry /var/backups/fintry /etc/fintry
sudo install -m 600 -o fintry -g fintry deploy/production.env /etc/fintry/production.env
sudo cp deploy/systemd/fintry-*.service deploy/systemd/fintry-*.timer /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now fintry-backup.timer fintry-health.timer
systemctl list-timers 'fintry-*'
journalctl -u fintry-backup.service -u fintry-health.service --since today
```

O timer de backup executa diariamente às 06:00 UTC (03:00 de Brasília), com atraso aleatório de até cinco minutos e recuperação da execução perdida. O monitor verifica HTTPS a cada cinco minutos. Falhas produzem saída diferente de zero e ficam no journal; não há notificação externa configurada automaticamente. Faça testes de restauração periodicamente e após mudanças de banco. Os timers são exemplos entregues no repositório; não estão instalados em um servidor real por esta entrega.

Não há exclusão automática de backups. Defina retenção e acompanhe espaço em disco conforme a operação e a cópia externa. A frequência diária é uma configuração inicial; a perda máxima de dados depende da idade da última cópia válida e externa. RTO precisa ser medido no ambiente real.

Referências: [pg_dump 17](https://www.postgresql.org/docs/17/app-pgdump.html), [pg_restore 17](https://www.postgresql.org/docs/17/app-pgrestore.html).
