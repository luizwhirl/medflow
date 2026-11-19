# Funções comuns de db-reset.sh, db-dump.sh e db-restore.sh (não execute direto).
# Tudo roda dentro dos containers do docker-compose: não precisa de psql/pg_dump instalados.
set -euo pipefail
RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# docker compose do projeto, de qualquer pasta (caminhos de arquivo continuam relativos à pasta atual)
dc() { docker compose -f "$RAIZ/docker-compose.yml" "$@"; }

# Pede confirmação antes de apagar dados; -y pula a pergunta.
confirmar() {
  [[ "${SIM:-0}" == 1 ]] && return 0
  read -r -p "$1 Continuar? [s/N] " resposta
  [[ "$resposta" =~ ^[sS]$ ]] || { echo "Cancelado."; exit 1; }
}

subir_banco() {
  dc up -d --wait db
}

# Apaga e cria de novo o banco do .env (POSTGRES_DB), derrubando conexões abertas (ex.: a API).
recriar_banco() {
  dc exec -T db sh -c \
    'psql -q -v ON_ERROR_STOP=1 -U $POSTGRES_USER -d postgres -v db=$POSTGRES_DB -v dono=$POSTGRES_USER' <<'SQL'
DROP DATABASE IF EXISTS :"db" WITH (FORCE);
CREATE DATABASE :"db" OWNER :"dono";
SQL
}
