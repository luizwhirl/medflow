# Setup do ambiente

## Pré-requisitos

| Ferramenta | Versão | Observação |
| --- | --- | --- |
| JDK | 21 | Temurin recomendado. O Gradle é baixado pelo wrapper (`./gradlew`), não precisa instalar. |
| Node.js | 22 (mín. 20.19) | Use `nvm use` na raiz (lê o `.nvmrc`). |
| Docker | Desktop ou Engine com Compose | Para o PostgreSQL e para os testes de integração. |
| IDE | IntelliJ IDEA (backend) / VS Code (frontend) | Extensões recomendadas em `.vscode/extensions.json`. |

## 1. Variáveis de ambiente

```bash
cp .env.example .env
```

Os valores padrão já funcionam localmente. O backend também tem defaults no `application.yml`, então o `.env` só é necessário se você mudar portas ou senhas.

## 2. Banco de dados

```bash
docker compose up -d                 # PostgreSQL em localhost:5432 (medflow / medflow)
docker compose --profile tools up -d # + pgAdmin em http://localhost:5050
```

As tabelas são criadas pelo **Flyway** quando o backend sobe. Para zerar o banco:

```bash
docker compose down -v
```

## 3. Backend

```bash
cd backend
./gradlew bootRun     # sobe em http://localhost:8080 com o perfil dev
./gradlew test        # testes (os de integração usam Testcontainers e exigem Docker)
./gradlew build       # compila, testa e gera o jar em build/libs
```

No Windows use `gradlew.bat` (ou `./gradlew` no Git Bash).

Perfis:

- `dev` (padrão): carrega `db/migration` + `db/seed` (dados fictícios), log DEBUG.
- `prod`: só `db/migration`, Swagger desligado. Ative com `SPRING_PROFILES_ACTIVE=prod`.

## 4. Frontend

```bash
cd frontend
npm install
npm start             # http://localhost:4200 (proxy de /api para :8080)
npm run test:ci       # testes unitários em Chrome headless
npm run format        # Prettier
```

Se não tiver Chrome instalado, aponte `CHROME_BIN` para outro navegador Chromium (ex.: Edge).

## 5. Tudo em containers (opcional)

```bash
docker compose --profile full up -d --build
```

Sobe banco, API (`:8080`) e web servida por Nginx (`:4200`).

## Problemas comuns

- **Porta 5432 ocupada:** mude `POSTGRES_PORT` no `.env` e `DB_URL` de acordo.
- **`Flyway validation failed`:** uma migration já aplicada foi editada. Nunca altere migrations antigas; crie uma nova (`V2__...sql`). Em dev, `docker compose down -v` recria o banco.
- **Testes de integração "skipped":** o Docker não está rodando.
