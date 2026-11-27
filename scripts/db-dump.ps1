# Salva o banco local num arquivo (pg_dump, formato custom).
# Uso: scripts\db-dump.ps1 [arquivo]   (padrão: backups\medflow-AAAAMMDD-HHMMSS.dump)
param([string]$Arquivo = "backups/medflow-$(Get-Date -Format 'yyyyMMdd-HHmmss').dump")
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '_db.ps1')

$pasta = Split-Path -Parent $Arquivo
if ($pasta) { New-Item -ItemType Directory -Force $pasta | Out-Null }

Invoke-Compose up -d --wait db
# o dump é gravado dentro do container e copiado depois: redirecionar a saída binária no PowerShell corromperia o arquivo
Invoke-Compose exec -T db sh -c "pg_dump -U `$POSTGRES_USER -d `$POSTGRES_DB -Fc -f $ArquivoNoContainer"
Invoke-Compose cp "db:$ArquivoNoContainer" $Arquivo
Invoke-Compose exec -T db rm -f $ArquivoNoContainer
Write-Host "Dump salvo em $Arquivo"
