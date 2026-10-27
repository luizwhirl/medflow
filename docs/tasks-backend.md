# Tasks do back-end

Base: `backend/` (Kotlin + Spring Boot, package-by-feature em `br.ufal.medflow`). Cada task = uma branch
`feat/<descricao>` a partir da `main` e um PR com 1 revisor (veja [CONTRIBUTING.md](../CONTRIBUTING.md)).
Toda task atualiza [api.md](api.md) quando cria ou muda endpoint, e vem com teste.

Legenda: **L** = Lucas, **F** = Felipe. Tamanho: P (≤ meio dia), M (1 dia), G (2+ dias).

## Visão geral

| ID | Task | Quem | Tam. | Depende de |
| --- | --- | --- | --- | --- |
| L1 | Migração V2: lotes e validade | L | M | — |
| L2 | Interface `FonteEstoque` + cliente BNAFAR | L | G | — |
| L3 | Job de sincronização (UBS, medicamentos, estoque) | L | G | L1, L2 |
| L4 | Regra de status do estoque | L | P | L1 |
| L5 | Busca por proximidade | L | M | — |
| L6 | Autenticação JWT e papéis | L | M | — |
| L7 | Painel da gestão | L | G | L1, L3, L6, F3 |
| L8 | Rastreio de remessa | L | M | decisão de produto |
| F1 | Filtro por categoria e lista de categorias | F | P | — |
| F2 | Resumo do estoque da UBS | F | P | — |
| F3 | Lista de desejos (CRUD) | F | M | — (usa L6 depois) |
| F4 | Resumo de disponibilidade do medicamento | F | P | — |
| F5 | Avisos (notificações) | F | M | F3, L3 |
| F6 | Validação de parâmetros e testes | F | P | — |

Ordem sugerida: **L1, L2, L5, F1, F2, F4, F6** em paralelo → **L3, L4, F3, L6** → **F5, L7, L8**.

## Decisões em aberto

1. **Status "reservado".** A BNAFAR não informa. Proposta: derivar de limiares configuráveis (L4).
2. **Rastreio de remessa (CAF → UBS).** A BNAFAR pública não traz remessas em trânsito. Sem fonte, só dá
   para simular (seed) ou cadastrar manualmente. Decidir antes de começar L8.
3. **Identificação do cidadão.** O schema usa `cidadao_cns` (Cartão SUS, dado sensível). Proposta: conta
   simples com JWT e, no MVP, não guardar o CNS (L6/F3).
4. **Código IBGE.** O schema guarda 7 dígitos (`2704302`) e a API da BNAFAR usa 6 (`270430`). Normalizar
   no cliente (L2) para não quebrar os filtros.

---

## Lucas

### L1 — Migração V2: lotes e validade
O schema atual guarda só `quantidade` por UBS e medicamento. O painel precisa de lote e validade.
- Criar `V2__lote_estoque.sql` com a tabela `lote_estoque` (`item_estoque_id`, `numero_lote`, `validade`,
  `quantidade`). Nunca editar o `V1`.
- Entidade e repositório; `ItemEstoque.quantidade` passa a ser a soma dos lotes.
- Atualizar o seed (`db/seed`) com lotes, incluindo alguns vencendo em até 60 dias.
- **Pronto quando:** a migração roda no teste de integração e o seed carrega no perfil `dev`.

### L2 — Interface `FonteEstoque` + cliente BNAFAR
Fonte pública: `GET https://apidadosabertos.saude.gov.br/daf/estoque-medicamentos-bnafar-horus`
(filtros `codigo_municipio`, `codigo_uf`, `codigo_cnes`, `data_posicao_estoque`; `limit` até 1000 e `offset`).
- Criar o pacote `integracao/` com a interface `FonteEstoque` e a implementação BNAFAR (`RestClient`).
- Paginar até esgotar os registros; tolerar falha e timeout sem derrubar a aplicação.
- Mapear cada registro (um lote) para um DTO interno. O registro traz CNES, CATMAT, quantidade, lote,
  validade, endereço, telefone e latitude/longitude.
- Normalizar o IBGE (6 → 7 dígitos) e tratar `data_validade` (`"2026-09-30 00:00:00-03"`).
- **Pronto quando:** teste com servidor simulado cobre paginação, erro HTTP e registro incompleto. Não
  chamar a API real nos testes.
- Observação: a `bnafar-api-hom` é só de envio (exige `Authorization`) e não serve aqui.

### L3 — Job de sincronização
- `@Scheduled` por município configurável (`app.sync.municipios`), com liga e desliga por propriedade.
- Upsert de UBS por CNES (usa o endereço e a geolocalização do registro), de medicamento por CATMAT e de
  estoque por (UBS, medicamento), com os lotes de L1.
- Agregar os lotes antes de gravar, atualizar `ultima_sincronizacao` e aplicar a regra de L4.
- Idempotente: rodar duas vezes seguidas não duplica nada.
- **Pronto quando:** teste de integração com a fonte simulada (L2) mostra a segunda execução sem mudança.
- Ponto de atenção: a API traz só `descricao_produto` e CATMAT. Nome, concentração e forma precisam ser
  extraídos da descrição ou vir de um catálogo próprio (RENAME). Decidir a abordagem aqui.

### L4 — Regra de status do estoque
- Função pura que recebe quantidade (e demanda, se houver) e devolve `DISPONIVEL`, `RESERVADO` ou `EM_FALTA`.
- Limiares em `application.yml`. `EM_FALTA` quando a quantidade é 0.
- **Pronto quando:** testes unitários cobrem os limites (0, abaixo e acima do limiar).

### L5 — Busca por proximidade
- `GET /ubs?lat=&lon=&raioKm=` e `GET /medicamentos/{id}/disponibilidade?lat=&lon=&raioKm=`.
- Distância por haversine em SQL (nativa ou JPQL), com `distanciaKm` na resposta, ordenada pela menor.
- Sem `lat`/`lon` o comportamento atual continua igual.
- **Pronto quando:** teste com UBS em coordenadas conhecidas confere a ordem e o corte do raio.

### L6 — Autenticação JWT e papéis
- Migração com `usuario` (email, senha com hash, papel `CIDADAO` ou `GESTOR`).
- `POST /api/auth/login` devolve o JWT (HS256, segredo por variável de ambiente `JWT_SECRET`).
- Segurança: leitura pública (`GET /ubs/**`, `/medicamentos/**`), `/api/gestao/**` só para `GESTOR`, o
  resto exige login. Ajustar o CORS que já existe.
- **Pronto quando:** testes cobrem 401 sem token, 403 com papel errado e 200 com token válido.

### L7 — Painel da gestão (`/api/gestao`)
- `GET /gestao/painel` (KPIs: itens em falta, cidadãos aguardando, lotes vencendo, UBS com sync atrasada).
- `GET /gestao/remanejamento` (sugere UBS com sobra para UBS em falta próxima) e `POST` para registrar a
  ação (remanejar ou descartar), com trilha de auditoria.
- `GET /gestao/demanda` (contagem da lista de desejos por UBS e medicamento), `/gestao/validade`
  (lotes em até 60 dias) e `/gestao/conexao` (UBS com sync atrasada).
- Só `GESTOR`. Dividir em PRs por aba.

### L8 — Rastreio de remessa
Só começar após a decisão 2. Modelo `remessa` (medicamento, UBS destino, etapa, datas, quantidade) e
`GET /medicamentos/{id}/remessa?ubsId=` com a linha do tempo (pedido, separado, em transporte, recebido,
disponível).

---

## Felipe

Todas as tasks abaixo seguem os padrões que já existem em `ubs/` e `medicamento/`: controller devolve
`*Response` (nunca a entidade), leitura com `@Transactional(readOnly = true)`, regra de negócio num `*Service`.

### F1 — Filtro por categoria e lista de categorias
- `GET /medicamentos?categoria=Diabetes` (combina com `busca`).
- `GET /medicamentos/categorias` devolve as categorias distintas.
- **Pronto quando:** teste de controller cobre filtro, combinação com busca e categoria inexistente.

### F2 — Resumo do estoque da UBS
- `GET /ubs/{id}/estoque` passa a trazer também as contagens por status (`disponivel`, `reservado`,
  `emFalta`). Filtro opcional `?status=`.
- **Pronto quando:** teste confere as contagens com o seed e o 404 de UBS inexistente.

### F3 — Lista de desejos (CRUD)
A tabela `item_lista_desejo` já existe no V1.
- Entidade, repositório e service. Endpoints: `GET`, `POST` e `DELETE /api/lista-desejos`, e `PATCH` para
  ligar ou desligar o aviso (`notificar`).
- Versão inicial identifica o cidadão por parâmetro (provisório). Quando L6 sair, passa a usar o usuário
  autenticado.
- Evitar duplicata (a constraint `uk_item_lista_desejo` já cobre; devolver 409 e não 500).
- **Pronto quando:** testes cobrem criar, listar, duplicado (409) e remover.

### F4 — Resumo de disponibilidade do medicamento
- `GET /medicamentos/{id}/disponibilidade/resumo` com o total de unidades, quantas têm o item e a unidade
  mais próxima com estoque (a distância vem de L5; antes disso, devolver só as contagens).
- **Pronto quando:** teste confere as contagens com o seed.

### F5 — Avisos (notificações)
- Migração `aviso` (usuário, tipo, título, texto, lido, criado_em). Endpoints `GET /avisos` e
  `PATCH /avisos/lidas`.
- Gerar aviso quando um item da lista de desejos passa para `DISPONIVEL` (evento chamado pela
  sincronização de L3) e quando uma UBS fica com dados atrasados.
- Push de verdade fica fora do MVP.

### F6 — Validação de parâmetros e testes
- Bean Validation nos parâmetros (`raioKm > 0`, `lat` entre -90 e 90, `lon` entre -180 e 180, tamanho de
  página limitado). Erros no padrão RFC 9457 que já existe em `ApiExceptionHandler`.
- Ampliar os testes de `UbsController` e de estoque (hoje só há teste de `MedicamentoController`).

---

## Regras de todos

- Comentários curtos, em português, só o essencial.
- Migrações nunca editadas depois de mergeadas; criar `V3__...`, `V4__...`.
- Antes do PR: `cd backend && ./gradlew build`.
