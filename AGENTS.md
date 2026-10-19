# Fintry — regras de entrega

- Respeite os dados existentes. Use novas migrações Flyway, sem editar migrações publicadas nem remover volumes para atualizar.
- Toda entrega altera a versão em backend/pom.xml e a versão atual no README.md, e registra funcionalidades, melhorias e correções em docs/01-product/changelog.md. Esse é o changelog canônico; não duplique seu conteúdo em outro arquivo.
- Funcionalidades e melhorias: incrementar MINOR. Correções isoladas: incrementar PATCH. Antes de 1.0, mudanças incompatíveis também incrementam MINOR. Não reutilize o número de uma versão já entregue.
- A versão da interface deve vir do backend, que recebe a versão do Maven por filtragem de application.yml. O nome do artefato é fintry.jar, independente da versão.
- Execute python3 scripts/check_release.py, verificações de JavaScript quando aplicáveis e os testes pertinentes antes de publicar a entrega.
- Registre no PR o que foi validado e limitações materiais do ambiente. Não declare Docker/PostgreSQL/Java 21 como testados quando não foram executados.

- A sequência de funcionalidades acordada está em docs/01-product/roadmap.md. Atualize a situação das entregas e preserve a ordem, salvo nova orientação do usuário.
