# Banco de dados

PostgreSQL 17, versionado com Flyway em `backend/src/main/resources/db/`.

```text
db/migration/   schema (todos os ambientes)       V1__schema_inicial.sql, V2__..., ...
db/seed/        dados fictícios (só perfil dev)   V1000__dados_exemplo.sql, V1001__lotes_exemplo.sql, V1002__estoque_completo.sql,
                                                  V1004__medicamentos_extras.sql
                                                  afterMigrate__sincronizacao_exemplo.sql (callback)
```

O seed do perfil `dev` cobre todas as telas: os 17 medicamentos nas 7 UBS (com os três status em cada uma), lotes
vencendo em até 60 dias e a USF Riacho Doce com a sincronização atrasada. O callback `afterMigrate` roda a cada
subida e mantém a "última sincronização" das UBS fictícias relativa a agora (senão todas ficariam atrasadas com o
tempo). As validades dos lotes são relativas ao dia em que o banco foi criado; para renová-las, recrie o banco
(`docker compose down -v && docker compose up -d`).

## Modelo

```text
ubs 1───* item_estoque *───1 medicamento
 │             │ 1               │
 │             * lote_estoque    │
 └───* item_lista_desejo *───────┘
             *
             │
             1
           usuario
```

| Tabela | Conteúdo | Chave natural |
| --- | --- | --- |
| `ubs` | Unidades de saúde, localização e última sincronização | `cnes` |
| `medicamento` | Itens da RENAME/REMUME | `catmat` |
| `usuario` | Contas autenticadas de cidadãos e gestores | `email` |
| `item_estoque` | Status e quantidade de um medicamento numa UBS | (`ubs_id`, `medicamento_id`) |
| `lote_estoque` | Lotes de um item de estoque, com validade (a soma dos lotes é, no máximo, `item_estoque.quantidade`: posição sem validade conta no total, mas não vira lote) | (`item_estoque_id`, `numero_lote`) |
| `item_lista_desejo` | Medicamentos que o cidadão quer ser avisado quando chegarem | (`usuario_id`, `ubs_id`, `medicamento_id`) |

A partir da migration `V5__lista_desejos_usuario.sql`, cada item da lista de desejos pertence a um usuário autenticado, identificado por `usuario_id`, que referencia `usuario(id)`.

A combinação (`usuario_id`, `ubs_id`, `medicamento_id`) é única, impedindo que um mesmo cidadão cadastre o mesmo medicamento mais de uma vez para a mesma UBS. Cidadãos diferentes podem cadastrar o mesmo medicamento e UBS em suas próprias listas.

### Migrations da lista de desejos

**V5 — Associação ao usuário autenticado**

A migration `V5__lista_desejos_usuario.sql` substitui a coluna `cidadao_cns` por `usuario_id` e atualiza a restrição de unicidade.

Como não há uma correspondência automática entre o CNS antigo e as contas autenticadas, a V5 interrompe a execução caso encontre registros antigos em `item_lista_desejo`.

Essa proteção impede a exclusão silenciosa de dados. Antes de implantar a V5 em um banco que contenha registros antigos, a equipe precisa decidir e executar uma estratégia de migração ou descarte desses registros.

**V5.1 — Exclusão em cascata**

A migration `V5_1__lista_desejos_cascade_usuario.sql` adiciona `ON DELETE CASCADE` à chave estrangeira `fk_item_lista_desejo_usuario`.

Quando um usuário é excluído, o PostgreSQL remove automaticamente os itens de sua lista de desejos, preservando os registros de outros usuários.

A V5 original não é modificada, preservando o histórico e o checksum do Flyway.

**Execução no perfil de desenvolvimento**

No perfil `dev`, o Flyway utiliza `db/migration` e `db/seed`, com `out-of-order: true`, permitindo aplicar novas migrations de schema em bancos onde as migrations de seed V1000 e V1001 já foram executadas.

No perfil `prod`, apenas `db/migration` é utilizado e `out-of-order` não é habilitado.

O perfil padrão da aplicação é `dev`; ambientes de produção devem ativar explicitamente o perfil `prod`.

## Regras para migrations

1. **Nunca edite** uma migration que já foi para a `main`. Crie uma nova com o próximo número disponível, respeitando as versões já utilizadas pela equipe.
2. Nome descritivo em português, com `snake_case`.
3. Seeds de desenvolvimento ficam em `db/seed` com numeração a partir de `V1000`, para não colidir com o schema.
4. Toda mudança de schema precisa refletir na entidade JPA (o Hibernate valida no startup).

## Acesso

- Linha de comando: `docker compose exec db psql -U medflow -d medflow`
- pgAdmin: `docker compose --profile tools up -d` → http://localhost:5050 (host `db`, usuário/senha `medflow`)
