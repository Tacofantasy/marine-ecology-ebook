$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot

Push-Location (Join-Path $projectRoot 'frontend')
try {
    & npm.cmd ci
    if ($LASTEXITCODE -ne 0) { throw 'Frontend dependency installation failed.' }
    & npm.cmd run build
    if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed.' }
} finally { Pop-Location }

Push-Location $projectRoot
try {
    & (Join-Path $PSScriptRoot 'with-jdk17.cmd') mvn -f backend/pom.xml clean package -Pdelivery
    if ($LASTEXITCODE -ne 0) { throw 'Delivery package build failed.' }
    Write-Host 'Delivery JAR: backend/target/marine-ebook-api-0.0.1-SNAPSHOT.jar'
    Write-Host 'Start from project root: scripts\with-jdk17.cmd java -jar backend/target/marine-ebook-api-0.0.1-SNAPSHOT.jar'
} finally { Pop-Location }
