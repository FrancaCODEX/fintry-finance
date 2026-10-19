# Testes de integração

Execute `sh backend/mvnw -f backend/pom.xml verify` com Java 21. O perfil padrão de teste usa H2 migrado; `-Dspring.profiles.active=postgres-test` usa PostgreSQL local em localhost:5432/fintry_test (fintry/fintry-test). O workflow executa ambos. Não há limpeza destrutiva de bancos de usuários.

A suíte inclui 18 cenários financeiros HTTP (sessão, CSRF, isolamento, cadastros, séries, relatórios, anexos e concorrência), cinco cenários de acesso/auditoria, dois de calendário de séries, um de migração incremental e um de contexto.

`AccessIntegrationTests` usa HTTP real, sessões independentes, banco relacional e migrações. Só a saída de e-mail é capturada por um JavaMailSender de teste (sem e-mails externos). Cobre:

- ADMIN/FINANCE/VIEWER na API, escrita proibida, equipe/histórico restritos e invalidação de sessão por perfil/inativação;
- proteção da própria conta administrativa, isolamento entre empresas e e-mail duplicado;
- convite pendente, hash do token, renovação, revogação, expiração, finalidade, CSRF e duas ativações simultâneas (uma aceita);
- resposta genérica da recuperação, captura de destinatário/link, token sem texto puro no banco/auditoria, link expirado/usado, senha inválida e limitação de tentativas;
- troca de senha por usuário de leitura, senha atual incorreta, senha repetida e encerramento de outras sessões;
- diferenças de campos na auditoria, operações repetidas sem alteração, falhas sem evento, isolamento e limites de consulta.

O teste de migração parte do schema anterior, conserva lançamentos e senha do administrador existente e confere ADMIN/ativo/authVersion após atualizar.

Antes de entregar UI, confira em navegador desktop e celular: cadastro/confirmação, equipe, criação/ativação/revogação de convite, troca/recuperação de senha, fluxo de e-mail local, leitura sem botões de escrita, comprovantes somente consulta, filtros/diferenças no histórico e temas claro/escuro. Inspecione também navegação com teclado, estados de erro e ausência de overflow horizontal.
