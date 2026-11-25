# Arquitetura

## Visão geral

```
[Navegador] ──HTTP──> [Frontend Angular] ──/api──> [API Kotlin/Spring] ──JPA──> [PostgreSQL]
                                                          │
                                                          └──HTTP (somente leitura)──> API aberta da BNAFAR
```

- O **frontend** é um SPA. Em desenvolvimento, o `ng serve` encaminha `/api` para `localhost:8080` (`proxy.conf.json`); em container, o Nginx faz o mesmo.
- O **backend** expõe uma API REST JSON. Erros seguem o padrão RFC 9457 (`application/problem+json`).
- O **banco** é versionado com Flyway; o Hibernate só valida o schema (`ddl-auto: validate`).

## Backend (`backend/`)

Organizado **por funcionalidade** (package-by-feature), não por camada:

```
br.ufal.medflow
├── ubs/           Ubs (entidade), UbsRepository, UbsController, UbsResponse
├── medicamento/   Medicamento, MedicamentoRepository, MedicamentoController, MedicamentoResponse
├── estoque/       ItemEstoque, StatusEstoque, ItemEstoqueRepository, DTOs de estoque/disponibilidade
├── integracao/    FonteEstoque e cliente da BNAFAR (BnafarFonteEstoque), PosicaoEstoque, CodigoIbge
├── sincronizacao/ job que traz o estoque da BNAFAR para o banco (SincronizacaoEstoqueService)
├── auth/          usuários, login/cadastro, JWT e regras de acesso (SecurityConfig)
├── common/        exceções e ApiExceptionHandler
└── config/        CORS e configurações web
```

Regras:

- Controllers nunca devolvem entidades JPA: sempre um `*Response` (data class) via `toResponse()`.
- Leituras usam `@Transactional(readOnly = true)`; consultas com relacionamento usam `join fetch` para evitar N+1.
- Quando surgir regra de negócio, crie um `*Service` no próprio pacote da funcionalidade.

## Frontend (`frontend/src/app`)

```
core/        modelos (interfaces da API), services HTTP e offline/ (dados salvos e conexão)
shared/      componentes reutilizáveis (StatusBadge, SyncIndicator, Esqueleto, EstadoVazio...)
layout/      Shell (cabeçalho, navegação, rodapé)
features/    uma pasta por área: home, ubs, medicamentos
```

Regras:

- Componentes **standalone**, estado com **signals**; rotas com `loadComponent` (lazy loading).
- Parâmetros de rota e query entram como `input()` (`withComponentInputBinding`).
- Estilo com **Tailwind**; tokens de cor do projeto ficam em `src/styles.css` (`@theme`).

### Carregamento, vazio, erro e sem internet

Nenhuma tela fica em branco. Cada carga da API vira um `Recurso` (`core/recurso.ts`: `carregando`, `erro` ou `pronto`), e a tela mostra um componente padrão para cada estado:

- `app-esqueleto`: blocos no formato do conteúdo (`cartao`, `linha` ou `detalhe`) enquanto carrega;
- `app-estado-vazio`: ícone, título, explicação e a ação para sair dele;
- `app-mensagem-erro`: alerta com "Tentar de novo"; sem internet, explica que os dados não estão salvos no aparelho.

Sem internet, o `dadosSalvosInterceptor` (`core/offline`) devolve a última resposta salva de cada `GET /api/...`. Ela fica no `localStorage`, com o horário em que chegou. O `app-banner-offline`, no topo das duas cascas, mostra "Sem internet" e o horário dos dados salvos.

Regras do cache:

- só respostas de `GET` são guardadas, uma por URL (sem `lat`/`lng`, que mudam a cada leitura do GPS);
- `/api/gestao/**` e `/api/auth/**` nunca são guardadas: dependem de quem está logado, e o `localStorage` não separa contas nem é limpo no logout. Sem internet, o painel da gestão mostra o erro padrão;
- das buscas de medicamento (`?busca=`), ficam só as 50 mais recentes;
- uma alteração bem-sucedida descarta as respostas salvas daquele recurso;
- erros da API (400, 404) nunca são trocados por dados salvos; só falta de rede ou servidor fora do ar (0, 502, 503 e 504).

As telas são pré-carregadas (`PreloadAllModules`), então trocar de tela sem internet também funciona. Abrir o app do zero sem internet ainda depende de um service worker, que fica para outra task.

## Decisões importantes

| Decisão | Motivo |
| --- | --- |
| Identificadores oficiais (CNES, CATMAT) como chaves únicas | Integração futura com BNAFAR / e-SUS AF sem tabelas de-para. |
| Status do estoque derivado da quantidade (`RegraStatusEstoque`) | A BNAFAR só informa quantidade. "Reservado" é regra nossa: saldo até o limite do medicamento (`medicamento.limite_reservado`) ou, se ele não tiver um, até `app.estoque.limite-reservado`, ou saldo igual ou menor que a demanda da lista de desejos (já comprometido com quem espera). |
| `ultima_sincronizacao` por UBS exposta na API | Transparência: o cidadão vê quão atual é o dado (unidades rurais sincronizam com atraso). |
| Dados de exemplo só no perfil `demo` (`db/seed`) | Produção e o dev do dia a dia usam os dados reais da BNAFAR; fictício só quando pedido. |
| Testcontainers nos testes de integração | Testa contra PostgreSQL real, não H2. |
| API sem sessão, com JWT (HS256) no cabeçalho `Authorization` | O front é um SPA; sem cookie não há CSRF. Segredo em `JWT_SECRET` (obrigatório fora do dev), validade de 8 h. |
| Não guardamos o CNS do cidadão | Dado sensível (LGPD). A conta é só nome, e-mail e senha (hash BCrypt); a lista de desejos é identificada só pelo aparelho (UUID no navegador), sem login. |
| Todo cadastro é `CIDADAO`; `GESTOR` só é criado direto no banco | Ninguém ganha acesso de gestão pela API. |

## Próximos passos sugeridos

1. Lista de desejos (`item_lista_desejo` já existe no schema) com endpoints e tela.
2. Filtro por proximidade (latitude/longitude já estão no banco).
3. Job de sincronização com a API da BNAFAR.
4. Painel da gestão (remanejamento, demanda, validade), conforme o protótipo em `front/`.
5. Autenticação (cidadão via CNS / servidor da farmácia).

## Integração com a BNAFAR (`integracao/`)

`FonteEstoque` é a interface que o resto do código usa; `BnafarFonteEstoque` é a implementação.
Se o formato da fonte mudar (migração Hórus → e-SUS AF), só essa classe precisa ser reescrita.

- Fonte: `GET https://apidadosabertos.saude.gov.br/daf/estoque-medicamentos-bnafar-horus` (pública, sem autenticação).
  A `bnafar-api-hom` é só de envio (exige `Authorization`) e não serve para leitura.
- Filtros: `codigo_municipio` (IBGE de 6 dígitos), `data_posicao_estoque`. Paginação por `limit` e `offset`, onde **`offset` é o número da página** (0, 1, 2...) e não a quantidade de linhas puladas (conferido em 11/11/2026: `limit=2000&offset=1` devolve as linhas 2000 em diante). O cliente falha se duas páginas seguidas se repetirem, para não somar dados em dobro caso isso mude.
- Cada registro é um lote. O cliente devolve `PosicaoEstoque` com o IBGE em 7 dígitos e o CNES com 7 dígitos.
- Registros sem CNES, CATMAT, quantidade ou data da posição são descartados (com aviso no log). Validade ausente vira nula.
- Falha de rede, timeout, erro HTTP ou resposta inválida lançam `FonteEstoqueIndisponivelException`; quem chama decide se tenta de novo.
- Se passar de `app.bnafar.max-paginas`, falha em vez de devolver dados truncados.
- O CATMAT vem como a fonte manda, às vezes com sufixo de unidade (`BR0267691U0042`).

Configuração em `app.bnafar` (`application.yml`): `base-url`, `tamanho-pagina`, `max-paginas`, `timeout-conexao`, `timeout-leitura`.

Os testes usam respostas reais gravadas em `backend/src/test/resources/bnafar` e nunca chamam a API de verdade.

## Sincronização do estoque (`sincronizacao/`)

`SincronizacaoEstoqueService.sincronizar(ibge)` lê as posições da `FonteEstoque` e grava UBS, medicamentos,
itens de estoque e lotes. Idempotente: com os mesmos dados da fonte, uma nova execução não muda nada.
A leitura da fonte fica fora da transação; se ela falhar, nada é gravado.

O job fica em `app.sync` (ou nas variáveis de ambiente). Nos perfis `dev` e `prod` ele vem ligado para Maceió e roda
também ao subir; no perfil `demo` (dados fictícios) e nos testes, desligado:

| Propriedade | Variável | Padrão (dev/prod) | Padrão (sem perfil) |
| --- | --- | --- | --- |
| `ativo` | `SYNC_ATIVO` | `true` | `false` |
| `municipios` | `SYNC_MUNICIPIOS` | `2704302` (Maceió) | vazio (IBGE de 6 ou 7 dígitos, separados por vírgula) |
| `cron` | `SYNC_CRON` | `0 0 * * * *` (de hora em hora, fuso de Maceió) | igual |
| `executar-ao-iniciar` | `SYNC_AO_INICIAR` | `true` | `false` |
| `tolerancia-limite-km` | | `3.5` | igual |

A BNAFAR publica uma posição por dia (em 25/11/2026 a mais recente era a de 23/11). Rodar de hora em hora só garante
que a posição nova entra logo que sai; o "tempo real" do app é o da fonte. As telas abertas (detalhe da UBS e painel
da gestão) buscam os dados de novo a cada 5 minutos.

#### Localização das UBS

A coordenada da BNAFAR é a do CNES, e ela erra: em Maceió, 10 das 86 unidades vêm a 4 km ou mais fora do município
(a US Aliomar Almeida Lins, no Benedito Bentes II, cai no mar, a 77 km). Para nenhum pino ficar no lugar errado:

1. Cada coordenada é conferida com o contorno do município na API de malhas do IBGE (`IbgeLimitesMunicipais`, em
   memória depois da primeira busca). A malha é simplificada e corta o litoral (Ipioca, Riacho Doce, Pontal ficam até
   3,3 km "fora"), por isso a tolerância de 3,5 km da borda. Sem o contorno (IBGE fora do ar), a sincronização falha e
   nada é gravado.
2. Coordenada fora: a unidade é procurada pelo nome no OpenStreetMap (Nominatim, 1 requisição por segundo, só lugares de
   saúde, dentro do município) → origem `LUGAR`. Sem ela, o centro do bairro → `BAIRRO` (o app mostra "localização
   aproximada"). Sem nada → `INVALIDA`: a unidade fica no banco (o painel conta o estoque), mas sai da lista, do mapa,
   da busca por raio e do detalhe.
3. O gestor corrige o pino no painel (`PUT /api/gestao/ubs/{id}/localizacao`) → `MANUAL`, que a sincronização não
   sobrescreve. `LUGAR` e `BAIRRO` também não são procurados de novo; se a fonte passar a mandar uma coordenada boa, ela
   volta a valer (`FONTE`).

A busca por raio (`GET /api/ubs?lat&lng&raioKm`) devolve todas as UBS visíveis dentro do raio, sem limite de quantidade.

Regras de conversão (a BNAFAR é uma base de gestão, não pensada para o cidadão):

- **Unidades:** só entram UBS, USF/ESF e farmácias da rede. Secretaria, CAF (almoxarifado), CAPS, UPA e hospital ficam de fora. Unidade sem coordenadas também fica de fora (não dá para mostrar no mapa); coordenada fora do município segue a regra de localização acima.
- **Medicamentos:** a base mistura medicamentos com materiais (agulha, curativo...). Só entra o que tem dose e forma farmacêutica na descrição. Nome, concentração e forma são extraídos do texto (`DescricaoProduto`); a categoria vem do princípio ativo (`CategoriaMedicamento`, heurística do MVP; o resto é "Outros").
- **CATMAT:** o código vem com a unidade de fornecimento (`BR0267691U0042`, às vezes com `INAT` no fim). O medicamento é só `BR` + 7 dígitos.
- **Medicamento que já existe não é sobrescrito**, inclusive o do seed.
- **Item:** quantidade é a soma das posições da data mais recente do item (se a fonte devolver mais de um dia, os antigos não somam); o status vem de `RegraStatusEstoque`. Item que uma unidade da resposta deixou de informar (o estoque acabou) vai para quantidade 0 e `EM_FALTA` e perde os lotes. Unidade que some inteira da resposta não é tocada: a data de sincronização dela envelhece até aparecer como atrasada.
- **Lotes:** só viram lote posições com validade e saldo; lotes repetidos no mesmo item somam e ficam com a menor validade. Posição sem validade conta no total do item, mas não vira lote, então **a soma dos lotes pode ser menor que `item_estoque.quantidade`** (o comentário da migration V2 diz que seria igual; ele não foi alterado porque migration aplicada não se edita).
- **`ultima_sincronizacao` da UBS** é o dia da posição mais recente que a fonte tem para ela (e não a hora da nossa execução). Assim, unidade que não atualiza na fonte aparece como atrasada.
- Nomes, bairros e endereços vêm em maiúsculas e sem acento; são convertidos para texto legível (`Nomes`).
