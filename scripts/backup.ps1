# Gera um backup do banco do Galo & Cia (Pastelaria do Galo e Peixaria).
#
# Uso (a partir da raiz do repositório):
#   powershell -ExecutionPolicy Bypass -File scripts\backup.ps1
#
# O backup é um arquivo .dump em backups\, com data e hora no nome.
# Não altera nem apaga nada no banco: só lê. Precisa do Docker Desktop rodando.

param(
    [string]$Container = "pastelaria-ss-db",
    [string]$Banco = "pastelaria",
    [string]$Usuario = "pastelaria",
    [string]$Destino = (Join-Path $PSScriptRoot "..\backups")
)

$ErrorActionPreference = "Stop"

New-Item -ItemType Directory -Force -Path $Destino | Out-Null

$nomeArquivo = "pastelaria-" + (Get-Date -Format "yyyyMMdd-HHmmss") + ".dump"
$caminhoNoContainer = "/tmp/$nomeArquivo"
$caminhoLocal = Join-Path (Resolve-Path $Destino) $nomeArquivo

# Formato custom (-Fc): comprimido e restaurável seletivamente pelo pg_restore.
docker exec $Container pg_dump -U $Usuario -d $Banco -Fc -f $caminhoNoContainer
if ($LASTEXITCODE -ne 0) { throw "pg_dump falhou. O container está rodando?" }

docker cp "${Container}:$caminhoNoContainer" $caminhoLocal
if ($LASTEXITCODE -ne 0) { throw "Não foi possível copiar o backup para fora do container." }

docker exec $Container rm -f $caminhoNoContainer | Out-Null

Write-Output "Backup gerado: $caminhoLocal"
