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
| GET | `/avisos` | Lista os 50 avisos mais recentes do cidadão autenticado |
| GET | `/avisos/nao-lidos/total` | Retorna a quantidade total de avisos não lidos do cidadão |
| PATCH | `/avisos/lidas` | Marca como lidos todos os avisos ainda não lidos do cidadão |

## Autenticação

A leitura de UBS e medicamentos (`GET /api/ubs/**` e `GET /api/medicamentos/**`), a saúde (`/actuator/health`) e a documentação (Swagger) são públicas. Todo o resto exige login. O prefixo `/api/gestao` é só do papel `GESTOR`, enquanto `/api/lista-desejos`, `/api/avisos` e suas subrotas são exclusivos do papel `CIDADAO`. Rota que não existe também responde `401` sem token.

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
| Papel sem permissão (cidadão em `/api/gestao` ou gestor em `/api/lista-desejos` ou `/api/avisos`) | `403` com `title: "Acesso negado"` |
| Login com e-mail ou senha errados | `401` com `title: "Credenciais inválidas"` (a mesma resposta nos dois casos) |
| Cadastro com e-mail que já existe (a caixa não importa) | `409` com `title: "E-mail já cadastrado"` |
| Corpo inválido (campo vazio, e-mail ruim, senha curta) | `400` |

Todos os erros seguem o padrão `application/problem+json`. O CORS libera o cabeçalho `Authorization` para as origens de `CORS_ALLOWED_ORIGINS`.

Com o suporte nativo a RFC 9457 habilitado, erros gerados automaticamente pelo Spring MVC podem apresentar o campo `title` em inglês, como `"Bad Request"` (400), `"Method Not Allowed"` (405) e `"Unsupported Media Type"` (415). Já os erros tratados pelos handlers de domínio utilizam títulos em português, como `"Parâmetro inválido"` e `"Recurso não encontrado"`. Para identificar erros programaticamente, utilize o código HTTP (`status`), em vez de depender do texto de `title`.

No perfil `dev` existem duas contas de exemplo, com a senha `medflow-dev-123`: `rita@medflow.local` (cidadã) e `carlos@medflow.local` (gestor). Elas nunca são criadas em produção.

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

O campo `notificar` controla a geração dos avisos persistidos pela API. Com `false`, o cidadão não recebe novos avisos relacionados àquele item. Os avisos já gerados permanecem disponíveis no histórico.

## Avisos do cidadão

Os avisos são registros persistidos no banco e consultados pelo cidadão pela API. Não há envio de push, e-mail ou SMS nesta funcionalidade. Todas as rotas exigem `Authorization: Bearer <token>` com papel `CIDADAO`; o usuário é identificado pelo `sub` do JWT, sem receber um `usuarioId` na URL ou no corpo.

### Listar avisos

`GET /api/avisos`

Retorna `200 OK` com **até 50 avisos mais recentes** do cidadão autenticado, ordenados por `criadoEm` decrescente e, em caso de empate, pelo ID decrescente.

O limite de 50 registros é aplicado diretamente na consulta ao banco. A rota não recebe parâmetros de paginação nesta versão.

Exemplo ilustrativo de resposta (os textos variam conforme o evento):

```json
[
  {
    "id": 21,
    "tipo": "MEDICAMENTO_DISPONIVEL",
    "titulo": "Medicamento disponível",
    "texto": "O medicamento acompanhado está disponível na UBS selecionada.",
    "lido": false,
    "criadoEm": "2026-11-19T16:30:00Z"
  },
  {
    "id": 18,
    "tipo": "UBS_DADOS_ATRASADOS",
    "titulo": "Dados da UBS desatualizados",
    "texto": "Os dados de estoque da unidade UBS Centro estão desatualizados. Consulte a data da última atualização antes de se dirigir à unidade.",
    "lido": true,
    "criadoEm": "2026-11-18T12:00:00Z"
  }
]
```

Os campos retornados são `id`, `tipo`, `titulo`, `texto`, `lido` e `criadoEm` (instante no formato ISO 8601). Se não houver avisos, retorna `[]`.

O limite de 50 considera avisos lidos e não lidos. Registros mais antigos permanecem armazenados, mas não aparecem nessa listagem.

### Contar avisos não lidos

`GET /api/avisos/nao-lidos/total`

Retorna `200 OK` com a quantidade total de avisos não lidos do cidadão autenticado.

Exemplo de resposta:

```json
{
  "naoLidos": 7
}
```

Se não houver avisos não lidos, retorna:

```json
{
  "naoLidos": 0
}
```

A contagem é realizada diretamente no banco de dados e considera **todos os avisos não lidos**, inclusive os mais antigos que não aparecem entre os 50 registros retornados pela listagem.

Essa rota pode ser utilizada pelo frontend para exibir a quantidade de avisos pendentes no menu de notificações, sem precisar carregar o histórico completo.

### Marcar todos os avisos como lidos

`PATCH /api/avisos/lidas`

Não recebe corpo na requisição. Retorna `200 OK` com a quantidade de avisos que passaram de não lidos para lidos:

```json
{
  "atualizados": 2
}
```

Se não houver avisos não lidos, retorna `{"atualizados": 0}`. A atualização é feita diretamente no banco e afeta apenas os avisos do cidadão autenticado. Esta operação não remove os avisos.

A atualização inclui todos os avisos não lidos do cidadão, inclusive aqueles que não aparecem na listagem limitada a 50 registros. Após a marcação, o contador de não lidos passa a retornar zero, desde que nenhum novo aviso tenha sido gerado.

### Geração automática dos avisos

Os tipos atualmente implementados são:

| Tipo | Quando é gerado |
| --- | --- |
| `MEDICAMENTO_DISPONIVEL` | Durante a sincronização do estoque, quando um item novo já está `DISPONIVEL` ou um item existente muda de outro status para `DISPONIVEL`. |
| `UBS_DADOS_ATRASADOS` | Durante a verificação do job de sincronização, quando uma UBS acompanhada possui dados de estoque antigos ou não possui data de referência. |

A geração considera os itens da lista de desejos com `notificar: true` no momento da verificação. O aviso de disponibilidade exige a combinação exata de UBS e medicamento acompanhados.

O aviso de atraso considera a UBS acompanhada, gerando no máximo um aviso por cidadão e UBS para a mesma referência de atualização, mesmo que ele acompanhe vários medicamentos naquela unidade.

**Deduplicação dos avisos de disponibilidade:**

Uma sincronização repetida, sem nova transição para `DISPONIVEL`, não gera outro aviso de disponibilidade.

Quando há uma nova transição para `DISPONIVEL`, o serviço verifica se o cidadão já possui um aviso de disponibilidade **não lido** para a mesma combinação de UBS e medicamento.

- Se já existe um aviso não lido, outro não é criado.
- Se não existe aviso não lido, a transição pode gerar um novo aviso.
- Marcar um aviso como lido não gera automaticamente outro; é necessária uma nova transição de disponibilidade.

Assim, oscilações de status entre `DISPONIVEL` e `RESERVADO` não acumulam avisos não lidos para a mesma combinação de UBS e medicamento durante o processamento sequencial.

Essa verificação de avisos não lidos não constitui um mecanismo de exclusão mútua entre sincronizações concorrentes. A coordenação de execuções simultâneas deve ser tratada separadamente.

**Deduplicação dos avisos de atraso:**

Os avisos de atraso utilizam uma chave de evento associada à UBS e à referência de atualização de seus dados. Verificações repetidas não geram avisos duplicados enquanto essa referência permanecer igual.

Se a UBS receber dados novos e, posteriormente, essa nova referência voltar a ficar atrasada, um novo aviso poderá ser gerado.

**Prazo para considerar uma UBS atrasada:**

O limite é configurado pela propriedade `app.avisos.atraso-horas`, com valor padrão de **24 horas**. A propriedade exige um número positivo de horas.

Uma UBS é considerada atrasada quando `ultimaSincronizacao` está ausente ou é **anterior** ao instante atual menos o prazo configurado.

Com o valor padrão:

| Tempo desde a última referência | Resultado |
| --- | --- |
| Menos de 24 horas | Atualizada |
| Exatamente 24 horas | Ainda não ultrapassou o limite |
| Mais de 24 horas | Atrasada |
| Sem data de referência | Atrasada |

O campo `ultimaSincronizacao` representa a data de referência dos dados recebidos da fonte, e não necessariamente a hora da execução do job.

O padrão de 24 horas segue o critério informado para o painel da gestão. Nesta implementação, a configuração pertence ao serviço de avisos; o compartilhamento de uma configuração única com o painel depende da integração das funcionalidades.

A verificação automática de atraso faz parte de `SincronizacaoAgendada` e depende de o job estar habilitado (`app.sync.ativo=true`). A verificação ainda é tentada quando a consulta à fonte falha para um município; isso não significa que a fonte conseguiu atualizar seus dados.

### Dados de demonstração no perfil dev

No perfil `dev`, o componente `AvisosDeExemplo` cria três avisos fictícios para a conta `rita@medflow.local`:

| Aviso | Tipo | Estado inicial |
| --- | --- | --- |
| Medicamento disponível (exemplo) | `MEDICAMENTO_DISPONIVEL` | Não lido |
| Dados da UBS desatualizados (exemplo) | `UBS_DADOS_ATRASADOS` | Não lido |
| Aviso já lido (exemplo) | `MEDICAMENTO_DISPONIVEL` | Lido |

Esses registros permitem testar a listagem, o contador e a marcação de avisos como lidos no frontend.

Em um banco de desenvolvimento sem avisos anteriores, após a criação dos exemplos, `GET /api/avisos` retorna três registros e `GET /api/avisos/nao-lidos/total` retorna `{"naoLidos": 2}`.

Os avisos são inseridos após a criação das contas de exemplo. Suas chaves de evento são fixas e a constraint `uk_aviso_usuario_evento` impede duplicações nas reinicializações.

Os avisos de demonstração não são recriados caso já existam, mesmo que tenham sido marcados como lidos. Portanto, a contagem de não lidos pode ser diferente de dois após o uso da aplicação.

Esses registros não são criados nos perfis `test` ou `prod`.

A conta `carlos@medflow.local` possui papel `GESTOR` e não recebe esses avisos de demonstração. Ela também não tem permissão para acessar os endpoints exclusivos de cidadãos.

### Segurança e respostas de erro

| Situação | Resposta |
| --- | --- |
| Requisição sem token ou com token inválido | `401 Unauthorized` |
| Usuário autenticado com papel `GESTOR` | `403 Forbidden` |
| Cidadão sem avisos na listagem | `200 OK` com `[]` |
| Cidadão sem avisos não lidos | `200 OK` com `{"naoLidos": 0}` no contador |
| Cidadão sem avisos para marcar como lidos | `200 OK` com `{"atualizados": 0}` |

Não é possível consultar nem marcar avisos de outro cidadão por essas rotas. A API utiliza o identificador do usuário autenticado presente no JWT.

Falhas de autenticação e autorização seguem o padrão `application/problem+json`.

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
