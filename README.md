# MedFlow

Consulta de estoque de medicamentos nas UBS e rastreamento da assistência farmacêutica no SUS (Alagoas).

> Projeto da disciplina de Projeto e Desenvolvimento de Software — UFAL.

O cidadão consulta **em quais Unidades Básicas de Saúde perto dele** o remédio está disponível, reservado ou em falta antes de sair de casa. A gestão ganha visibilidade do estoque para remanejar antes que o medicamento vença. Os dados vêm da BNAFAR / e-SUS AF, identificados por **CNES** (unidade), **CATMAT** (medicamento) e **RENAME**.

## Stack

| Camada | Tecnologia |
| --- | --- |
| Frontend | Angular 20 (standalone, signals) + Tailwind CSS 4 |
| Backend | Kotlin 2.3 + Spring Boot 4 (Web MVC, Data JPA, Validation, Actuator) |
| Banco | PostgreSQL 17 + Flyway (migrations) |
| Docs da API | springdoc-openapi (Swagger UI) |
| Testes | Jasmine/Karma (front) · JUnit 5 + MockMvc + Testcontainers (back) |
| Infra | Docker Compose · GitHub Actions |

## Estrutura

```
medflow/
├── backend/            API REST em Kotlin (Spring Boot)
├── frontend/           Aplicação web em Angular + Tailwind
├── front/              Protótipo de média fidelidade (HTML estático, deploy no Netlify)
├── docs/               Arquitetura, setup, API, banco e convenções
├── docker-compose.yml  PostgreSQL (+ pgAdmin e stack completa opcionais)
└── .github/            CI e templates de PR/issue
```

## Início rápido

Pré-requisitos: **JDK 21**, **Node 22** (veja `.nvmrc`) e **Docker**.

```bash
cp .env.example .env
docker compose up -d                 # PostgreSQL em localhost:5432
cd backend && ./gradlew bootRun      # API em http://localhost:8080
cd frontend && npm install && npm start   # Web em http://localhost:4200
```

- Swagger UI: http://localhost:8080/swagger-ui.html
- Health check: http://localhost:8080/actuator/health

O perfil `dev` (padrão) já carrega dados fictícios de exemplo. Guia completo em [docs/setup.md](docs/setup.md).

## Documentação

- [Setup do ambiente](docs/setup.md)
- [Arquitetura](docs/arquitetura.md)
- [API](docs/api.md)
- [Banco de dados](docs/banco-de-dados.md)
- [Como contribuir](CONTRIBUTING.md)

## Equipe

Luiz Bomfim · Lucas Q. · Rayssa · Pedro H. · Luís F.
