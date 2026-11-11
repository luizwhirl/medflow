# API

Base: `http://localhost:8080/api`. A documentação interativa fica em `/swagger-ui.html` (perfil `dev`).

| Método | Rota | Descrição |
| --- | --- | --- |
| GET | `/ubs` | Lista as unidades por nome. Com `lat` e `lng`, da mais próxima para a mais distante (veja abaixo) |
| GET | `/ubs/{id}` | Detalhe de uma unidade |
| GET | `/ubs/{id}/estoque` | Lista os itens do estoque da UBS, com filtro opcional por status |
| GET | `/ubs/{id}/estoque/resumo` | Resumo do estoque da UBS, com contagens por status e filtro opcional |
| GET | `/medicamentos?busca=` | Lista medicamentos; `busca` filtra por nome ou categoria |
| GET | `/medicamentos?categoria=` | Filtra medicamentos por categoria; pode ser combinado com `busca` |
| GET | `/medicamentos/categorias` | Lista as categorias distintas dos medicamentos cadastrados |
| GET | `/medicamentos/{id}` | Detalhe de um medicamento |
| GET | `/medicamentos/{id}/disponibilidade` | Unidades com o medicamento (disponíveis primeiro) |

## Filtro por categoria

Filtrar medicamentos pela categoria:

`GET /api/medicamentos?categoria=Diabetes`

Combinar a categoria com a busca textual:

`GET /api/medicamentos?busca=metformina&categoria=Diabetes`

Listar as categorias disponíveis:

`GET /api/medicamentos/categorias`

A categoria é filtrada sem diferenciar letras maiúsculas de minúsculas. Se nenhuma categoria corresponder, a listagem de medicamentos retorna uma lista vazia.

Status de estoque: `DISPONIVEL`, `RESERVADO`, `EM_FALTA`.

## UBS por proximidade

`GET /api/ubs?lat=-9.55&lng=-35.72&raioKm=3&medicamentoId=1`

Sem parâmetros, a rota lista as unidades por nome, como sempre. Com `lat` e `lng`, devolve da unidade mais próxima para a mais distante, com a distância em km (Haversine, calculada no banco, arredondada para 2 casas).

| Parâmetro | Obrigatório | Regra |
| --- | --- | --- |
| `lat` | com `lng` | de -90 a 90 |
| `lng` | com `lat` | de -180 a 180 |
| `raioKm` | não | maior que zero; só entram unidades até essa distância. Sem ele, não há limite |
| `medicamentoId` | não | cada unidade que tem o item traz `status` e `quantidade`; as outras continuam na lista, sem esses campos |

`raioKm` e `medicamentoId` exigem `lat` e `lng`. Parâmetro fora da regra, ou só um dos dois, retorna `400`; `medicamentoId` que não existe retorna `404`. Distâncias iguais desempatam pelo nome.

```json
[
  {
    "id": 3, "cnes": "9000103", "nome": "UBS Clima Bom", "tipo": "UBS", "bairro": "Clima Bom",
    "endereco": "Rua do Posto, 300", "telefone": "(82) 3000-0103", "horario": "Seg a Sex · 7h às 17h",
    "latitude": -9.56, "longitude": -35.74, "ultimaSincronizacao": "2026-10-26T10:00:00Z",
    "distanciaKm": 2.35, "status": "DISPONIVEL", "quantidade": 220
  }
]
```

Sem `raioKm`, a resposta traz todas as unidades do banco; a paginação virá com a PDS-22.

## Resumo do estoque da UBS

Consultar a lista de itens do estoque de uma UBS:

`GET /api/ubs/{id}/estoque`

A resposta é uma lista JSON de itens do estoque, mantendo o formato original da API.

Filtrar a lista por status:

`GET /api/ubs/{id}/estoque?status=DISPONIVEL`

Consultar o resumo do estoque, incluindo as contagens por status:

`GET /api/ubs/{id}/estoque/resumo`

A resposta é um objeto JSON contendo:

- `disponivel`: quantidade de itens com status `DISPONIVEL`.
- `reservado`: quantidade de itens com status `RESERVADO`.
- `emFalta`: quantidade de itens com status `EM_FALTA`.
- `itens`: lista dos itens do estoque, incluindo medicamento, status, quantidade e data de atualização.

Filtrar os itens do resumo por status:

`GET /api/ubs/{id}/estoque/resumo?status=EM_FALTA`

Exemplo de resposta do resumo filtrado, para uma UBS sem itens em falta:

```json
{
  "disponivel": 3,
  "reservado": 1,
  "emFalta": 0,
  "itens": []
}
```

O parâmetro `status` é opcional nas duas rotas e aceita os valores `DISPONIVEL`, `RESERVADO` e `EM_FALTA`.

Sem o parâmetro, todos os itens do estoque são retornados.

No endpoint `/estoque/resumo`, as contagens sempre representam o estoque completo da UBS, mesmo quando o filtro por status é aplicado à lista `itens`.

Se a UBS informada não existir, a API retorna `404` no padrão `application/problem+json`. Um status inválido retorna `400`.

## Exemplos

`GET /api/medicamentos/1/disponibilidade`

```json
[
  {
    "ubs": {
      "id": 3,
      "cnes": "9000103",
      "nome": "UBS Clima Bom",
      "tipo": "UBS",
      "bairro": "Clima Bom",
      "endereco": "Rua do Posto, 300",
      "telefone": "(82) 3000-0103",
      "horario": "Seg a Sex · 7h às 17h",
      "latitude": -9.56,
      "longitude": -35.74,
      "ultimaSincronizacao": "2026-10-26T10:00:00Z"
    },
    "status": "DISPONIVEL",
    "quantidade": 220,
    "atualizadoEm": "2026-10-26T13:00:00Z"
  }
]
```

Erro (`404`, `application/problem+json`):

```json
{ "type": "about:blank", "title": "Recurso não encontrado", "status": 404, "detail": "UBS 999 não encontrado(a)", "instance": "/api/ubs/999" }
```

## Health check

`GET /actuator/health` → `{"status":"UP"}`
