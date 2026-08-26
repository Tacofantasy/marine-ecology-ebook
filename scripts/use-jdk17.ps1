<#
.SYNOPSIS
Displays the private Temurin JDK 17 configured for this project.

.DESCRIPTION
Use scripts\with-jdk17.cmd when a command needs to run with this JDK. The CMD
wrapper safely forwards options such as -v and --version.
#>
$projectRoot = Split-Path -Parent $PSScriptRoot
$jdkRoot = Join-Path $projectRoot '.tools\jdk17'
$jdkHome = Get-ChildItem -LiteralPath $jdkRoot -Directory -ErrorAction Stop |
    Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'bin\java.exe') } |
    Select-Object -First 1 -ExpandProperty FullName

if ([string]::IsNullOrWhiteSpace($jdkHome)) {
    throw "Project JDK 17 was not found under $jdkRoot"
}

$java = Join-Path $jdkHome 'bin\java.exe'
& $java --version
Write-Output "For project commands, use: .\scripts\with-jdk17.cmd <command>"
exit $LASTEXITCODE
