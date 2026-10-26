# Banco de dados

PostgreSQL 17, versionado com Flyway em `backend/src/main/resources/db/`.

```
db/migration/   schema (todos os ambientes)       V1__schema_inicial.sql, V2__..., ...
db/seed/        dados fictícios (só perfil dev)   V1000__dados_exemplo.sql
```

## Modelo

```
ubs 1───* item_estoque *───1 medicamento
 │                                 │
 └───* item_lista_desejo *─────────┘
```

| Tabela | Conteúdo | Chave natural |
| --- | --- | --- |
| `ubs` | Unidades de saúde, localização e última sincronização | `cnes` |
| `medicamento` | Itens da RENAME/REMUME | `catmat` |
| `item_estoque` | Status e quantidade de um medicamento numa UBS | (`ubs_id`, `medicamento_id`) |
| `item_lista_desejo` | Medicamentos que o cidadão quer ser avisado quando chegarem | (`cidadao_cns`, `ubs_id`, `medicamento_id`) |

## Regras para migrations

1. **Nunca edite** uma migration que já foi para a `main`. Crie uma nova com o próximo número: `V2__adiciona_coluna_x.sql`.
2. Nome descritivo em português, com `snake_case`.
3. Seeds de desenvolvimento ficam em `db/seed` com numeração a partir de `V1000`, para não colidir com o schema.
4. Toda mudança de schema precisa refletir na entidade JPA (o Hibernate valida no startup).

## Acesso

- Linha de comando: `docker compose exec db psql -U medflow -d medflow`
- pgAdmin: `docker compose --profile tools up -d` → http://localhost:5050 (host `db`, usuário/senha `medflow`)
