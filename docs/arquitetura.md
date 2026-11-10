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
├── common/        exceções e ApiExceptionHandler
└── config/        CORS e configurações web
```

Regras:

- Controllers nunca devolvem entidades JPA: sempre um `*Response` (data class) via `toResponse()`.
- Leituras usam `@Transactional(readOnly = true)`; consultas com relacionamento usam `join fetch` para evitar N+1.
- Quando surgir regra de negócio, crie um `*Service` no próprio pacote da funcionalidade.

## Frontend (`frontend/src/app`)

```
core/        modelos (interfaces da API) e services HTTP
shared/      componentes reutilizáveis (StatusBadge, SyncIndicator)
layout/      Shell (cabeçalho, navegação, rodapé)
features/    uma pasta por área: home, ubs, medicamentos
```

Regras:

- Componentes **standalone**, estado com **signals**; rotas com `loadComponent` (lazy loading).
- Parâmetros de rota e query entram como `input()` (`withComponentInputBinding`).
- Estilo com **Tailwind**; tokens de cor do projeto ficam em `src/styles.css` (`@theme`).

## Decisões importantes

| Decisão | Motivo |
| --- | --- |
| Identificadores oficiais (CNES, CATMAT) como chaves únicas | Integração futura com BNAFAR / e-SUS AF sem tabelas de-para. |
| `ultima_sincronizacao` por UBS exposta na API | Transparência: o cidadão vê quão atual é o dado (unidades rurais sincronizam com atraso). |
| Dados de exemplo só no perfil `dev` (`db/seed`) | Produção nunca recebe dados fictícios. |
| Testcontainers nos testes de integração | Testa contra PostgreSQL real, não H2. |

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
- Filtros: `codigo_municipio` (IBGE de 6 dígitos), `data_posicao_estoque`; paginação por `limit` (até 1000) e `offset`.
- Cada registro é um lote. O cliente devolve `PosicaoEstoque` com o IBGE em 7 dígitos e o CNES com 7 dígitos.
- Registros sem CNES, CATMAT, quantidade ou data da posição são descartados (com aviso no log). Validade ausente vira nula.
- Falha de rede, timeout, erro HTTP ou resposta inválida lançam `FonteEstoqueIndisponivelException`; quem chama decide se tenta de novo.
- Se passar de `app.bnafar.max-paginas`, falha em vez de devolver dados truncados.
- O CATMAT vem como a fonte manda, às vezes com sufixo de unidade (`BR0267691U0042`).

Configuração em `app.bnafar` (`application.yml`): `base-url`, `tamanho-pagina`, `max-paginas`, `timeout-conexao`, `timeout-leitura`.

Os testes usam respostas reais gravadas em `backend/src/test/resources/bnafar` e nunca chamam a API de verdade.
