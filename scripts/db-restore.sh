#!/usr/bin/env bash
# Substitui o banco local pelo conteúdo de um dump gerado pelo db-dump.
# Uso: scripts/db-restore.sh <arquivo> [-y]
source "$(dirname "$0")/_db.sh"

arquivo=""
for arg in "$@"; do
  if [[ "$arg" == "-y" ]]; then SIM=1; else arquivo="$arg"; fi
done
[[ -n "$arquivo" ]] || { echo "Uso: scripts/db-restore.sh <arquivo> [-y]"; exit 1; }
[[ -f "$arquivo" ]] || { echo "Arquivo não encontrado: $arquivo"; exit 1; }
confirmar "Isso substitui todos os dados do banco local por $arquivo."

subir_banco
recriar_banco
dc exec -T db sh -c 'pg_restore -U $POSTGRES_USER -d $POSTGRES_DB --no-owner --exit-on-error' <"$arquivo"
echo "Banco restaurado de $arquivo"
