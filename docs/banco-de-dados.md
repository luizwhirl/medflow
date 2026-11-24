# Banco de dados

PostgreSQL 17, versionado com Flyway em `backend/src/main/resources/db/`.

```text
db/migration/   schema (todos os ambientes)       V1__schema_inicial.sql, V2__..., ...
db/seed/        dados fictícios (só perfil dev)   V1000__dados_exemplo.sql, V1001__lotes_exemplo.sql, V1006__remessas_exemplo.sql,
                                                  V1007__movimentacoes_exemplo.sql, V1008__limites_reservado_exemplo.sql,
                                                  V1100__estoque_completo.sql, V1101__medicamentos_extras.sql,
                                                  V1102__remessa_recebida_exemplo.sql
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
| `horario_funcionamento` | Turnos de atendimento da UBS por dia da semana (`dia_semana` de 1 = segunda a 7 = domingo; `fecha` antes de `abre` vira a noite; `abre` igual a `fecha` é um turno de 24 h) | (`ubs_id`, `dia_semana`, `abre`) |
| `medicamento` | Itens da RENAME/REMUME, com limite de reservado próprio (opcional) | `catmat` |
| `usuario` | Contas autenticadas de cidadãos e gestores; o gestor pode ter vínculo com uma UBS | `email` |
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

A BNAFAR pública não informa remessas em trânsito, então não há fonte oficial. No perfil `dev`, o seed `V1006__remessas_exemplo.sql` cria seis remessas em etapas diferentes (uma delas já concluída), e o `V1102__remessa_recebida_exemplo.sql` acrescenta uma parada em `RECEBIDO`, para haver remessa em cada etapa.

## Índices da busca

A busca de medicamentos (`GET /api/medicamentos`) filtra com `lower(nome) LIKE '%termo%'` e `lower(categoria)`
(`LIKE` ou `=`). Um B-tree não atende `LIKE` com curinga no início, então a `V10__indices_busca_medicamento.sql`
cria a extensão `pg_trgm` e dois índices GIN (`idx_medicamento_nome_trgm` e `idx_medicamento_categoria_trgm`),
que atendem tanto o `LIKE` quanto a igualdade. O estoque da UBS já usa a chave única `uk_item_estoque`
(`ubs_id`, `medicamento_id`) e não precisou de índice novo.

`EXPLAIN ANALYZE` com 100 mil medicamentos e 100 mil itens de estoque (PostgreSQL 17):

| Consulta | Sem a V10 | Com a V10 |
| --- | --- | --- |
| Busca por termo (página de 20) | Seq Scan, 46 ms | BitmapOr nos dois índices trgm, 2,5 ms |
| Busca por termo (contagem da página) | Seq Scan, 49 ms | BitmapOr nos dois índices trgm, 1,6 ms |
| Filtro exato por categoria | Seq Scan, 19 ms | Bitmap Index Scan em `idx_medicamento_categoria_trgm`, 8,5 ms |
| Estoque da UBS (página de 20) | Bitmap Index Scan em `uk_item_estoque`, ~1 ms | igual |

Com poucas linhas (como no seed) o planejador prefere o Seq Scan, o que é o certo nesse tamanho. Termos com menos de
3 letras não formam trigramas e também caem no Seq Scan. O teste `IndicesBuscaMedicamentoIntegrationTests` gera
volume e confere que os planos usam os índices.

### Vínculo do gestor com a UBS

A migration `V13__vinculo_gestor_ubs.sql` adiciona `usuario.ubs_gestao_id` (opcional), que referencia `ubs(id)`. Só o papel `GESTOR` pode ter vínculo (constraint `ck_usuario_ubs_gestao_so_gestor`). A chave estrangeira é `ON DELETE RESTRICT`: apagar a UBS deixaria o gestor sem vínculo e, com isso, vendo a rede toda. No perfil `dev`, Seu Carlos é vinculado à UBS `9000101`, e Dona Marta não tem vínculo.

## Regras para migrations

1. **Nunca edite** uma migration que já foi para a `main`. Crie uma nova com o próximo número disponível, respeitando as versões já utilizadas pela equipe.
2. Nome descritivo em português, com `snake_case`.
3. Seeds de desenvolvimento ficam em `db/seed` com numeração a partir de `V1000`, para não colidir com o schema.
4. Toda mudança de schema precisa refletir na entidade JPA (o Hibernate valida no startup).

## Recriar, salvar e restaurar o banco local

Scripts em `scripts/`, em duas versões com o mesmo comportamento: `.sh` (Linux, macOS e Git Bash no Windows) e
`.ps1` (PowerShell no Windows). Funcionam de qualquer pasta, sobem o container `db` se ele estiver parado e rodam
tudo dentro dos containers do `docker-compose`, então não precisam de `psql`, `pg_dump` nem da API.

| Comando | O que faz |
| --- | --- |
| `scripts/db-reset.sh` | Apaga o banco, aplica as migrations (`db/migration`) e o seed (`db/seed`) |
| `scripts/db-dump.sh [arquivo]` | Salva o banco com `pg_dump` (padrão: `backups/medflow-AAAAMMDD-HHMMSS.dump`) |
| `scripts/db-restore.sh <arquivo>` | Substitui o banco pelo conteúdo de um dump |

No PowerShell, troque por `.\scripts\db-reset.ps1`, `.\scripts\db-dump.ps1` e `.\scripts\db-restore.ps1`. Se o
Windows bloquear scripts, rode uma vez `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned`.

- `db-reset` e `db-restore` pedem confirmação; `-y` pula a pergunta.
- O banco é apagado com `DROP DATABASE ... WITH (FORCE)`, que derruba as conexões abertas. A API pode ficar rodando:
  ela reconecta sozinha. Os dados de exemplo ficam como se a API tivesse subido num banco vazio.
- O `db-reset` usa o serviço `flyway` do `docker-compose` (perfil `migracao`, não sobe com o `up`), na mesma versão do
  Flyway do Spring Boot e com a configuração do perfil `dev` (`db/migration` + `db/seed`, `out-of-order`). Ao trocar
  de branch, ele resolve o `Flyway validation failed` causado por migrations que só existem em outra branch.
- Os dumps ficam em `backups/`, que não vai para o git. Um dump traz também o `flyway_schema_history`: restaure-o
  numa branch com as mesmas migrations em que ele foi gerado.

## Acesso

- Linha de comando: `docker compose exec db psql -U medflow -d medflow`
- pgAdmin: `docker compose --profile tools up -d` → http://localhost:5050 (host `db`, usuário/senha `medflow`)
