# Verificações de entrega

O CI executa em Java 21:

1. Versão/changelog e incremento contra a base do PR.
2. Testes de falhas das rotinas de operação em Python e sintaxe de JavaScript.
3. Suíte Maven em H2 e em PostgreSQL 17.
4. Construção da imagem Docker de produção.
5. Instalação de teste isolada com a composição de produção: TLS por CA interna, regras reais do Caddy, headers/cookies Secure, SMTP STARTTLS autenticado contra Mailpit com certificado confiado explicitamente, envio de teste e recuperação de senha.
6. Backup e restauração em novo banco, conferência de lançamentos e bytes dos comprovantes, recusa de arquivo corrompido e verificação de que a origem não mudou. O teste de restauração temporário também é executado.
7. Interrupção do banco de teste para conferir readiness indisponível e liveness disponível, inclusive com cookie existente.

O projeto Docker de teste recebe nome aleatório fintry-ci-..., certificados/credenciais descartáveis e volumes próprios; o teardown remove somente esse ambiente criado pelo teste. Nenhum e-mail é enviado à internet e não são solicitados certificados públicos durante o CI. Os arquivos sensíveis temporários não são publicados como artifacts; somente o JAR é disponibilizado pelo workflow.

O CI não valida DNS real, emissão ACME pública, entregabilidade do provedor SMTP, armazenamento externo de backups ou recuperação na hospedagem escolhida. Esses passos precisam ser conferidos na publicação. Integração na main não executa deploy automático nem migra dados de um servidor.
