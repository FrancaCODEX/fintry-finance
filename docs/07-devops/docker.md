# Docker

A imagem compila o backend em Java 21 e executa com JRE, usuário sem privilégios e verificação de prontidão. A porta interna é 8080; a versão vem do Maven e o artefato continua fintry.jar. curl é usado para a verificação interna, sem credenciais.

O Docker local continua em docker-compose.yml. A composição de produção em deploy/compose.production.yml acrescenta Caddy, separa redes/volumes, usa sistema de arquivos da aplicação somente leitura com /tmp temporário, limite inicial de 1 GiB e logs rotacionados (10 MB × 3 arquivos por serviço). PostgreSQL fica em rede interna; a aplicação tem saída de rede para SMTP e comunica-se com o proxy. Banco e aplicação não publicam portas.

A prontidão consulta estado da aplicação e banco. O liveness consulta somente o estado do processo. O status unhealthy não provoca restart por si só no Docker; unless-stopped reinicia um container cujo processo encerrou, conforme o estado do daemon. Use o monitor operacional para identificar indisponibilidade de dependências.

O proxy termina TLS, remove o cabeçalho Forwarded enviado pelo cliente e define os X-Forwarded-* ao encaminhar. O perfil prod confia nesses cabeçalhos porque só o proxy pode acessar a aplicação nesta composição. Se mudar a topologia, preserve essa fronteira de confiança.

As tags postgres:17-alpine e caddy:2-alpine acompanham atualizações da série. Mudanças nas imagens devem passar novamente pelo CI; use digests revisados se precisar fixar exatamente as imagens de infraestrutura na sua instalação. Tags próprias FINTRY_IMAGE por versão permitem preservar a aplicação anterior.

[Publicação](deployment.md) · [Backups](backups.md) · [Monitoramento](monitoring.md)
