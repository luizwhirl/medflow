# Banco de dados

PostgreSQL 17, versionado com Flyway em `backend/src/main/resources/db/`.

```text
db/migration/   schema (todos os ambientes)       V1__schema_inicial.sql, V2__..., ...
db/seed/        dados fictícios (só perfil dev)   V1000__dados_exemplo.sql, V1001__lotes_exemplo.sql
```

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
