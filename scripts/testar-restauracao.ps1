# Testa se o backup realmente restaura.
#
# Faz o que a rotina de backup faz, restaura o arquivo num banco TEMPORÁRIO
# separado e compara o número de linhas de cada tabela com o banco original.
# No fim, apaga o banco temporário. O banco da pastelaria não é alterado.
#
# Uso (a partir da raiz do repositório):
#   powershell -ExecutionPolicy Bypass -File scripts\testar-restauracao.ps1

param(
    [string]$Container = "pastelaria-ss-db",
    [string]$Banco = "pastelaria",
    [string]$Usuario = "pastelaria"
)

$ErrorActionPreference = "Stop"
# Mostra acentos corretamente no console do Windows.
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

# Nome fixo e fácil de reconhecer: este banco só existe durante o teste.
$bancoTemporario = "pastelaria_teste_restauracao"
$caminhoNoContainer = "/tmp/teste_restauracao.dump"

$backup = & (Join-Path $PSScriptRoot "backup.ps1") -Container $Container -Banco $Banco -Usuario $Usuario
if ($LASTEXITCODE -ne 0) { throw "O backup falhou." }
$arquivo = ($backup | Select-Object -Last 1) -replace "^Backup gerado: ", ""

docker cp $arquivo "${Container}:$caminhoNoContainer"
if ($LASTEXITCODE -ne 0) { throw "Não foi possível enviar o backup ao container." }

try {
    docker exec $Container dropdb -U $Usuario --if-exists $bancoTemporario | Out-Null
    docker exec $Container createdb -U $Usuario $bancoTemporario
    if ($LASTEXITCODE -ne 0) { throw "Não foi possível criar o banco temporário." }

    docker exec $Container pg_restore -U $Usuario -d $bancoTemporario --no-owner $caminhoNoContainer
    if ($LASTEXITCODE -ne 0) { throw "pg_restore falhou: o backup não restaurou." }

    $tabelas = @(
        "flyway_schema_history", "usuario", "registro_auditoria", "caixa", "movimentacao_caixa",
        "produto", "localidade", "venda", "venda_item", "despesa", "despesa_item",
        "repasse_entrega"
    )
    $divergencias = 0
    Write-Output ("{0,-24} {1,10} {2,12}" -f "Tabela", "Original", "Restaurado")
    foreach ($tabela in $tabelas) {
        # Tabelas que o banco ainda não tem (migrações não aplicadas) são puladas, não contadas.
        $existe = (docker exec $Container psql -U $Usuario -d $Banco -tAc "select to_regclass('public.$tabela') is not null").Trim()
        if ($existe -ne "t") {
            Write-Output ("{0,-24} {1}" -f $tabela, "(ainda não criada neste banco)")
            continue
        }
        $original = (docker exec $Container psql -U $Usuario -d $Banco -tAc "select count(*) from $tabela").Trim()
        $restaurado = (docker exec $Container psql -U $Usuario -d $bancoTemporario -tAc "select count(*) from $tabela").Trim()
        Write-Output ("{0,-24} {1,10} {2,12}" -f $tabela, $original, $restaurado)
        if ($original -ne $restaurado) { $divergencias++ }
    }
}
finally {
    docker exec $Container dropdb -U $Usuario --if-exists $bancoTemporario | Out-Null
    docker exec $Container rm -f $caminhoNoContainer | Out-Null
}

if ($divergencias -gt 0) { throw "Restauração NÃO confere: $divergencias tabela(s) diferente(s)." }
Write-Output "Restauração confere: todas as tabelas têm o mesmo número de linhas."
