# 启动后端。首次启动会生成本机专用的 Token 加密密钥，文件位于 work（已被 Git 忽略）。
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$env:JAVA_HOME = 'D:\Java\jdk-21'
$tokenFile = Join-Path $projectRoot 'work\csqaq_token.txt'
$encryptionKeyFile = Join-Path $projectRoot 'work\app_encryption_key.txt'
if (-not (Test-Path -LiteralPath $encryptionKeyFile)) {
    $bytes = New-Object byte[] 32
    [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    [Convert]::ToBase64String($bytes) | Set-Content -LiteralPath $encryptionKeyFile -Encoding Ascii -NoNewline
}
$env:APP_ENCRYPTION_KEY = (Get-Content -LiteralPath $encryptionKeyFile -Raw).Trim()
if (Test-Path $tokenFile) {
    $env:CSQAQ_TOKEN = (Get-Content -LiteralPath $tokenFile -Raw).Trim()
}
$jar = Join-Path $projectRoot 'backend\target\skin-ledger-0.1.0.jar'
Start-Process -FilePath "$env:JAVA_HOME\bin\java.exe" -ArgumentList "-jar", "`"$jar`"" -WorkingDirectory $projectRoot -WindowStyle Hidden
Write-Host 'backend started: http://localhost:8080 (log: work/backend.log)'
