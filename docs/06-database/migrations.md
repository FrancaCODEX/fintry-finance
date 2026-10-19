
## 0.5.0 — comprovantes

PostgreSQL `V6__entry_attachments.sql` e H2 `V4__entry_attachments.sql` adicionam a tabela entry_attachments e a chave única (id, company_id) em financial_entries, sem alterar migrações anteriores. O vínculo composto impede associar um comprovante a um lançamento de outra empresa. Os bytes usam BYTEA, mapeado como byte[] sem LOB OID; nome, MIME, tamanho, usuário e data ficam no mesmo registro. Listagens usam projeção de metadados sem carregar o conteúdo.

Arquivos fazem parte do backup do banco. Não há diretório público, novo serviço de armazenamento nem volume adicional. A remoção de comprovante é física; a baixa/cancelamento do lançamento não o remove. Não remover volumes para atualizar.
