# Recria o banco local do zero: apaga tudo, aplica as migrations (db/migration) e o seed (db/seed).
# Uso: scripts\db-reset.ps1 [-y]
param([Alias('y')][switch]$Sim)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '_db.ps1')

Confirm-Apagar 'Isso apaga todos os dados do banco local.' $Sim

Invoke-Compose up -d --wait db
Reset-Banco
Invoke-Compose run --rm flyway
Write-Host 'Banco recriado com migrations e seed.'
