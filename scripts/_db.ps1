# Funções comuns de db-reset.ps1, db-dump.ps1 e db-restore.ps1 (não execute direto).
# Tudo roda dentro dos containers do docker-compose: não precisa de psql/pg_dump instalados.
$Raiz = Split-Path -Parent $PSScriptRoot
$Compose = Join-Path $Raiz 'docker-compose.yml'
$ArquivoNoContainer = '/tmp/medflow.dump'

# docker compose do projeto, de qualquer pasta (caminhos de arquivo continuam relativos à pasta atual).
function Invoke-Compose {
    # o docker escreve progresso no stderr; no PowerShell 5.1, com 'Stop', isso viraria erro. Vale o código de saída.
    $ErrorActionPreference = 'Continue'
    docker compose -f $Compose @args
    if ($LASTEXITCODE -ne 0) { throw "Falhou: docker compose $args" }
}

# Pede confirmação antes de apagar dados; -y pula a pergunta.
function Confirm-Apagar([string]$Mensagem, [bool]$Sim) {
    if ($Sim) { return }
    $resposta = Read-Host "$Mensagem Continuar? [s/N]"
    if ($resposta -notmatch '^[sS]$') { Write-Host 'Cancelado.'; exit 1 }
}

# Apaga e cria de novo o banco do .env (POSTGRES_DB), derrubando conexões abertas (ex.: a API).
function Reset-Banco {
    $sql = @'
DROP DATABASE IF EXISTS :"db" WITH (FORCE);
CREATE DATABASE :"db" OWNER :"dono";
'@
    $ErrorActionPreference = 'Continue'
    $sql | docker compose -f $Compose exec -T db sh -c 'psql -q -v ON_ERROR_STOP=1 -U $POSTGRES_USER -d postgres -v db=$POSTGRES_DB -v dono=$POSTGRES_USER'
    if ($LASTEXITCODE -ne 0) { throw 'Falhou ao recriar o banco.' }
}
