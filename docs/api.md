# API

Base: `http://localhost:8080/api`. A documentação interativa fica em `/swagger-ui.html` (perfil `dev`).

| Método | Rota                                 | Descrição                                                         |
| ------ | ------------------------------------ | ----------------------------------------------------------------- |
| GET    | `/ubs`                               | Lista as unidades, ordenadas por nome                             |
| GET    | `/ubs/{id}`                          | Detalhe de uma unidade                                            |
| GET    | `/ubs/{id}/estoque`                  | Estoque da farmácia da unidade                                    |
| GET    | `/medicamentos?busca=`               | Lista medicamentos; `busca` filtra por nome ou categoria          |
| GET    | `/medicamentos?categoria=`           | Filtra medicamentos por categoria; pode ser combinado com `busca` |
| GET    | `/medicamentos/categorias`           | Lista as categorias distintas dos medicamentos cadastrados        |
| GET    | `/medicamentos/{id}`                 | Detalhe de um medicamento                                         |
| GET    | `/medicamentos/{id}/disponibilidade` | Unidades com o medicamento (disponíveis primeiro)                 |

## Filtro por categoria

Filtrar medicamentos pela categoria:

`GET /api/medicamentos?categoria=Diabetes`

Combinar a categoria com a busca textual:

`GET /api/medicamentos?busca=metformina&categoria=Diabetes`

Listar as categorias disponíveis:

`GET /api/medicamentos/categorias`

A categoria é filtrada sem diferenciar letras maiúsculas de minúsculas. Se nenhuma categoria corresponder, a listagem de medicamentos retorna uma lista vazia.

Status de estoque: `DISPONIVEL`, `RESERVADO`, `EM_FALTA`.

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
{
  "type": "about:blank",
  "title": "Recurso não encontrado",
  "status": 404,
  "detail": "UBS 999 não encontrado(a)",
  "instance": "/api/ubs/999"
}
```

## Health check

`GET /actuator/health` → `{"status":"UP"}`
