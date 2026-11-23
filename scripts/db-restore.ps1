# Substitui o banco local pelo conteúdo de um dump gerado pelo db-dump.
# Uso: scripts\db-restore.ps1 <arquivo> [-y]
param([Parameter(Mandatory)][string]$Arquivo, [Alias('y')][switch]$Sim)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '_db.ps1')

if (-not (Test-Path -PathType Leaf $Arquivo)) { Write-Host "Arquivo não encontrado: $Arquivo"; exit 1 }
Confirm-Apagar "Isso substitui todos os dados do banco local por $Arquivo." $Sim

Invoke-Compose up -d --wait db
Invoke-Compose cp $Arquivo "db:$ArquivoNoContainer"
Reset-Banco
Invoke-Compose exec -T db sh -c "pg_restore -U `$POSTGRES_USER -d `$POSTGRES_DB --no-owner --exit-on-error $ArquivoNoContainer"
Invoke-Compose exec -T db rm -f $ArquivoNoContainer
Write-Host "Banco restaurado de $Arquivo"
