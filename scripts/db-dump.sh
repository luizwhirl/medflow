#!/usr/bin/env bash
# Salva o banco local num arquivo (pg_dump, formato custom).
# Uso: scripts/db-dump.sh [arquivo]   (padrão: backups/medflow-AAAAMMDD-HHMMSS.dump)
source "$(dirname "$0")/_db.sh"

arquivo="${1:-backups/medflow-$(date +%Y%m%d-%H%M%S).dump}"
mkdir -p "$(dirname "$arquivo")"

subir_banco
dc exec -T db sh -c 'pg_dump -U $POSTGRES_USER -d $POSTGRES_DB -Fc' >"$arquivo" || { rm -f "$arquivo"; exit 1; }
echo "Dump salvo em $arquivo"
