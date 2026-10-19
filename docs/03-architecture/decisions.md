# Decisões da primeira versão funcional

1. **Monólito por domínio:** mantém a organização já iniciada no projeto.
2. **Sessão HTTP, em vez de JWT neste primeiro cliente web:** interface e API na mesma origem; cookie HttpOnly/SameSite e CSRF ativo. A identidade autenticada determina a empresa. O cliente não escolhe companyId.
3. **Saldo derivado:** saldo inicial + soma dos lançamentos realizados. A baixa altera o estado sob bloqueio pessimista, sem incrementar uma coluna de saldo. Repetir a baixa não duplica um movimento.
4. **Transferências:** duas entradas vinculadas por transferId na mesma transação. Não entram em receita/despesa operacional. Para desfazer, registrar transferência inversa; não se permite cancelar um lado isolado.
5. **Frontend servido pelo Spring:** HTML/CSS/JS sem compilação, sem dependências externas em tempo de navegação. Reduz a configuração necessária para rodar a primeira versão. A proposta de React continua no backlog.
6. **H2 opcional:** modo de demonstração local, com schema equivalente; PostgreSQL continua sendo o ambiente principal. A CI testa os dois bancos.
7. **Escopo:** uma empresa por usuário, administrador único criado no cadastro. Convites e papéis adicionais ainda não implementados. Categorias são texto livre com sugestões, sem cadastro próprio.
8. **Exclusões:** lançamentos são cancelados e preservados. A API de exclusão física de empresas não é mais exposta.

Este documento registra as decisões implementadas e prevalece sobre propostas antigas da documentação de planejamento.

## Evolução 0.2

- Cadastros ficam no módulo directory e usam companyId derivado da sessão. Documentos de contatos e nomes normalizados de categorias têm unicidade por empresa.
- As referências financeiras têm chaves estrangeiras compostas com companyId, além das verificações de autorização na aplicação.
- Os nomes são copiados ao salvar o lançamento. Renomear/inativar o cadastro não reescreve o histórico existente. Não há exclusão física.
- Edição de pendências, baixa e cancelamento usam o mesmo bloqueio pessimista da linha financeira. Atribuição e alteração/inativação de cadastros também compartilham bloqueio para evitar novos vínculos após inativação.
- V4 no PostgreSQL e V2 no H2 são migrações incrementais. As migrações já publicadas permanecem intactas. Categorias avulsas da versão inicial continuam compatíveis.
