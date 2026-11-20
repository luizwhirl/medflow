#!/usr/bin/env bash
# Recria o banco local do zero: apaga tudo, aplica as migrations (db/migration) e o seed (db/seed).
# Uso: scripts/db-reset.sh [-y]
source "$(dirname "$0")/_db.sh"

for arg in "$@"; do
  if [[ "$arg" == "-y" ]]; then SIM=1; else echo "Uso: scripts/db-reset.sh [-y]"; exit 1; fi
done
confirmar "Isso apaga todos os dados do banco local."

subir_banco
recriar_banco
dc run --rm flyway
echo "Banco recriado com migrations e seed."
