# API

Base: `http://localhost:8080/api`. A documentação interativa fica em `/swagger-ui.html` (perfil `dev`).

| Método | Rota | Descrição |
| --- | --- | --- |
| GET | `/ubs` | Lista as unidades, ordenadas por nome |
| GET | `/ubs/{id}` | Detalhe de uma unidade |
| GET | `/ubs/{id}/estoque` | Lista os itens do estoque da UBS, com filtro opcional por status |
| GET | `/ubs/{id}/estoque/resumo` | Resumo do estoque da UBS, com contagens por status e filtro opcional |
| GET | `/medicamentos?busca=` | Lista medicamentos; `busca` filtra por nome ou categoria |
| GET | `/medicamentos?categoria=` | Filtra medicamentos por categoria; pode ser combinado com `busca` |
| GET | `/medicamentos/categorias` | Lista as categorias distintas dos medicamentos cadastrados |
| GET | `/medicamentos/{id}` | Detalhe de um medicamento |
| GET | `/medicamentos/{id}/disponibilidade` | Unidades com o medicamento (disponíveis primeiro) |
| GET | `/medicamentos/{id}/disponibilidade/resumo` | Total de unidades e unidades com estoque disponível, com filtro opcional por município |

## Filtro por categoria

Filtrar medicamentos pela categoria:

`GET /api/medicamentos?categoria=Diabetes`

Combinar a categoria com a busca textual:

`GET /api/medicamentos?busca=metformina&categoria=Diabetes`

Listar as categorias disponíveis:

`GET /api/medicamentos/categorias`

A categoria é filtrada sem diferenciar letras maiúsculas de minúsculas. Se nenhuma categoria corresponder, a listagem de medicamentos retorna uma lista vazia.

Status de estoque: `DISPONIVEL`, `RESERVADO`, `EM_FALTA`.

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

## Resumo de disponibilidade do medicamento

Consultar o resumo de disponibilidade de um medicamento nas unidades de saúde:

`GET /api/medicamentos/{id}/disponibilidade/resumo`

Filtrar as unidades por município, usando o código IBGE de 7 dígitos:

`GET /api/medicamentos/{id}/disponibilidade/resumo?ibge=2704302`

Também é possível utilizar o código de 6 dígitos, conforme o formato da BNAFAR:

`GET /api/medicamentos/{id}/disponibilidade/resumo?ibge=270430`

O código de 6 dígitos é normalizado para 7 dígitos por `CodigoIbge.paraSeteDigitos()` antes das consultas. Assim, os dois exemplos acima consultam o mesmo município.

A resposta contém:

- `totalUnidades`: quantidade total de unidades de saúde consideradas na consulta.
- `unidadesDisponiveis`: quantidade de unidades com o medicamento em status `DISPONIVEL` e quantidade maior que zero.

Exemplo de resposta para a Metformina, conforme o seed de desenvolvimento:

```json
{
  "totalUnidades": 7,
  "unidadesDisponiveis": 3
}
```

O parâmetro `ibge` é opcional. Sem ele, o resumo considera todas as unidades cadastradas. Quando informado, as duas contagens consideram apenas as unidades do município indicado.

Unidades com medicamento `RESERVADO`, `EM_FALTA` ou quantidade zero não entram em `unidadesDisponiveis`.

As contagens são realizadas diretamente no banco de dados.

A informação sobre a unidade mais próxima com estoque será adicionada após a implementação da L5.

Se o medicamento informado não existir, a API retorna `404` no padrão `application/problem+json`.

Se o parâmetro `ibge` tiver formato inválido (não numérico ou diferente de 6 ou 7 dígitos), a API retorna `400` no padrão `application/problem+json`, com o título `Parâmetro inválido`.

A normalização valida o formato do código IBGE, mas não verifica a existência do município. Portanto, um código com formato válido que não corresponda a nenhuma UBS cadastrada pode resultar em contagens zeradas.

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
