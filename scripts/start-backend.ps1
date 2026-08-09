# 启动后端（自动读取 work/csqaq_token.txt 作为 CSQAQ_TOKEN，避免密钥入库）
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$env:JAVA_HOME = 'D:\Java\jdk-21'
$tokenFile = Join-Path $projectRoot 'work\csqaq_token.txt'
if (Test-Path $tokenFile) {
    $env:CSQAQ_TOKEN = (Get-Content -LiteralPath $tokenFile -Raw).Trim()
}
$jar = Join-Path $projectRoot 'backend\target\skin-ledger-0.1.0.jar'
Start-Process -FilePath "$env:JAVA_HOME\bin\java.exe" -ArgumentList "-jar", "`"$jar`"" -WorkingDirectory $projectRoot -WindowStyle Hidden
Write-Host 'backend started: http://localhost:8080 (log: work/backend.log)'