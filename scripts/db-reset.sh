#!/usr/bin/env bash
# Recria o banco local do zero: apaga tudo, aplica as migrations (db/migration) e o seed (db/seed).
# Uso: scripts/db-reset.sh [-y]
source "$(dirname "$0")/_db.sh"

[[ "${1:-}" == "-y" ]] && SIM=1
confirmar "Isso apaga todos os dados do banco local."

subir_banco
recriar_banco
dc run --rm flyway
echo "Banco recriado com migrations e seed."
