# Como contribuir

## Fluxo de trabalho

1. Crie uma branch a partir da `main` atualizada:
   - `feat/<descricao>` para funcionalidade
   - `fix/<descricao>` para correção
   - `docs/<descricao>`, `chore/<descricao>`, `refactor/<descricao>`
2. Faça commits pequenos e com mensagem clara (veja abaixo).
3. Abra um Pull Request para a `main` preenchendo o template, com pelo menos **1 revisor**.
4. O CI precisa passar (build + testes de backend e frontend) antes do merge.

## Mensagens de commit

Seguimos [Conventional Commits](https://www.conventionalcommits.org/pt-br/), em português:

```
<tipo>(<escopo>): <descrição no imperativo>

feat(backend): adiciona endpoint de lista de desejos
fix(frontend): corrige filtro de busca vazio
docs: atualiza guia de setup
```

Tipos: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`, `ci`, `build`.
Escopos: `backend`, `frontend`, `db`, `infra`, `front` (protótipo).

## Padrões de código

- **Kotlin:** estilo oficial do Kotlin (4 espaços), package-by-feature, DTOs como `data class`.
- **Angular/TS:** Prettier (`npm run format`), componentes standalone, signals, Tailwind para estilos.
- **SQL:** palavras-chave em maiúsculas, nomes em `snake_case`, migrations nunca editadas depois de mergeadas.
- Escreva testes junto com a funcionalidade: `*Test.kt` / `*.spec.ts`.

## Antes de abrir o PR

```bash
cd backend && ./gradlew build
cd frontend && npm run format:check && npm run build && npm run test:ci
```
