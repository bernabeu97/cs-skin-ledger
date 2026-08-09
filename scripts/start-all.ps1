# CS 饰品账本一键启动（MySQL + 后端 + 前端）
$ErrorActionPreference = 'SilentlyContinue'
$projectRoot = Split-Path -Parent $PSScriptRoot
$mysqlBin = 'D:\mysql\mysql-8.4.11-winx64\bin'
$mysqlData = 'D:\mysql\data'
$env:JAVA_HOME = 'D:\Java\jdk-21'

function Test-Port($port) {
    return [bool](Test-NetConnection -ComputerName localhost -Port $port -WarningAction SilentlyContinue -InformationLevel Quiet)
}

Write-Host '== CS 饰品账本启动中 ==' -ForegroundColor Cyan

# 1. MySQL
if (-not (Test-Port 3306)) {
    Write-Host '[1/3] 启动 MySQL ...'
    Start-Process -FilePath "$mysqlBin\mysqld.exe" -ArgumentList "--datadir=$mysqlData", '--port=3306' -WindowStyle Hidden
    for ($i = 0; $i -lt 30; $i++) {
        Start-Sleep -Seconds 1
        if (Test-Port 3306) { break }
    }
    if (Test-Port 3306) { Write-Host '      MySQL OK' -ForegroundColor Green } else { Write-Host '      MySQL 启动失败，请检查 D:\mysql' -ForegroundColor Red }
} else {
    Write-Host '[1/3] MySQL 已在运行'
}

# 2. 后端
if (-not (Test-Port 8080)) {
    Write-Host '[2/3] 启动后端 ...'
    & (Join-Path $PSScriptRoot 'start-backend.ps1')
    Start-Sleep -Seconds 3
    for ($i = 0; $i -lt 20; $i++) {
        if (Test-Port 8080) { break }
        Start-Sleep -Seconds 1
    }
    if (Test-Port 8080) { Write-Host '      后端 OK: http://localhost:8080' -ForegroundColor Green } else { Write-Host '      后端启动失败，查看 work/backend.log' -ForegroundColor Red }
} else {
    Write-Host '[2/3] 后端已在运行'
}

# 3. 前端
if (-not (Test-Port 5173)) {
    Write-Host '[3/3] 启动前端 ...'
    Start-Process -FilePath 'npm.cmd' -ArgumentList 'run', 'dev' -WorkingDirectory (Join-Path $projectRoot 'frontend') -WindowStyle Hidden
    for ($i = 0; $i -lt 30; $i++) {
        if (Test-Port 5173) { break }
        Start-Sleep -Seconds 1
    }
    if (Test-Port 5173) { Write-Host '      前端 OK: http://localhost:5173' -ForegroundColor Green } else { Write-Host '      前端启动失败' -ForegroundColor Red }
} else {
    Write-Host '[3/3] 前端已在运行'
}

# 打开页面
Start-Process 'http://localhost:5173'
Write-Host '== 启动完成，浏览器已打开 http://localhost:5173 ==' -ForegroundColor Cyan