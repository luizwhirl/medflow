# Arquitetura

## Visão geral

```
[Navegador] ──HTTP──> [Frontend Angular] ──/api──> [API Kotlin/Spring] ──JPA──> [PostgreSQL]
                                                          │
                                                          └──(futuro)──> APIs BNAFAR / e-SUS AF
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
