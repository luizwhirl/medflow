# MedFlow API

Backend em Kotlin + Spring Boot 4, com PostgreSQL e Flyway.

```bash
docker compose up -d   # na raiz do repositório: sobe o PostgreSQL
./gradlew bootRun      # http://localhost:8080 (perfil dev, com dados de exemplo)
./gradlew test         # testes (integração usa Testcontainers -> precisa de Docker)
./gradlew build        # jar em build/libs
```

- Swagger UI: http://localhost:8080/swagger-ui.html
- Endpoints: [../docs/api.md](../docs/api.md) · Banco: [../docs/banco-de-dados.md](../docs/banco-de-dados.md)
