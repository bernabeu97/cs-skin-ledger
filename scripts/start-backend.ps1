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
$jar = Get-ChildItem (Join-Path $projectRoot 'backend\target') -Filter 'skin-ledger-*.jar' |
    Where-Object { $_.Name -notlike '*.original' } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1 -ExpandProperty FullName
if (-not $jar) { throw '未找到后端 JAR，请先运行 Maven package' }
Start-Process -FilePath "$env:JAVA_HOME\bin\java.exe" -ArgumentList "-jar", "`"$jar`"" -WorkingDirectory $projectRoot -WindowStyle Hidden
Write-Host 'backend started: http://localhost:8080 (log: work/backend.log)'
