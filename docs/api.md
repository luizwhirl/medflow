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
| GET | `/medicamentos/{id}/disponibilidade/resumo` | Total de unidades e unidades com estoque disponível, com filtro opcional por município |
| GET | `/lista-desejos` | Lista os medicamentos desejados pelo cidadão autenticado |
| POST | `/lista-desejos` | Adiciona um medicamento à lista de desejos |
| PATCH | `/lista-desejos/{id}` | Ativa ou desativa a notificação de um item |
| DELETE | `/lista-desejos/{id}` | Remove um item da lista de desejos |
| GET | `/me` | Consulta o perfil do cidadão autenticado |
| PUT | `/me` | Atualiza o perfil, a UBS de referência e as preferências do cidadão |

## Autenticação

A leitura de UBS e medicamentos (`GET /api/ubs/**` e `GET /api/medicamentos/**`), a saúde (`/actuator/health`) e a documentação (Swagger) são públicas. Todo o resto exige login. O prefixo `/api/gestao` é só do papel `GESTOR`, enquanto `/api/lista-desejos`, `/api/me` e suas subrotas são exclusivos do papel `CIDADAO`. Rota que não existe também responde `401` sem token.

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
| Papel sem permissão (cidadão em `/api/gestao` ou gestor em `/api/lista-desejos` ou `/api/me`) | `403` com `title: "Acesso negado"` |
| Login com e-mail ou senha errados | `401` com `title: "Credenciais inválidas"` (a mesma resposta nos dois casos) |
| Cadastro com e-mail que já existe (a caixa não importa) | `409` com `title: "E-mail já cadastrado"` |
| Corpo inválido (campo vazio, e-mail ruim, senha curta) | `400` |

Todos os erros seguem o padrão `application/problem+json`. O CORS libera o cabeçalho `Authorization` para as origens de `CORS_ALLOWED_ORIGINS`.

Com o suporte nativo a RFC 9457 habilitado, erros gerados automaticamente pelo Spring MVC podem apresentar o campo `title` em inglês, como `"Bad Request"` (400), `"Method Not Allowed"` (405) e `"Unsupported Media Type"` (415). Já os erros tratados pelos handlers de domínio utilizam títulos em português, como `"Parâmetro inválido"` e `"Recurso não encontrado"`. Para identificar erros programaticamente, utilize o código HTTP (`status`), em vez de depender do texto de `title`.

No perfil `dev` existem duas contas de exemplo, com a senha `medflow-dev-123`: `rita@medflow.local` (cidadã) e `carlos@medflow.local` (gestor). Elas nunca são criadas em produção.

## Perfil do cidadão

O endpoint `GET /api/auth/eu` retorna os dados básicos da conta autenticada e pode ser utilizado por cidadãos e gestores. Já `GET /api/me` é exclusivo do papel `CIDADAO` e retorna, além dos dados básicos, a UBS de referência, o bairro, a localização aproximada e as preferências do perfil.

A API de perfil permite que o cidadão configure seus dados pessoais de apresentação, sua UBS de referência, uma localização aproximada e preferências da interface.

Essas informações podem ser utilizadas pela tela inicial, pela saudação "Olá, ...", pelo card "Minha UBS de referência", pelo botão "Definir como minha UBS" e pela tela de Perfil.

As duas rotas exigem um JWT válido com o papel `CIDADAO`. O cidadão é identificado automaticamente pela claim `sub` do token.

Não é necessário nem permitido escolher o ID do proprietário do perfil na requisição.

### Consultar perfil

`GET /api/me`

Retorna `200 OK` com as informações do cidadão autenticado.

Exemplo de resposta para um perfil já configurado:

```json
{
  "id": 7,
  "nome": "Rita Maria",
  "email": "rita@medflow.local",
  "ubsReferencia": {
    "id": 3,
    "cnes": "9000103",
    "nome": "UBS Clima Bom",
    "bairro": "Clima Bom",
    "endereco": "Rua do Posto, 300"
  },
  "bairro": "Clima Bom",
  "latitudeAproximada": -9.56,
  "longitudeAproximada": -35.74,
  "letraMaior": true,
  "salvarBuscasRecentes": true,
  "buscasRecentes": [
    "Metformina",
    "Losartana"
  ]
}
```

O objeto `ubsReferencia` apresenta informações da unidade cadastrada no sistema. Esses dados são consultados a partir do relacionamento com a tabela `ubs`, sem duplicar o cadastro da unidade no perfil.

Para um cidadão que ainda não configurou suas preferências, a resposta terá os valores padrão:

```json
{
  "id": 7,
  "nome": "Dona Rita",
  "email": "rita@medflow.local",
  "ubsReferencia": null,
  "bairro": null,
  "latitudeAproximada": null,
  "longitudeAproximada": null,
  "letraMaior": false,
  "salvarBuscasRecentes": false,
  "buscasRecentes": []
}
```

O perfil existe para todo usuário cadastrado, pois seus dados básicos são armazenados na tabela `usuario`.

Não é necessário criar o perfil por uma rota separada.

### Atualizar perfil

`PUT /api/me`

Recebe um objeto JSON com os campos editáveis do perfil e retorna `200 OK` com os dados atualizados, no mesmo formato da consulta.

Exemplo de requisição:

```json
{
  "nome": "Rita Maria",
  "ubsReferenciaId": 3,
  "bairro": "Clima Bom",
  "latitudeAproximada": -9.56167,
  "longitudeAproximada": -35.74167,
  "letraMaior": true,
  "salvarBuscasRecentes": true,
  "buscasRecentes": [
    "Metformina",
    "Losartana"
  ]
}
```

A atualização substitui os campos editáveis do perfil. Portanto, o frontend deve enviar os valores atuais dos campos que deseja manter, além daqueles que deseja modificar.

| Campo | Obrigatório | Regra |
| --- | --- | --- |
| `nome` | sim | Texto não vazio, com até 120 caracteres |
| `ubsReferenciaId` | não | ID positivo de uma UBS existente ou `null` |
| `bairro` | não | Texto com até 120 caracteres ou `null` |
| `latitudeAproximada` | não | Número entre -90 e 90, informado junto com a longitude, ou `null` |
| `longitudeAproximada` | não | Número entre -180 e 180, informado junto com a latitude, ou `null` |
| `letraMaior` | sim | Booleano `true` ou `false` |
| `salvarBuscasRecentes` | sim | Booleano `true` ou `false` |
| `buscasRecentes` | sim | Lista com até 10 termos, podendo ser vazia |

Os campos `ubsReferenciaId`, `bairro`, `latitudeAproximada` e `longitudeAproximada` podem ser enviados como `null`.

Os campos `nome`, `letraMaior`, `salvarBuscasRecentes` e `buscasRecentes` devem estar presentes na requisição.

O e-mail e o ID do usuário não podem ser alterados por esse endpoint.

O nome e o bairro são normalizados, removendo espaços extras. Um bairro vazio é armazenado como `null`.

### UBS de referência

O campo `ubsReferenciaId` identifica a unidade escolhida pelo cidadão.

Por exemplo, para definir a UBS de ID 3, o frontend envia:

```json
{
  "nome": "Rita Maria",
  "ubsReferenciaId": 3,
  "bairro": "Clima Bom",
  "latitudeAproximada": -9.56,
  "longitudeAproximada": -35.74,
  "letraMaior": true,
  "salvarBuscasRecentes": false,
  "buscasRecentes": []
}
```

A API consulta a UBS pelo ID diretamente no banco de dados.

Se a unidade não existir, retorna `404 Not Found`. Um ID não positivo retorna `400 Bad Request`.

Para remover a UBS de referência, envie `ubsReferenciaId: null` em uma atualização completa do perfil.

A mesma UBS pode ser escolhida por vários cidadãos. A escolha de uma unidade não altera os dados da UBS nem a lista de desejos.

Se uma UBS referenciada for excluída do cadastro, o relacionamento é removido pela constraint `ON DELETE SET NULL`.

### Bairro e localização aproximada

O bairro e a localização aproximada são dados opcionais informados pelo próprio cidadão.

Latitude e longitude devem ser fornecidas juntas:

- Ambas podem ser `null`.
- Se apenas uma coordenada for informada, a API retorna `400`.
- Latitude deve estar entre -90 e 90.
- Longitude deve estar entre -180 e 180.
- Valores não finitos são rejeitados.

As coordenadas válidas são arredondadas para duas casas decimais antes de serem armazenadas.

Por exemplo:

```json
{
  "latitudeAproximada": -9.56167,
  "longitudeAproximada": -35.74167
}
```

São armazenadas e retornadas como:

```json
{
  "latitudeAproximada": -9.56,
  "longitudeAproximada": -35.74
}
```

Essas coordenadas são preferências de localização do cidadão. Elas não modificam a localização oficial da UBS.

### Preferência por letras maiores

O campo `letraMaior` controla uma preferência de acessibilidade da interface.

| Valor | Significado |
| --- | --- |
| `false` | Utilizar tamanho padrão de texto |
| `true` | Preferir textos maiores |

O valor padrão é `false`.

A API apenas persiste e devolve essa preferência. A aplicação frontend é responsável por aplicá-la visualmente.

### Buscas recentes

O campo `salvarBuscasRecentes` define se o cidadão deseja manter um histórico de pesquisas.

O valor padrão é `false`.

Quando o armazenamento está habilitado, o campo `buscasRecentes` aceita até 10 termos, ordenados do mais recente para o mais antigo.

Exemplo:

```json
{
  "salvarBuscasRecentes": true,
  "buscasRecentes": [
    "Metformina",
    "Losartana",
    "Dipirona"
  ]
}
```

As regras de armazenamento são:

- Cada termo deve possuir entre 1 e 120 caracteres após a normalização.
- Espaços excedentes são removidos.
- Termos repetidos são eliminados, ignorando diferenças entre letras maiúsculas e minúsculas.
- A primeira ocorrência de cada termo é preservada.
- A lista deve conter no máximo 10 elementos.

Por exemplo:

```json
{
  "salvarBuscasRecentes": true,
  "buscasRecentes": [
    " Metformina ",
    "METFORMINA",
    "Losartana"
  ]
}
```

Resulta no histórico:

```json
{
  "buscasRecentes": [
    "Metformina",
    "Losartana"
  ]
}
```

Se `salvarBuscasRecentes` for `false`, o serviço remove as buscas anteriormente armazenadas e retorna uma lista vazia, independentemente dos termos enviados dentro do limite aceito.

Cada `PUT /api/me` substitui integralmente o histórico anterior pela lista enviada em `buscasRecentes`; os termos não são adicionados ao histórico existente. A atualização do perfil e a substituição do histórico ocorrem na mesma transação.

Nesta versão, o histórico **não é registrado automaticamente** pelas consultas realizadas em `GET /api/medicamentos?busca=`.

O frontend deve manter e enviar a lista de termos por `PUT /api/me` quando desejar atualizar o histórico.

### Segurança e respostas de erro

| Situação | Resposta |
| --- | --- |
| Requisição sem token ou com token inválido | `401 Unauthorized` |
| Token sem identificador de usuário válido | `401 Unauthorized` |
| Usuário autenticado com papel `GESTOR` | `403 Forbidden` |
| Usuário do token não encontrado | `401 Unauthorized` |
| Nome vazio ou com tamanho inválido | `400 Bad Request` |
| UBS de referência com ID não positivo | `400 Bad Request` |
| UBS de referência inexistente | `404 Not Found` |
| Latitude ou longitude inválidas | `400 Bad Request` |
| Apenas uma das coordenadas informada | `400 Bad Request` |
| Histórico com mais de 10 buscas | `400 Bad Request` |
| Termo de busca vazio ou com mais de 120 caracteres | `400 Bad Request` |

As falhas seguem o padrão `application/problem+json`.

A API não recebe `usuarioId` no corpo nem na URL. Todas as operações são associadas ao cidadão identificado pelo JWT.

O perfil de um cidadão não pode ser consultado ou modificado por outro cidadão por meio dessas rotas.

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

Sem `raioKm` a busca usa 20 km, para não varrer todas as unidades do banco. A caixa de latitude/longitude (índice `idx_ubs_localizacao`) descarta as unidades distantes antes da conta de distância. Para raios grandes a resposta pode ser longa; a paginação virá com a PDS-22.

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
