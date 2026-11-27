# Recria o banco local do zero: apaga tudo e aplica as migrations (db/migration).
# Com -Demo, aplica também os dados fictícios (db/seed), os mesmos do perfil "demo" da API.
# Sem -Demo o banco fica vazio e a API (perfil dev) o enche com os dados reais da BNAFAR ao subir.
# Uso: scripts\db-reset.ps1 [-y] [-Demo]
param([Alias('y')][switch]$Sim, [switch]$Demo)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '_db.ps1')

Confirm-Apagar 'Isso apaga todos os dados do banco local.' $Sim

$locais = 'filesystem:/flyway/db/migration'
if ($Demo) { $locais += ',filesystem:/flyway/db/seed' }

Invoke-Compose up -d --wait db
Reset-Banco
Invoke-Compose run --rm -e "FLYWAY_LOCATIONS=$locais" flyway
if ($Demo) {
    Write-Host 'Banco recriado com migrations e dados fictícios (suba a API com SPRING_PROFILES_ACTIVE=dev,demo).'
} else {
    Write-Host 'Banco recriado só com migrations. Suba a API (perfil dev) para trazer os dados reais da BNAFAR.'
}
