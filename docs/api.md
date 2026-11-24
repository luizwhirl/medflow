# API

Base: `http://localhost:8080/api`. A documentação interativa fica em `/swagger-ui.html` (perfil `dev`).

| Método | Rota | Descrição |
| --- | --- | --- |
| GET | `/ubs` | Lista as unidades por nome. Com `lat` e `lng`, da mais próxima para a mais distante (veja abaixo) |
| GET | `/ubs/{id}` | Detalhe de uma unidade |
| GET | `/ubs/{id}/estoque` | Itens do estoque da UBS, paginados, com filtro opcional por status |
| GET | `/ubs/{id}/movimentacoes` | Últimas movimentações de estoque da UBS, com filtro opcional por medicamento |
| GET | `/ubs/{id}/estoque/resumo` | Resumo do estoque da UBS, com contagens por status e filtro opcional |
| GET | `/medicamentos?busca=` | Medicamentos paginados, por nome; `busca` filtra por nome ou categoria |
| GET | `/medicamentos?categoria=` | Filtra medicamentos por categoria; pode ser combinado com `busca` |
| GET | `/medicamentos/categorias` | Lista as categorias distintas dos medicamentos cadastrados |
| GET | `/medicamentos/{id}` | Detalhe de um medicamento |
| GET | `/medicamentos/{id}/disponibilidade` | Unidades com o medicamento (disponíveis primeiro) |
| GET | `/medicamentos/{id}/rastreamento?ubsId=` | Remessa do medicamento para a UBS: etapa atual, previsão de chegada e linha do tempo |
| GET | `/medicamentos/{id}/disponibilidade/resumo` | Total de unidades e unidades com estoque disponível, com filtro opcional por município |
| GET | `/lista-desejos` | Lista os medicamentos desejados pelo cidadão autenticado |
| POST | `/lista-desejos` | Adiciona um medicamento à lista de desejos |
| PATCH | `/lista-desejos/{id}` | Ativa ou desativa a notificação de um item |
| DELETE | `/lista-desejos/{id}` | Remove um item da lista de desejos |

## Autenticação

A leitura de UBS e medicamentos (`GET /api/ubs/**` e `GET /api/medicamentos/**`), a saúde (`/actuator/health`) e a documentação (Swagger) são públicas. Todo o resto exige login. O prefixo `/api/gestao` é só do papel `GESTOR`, enquanto `/api/lista-desejos` e suas subrotas são exclusivos do papel `CIDADAO`. Rota que não existe também responde `401` sem token.

| Método | Rota | Quem | Descrição |
| --- | --- | --- | --- |
| POST | `/api/auth/login` | público | E-mail e senha, devolve o token |
| POST | `/api/auth/cadastro` | público | Cria uma conta de **cidadão** |
| GET | `/api/auth/eu` | logado | Dados da conta do token |

`POST /api/auth/login` com `{"email": "rita@medflow.local", "senha": "..."}`:

```json
{ "token": "eyJhbGciOiJIUzI1NiJ9...", "tipo": "Bearer", "expiraEmSegundos": 28800, "papel": "CIDADAO" }
```

Nas demais chamadas, envie `Authorization: Bearer <token>`. O token é um JWT assinado com HS256, vale 8 horas (`JWT_EXPIRACAO`) e leva o id da conta (`sub`), o `email` e o `papel` (`CIDADAO` ou `GESTOR`).

`POST /api/auth/cadastro` com `{"nome": "...", "email": "...", "senha": "..."}` (senha de 8 a 72 caracteres) devolve `201` com `id`, `nome`, `email` e `papel`. O papel **não** pode ser escolhido: toda conta criada por aqui é `CIDADAO`. Conta de `GESTOR` só é criada direto no banco, com a senha em BCrypt.

| Situação | Resposta |
| --- | --- |
| Sem token, token inválido, adulterado ou expirado | `401` com `title: "Não autenticado"` e cabeçalho `WWW-Authenticate` |
| Papel sem permissão (cidadão em `/api/gestao` ou gestor em `/api/lista-desejos`) | `403` com `title: "Acesso negado"` |
| Login com e-mail ou senha errados | `401` com `title: "Credenciais inválidas"` (a mesma resposta nos dois casos) |
| Cadastro com e-mail que já existe (a caixa não importa) | `409` com `title: "E-mail já cadastrado"` |
| Corpo inválido (campo vazio, e-mail ruim, senha curta) | `400` |

Todos os erros seguem o padrão `application/problem+json`. O CORS libera o cabeçalho `Authorization` para as origens de `CORS_ALLOWED_ORIGINS`.

Com o suporte nativo a RFC 9457 habilitado, erros gerados automaticamente pelo Spring MVC podem apresentar o campo `title` em inglês, como `"Bad Request"` (400), `"Method Not Allowed"` (405) e `"Unsupported Media Type"` (415). Já os erros tratados pelos handlers de domínio utilizam títulos em português, como `"Parâmetro inválido"` e `"Recurso não encontrado"`. Para identificar erros programaticamente, utilize o código HTTP (`status`), em vez de depender do texto de `title`.

No perfil `dev` existem três contas de exemplo, com a senha `medflow-dev-123`: `rita@medflow.local` (cidadã), `carlos@medflow.local` (gestor da UBS Benedito Bentes I) e `marta@medflow.local` (gestora sem vínculo, vê a rede toda). Elas nunca são criadas em produção.

## Lista de desejos

A lista de desejos permite que um cidadão acompanhe medicamentos em uma UBS de referência e escolha se deseja receber avisos de disponibilidade.

Todas as rotas exigem um JWT válido com o papel `CIDADAO`. O proprietário é identificado automaticamente pela claim `sub` do token; não é necessário informar o ID do usuário na requisição.

### Listar desejos

`GET /api/lista-desejos`

Retorna os itens cadastrados pelo cidadão autenticado, ordenados do mais recente para o mais antigo.

Exemplo de resposta (`200`):

```json
[
  {
    "id": 10,
    "ubsId": 1,
    "ubsNome": "UBS Centro",
    "medicamentoId": 3,
    "medicamentoNome": "Metformina",
    "notificar": true,
    "criadoEm": "2026-11-17T15:00:00Z"
  }
]
```

Se o cidadão não possuir itens, a resposta será uma lista vazia (`[]`).

### Adicionar medicamento

`POST /api/lista-desejos`

Corpo da requisição:

```json
{
  "ubsId": 1,
  "medicamentoId": 3,
  "notificar": true
}
```

| Campo | Obrigatório | Regra |
| --- | --- | --- |
| `ubsId` | sim | ID positivo de uma UBS existente |
| `medicamentoId` | sim | ID positivo de um medicamento existente |
| `notificar` | não | `true` ou `false`; padrão `true` |

A resposta retorna `201 Created` com os dados do item cadastrado, no mesmo formato apresentado na listagem.

O mesmo cidadão não pode cadastrar duas vezes o mesmo medicamento para a mesma UBS. A unicidade é garantida pela constraint `uk_item_lista_desejo`, baseada em `(usuario_id, ubs_id, medicamento_id)`.

Uma tentativa de cadastro duplicado retorna `409 Conflict` no padrão `application/problem+json`:

```json
{
  "type": "about:blank",
  "title": "Item já cadastrado",
  "status": 409,
  "detail": "Medicamento já cadastrado na lista de desejos para esta UBS"
}
```

Cidadãos diferentes podem adicionar o mesmo medicamento para a mesma UBS, pois cada lista pertence ao respectivo usuário.

### Ativar ou desativar notificações

`PATCH /api/lista-desejos/{id}`

Corpo da requisição:

```json
{
  "notificar": false
}
```

O campo `notificar` é obrigatório e deve ser `true` ou `false`.

A resposta retorna `200 OK` com os dados atualizados do item.

Essa operação modifica apenas a preferência de notificação. O item permanece na lista de desejos.

### Remover medicamento

`DELETE /api/lista-desejos/{id}`

Remove o item indicado da lista do cidadão autenticado.

Em caso de sucesso, retorna `204 No Content`, sem corpo na resposta.

### Segurança e tratamento de erros

Cada cidadão pode listar, atualizar e remover somente os próprios itens.

As consultas de propriedade são filtradas diretamente no banco de dados pelo ID do usuário autenticado. Se um cidadão tentar atualizar ou remover um item que pertence a outro usuário, a API retorna `404`, sem revelar os dados do proprietário.

| Situação | Resposta |
| --- | --- |
| Requisição sem token ou com token inválido | `401 Unauthorized` |
| Usuário com papel `GESTOR` | `403 Forbidden` |
| IDs inválidos ou corpo inválido | `400 Bad Request` |
| UBS ou medicamento inexistente no cadastro | `404 Not Found` |
| Item inexistente ou pertencente a outro usuário no PATCH/DELETE | `404 Not Found` |
| Medicamento já cadastrado para a mesma UBS e usuário | `409 Conflict` |

Os erros de domínio utilizam o padrão RFC 9457 (`application/problem+json`).

O campo `notificar` controla a preferência de aviso. O envio efetivo das notificações será implementado na F5.

## Paginação

`GET /api/medicamentos` e `GET /api/ubs/{id}/estoque` respondem sempre no mesmo formato:

`GET /api/medicamentos?busca=met&pagina=0&tamanho=20`

```json
{
  "itens": [{ "id": 1, "catmat": "BR0267690", "nome": "Metformina", "...": "..." }],
  "pagina": 0,
  "tamanho": 20,
  "totalItens": 1,
  "totalPaginas": 1
}
```

| Parâmetro | Padrão | Regra |
| --- | --- | --- |
| `pagina` | `0` | a partir de 0 |
| `tamanho` | `20` | de 1 a 100 |

Fora da regra retorna `400` (`application/problem+json`). Página além do fim vem com `itens` vazio e os totais preenchidos. A ordem é por nome (do medicamento, no estoque), com o `id` desempatando, então a mesma página traz sempre os mesmos itens.

Ficam sem paginação, por terem tamanho limitado: `/ubs/{id}/estoque/resumo` (as contagens precisam do estoque inteiro), `/medicamentos/{id}/disponibilidade` (uma linha por UBS, e a busca do front conta "Em X de Y UBS" com a lista completa) e `/medicamentos/categorias`.

A busca por nome e categoria usa índices `pg_trgm` (veja [banco-de-dados.md](banco-de-dados.md#índices-da-busca)).

## Filtro por categoria

Filtrar medicamentos pela categoria:

`GET /api/medicamentos?categoria=Diabetes`

Combinar a categoria com a busca textual:

`GET /api/medicamentos?busca=metformina&categoria=Diabetes`

Listar as categorias disponíveis:

`GET /api/medicamentos/categorias`

A categoria é filtrada sem diferenciar letras maiúsculas de minúsculas. Se nenhuma categoria corresponder, a listagem de medicamentos retorna uma página vazia (`itens: []`, `totalItens: 0`).

Status de estoque: `DISPONIVEL`, `RESERVADO`, `EM_FALTA`.

## UBS por proximidade

`GET /api/ubs?lat=-9.55&lng=-35.72&raioKm=3&medicamentoId=1`

Sem parâmetros, a rota lista as unidades por nome, como sempre. Com `lat` e `lng`, devolve da unidade mais próxima para a mais distante, com a distância em km (Haversine, calculada no banco, arredondada para 2 casas).

| Parâmetro | Obrigatório | Regra |
| --- | --- | --- |
| `lat` | com `lng` | de -90 a 90 |
| `lng` | com `lat` | de -180 a 180 |
| `raioKm` | não | maior que zero; só entram unidades até essa distância. Sem ele, usa 20 km |
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

Sem `raioKm` a busca usa 20 km, para não varrer todas as unidades do banco. A caixa de latitude/longitude (índice `idx_ubs_localizacao`) descarta as unidades distantes antes da conta de distância. Para raios grandes a resposta pode ser longa: esta rota ainda não é paginada.

## Resumo do estoque da UBS

Consultar a lista de itens do estoque de uma UBS:

`GET /api/ubs/{id}/estoque`

A resposta segue o [formato paginado](#paginação), em ordem de nome do medicamento (`?pagina=` e `?tamanho=`).

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

Sem o parâmetro, entram itens de todos os status (em `/estoque`, paginados).

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

## Escopo do gestor

Um gestor pode ter vínculo com uma UBS (`usuario.ubs_gestao_id`). Por enquanto o vínculo só é definido direto no banco (não há endpoint nem tela para isso). **Regra:** todo endpoint de `/api/gestao` deve resolver o escopo chamando o `EscopoGestaoService`; o código não impõe isso sozinho, então cada rota nova precisa fazer a chamada (e ter teste).

| Gestor | O que vê | Pedir `ubsId` |
| --- | --- | --- |
| Com vínculo | Só a UBS vinculada | A própria UBS é aceita; outra UBS responde `403` |
| Sem vínculo | A rede toda | Filtra pela UBS informada (`404` se não existir) |

`GET /api/gestao/escopo` (só `GESTOR`, `?ubsId=` opcional) mostra o escopo atual:

```json
{ "ubs": { "id": 1, "nome": "UBS Benedito Bentes I", "cnes": "9000101" }, "restrito": true }
```

Para o gestor sem vínculo e sem `ubsId`, a resposta é `{ "ubs": null, "restrito": false }`. Conta apagada, ou que deixou de ser gestora, deixa de valer mesmo com o token antigo (`401`), em vez de ver a rede.

Atenção: hoje **gestor sem vínculo vê a rede toda**. Esquecer de preencher o vínculo no cadastro dá acesso total; esse comportamento foi escolhido para o MVP e está para confirmação do produto. Se mudar, a visão da rede passa a ser explícita (coluna ou papel próprio) e o gestor sem vínculo e sem essa permissão recebe `403`.

## Rastreamento da remessa

`GET /api/medicamentos/{id}/rastreamento?ubsId=` (público). Mostra a remessa do medicamento a caminho da UBS.

```json
{
  "medicamentoId": 1,
  "ubsId": 1,
  "remessa": {
    "id": 4,
    "quantidade": 300,
    "previsaoChegada": "2026-11-22",
    "etapaAtual": "EM_TRANSPORTE",
    "concluida": false,
    "linhaDoTempo": [
      { "etapa": "PEDIDO", "ocorridoEm": "2026-11-17T12:00:00Z" },
      { "etapa": "SEPARADO_CAF", "ocorridoEm": "2026-11-17T20:00:00Z" },
      { "etapa": "EM_TRANSPORTE", "ocorridoEm": "2026-11-18T04:00:00Z" },
      { "etapa": "RECEBIDO", "ocorridoEm": null },
      { "etapa": "DISPONIVEL", "ocorridoEm": null }
    ]
  }
}
```

- A `linhaDoTempo` sempre traz as cinco etapas na ordem; `ocorridoEm` é `null` nas que ainda não aconteceram. A `etapaAtual` é a mais avançada com data. Uma remessa sem nenhum evento (não deveria existir) sai com `etapaAtual: "PEDIDO"` e a linha do tempo toda sem datas.
- Com mais de uma remessa do mesmo medicamento na UBS, vale a que ainda está em andamento e chega primeiro. Sem nenhuma em andamento, vale a mais recente.
- Sem remessa, a resposta é `200` com `"remessa": null`.
- `404` se o medicamento ou a UBS não existir; `400` se `ubsId` faltar ou não for um número positivo.
- A BNAFAR pública não informa remessas em trânsito: os dados vêm de cadastro, e no perfil `dev`, do seed.

## Movimentações de estoque

`GET /api/ubs/{id}/movimentacoes` (público), da mais recente para a mais antiga.

| Parâmetro | Regra |
| --- | --- |
| `medicamentoId` | Opcional. Mostra só as movimentações desse medicamento (`404` se não existir) |
| `limite` | Opcional, de 1 a 100 (padrão 20). Fora disso, `400` |

```json
[
  {
    "id": 17,
    "medicamentoId": 4,
    "medicamentoNome": "Losartana potássica",
    "tipo": "DISPENSACAO",
    "quantidade": -20,
    "ocorridoEm": "2026-11-19T15:00:00Z"
  }
]
```

A `quantidade` tem sinal: `ENTRADA` é positiva, `DISPENSACAO` é negativa e `AJUSTE` pode ser as duas.

- `404` se a UBS ou o `medicamentoId` informado não existir. Um medicamento que existe mas não tem movimentação na UBS responde `200` com `[]`.
- É uma lista simples com as últimas `limite` movimentações, e não uma página como as listagens de estoque e medicamentos (`pagina` e `tamanho`): a tela só mostra as mais recentes. Se virar histórico completo, passa a usar `PaginaResponse`.

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
