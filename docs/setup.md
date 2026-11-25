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

As tabelas são criadas pelo **Flyway** quando o backend sobe. Para recriar o banco com as migrations e os dados de
exemplo, sem precisar subir o backend:

```bash
scripts/db-reset.sh          # PowerShell: .\scripts\db-reset.ps1
```

Salvar e restaurar o banco (`db-dump` e `db-restore`) estão em [banco-de-dados.md](banco-de-dados.md#recriar-salvar-e-restaurar-o-banco-local).

## 3. Backend

```bash
cd backend
./gradlew bootRun     # sobe em http://localhost:8080 com o perfil dev
./gradlew test        # testes (os de integração usam Testcontainers e exigem Docker)
./gradlew build       # compila, testa e gera o jar em build/libs
```

No Windows use `gradlew.bat` (ou `./gradlew` no Git Bash).

Perfis:

- `dev` (padrão): dados reais. Só `db/migration`; ao subir, sincroniza Maceió (`SYNC_MUNICIPIOS=2704302`) com a BNAFAR e repete de hora em hora. Log DEBUG.
- `demo` (junto com o dev: `SPRING_PROFILES_ACTIVE=dev,demo`): acrescenta os dados fictícios de `db/seed` e desliga a sincronização. As UBS do seed não existem de verdade; não use para mostrar o mapa a quem vai à unidade.
- `prod`: só `db/migration`, Swagger desligado. Ative com `SPRING_PROFILES_ACTIVE=prod` (é o padrão da imagem Docker do backend; o `docker-compose.yml` troca para `dev`; nele o perfil se escolhe com `API_PROFILE`). **Exige `JWT_SECRET`** (pelo menos 32 caracteres); sem ele a aplicação não sobe.

Contas de exemplo no perfil `dev` (senha `medflow-dev-123`): `rita@medflow.local` (cidadã), `carlos@medflow.local` (gestor da UBS Benedito Bentes I, fictícia: só tem vínculo com o perfil `demo`; sem ele, vê a rede toda) e `marta@medflow.local` (gestora sem vínculo). A área da gestão (`/gestao`) pede entrar com uma conta de gestor. Veja [api.md](api.md#autenticação).

## 4. Frontend

```bash
cd frontend
npm install
npm start             # http://localhost:4200 (proxy de /api para :8080)
npm run test:ci       # testes unitários em Chrome headless
npm run format        # Prettier
npm run lint          # ESLint (angular-eslint); o CI também roda
```

Se não tiver Chrome instalado, aponte `CHROME_BIN` para outro navegador Chromium (ex.: Edge).

### Testar a busca por voz no celular

O microfone só funciona em contexto seguro (HTTPS ou `localhost`). Abrir `http://<ip-da-máquina>:4200` no celular não serve: o botão de microfone nem aparece. Duas saídas, com o `npm start` rodando:

- **Android (Chrome):** ligue a depuração USB no celular, conecte no computador e abra `chrome://inspect` no Chrome do computador. Em **Port forwarding**, encaminhe a porta `4200` para `localhost:4200`. No celular, abra `http://localhost:4200`: para o navegador é `localhost`, então o microfone funciona. Não precisa de túnel nem de certificado.
- **iPhone (Safari) e qualquer outro aparelho:** use a homologação com HTTPS (PDS-25), quando existir, ou um túnel HTTPS para a porta 4200 da sua máquina (por exemplo, `cloudflared tunnel --url http://localhost:4200`). O `/api` passa pelo proxy do `npm start`, então basta expor a 4200. Se o Angular recusar o endereço do túnel ("Blocked request"), acrescente o domínio em `allowedHosts` nas opções do `serve` no `angular.json`, só na sua máquina.

O roteiro do teste (navegadores, permissões e frases) está na PDS-54. O que é enviado e as mensagens de erro estão em [busca-por-voz.md](busca-por-voz.md).

## 5. Tudo em containers (opcional)

Serve para quem não tem JDK ou Node, ou está com problema no Gradle local. Só o Docker é necessário:

```bash
docker compose --profile full up -d --build
```

Sobe o banco, a API e a web (Nginx) **com os dados de exemplo**, porque a API roda no perfil `dev`:

- Web: http://localhost:4200 (o Nginx encaminha `/api` para a API)
- API: http://localhost:8080 · Swagger: http://localhost:8080/swagger-ui.html (o Swagger só existe no perfil `dev`)
- Contas de exemplo: veja [api.md](api.md#autenticação)

Os endereços usam as portas padrão; se você mudou `API_PORT` ou `WEB_PORT`, troque-as. As portas (inclusive a do banco) só aceitam conexões do seu próprio computador, porque no perfil `dev` a API tem segredo JWT público e uma conta de gestor de exemplo.

O primeiro build leva alguns minutos (baixa as dependências do Gradle e do npm). Para parar: `docker compose --profile full down`; para zerar o banco, acrescente `-v`.

Variáveis (no `.env` ou no ambiente; veja `.env.example`):

| Variável | Padrão | Para quê |
| --- | --- | --- |
| `API_PORT` | `8080` | Porta da API no seu computador |
| `WEB_PORT` | `4200` | Porta da web (a API libera o CORS para ela) |
| `API_PROFILE` | `dev` | Perfil da API. `prod` não carrega o seed nem as contas de exemplo e **exige `JWT_SECRET`** |
| `JWT_SECRET`, `JWT_EXPIRACAO` | do perfil | Repassados à API só quando definidos |

O nome é `API_PROFILE` (e não `SPRING_PROFILES_ACTIVE`) de propósito: assim a variável que você exporta para o `bootRun` não muda o perfil da stack em containers. Trocar de perfil não apaga o banco: os dados de exemplo já gravados continuam lá, mesmo em `prod`. Para zerar, use `docker compose --profile full down -v`.

## Problemas comuns

- **Porta 5432 ocupada:** mude `POSTGRES_PORT` no `.env` e `DB_URL` de acordo. Na stack em containers, `API_PORT` e `WEB_PORT` fazem o mesmo para a API e a web.
- **Telas vazias na stack em containers:** a API subiu fora do perfil `dev`. Confira `API_PROFILE` (o padrão é `dev`) e suba de novo com `docker compose --profile full up -d`; não precisa apagar o banco, porque ao voltar para `dev` o Flyway aplica os seeds que faltam.
- **`Flyway validation failed`:** uma migration já aplicada foi editada. Nunca altere migrations antigas; crie uma nova (`V2__...sql`). Em dev, `scripts/db-reset.sh` recria o banco.
- **Testes de integração "skipped":** o Docker não está rodando.
