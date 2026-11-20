# Banco de dados

PostgreSQL 17, versionado com Flyway em `backend/src/main/resources/db/`.

```text
db/migration/   schema (todos os ambientes)       V1__schema_inicial.sql, V2__..., ...
db/seed/        dados fictícios (só perfil dev)   V1000__dados_exemplo.sql, V1001__lotes_exemplo.sql, V1006__remessas_exemplo.sql, V1007__movimentacoes_exemplo.sql, V1008__limites_reservado_exemplo.sql
```

## Modelo

```text
ubs 1───* item_estoque *───1 medicamento
 │  │          │ 1               │ │
 │  │          * lote_estoque    │ │
 │  └───* remessa *──────────────┘ │
 │          │ 1                    │
 │          * remessa_evento       │
 └───* item_lista_desejo *─────────┘
             *
             │
             1
           usuario
```

| Tabela | Conteúdo | Chave natural |
| --- | --- | --- |
| `ubs` | Unidades de saúde, localização e última sincronização | `cnes` |
| `medicamento` | Itens da RENAME/REMUME, com limite de reservado próprio (opcional) | `catmat` |
| `usuario` | Contas autenticadas de cidadãos e gestores | `email` |
| `item_estoque` | Status e quantidade de um medicamento numa UBS | (`ubs_id`, `medicamento_id`) |
| `lote_estoque` | Lotes de um item de estoque, com validade (a soma dos lotes é, no máximo, `item_estoque.quantidade`: posição sem validade conta no total, mas não vira lote) | (`item_estoque_id`, `numero_lote`) |
| `remessa` | Remessa de um medicamento da CAF para uma UBS, com quantidade e previsão de chegada | — |
| `remessa_evento` | Momento em que a remessa chegou a cada etapa (`PEDIDO`, `SEPARADO_CAF`, `EM_TRANSPORTE`, `RECEBIDO`, `DISPONIVEL`) | (`remessa_id`, `etapa`) |
| `movimentacao_estoque` | Entradas, dispensações e ajustes de um item de estoque, com quantidade com sinal e data | — |
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

### Limite de reservado por medicamento

A migration `V11__limite_reservado_medicamento.sql` adiciona `medicamento.limite_reservado` (inteiro maior ou igual a zero, nulo por padrão). Com saldo até esse valor o item fica `RESERVADO`; quando for nulo, vale o limite global `app.estoque.limite-reservado`. A sincronização usa o limite do medicamento ao calcular o status, então mudar o valor só reflete no estoque na próxima sincronização.

No seed (`V1008__limites_reservado_exemplo.sql`): Metformina 50, Losartana 30 e Insulina NPH 10. Os itens do seed criados por outros arquivos podem ter sido classificados só com o limite global; o status é recalculado na próxima sincronização.

### Movimentações de estoque

A migration `V7__movimentacao_estoque.sql` cria `movimentacao_estoque`, ligada a `item_estoque` (a UBS e o medicamento vêm do item). A quantidade tem sinal, garantido por uma constraint: `ENTRADA` é maior que zero, `DISPENSACAO` é menor que zero e `AJUSTE` é diferente de zero. A chave estrangeira é `ON DELETE RESTRICT`: não dá para apagar um item que tem histórico. Os índices são por item e data, e por data.

No seed (`V1007__movimentacoes_exemplo.sql`), a soma das movimentações de cada item do seed é igual a `item_estoque.quantidade`.

### Remessas

A migration `V6__remessas.sql` cria `remessa` e `remessa_evento`. A etapa atual de uma remessa é a mais avançada registrada nos eventos; ela está em andamento enquanto não tiver o evento `DISPONIVEL`. Cada etapa só acontece uma vez por remessa, e apagar a remessa apaga seus eventos.

A BNAFAR pública não informa remessas em trânsito, então não há fonte oficial. No perfil `dev`, o seed `V1006__remessas_exemplo.sql` cria seis remessas em etapas diferentes (uma delas já concluída).

## Regras para migrations

1. **Nunca edite** uma migration que já foi para a `main`. Crie uma nova com o próximo número disponível, respeitando as versões já utilizadas pela equipe.
2. Nome descritivo em português, com `snake_case`.
3. Seeds de desenvolvimento ficam em `db/seed` com numeração a partir de `V1000`, para não colidir com o schema.
4. Toda mudança de schema precisa refletir na entidade JPA (o Hibernate valida no startup).

## Acesso

- Linha de comando: `docker compose exec db psql -U medflow -d medflow`
- pgAdmin: `docker compose --profile tools up -d` → http://localhost:5050 (host `db`, usuário/senha `medflow`)
