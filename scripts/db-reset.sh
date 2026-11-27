#!/usr/bin/env bash
# Recria o banco local do zero: apaga tudo e aplica as migrations (db/migration).
# Com --demo, aplica também os dados fictícios (db/seed), os mesmos do perfil "demo" da API.
# Sem --demo o banco fica vazio e a API (perfil dev) o enche com os dados reais da BNAFAR ao subir.
# Uso: scripts/db-reset.sh [-y] [--demo]
source "$(dirname "$0")/_db.sh"

LOCAIS="filesystem:/flyway/db/migration"
for arg in "$@"; do
  case "$arg" in
    -y) SIM=1 ;;
    --demo) LOCAIS="$LOCAIS,filesystem:/flyway/db/seed" ;;
    *) echo "Uso: scripts/db-reset.sh [-y] [--demo]"; exit 1 ;;
  esac
done
confirmar "Isso apaga todos os dados do banco local."

subir_banco
recriar_banco
dc run --rm -e FLYWAY_LOCATIONS="$LOCAIS" flyway
if [[ "$LOCAIS" == *seed* ]]; then
  echo "Banco recriado com migrations e dados fictícios (suba a API com SPRING_PROFILES_ACTIVE=dev,demo)."
else
  echo "Banco recriado só com migrations. Suba a API (perfil dev) para trazer os dados reais da BNAFAR."
fi
