# Segurança e acesso

A sessão HttpOnly/SameSite deriva usuário/empresa do servidor. CSRF é exigido em todas as escritas, inclusive login, recuperação e ativação. CSP usa arquivos locais, sem scripts/estilos inline. Em HTTPS, configure COOKIE_SECURE=true e proteja o proxy.

| Perfil | Consultar/exportar | Gerenciar finanças/comprovantes | Empresa/equipe/histórico |
|---|---|---|---|
| ADMIN | Sim | Sim | Sim |
| FINANCE | Sim | Sim | Não |
| VIEWER | Sim | Não | Não |

Todo perfil autenticado pode alterar a própria senha. As permissões são verificadas no filtro de segurança; esconder botões é apenas uma conveniência. As consultas continuam isoladas por empresa. O usuário não pode desativar/rebaixar a própria conta administrativa; alterações da equipe são serializadas pelo bloqueio da empresa e preservam um administrador ativo.

Cada sessão carrega id e authVersion. Um filtro confere usuário ativo, convite aceito e versão em cada requisição. Alterações de nome/perfil/situação, renovação/revogação de convite e mudanças de senha incrementam a versão. A requisição seguinte exige novo login. A sessão antiga da 0.5 também é invalidada.

Tokens têm 32 bytes de SecureRandom, Base64 URL sem padding; somente hash SHA-256 persiste em access_tokens. Recuperação expira em 15 minutos; convite em 7 dias. Reemissão revoga links anteriores da mesma finalidade. Consumo bloqueia primeiro o usuário e depois o token, verifica expiração/finalidade/uso e revoga os demais links atomicamente. Não há login automático após aceitar convite ou redefinir senha.

APP_BASE_URL define a origem confiável dos links; não use Host/X-Forwarded-Host. O frontend recebe o token no fragmento (#reset= ou #invite=), remove-o imediatamente do endereço e envia por POST com CSRF. Não armazena o token em localStorage. Convites são exibidos somente ao Administrador, com Cache-Control no-store. Quem possui o link pode ativar o acesso: compartilhe diretamente com o destinatário.

A recuperação retorna 202 com a mesma mensagem para e-mails ativos, desconhecidos e inativos. MAIL_ENABLED=false retorna 503 genericamente e a interface explica a indisponibilidade. O envio acontece após commit, em executor com fila limitada, via SMTP configurado; erro produz aviso sem endereço/token. Não há garantia de entrega ou retentativa persistente. Mailpit é apenas uma caixa local de avaliação.

Limites em memória por instância/janela de 15 minutos: login 120 por IP e 10 por e-mail; recuperação 30 por IP e 3 por e-mail; consumo de link 60 por IP; consulta de convite 100 por IP. Usa endereço remoto da conexão, sem confiar em cabeçalhos arbitrários de proxy. Cache limitado a 5.000 chaves. Instalações com réplicas/proxy devem planejar limitação centralizada e configuração confiável de endereços.

Auditoria grava campos explicitamente permitidos na mesma transação da operação. Senhas, hashes de senha, tokens e bytes de anexos não entram nos eventos. As consultas são exclusivas de Administradores da própria empresa, paginadas e limitadas a 366 dias. Não existe API de alteração/exclusão de eventos; um administrador do banco ainda possui acesso técnico. Defina política de backup/retenção conforme a instalação. Eventos começam na versão 0.6.0, sem inventar histórico passado.
