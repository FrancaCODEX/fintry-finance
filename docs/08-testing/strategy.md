# Validação da versão com cadastros

A suíte de integração HTTP testa sessões e CSRF, isolamento por empresa, valores e datas, baixa repetida e simultânea, cancelamento e transferências. A evolução inclui cadastros e edição de pendências.

Cenários adicionais:

- Criar, pesquisar, renomear e inativar contatos e categorias.
- Normalizar máscara de CPF/CNPJ e impedir duplicidade dentro da empresa.
- Permitir o mesmo documento em empresas diferentes e bloquear referências cruzadas.
- Validar o tipo do contato/categoria, incluindo contatos que são clientes e fornecedores.
- Editar pendências sem modificar o saldo realizado.
- Bloquear edição de pagos e cancelados; exigir a ação de baixa para pagar.
- Preservar nomes históricos e manter vínculos inativos preexistentes.
- Atualizar banco com dados da primeira versão sem remover lançamentos.

Execute `cd backend` e `sh mvnw verify`. O perfil padrão dos testes usa H2; `-Dspring.profiles.active=postgres-test` utiliza o PostgreSQL dedicado configurado para CI. O teste isolado de migração reproduz a atualização H2 com um pool de conexões, tal como a aplicação.

Na validação local desta entrega foi utilizado Java 17 como override (`-Djava.version=17`); o projeto mantém Java 21 como alvo. Docker e PostgreSQL não estão disponíveis no ambiente local. O workflow do GitHub conserva as verificações de Java 21, PostgreSQL e Docker.
