Option Explicit

Dim shell, projectDir, command
Set shell = CreateObject("WScript.Shell")
projectDir = "E:\codex_workspace\cs饰品项目"
command = "powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File """ & projectDir & "\scripts\start-all.ps1""""

' 0 = 隐藏窗口，False = 不阻塞快捷方式。
shell.Run command, 0, False
